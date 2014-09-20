/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.executiongraph;

import static org.apache.flink.runtime.execution.ExecutionState.*;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.apache.flink.runtime.JobException;
import org.apache.flink.runtime.deployment.TaskDeploymentDescriptor;
import org.apache.flink.runtime.execution.ExecutionState;
import org.apache.flink.runtime.instance.AllocatedSlot;
import org.apache.flink.runtime.instance.Instance;
import org.apache.flink.runtime.jobmanager.scheduler.DefaultScheduler;
import org.apache.flink.runtime.jobmanager.scheduler.NoResourceAvailableException;
import org.apache.flink.runtime.jobmanager.scheduler.ScheduledUnit;
import org.apache.flink.runtime.jobmanager.scheduler.SlotAllocationFuture;
import org.apache.flink.runtime.jobmanager.scheduler.SlotAllocationFutureAction;
import org.apache.flink.runtime.taskmanager.TaskOperationResult;
import org.apache.flink.util.ExceptionUtils;
import org.slf4j.Logger;

import com.google.common.base.Preconditions;

/**
 * A single execution of a vertex. While an {@link ExecutionVertex} can be executed multiple times (for recovery,
 * or other re-computation), this class tracks the state of a single execution of that vertex and the resources.
 * 
 * NOTE ABOUT THE DESIGN RATIONAL:
 * 
 * In several points of the code, we need to deal with possible concurrent state changes and actions.
 * For example, while the call to deploy a task (send it to the TaskManager) happens, the task gets cancelled.
 * 
 * We could lock the entire portion of the code (decision to deploy, deploy, set state to running) such that
 * it is guaranteed that any "cancel command" will only pick up after deployment is done and that the "cancel
 * command" call will never overtake the deploying call.
 * 
 * This blocks the threads big time, because the remote calls may take long. Depending of their locking behavior, it
 * may even result in distributed deadlocks (unless carefully avoided). We therefore use atomic state updates and
 * occasional double-checking to ensure that the state after a completed call is as expected, and trigger correcting
 * actions if it is not. Many actions are also idempotent (like canceling).
 */
public class Execution {

	private static final AtomicReferenceFieldUpdater<Execution, ExecutionState> STATE_UPDATER =
			AtomicReferenceFieldUpdater.newUpdater(Execution.class, ExecutionState.class, "state");
	
	private static final Logger LOG = ExecutionGraph.LOG;
	
	private static final int NUM_CANCEL_CALL_TRIES = 3;
	
	// --------------------------------------------------------------------------------------------
	
	private final ExecutionVertex vertex;
	
	private final ExecutionAttemptID attemptId;
	
	private final long[] stateTimestamps;
	
	private final int attemptNumber;
	
	
	private volatile ExecutionState state = CREATED;
	
	private volatile AllocatedSlot assignedResource;  // once assigned, never changes
	
	private volatile Throwable failureCause;          // once assigned, never changes
	
	// --------------------------------------------------------------------------------------------
	
	public Execution(ExecutionVertex vertex, int attemptNumber, long startTimestamp) {
		Preconditions.checkNotNull(vertex);
		Preconditions.checkArgument(attemptNumber >= 0);
		
		this.vertex = vertex;
		this.attemptId = new ExecutionAttemptID();
		this.attemptNumber = attemptNumber;
		
		this.stateTimestamps = new long[ExecutionState.values().length];
		markTimestamp(ExecutionState.CREATED, startTimestamp);
	}
	
	// --------------------------------------------------------------------------------------------
	//   Properties
	// --------------------------------------------------------------------------------------------
	
	public ExecutionVertex getVertex() {
		return vertex;
	}
	
	public ExecutionAttemptID getAttemptId() {
		return attemptId;
	}

	public int getAttemptNumber() {
		return attemptNumber;
	}
	
	public ExecutionState getState() {
		return state;
	}
	
	public AllocatedSlot getAssignedResource() {
		return assignedResource;
	}
	
	public Throwable getFailureCause() {
		return failureCause;
	}
	
	public long getStateTimestamp(ExecutionState state) {
		return this.stateTimestamps[state.ordinal()];
	}
	
	public boolean isFinished() {
		return state == FINISHED || state == FAILED || state == CANCELED;
	}
	
	// --------------------------------------------------------------------------------------------
	//  Actions
	// --------------------------------------------------------------------------------------------
	
	/**
	 * NOTE: This method only throws exceptions if it is in an illegal state to be scheduled, or if the tasks needs
	 *       to be scheduled immediately and no resource is available. If the task is accepted by the schedule, any
	 *       error sets the vertex state to failed and triggers the recovery logic.
	 * 
	 * @param scheduler
	 * 
	 * @throws IllegalStateException Thrown, if the vertex is not in CREATED state, which is the only state that permits scheduling.
	 * @throws NoResourceAvailableException Thrown is no queued scheduling is allowed and no resources are currently available.
	 */
	public void scheduleForExecution(DefaultScheduler scheduler, boolean queued) throws NoResourceAvailableException {
		if (scheduler == null) {
			throw new NullPointerException();
		}
		
		if (transitionState(CREATED, SCHEDULED)) {
			
			// record that we were scheduled
			vertex.notifyStateTransition(attemptId, SCHEDULED, null);
			
			ScheduledUnit toSchedule = new ScheduledUnit(this, vertex.getJobVertex().getSlotSharingGroup());
		
			// IMPORTANT: To prevent leaks of cluster resources, we need to make sure that slots are returned
			//     in all cases where the deployment failed. we use many try {} finally {} clauses to assure that
			if (queued) {
				SlotAllocationFuture future = scheduler.scheduleQueued(toSchedule);
				
				future.setFutureAction(new SlotAllocationFutureAction() {
					@Override
					public void slotAllocated(AllocatedSlot slot) {
						try {
							deployToSlot(slot);
						}
						catch (Throwable t) {
							try {
								slot.releaseSlot();
							} finally {
								markFailed(t);
							}
						}
					}
				});
			}
			else {
				AllocatedSlot slot = scheduler.scheduleImmediately(toSchedule);
				try {
					deployToSlot(slot);
				}
				catch (Throwable t) {
					try {
						slot.releaseSlot();
					} finally {
						markFailed(t);
					}
				}
			}
		}
		else if (this.state == CANCELED) {
			// this can occur very rarely through heavy races. if the task was canceled, we do not
			// schedule it
			return;
		}
		else {
			throw new IllegalStateException("The vertex must be in CREATED state to be scheduled.");
		}
	}
	
	public void deployToSlot(final AllocatedSlot slot) throws JobException {
		// sanity checks
		if (slot == null) {
			throw new NullPointerException();
		}
		if (!slot.isAlive()) {
			throw new JobException("Traget slot for deployment is not alive.");
		}
		
		// make sure exactly one deployment call happens from the correct state
		// note: the transition from CREATED to DEPLOYING is for testing purposes only
		ExecutionState previous = this.state;
		if (previous == SCHEDULED || previous == CREATED) {
			if (!transitionState(previous, DEPLOYING)) {
				// race condition, someone else beat us to the deploying call.
				// this should actually not happen and indicates a race somewhere else
				throw new IllegalStateException("Cannot deploy task: Concurrent deployment call race.");
			}
			
			vertex.notifyStateTransition(attemptId, DEPLOYING, null);
		}
		else {
			// vertex may have been cancelled, or it was already scheduled
			throw new IllegalStateException("The vertex must be in CREATED or SCHEDULED state to be deployed. Found state " + previous);
		}
		
		try {
			// good, we are allowed to deploy
			if (!slot.setExecutedVertex(this)) {
				throw new JobException("Could not assign the ExecutionVertex to the slot " + slot);
			}
			this.assignedResource = slot;
			
			final TaskDeploymentDescriptor deployment = vertex.createDeploymentDescriptor(attemptId, slot);
			
			// register this execution at the execution graph, to receive callbacks
			vertex.getExecutionGraph().registerExecution(this);
			
			// we execute the actual deploy call in a concurrent action to prevent this call from blocking for long
			Runnable deployaction = new Runnable() {
	
				@Override
				public void run() {
					try {
						Instance instance = slot.getInstance();
						instance.checkLibraryAvailability(vertex.getJobId());
						
						TaskOperationResult result = instance.getTaskManagerProxy().submitTask(deployment);
						if (result == null) {
							markFailed(new Exception("Failed to deploy the task to slot " + slot + ": TaskOperationResult was null"));
						}
						else if (!result.getExecutionId().equals(attemptId)) {
							markFailed(new Exception("Answer execution id does not match the request execution id."));
						}
						else if (result.isSuccess()) {
							switchToRunning();
						}
						else {
							// deployment failed :(
							markFailed(new Exception("Failed to deploy the task " + getVertexWithAttempt() + " to slot " + slot + ": " + result.getDescription()));
						}
					}
					catch (Throwable t) {
						// some error occurred. fail the task
						markFailed(t);
					}
				}
			};
			
			vertex.execute(deployaction);
		}
		catch (Throwable t) {
			markFailed(t);
			ExceptionUtils.rethrow(t);
		}
	}
	
	
	public void cancel() {
		// depending on the previous state, we go directly to cancelled (no cancel call necessary)
		// -- or to canceling (cancel call needs to be sent to the task manager)
		
		// because of several possibly previous states, we need to again loop until we make a
		// successful atomic state transition
		while (true) {
			
			ExecutionState current = this.state;
			
			if (current == CANCELING || current == CANCELED) {
				// already taken care of, no need to cancel again
				return;
			}
				
			// these two are the common cases where we need to send a cancel call
			else if (current == RUNNING || current == DEPLOYING) {
				// try to transition to canceling, if successful, send the cancel call
				if (transitionState(current, CANCELING)) {
					vertex.notifyStateTransition(attemptId, CANCELING, null);
					sendCancelRpcCall();
					return;
				}
				// else: fall through the loop
			}
			
			else if (current == FINISHED || current == FAILED) {
				// nothing to do any more. finished failed before it could be cancelled.
				// in any case, the task is removed from the TaskManager already
				return;
			}
			else if (current == CREATED || current == SCHEDULED) {
				// from here, we can directly switch to cancelled, because the no task has been deployed
				if (transitionState(current, CANCELED)) {
					
					// we skip the canceling state. set the timestamp, for a consistent appearance
					markTimestamp(CANCELING, getStateTimestamp(CANCELED));
					vertex.notifyStateTransition(attemptId, CANCELED, null);
					return;
				}
				// else: fall through the loop
			}
			else {
				throw new IllegalStateException(current.name());
			}
		}
	}
	
	/**
	 * This method fails the vertex due to an external condition. The task will move to state FAILED.
	 * If the task was in state RUNNING or DEPLOYING before, it will send a cancel call to the TaskManager.
	 * 
	 * @param t The exception that caused the task to fail.
	 */
	public void fail(Throwable t) {
		if (processFail(t, false)) {
			if (LOG.isErrorEnabled()) {
				LOG.error("Task " + getVertexWithAttempt() + " was failed.", t);
			}
		}
	}
	
	// --------------------------------------------------------------------------------------------
	//   Callbacks
	// --------------------------------------------------------------------------------------------
	
	/**
	 * This method marks the task as failed, but will make no attempt to remove task execution from the task manager.
	 * It is intended for cases where the task is known not to be running, or then the TaskManager reports failure
	 * (in which case it has already removed the task).
	 * 
	 * @param t The exception that caused the task to fail.
	 */
	void markFailed(Throwable t) {
		// the call returns true if it actually made the state transition (was not already failed before, etc)
		if (processFail(t, true)) {
			if (LOG.isErrorEnabled()) {
				LOG.error("Task " + getVertexWithAttempt() + " failed.", t);
			}
		}
	}
	
	void markFinished() {
		// this call usually comes during RUNNING, but may also come while still in deploying (very fast tasks!)
		while (true) {
			ExecutionState current = this.state;
			
			if (current == RUNNING || current == DEPLOYING) {
			
				if (transitionState(current, FINISHED)) {
					try {
						vertex.notifyStateTransition(attemptId, FINISHED, null);
						vertex.executionFinished();
						return;
					}
					finally {
						vertex.getExecutionGraph().deregisterExecution(this);
						assignedResource.releaseSlot();
					}
				}
			}
			else {
				if (current == CANCELED || current == CANCELING || current == FAILED) {
					if (LOG.isDebugEnabled()) {
						LOG.debug("Task FINISHED, but concurrently went to state " + state);
					}
					return;
				}
				else {
					// this should not happen, we need to fail this
					markFailed(new Exception("Vertex received FINISHED message while being in state " + state));
					return;
				}
			}
		}
	}
	
	void cancelingComplete() {
		if (transitionState(CANCELING, CANCELED)) {
			try {
				vertex.executionCanceled();
				vertex.notifyStateTransition(attemptId, CANCELED, null);
			}
			finally {
				vertex.getExecutionGraph().deregisterExecution(this);
				assignedResource.releaseSlot();
			}
		}
		else {
			ExecutionState actualState = this.state;
			// failing in the meantime may happen and is no problem.
			// anything else is a serious problem !!!
			if (actualState != FAILED) {
				String message = String.format("Asynchronous race: Found state %s after successful cancel call.", state);
				LOG.error(message);
				vertex.getExecutionGraph().fail(new Exception(message));
			}
		}
	}
	
	// --------------------------------------------------------------------------------------------
	//  Internal Actions
	// --------------------------------------------------------------------------------------------
	
	private boolean processFail(Throwable t, boolean isCallback) {
		
		// damn, we failed. This means only that we keep our books and notify our parent JobExecutionVertex
		// the actual computation on the task manager is cleaned up by the TaskManager that noticed the failure
		
		// we may need to loop multiple times (in the presence of concurrent calls) in order to
		// atomically switch to failed 
		while (true) {
			ExecutionState current = this.state;
			
			if (current == FAILED) {
				// already failed. It is enough to remember once that we failed (its sad enough)
				return false;
			}
			
			if (current == CANCELED || (current == CANCELING && isCallback)) {
				// we are already aborting or are already aborted
				if (LOG.isDebugEnabled()) {
					LOG.debug(String.format("Ignoring transition of vertex %s to %s while being %s", 
							getVertexWithAttempt(), FAILED, current));
				}
				return false;
			}
			
			if (transitionState(current, FAILED)) {
				// success (in a manner of speaking)
				
				if (!isCallback && (current == RUNNING || current == DEPLOYING)) {
					if (LOG.isDebugEnabled()) {
						LOG.debug("Sending out cancel request, to remove task execution from TaskManager.");
					}
					
					try {
						if (assignedResource != null) {
							sendCancelRpcCall();
						}
					} catch (Throwable tt) {
						// no reason this should ever happen, but log it to be safe
						LOG.error("Error triggering cancel call while marking task as failed.", tt);
					}
				}
				
				try {
					this.failureCause = t;
					vertex.executionFailed(t);
					vertex.notifyStateTransition(attemptId, FAILED, t);
				}
				finally {
					if (assignedResource != null) {
						assignedResource.releaseSlot();
					}
					vertex.getExecutionGraph().deregisterExecution(this);
				}
				
				// leave the loop
				return true;
			}
		}
	}
	
	private void switchToRunning() {
		
		// transition state, the common case
		if (transitionState(DEPLOYING, RUNNING)) {
			vertex.notifyStateTransition(attemptId, RUNNING, null);
		}
		else {
			// something happened while the call was in progress.
			// it can mean:
			//  - canceling, while deployment was in progress. state is now canceling, or canceled, if the response overtook
			//  - finishing (execution and finished call overtook the deployment answer, which is possible and happens for fast tasks)
			//  - failed (execution, failure, and failure message overtook the deployment answer)
			
			ExecutionState currentState = this.state;
			
			if (currentState == FINISHED || currentState == CANCELED) {
				// do nothing, this is nice, the task was really fast
			}
			
			if (currentState == CANCELING || currentState == FAILED) {
				if (LOG.isDebugEnabled()) {
					LOG.debug(String.format("Concurrent canceling/failing of %s while deployment was in progress.", getVertexWithAttempt()));
				}
				sendCancelRpcCall();
			}
			else {
				String message = String.format("Concurrent unexpected state transition of task %s to %s while deployment was in progress.",
						getVertexWithAttempt(), currentState);
				
				if (LOG.isDebugEnabled()) {
					LOG.debug(message);
				}
				
				// undo the deployment
				sendCancelRpcCall();
				
				// record the failure
				markFailed(new Exception(message));
			}
		}
	}
	
	private void sendCancelRpcCall() {
		final AllocatedSlot slot = this.assignedResource;
		if (slot == null) {
			throw new IllegalStateException("Cannot cancel when task was not running or deployed.");
		}
		
		Runnable cancelAction = new Runnable() {
			
			@Override
			public void run() {
				Throwable exception = null;
				
				for (int triesLeft = NUM_CANCEL_CALL_TRIES; triesLeft > 0; --triesLeft) {
					
					try {
						// send the call. it may be that the task is not really there (asynchronous / overtaking messages)
						// in which case it is fine (the deployer catches it)
						TaskOperationResult result = slot.getInstance().getTaskManagerProxy().cancelTask(attemptId);
						
						if (!result.isSuccess()) {
							// the task was not found, which may be when the task concurrently finishes or fails, or
							// when the cancel call overtakes the deployment call
							if (LOG.isDebugEnabled()) {
								LOG.debug("Cancel task call did not find task. Probably RPC call race.");
							}
						}
						
						// in any case, we need not call multiple times, so we quit
						return;
					}
					catch (Throwable t) {
						if (exception == null) {
							exception = t;
						}
						LOG.error("Canceling vertex " + getVertexWithAttempt() + " failed (" + triesLeft + " tries left): " + t.getMessage() , t);
					}
				}
				
				// dang, utterly unsuccessful - the target node must be down, in which case the tasks are lost anyways
				fail(new Exception("Task could not be canceled.", exception));
			}
		};
		
		vertex.execute(cancelAction);
	}
	
	// --------------------------------------------------------------------------------------------
	//  Miscellaneous
	// --------------------------------------------------------------------------------------------
	
	private boolean transitionState(ExecutionState currentState, ExecutionState targetState) {
		if (STATE_UPDATER.compareAndSet(this, currentState, targetState)) {
			markTimestamp(targetState);
			return true;
		} else {
			return false;
		}
	}
	
	private void markTimestamp(ExecutionState state) {
		markTimestamp(state, System.currentTimeMillis());
	}
	
	private void markTimestamp(ExecutionState state, long timestamp) {
		this.stateTimestamps[state.ordinal()] = timestamp;
	}
	
	public String getVertexWithAttempt() {
		return vertex.getSimpleName() + " - execution #" + attemptNumber;
	}
	
	@Override
	public String toString() {
		return String.format("Attempt #%d (%s) @ %s - [%s]", attemptNumber, vertex.getSimpleName(),
				(assignedResource == null ? "(unassigned)" : assignedResource.toString()), state);
	}
}

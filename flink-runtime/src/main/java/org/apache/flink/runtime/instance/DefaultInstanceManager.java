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

package org.apache.flink.runtime.instance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.flink.configuration.ConfigConstants;
import org.apache.flink.configuration.GlobalConfiguration;

/**
 * A simple implementation of an {@link InstanceManager}.
 */
public class DefaultInstanceManager implements InstanceManager {

	private static final Logger LOG = LoggerFactory.getLogger(DefaultInstanceManager.class);

	// ------------------------------------------------------------------------
	// Fields
	// ------------------------------------------------------------------------

	/** Global lock */
	private final Object lock = new Object();

	/** Set of hosts known to run a task manager that are thus able to execute tasks (by ID). */
	private final Map<InstanceID, Instance> registeredHostsById;

	/** Set of hosts known to run a task manager that are thus able to execute tasks (by connection). */
	private final Map<InstanceConnectionInfo, Instance> registeredHostsByConnection;
	
	/** Set of hosts that were present once and have died */
	private final Set<InstanceConnectionInfo> deadHosts;

	/** Duration after which a task manager is considered dead if it did not send a heart-beat message. */
	private final long heartbeatTimeout;
	
	/** The total number of task slots that the system has */
	private int totalNumberOfAliveTaskSlots;

	/** Flag marking the system as shut down */
	private volatile boolean shutdown;

	// ------------------------------------------------------------------------
	// Constructor and set-up
	// ------------------------------------------------------------------------
	
	/**
	 * Creates an instance manager, using the global configuration value for maximum interval between heartbeats
	 * where a task manager is still considered alive.
	 */
	public DefaultInstanceManager() {
		this(1000 * GlobalConfiguration.getLong(
				ConfigConstants.JOB_MANAGER_DEAD_TASKMANAGER_TIMEOUT_KEY,
				ConfigConstants.DEFAULT_JOB_MANAGER_DEAD_TASKMANAGER_TIMEOUT));
	}
	
	public DefaultInstanceManager(long heartbeatTimeout) {
		this(heartbeatTimeout, heartbeatTimeout);
	}
	
	public DefaultInstanceManager(long heartbeatTimeout, long cleanupInterval) {
		if (heartbeatTimeout <= 0 || cleanupInterval <= 0) {
			throw new IllegalArgumentException("Heartbeat timeout and cleanup interval must be positive.");
		}
		
		this.registeredHostsById = new HashMap<InstanceID, Instance>();
		this.registeredHostsByConnection = new HashMap<InstanceConnectionInfo, Instance>();
		this.deadHosts = new HashSet<InstanceConnectionInfo>();
		this.heartbeatTimeout = heartbeatTimeout;

		new Timer(true).schedule(cleanupStaleMachines, cleanupInterval, cleanupInterval);
	}

	@Override
	public void shutdown() {
		synchronized (this.lock) {
			if (this.shutdown) {
				return;
			}
			this.shutdown = true;

			this.cleanupStaleMachines.cancel();

			for (Instance i : this.registeredHostsById.values()) {
				i.destroy();
			}
			
			this.registeredHostsById.clear();
			this.registeredHostsByConnection.clear();
			this.deadHosts.clear();
			this.totalNumberOfAliveTaskSlots = 0;
		}
	}

	@Override
	public boolean reportHeartBeat(InstanceID instanceId) {
		if (instanceId == null) {
			throw new IllegalArgumentException("InstanceID may not be null.");
		}
		
		synchronized (this.lock) {
			if (this.shutdown) {
				throw new IllegalStateException("InstanceManager is shut down.");
			}
			
			Instance host = registeredHostsById.get(instanceId);

			if (host == null){
				if (LOG.isDebugEnabled()) {
					LOG.debug("Received hearbeat from unknown TaskManager with instance ID " + instanceId.toString() + 
							" Possibly TaskManager was maked as dead (timed-out) earlier. " +
							"Reporting back that task manager is no longer known.");
				}
				return false;
			}

			host.reportHeartBeat();
			return true;
		}
	}

	@Override
	public InstanceID registerTaskManager(InstanceConnectionInfo instanceConnectionInfo, HardwareDescription resources, int numberOfSlots){
		synchronized(this.lock){
			if (this.shutdown) {
				throw new IllegalStateException("InstanceManager is shut down.");
			}
			
			Instance prior = registeredHostsByConnection.get(instanceConnectionInfo);
			if (prior != null) {
				LOG.error("Registration attempt from TaskManager with connection info " + instanceConnectionInfo + 
						". This connection is already registered under ID " + prior.getId());
				return null;
			}
			
			boolean wasDead = this.deadHosts.remove(instanceConnectionInfo);
			if (wasDead) {
				LOG.info("Registering TaskManager with connection info " + instanceConnectionInfo + 
						" which was marked as dead earlier because of a heart-beat timeout.");
			}

			InstanceID id = null;
			do {
				id = new InstanceID();
			} while (registeredHostsById.containsKey(id));
			
			
			Instance host = new Instance(instanceConnectionInfo, id, resources, numberOfSlots);
			
			registeredHostsById.put(id, host);
			registeredHostsByConnection.put(instanceConnectionInfo, host);
			
			totalNumberOfAliveTaskSlots += numberOfSlots;
			
			if (LOG.isInfoEnabled()) {
				LOG.info(String.format("Registered TaskManager at %s as %s. Current number of registered hosts is %d.",
						instanceConnectionInfo, id, registeredHostsById.size()));
			}

			host.reportHeartBeat();
			
			return id;
		}
	}

	@Override
	public int getNumberOfRegisteredTaskManagers() {
		return this.registeredHostsById.size();
	}

	@Override
	public int getTotalNumberOfSlots() {
		return this.totalNumberOfAliveTaskSlots;
	}
	
	@Override
	public Map<InstanceID, Instance> getAllRegisteredInstances() {
		return this.registeredHostsById;
	}
	
	// --------------------------------------------------------------------------------------------
	
	/**
	 * Periodic task that checks whether hosts have not sent their heart-beat
	 * messages and purges the hosts in this case.
	 */
	private final TimerTask cleanupStaleMachines = new TimerTask() {

		@Override
		public void run() {

			final long now = System.currentTimeMillis();
			final long timeout = DefaultInstanceManager.this.heartbeatTimeout;
			
			synchronized (DefaultInstanceManager.this.lock) {
				if (DefaultInstanceManager.this.shutdown) {
					return;
				}

				final Iterator<Map.Entry<InstanceID, Instance>> entries = registeredHostsById.entrySet().iterator();
				
				// check all hosts whether they did not send heart-beat messages.
				while (entries.hasNext()) {
					
					final Map.Entry<InstanceID, Instance> entry = entries.next();
					final Instance host = entry.getValue();
					
					if (!host.isStillAlive(now, timeout)) {
						
						// remove from the living
						entries.remove();
						registeredHostsByConnection.remove(host.getInstanceConnectionInfo());
						
						// add to the dead
						deadHosts.add(host.getInstanceConnectionInfo());
						
						host.markDied();
						
						totalNumberOfAliveTaskSlots -= host.getNumberOfSlots();
						
						LOG.info(String.format("TaskManager %s at %s did not report a heartbeat for %d msecs - marking as dead. Current number of registered hosts is %d.",
								host.getId(), host.getInstanceConnectionInfo(), heartbeatTimeout, registeredHostsById.size()));
					}
				}
			}
		}
	};
}

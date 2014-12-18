/*
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

package org.apache.flink.runtime.jobmanager

import java.io.File
import java.net.{InetSocketAddress}

import akka.actor._
import akka.pattern.Patterns
import com.google.common.base.Preconditions
import org.apache.flink.configuration.{ConfigConstants, GlobalConfiguration, Configuration}
import org.apache.flink.core.io.InputSplitAssigner
import org.apache.flink.runtime.accumulators.AccumulatorEvent
import org.apache.flink.runtime.blob.BlobServer
import org.apache.flink.runtime.io.network.ConnectionInfoLookupResponse
import org.apache.flink.runtime.messages.ExecutionGraphMessages.{JobNotFound, CurrentJobStatus,
JobStatusChanged}
import org.apache.flink.runtime.messages.JobResult
import org.apache.flink.runtime.messages.JobResult.{JobCancelResult, JobSubmissionResult}
import org.apache.flink.runtime.{JobException, ActorLogMessages}
import org.apache.flink.runtime.akka.AkkaUtils
import org.apache.flink.runtime.execution.librarycache.BlobLibraryCacheManager
import org.apache.flink.runtime.executiongraph.{ExecutionJobVertex, ExecutionGraph}
import org.apache.flink.runtime.instance.{InstanceManager}
import org.apache.flink.runtime.jobgraph.{JobStatus, JobID}
import org.apache.flink.runtime.jobmanager.accumulators.AccumulatorManager
import org.apache.flink.runtime.jobmanager.scheduler.{Scheduler => FlinkScheduler}
import org.apache.flink.runtime.messages.EventCollectorMessages._
import org.apache.flink.runtime.messages.JobManagerMessages._
import org.apache.flink.runtime.messages.RegistrationMessages._
import org.apache.flink.runtime.messages.TaskManagerMessages.{NextInputSplit, Heartbeat}
import org.apache.flink.runtime.profiling.ProfilingUtils
import org.apache.flink.util.StringUtils
import org.slf4j.LoggerFactory

import scala.collection.convert.WrapAsScala
import scala.concurrent.Future
import scala.concurrent.duration._

class JobManager(val archiveCount: Int, val profiling: Boolean, val recommendedPollingInterval:
Int, cleanupInterval: Long) extends Actor with ActorLogMessages with ActorLogging with WrapAsScala {

  import context._

  def profilerProps: Props = Props(classOf[JobManagerProfiler])

  def archiveProps: Props = Props(classOf[MemoryArchivist], archiveCount)

  def eventCollectorProps: Props = Props(classOf[EventCollector], recommendedPollingInterval)

  val profiler = profiling match {
    case true => Some(context.actorOf(profilerProps, JobManager.PROFILER_NAME))
    case false => None
  }

  // will be removed
  val archive = context.actorOf(archiveProps, JobManager.ARCHIVE_NAME)
  val eventCollector = context.actorOf(eventCollectorProps, JobManager.EVENT_COLLECTOR_NAME)

  val accumulatorManager = new AccumulatorManager(Math.min(1, archiveCount))
  val instanceManager = new InstanceManager()
  val scheduler = new FlinkScheduler()
  val libraryCacheManager = new BlobLibraryCacheManager(new BlobServer(), cleanupInterval)
  val webserver = null

  val currentJobs = scala.collection.concurrent.TrieMap[JobID, ExecutionGraph]()
  val jobTerminationListener = scala.collection.mutable.HashMap[JobID, Set[ActorRef]]()

  eventCollector ! RegisterArchiveListener(archive)

  instanceManager.addInstanceListener(scheduler)

  log.info(s"Started job manager. Waiting for incoming messages.")

  override def postStop(): Unit = {
    log.info(s"Stopping job manager ${self.path}.")
    instanceManager.shutdown()
    scheduler.shutdown()
    libraryCacheManager.shutdown()
  }

  override def receiveWithLogMessages: Receive = {
    case RegisterTaskManager(connectionInfo, hardwareInformation, numberOfSlots) => {
      val taskManager = sender()
      val instanceID = instanceManager.registerTaskManager(taskManager, connectionInfo,
        hardwareInformation, numberOfSlots)
      context.watch(taskManager);
      taskManager ! AcknowledgeRegistration(instanceID, libraryCacheManager.getBlobServerPort)
    }

    case RequestNumberRegisteredTaskManager => {
      sender() ! instanceManager.getNumberOfRegisteredTaskManagers
    }

    case RequestAvailableSlots => {
      sender() ! instanceManager.getTotalNumberOfSlots
    }

    case SubmitJob(jobGraph) => {
      var executionGraph: ExecutionGraph = null

      try {
        if (jobGraph == null) {
          JobSubmissionResult(JobResult.ERROR, "Submitted job is null.")
        } else {

          log.info(s"Received job ${jobGraph.getJobID} (${jobGraph.getName}}).")

          // Create the user code class loader
          libraryCacheManager.register(jobGraph.getJobID, jobGraph.getUserJarBlobKeys)

          executionGraph = currentJobs.getOrElseUpdate(jobGraph.getJobID(),
            new ExecutionGraph(jobGraph.getJobID,
            jobGraph.getName, jobGraph.getJobConfiguration, jobGraph.getUserJarBlobKeys))

          val userCodeLoader = libraryCacheManager.getClassLoader(jobGraph.getJobID)

          if (userCodeLoader == null) {
            throw new JobException("The user code class loader could not be initialized.")
          }

          log.debug(s"Running master initialization of job ${jobGraph.getJobID} (${jobGraph
            .getName}).")

          for (vertex <- jobGraph.getVertices) {
            val executableClass = vertex.getInvokableClassName
            if (executableClass == null || executableClass.length == 0) {
              throw new JobException(s"The vertex ${vertex.getID} (${vertex.getName}) has no " +
                s"invokable class.")
            }

            vertex.initializeOnMaster(userCodeLoader)
          }

          // topological sorting of the job vertices
          val sortedTopology = jobGraph.getVerticesSortedTopologicallyFromSources

          log.debug(s"Adding ${sortedTopology.size()} vertices from job graph ${jobGraph
            .getJobID} (${jobGraph.getName}).")

          executionGraph.attachJobGraph(sortedTopology)

          log.debug(s"Successfully created execution graph from job graph ${jobGraph.getJobID} " +
            s"(${jobGraph.getName}).")

          // should the job fail if a vertex cannot be deployed immediately (streams,
          // closed iterations)
          executionGraph.setQueuedSchedulingAllowed(jobGraph.getAllowQueuedScheduling)

          eventCollector ! RegisterJob(executionGraph, false, System.currentTimeMillis())

          executionGraph.registerJobStatusListener(self)

          log.info(s"Scheduling job ${jobGraph.getName}.")

          executionGraph.scheduleForExecution(scheduler)

          sender() ! JobSubmissionResult(JobResult.SUCCESS, null)
        }
      } catch {
        case t: Throwable =>
          log.error(t, "Job submission failed.")
          if(executionGraph != null){
            executionGraph.fail(t)

            val status = Patterns.ask(self, RequestJobStatusWhenTerminated, 10 second)
            status.onFailure{
              case _: Throwable => self ! JobStatusChanged(executionGraph, JobStatus.FAILED,
                s"Cleanup job ${jobGraph.getJobID}.")
            }
          }else {
            libraryCacheManager.unregister(jobGraph.getJobID)
            currentJobs.remove(jobGraph.getJobID)
          }

          sender() ! JobSubmissionResult(JobResult.ERROR, StringUtils.stringifyException(t))
      }
    }

    case CancelJob(jobID) => {
      log.info(s"Trying to cancel job with ID ${jobID}.")

      currentJobs.get(jobID) match {
        case Some(executionGraph) =>
          Future {
            executionGraph.cancel()
          }
          sender() ! JobCancelResult(JobResult.SUCCESS, null)
        case None =>
          log.info(s"No job found with ID ${jobID}.")
          sender() ! JobCancelResult(JobResult.ERROR, s"Cannot find job with ID ${jobID}")
      }
    }

    case UpdateTaskExecutionState(taskExecutionState) => {
      Preconditions.checkNotNull(taskExecutionState)

      currentJobs.get(taskExecutionState.getJobID) match {
        case Some(executionGraph) => sender() ! executionGraph.updateState(taskExecutionState)
        case None => log.error(s"Cannot find execution graph for ID ${taskExecutionState
          .getJobID} to change state to" +
          s" ${taskExecutionState.getExecutionState}.")
          sender() ! false
      }
    }

    case RequestNextInputSplit(jobID, vertexID) => {
      val nextInputSplit = currentJobs.get(jobID) match {
        case Some(executionGraph) => executionGraph.getJobVertex(vertexID) match {
          case vertex: ExecutionJobVertex => vertex.getSplitAssigner match {
            case splitAssigner: InputSplitAssigner => splitAssigner.getNextInputSplit(null)
            case _ =>
              log.error(s"No InputSplitAssigner for vertex ID ${vertexID}.")
              null
          }
          case _ =>
            log.error(s"Cannot find execution vertex for vertex ID ${vertexID}.")
            null
        }
        case None =>
          log.error(s"Cannot find execution graph for job ID ${jobID}.")
          null
      }

      sender() ! NextInputSplit(nextInputSplit)
    }

    case JobStatusChanged(executionGraph, newJobStatus, optionalMessage) => {
      val jobID = executionGraph.getJobID

      log.info(s"Status of job ${jobID} (${executionGraph.getJobName}) changed to " +
        s"${newJobStatus}${optionalMessage}.")

      if (Set(JobStatus.FINISHED, JobStatus.CANCELED, JobStatus.FAILED) contains newJobStatus) {
        // send final job status to job termination listeners
        jobTerminationListener.get(jobID) foreach {
          listeners =>
            listeners foreach {
              _ ! CurrentJobStatus(jobID, newJobStatus)
            }
        }
        currentJobs.remove(jobID)

        try {
          libraryCacheManager.unregister(jobID)
        } catch {
          case t: Throwable =>
            log.error(t, s"Could not properly unregister job ${jobID} form the library cache.")
        }
      }
    }

    case LookupConnectionInformation(connectionInformation, jobID, sourceChannelID) => {
      currentJobs.get(jobID) match {
        case Some(executionGraph) =>
          sender() ! ConnectionInformation(executionGraph.lookupConnectionInfoAndDeployReceivers
            (connectionInformation, sourceChannelID))
        case None =>
          log.error(s"Cannot find execution graph for job ID ${jobID}.")
          sender() ! ConnectionInformation(ConnectionInfoLookupResponse.createReceiverNotFound())
      }
    }

    case ReportAccumulatorResult(accumulatorEvent) => {
      accumulatorManager.processIncomingAccumulators(accumulatorEvent.getJobID,
        accumulatorEvent.getAccumulators
        (libraryCacheManager.getClassLoader(accumulatorEvent.getJobID)))
    }

    case RequestAccumulatorResult(jobID) => {
      sender() ! new AccumulatorEvent(jobID, accumulatorManager.getJobAccumulators(jobID))
    }

    case RegisterJobStatusListener(jobID) => {
      currentJobs.get(jobID) match {
        case Some(executionGraph) =>
          executionGraph.registerJobStatusListener(sender())
          sender() ! CurrentJobStatus(jobID, executionGraph.getState)
        case None =>
          log.warning(s"There is no running job with job ID ${jobID}.")
          sender() ! JobNotFound(jobID)
      }
    }

    case RequestJobStatusWhenTerminated(jobID) => {
      if (currentJobs.contains(jobID)) {
        val listeners = jobTerminationListener.getOrElse(jobID, Set())
        jobTerminationListener += jobID -> (listeners + sender())
      } else {
        eventCollector.tell(RequestJobStatus(jobID), sender())
      }
    }

    case RequestJobStatus(jobID) => {
      currentJobs.get(jobID) match {
        case Some(executionGraph) => sender() ! CurrentJobStatus(jobID, executionGraph.getState)
        case None => eventCollector.tell(RequestJobStatus(jobID), sender())
      }
    }

    case RequestRecentJobEvents => {
      eventCollector.tell(RequestRecentJobEvents, sender())
    }

    case msg: RequestJobProgress => {
      eventCollector forward msg
    }

    case RequestBlobManagerPort => {
      sender() ! libraryCacheManager.getBlobServerPort
    }

    case RequestPollingInterval => {
      sender() ! recommendedPollingInterval
    }

    case Heartbeat(instanceID) => {
      instanceManager.reportHeartBeat(instanceID)
    }

    case Terminated(taskManager) => {
      log.info(s"Task manager ${taskManager.path} terminated.")
      instanceManager.unregisterTaskManager(taskManager)
      context.unwatch(taskManager)
    }
  }
}

object JobManager {
  val LOG = LoggerFactory.getLogger(classOf[JobManager])
  val FAILURE_RETURN_CODE = 1
  val JOB_MANAGER_NAME = "jobmanager"
  val EVENT_COLLECTOR_NAME = "eventcollector"
  val ARCHIVE_NAME = "archive"
  val PROFILER_NAME = "profiler"

  def main(args: Array[String]): Unit = {
    val (hostname, port, configuration) = initialize(args)

    val (jobManagerSystem, _) = startActorSystemAndActor(hostname, port, configuration)
    jobManagerSystem.awaitTermination()
  }

  def initialize(args: Array[String]): (String, Int, Configuration) = {
    val parser = new scopt.OptionParser[JobManagerCLIConfiguration]("jobmanager") {
      head("flink jobmanager")
      opt[String]("configDir") action { (x, c) =>
        c.copy(configDir = x)
      } text ("Specify configuration directory.")
    }

    parser.parse(args, JobManagerCLIConfiguration()) map {
      config =>
        GlobalConfiguration.loadConfiguration(config.configDir)
        val configuration = GlobalConfiguration.getConfiguration()
        if (config.configDir != null && new File(config.configDir).isDirectory) {
          configuration.setString(ConfigConstants.FLINK_BASE_DIR_PATH_KEY, config.configDir + "/..")
        }

        val hostname = configuration.getString(ConfigConstants.JOB_MANAGER_IPC_ADDRESS_KEY, null)
        val port = configuration.getInteger(ConfigConstants.JOB_MANAGER_IPC_PORT_KEY,
          ConfigConstants.DEFAULT_JOB_MANAGER_IPC_PORT)

        (hostname, port, configuration)
    } getOrElse {
      LOG.error("CLI Parsing failed. Usage: " + parser.usage)
      sys.exit(FAILURE_RETURN_CODE)
    }
  }

  def startActorSystemAndActor(hostname: String, port: Int, configuration: Configuration):
  (ActorSystem, ActorRef) = {
    implicit val actorSystem = AkkaUtils.createActorSystem(hostname, port, configuration)
    (actorSystem, (startActor _).tupled(parseConfiguration(configuration)))
  }

  def parseConfiguration(configuration: Configuration): (Int, Boolean, Int, Long) = {
    val archiveCount = configuration.getInteger(ConfigConstants.JOB_MANAGER_WEB_ARCHIVE_COUNT,
      ConfigConstants.DEFAULT_JOB_MANAGER_WEB_ARCHIVE_COUNT)
    val profilingEnabled = configuration.getBoolean(ProfilingUtils.PROFILE_JOB_KEY, true)
    val recommendedPollingInterval = configuration.getInteger(ConfigConstants
      .JOBCLIENT_POLLING_INTERVAL_KEY,
      ConfigConstants.DEFAULT_JOBCLIENT_POLLING_INTERVAL)

    val cleanupInterval = configuration.getLong(ConfigConstants
      .LIBRARY_CACHE_MANAGER_CLEANUP_INTERVAL,
      ConfigConstants.DEFAULT_LIBRARY_CACHE_MANAGER_CLEANUP_INTERVAL) * 1000

    (archiveCount, profilingEnabled, recommendedPollingInterval, cleanupInterval)
  }

  def startActorWithConfiguration(configuration: Configuration)(implicit actorSystem:
  ActorSystem) = {
    (startActor _).tupled(parseConfiguration(configuration))
  }

  def startActor(archiveCount: Int, profilingEnabled: Boolean, recommendedPollingInterval: Int,
                 cleanupInterval: Long)
                (implicit actorSystem:
  ActorSystem): ActorRef = {
    actorSystem.actorOf(Props(classOf[JobManager], archiveCount, profilingEnabled,
      recommendedPollingInterval, cleanupInterval),
      JOB_MANAGER_NAME)
  }

  def getAkkaURL(address: String): String = {
    s"akka.tcp://flink@${address}/user/jobmanager"
  }

  def getProfiler(jobManager: ActorRef)(implicit system: ActorSystem): ActorRef = {
    AkkaUtils.getChild(jobManager, PROFILER_NAME)
  }

  def getEventCollector(jobManager: ActorRef)(implicit system: ActorSystem): ActorRef = {
    AkkaUtils.getChild(jobManager, EVENT_COLLECTOR_NAME)
  }

  def getArchivist(jobManager: ActorRef)(implicit system: ActorSystem): ActorRef = {
    AkkaUtils.getChild(jobManager, ARCHIVE_NAME)
  }

  def getJobManager(address: InetSocketAddress): ActorRef = {
    AkkaUtils.getReference(getAkkaURL(address.getHostName + ":" + address.getPort))
  }
}

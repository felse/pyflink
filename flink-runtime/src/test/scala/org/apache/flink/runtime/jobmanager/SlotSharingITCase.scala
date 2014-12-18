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

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.apache.flink.runtime.execution.librarycache.LibraryCacheManager
import org.apache.flink.runtime.jobgraph.{JobStatus, JobGraph, DistributionPattern,
AbstractJobVertex}
import org.apache.flink.runtime.jobmanager.Tasks.{AgnosticBinaryReceiver, Receiver}
import org.apache.flink.runtime.jobmanager.scheduler.SlotSharingGroup
import org.apache.flink.runtime.messages.ExecutionGraphMessages.JobStatusFound
import org.apache.flink.runtime.messages.JobManagerMessages.{RequestJobStatusWhenTerminated,
SubmitJob}
import org.apache.flink.runtime.messages.JobResult
import org.apache.flink.runtime.messages.JobResult.JobSubmissionResult
import org.apache.flink.runtime.taskmanager.TaskManagerTest.Sender
import org.apache.flink.runtime.testingUtils.TestingUtils
import org.scalatest.{Matchers, WordSpecLike, BeforeAndAfterAll}
import scala.concurrent.duration._

class SlotSharingITCase(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with
WordSpecLike with Matchers with BeforeAndAfterAll {
  def this() = this(ActorSystem("TestingActorSystem", TestingUtils.testConfig))

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "The JobManager actor" must {
    "support slot sharing for forward job" in {
      val num_tasks = 31

      val sender = new AbstractJobVertex("Sender")
      val receiver = new AbstractJobVertex("Receiver")

      sender.setInvokableClass(classOf[Sender])
      receiver.setInvokableClass(classOf[Receiver])

      sender.setParallelism(num_tasks)
      receiver.setParallelism(num_tasks)

      receiver.connectNewDataSetAsInput(sender, DistributionPattern.POINTWISE)

      val sharingGroup = new SlotSharingGroup(sender.getID, receiver.getID)
      sender.setSlotSharingGroup(sharingGroup)
      receiver.setSlotSharingGroup(sharingGroup)

      val jobGraph = new JobGraph("Pointwise Job", sender, receiver)

      val cluster = TestingUtils.startTestingCluster(num_tasks)
      val jm = cluster.getJobManager

      try {
        LibraryCacheManager.register(jobGraph.getJobID, Array[String]())

        within(1 second) {
          jm ! SubmitJob(jobGraph)
          expectMsg(new JobSubmissionResult(JobResult.SUCCESS, null))

          jm ! RequestJobStatusWhenTerminated(jobGraph.getJobID)
          expectMsg(JobStatusFound(jobGraph.getJobID, JobStatus.FINISHED))
        }
      } finally {
        cluster.stop()
      }
    }

    /**
     * This job runs in N slots with 2 * N senders and N receivers. Unless slot sharing is used,
     * it cannot complete.
     */
    "support jobs with two inputs and slot sharing" in {
      val num_tasks = 11

      val sender1 = new AbstractJobVertex("Sender1")
      val sender2 = new AbstractJobVertex("Sender2")
      val receiver = new AbstractJobVertex("Receiver")

      sender1.setInvokableClass(classOf[Sender])
      sender2.setInvokableClass(classOf[Sender])
      receiver.setInvokableClass(classOf[AgnosticBinaryReceiver])

      sender1.setParallelism(num_tasks)
      sender2.setParallelism(num_tasks)
      receiver.setParallelism(num_tasks)

      val sharingGroup = new SlotSharingGroup(sender1.getID, sender2.getID, receiver.getID)
      sender1.setSlotSharingGroup(sharingGroup)
      sender2.setSlotSharingGroup(sharingGroup)
      receiver.setSlotSharingGroup(sharingGroup)

      receiver.connectNewDataSetAsInput(sender1, DistributionPattern.POINTWISE)
      receiver.connectNewDataSetAsInput(sender2, DistributionPattern.BIPARTITE)

      val jobGraph = new JobGraph("Bipartite job", sender1, sender2, receiver)

      val cluster = TestingUtils.startTestingCluster(num_tasks)
      val jm = cluster.getJobManager
      try {
        LibraryCacheManager.register(jobGraph.getJobID, Array[String]())

        within(1 second) {
          jm ! SubmitJob(jobGraph)
          expectMsg(JobSubmissionResult(JobResult.SUCCESS, null))

          jm ! RequestJobStatusWhenTerminated(jobGraph.getJobID)
          expectMsg(JobStatusFound(jobGraph.getJobID, JobStatus.FINISHED))
        }
      } finally {
        cluster.stop()
      }

    }
  }
}

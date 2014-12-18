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

package org.apache.flink.runtime.messages

import org.apache.flink.runtime.execution.{ExecutionState}
import org.apache.flink.runtime.executiongraph.{ExecutionAttemptID, ExecutionGraph}
import org.apache.flink.runtime.jobgraph.{JobStatus, JobVertexID, JobID}

object ExecutionGraphMessages {

  case class ExecutionStateChanged(jobID: JobID, vertexID: JobVertexID, subtask: Int,
                                   executionID: ExecutionAttemptID,
                                   newExecutionState: ExecutionState,
                                   timestamp: Long, optionalMessage: String)


  sealed trait JobStatusResponse {
    def jobID: JobID
  };

  case class CurrentJobStatus(jobID: JobID, status: JobStatus) extends JobStatusResponse
  case class JobNotFound(jobID: JobID) extends JobStatusResponse
  case class JobStatusChanged(jobID: JobID, newJobStatus: JobStatus, timestamp: Long,
                              optionalMessage: String)

}

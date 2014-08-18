/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.flink.streaming.api.datastream;

import org.apache.flink.streaming.partitioner.ForwardPartitioner;

/**
 * The iterative data stream represents the start of an iteration in a
 * {@link DataStream}.
 * 
 * @param <T>
 *            Type of the DataStream
 */
public class IterativeDataStream<T> extends SingleOutputStreamOperator<T, IterativeDataStream<T>> {

	static Integer iterationCount = 0;
	protected Integer iterationID;

	protected IterativeDataStream(DataStream<T> dataStream) {
		super(dataStream);
		iterationID = iterationCount;
		iterationCount++;
	}

	protected IterativeDataStream(DataStream<T> dataStream, Integer iterationID) {
		super(dataStream);
		this.iterationID = iterationID;
	}

	/**
	 * Closes the iteration. This method defines the end of the iterative
	 * program part. By default the DataStream represented by the parameter will
	 * be fed back to the iteration head, however the user can explicitly select
	 * which tuples should be iterated by {@code directTo(OutputSelector)}.
	 * Tuples directed to 'iterate' will be fed back to the iteration head.
	 * 
	 * @param iterationResult
	 *            The data stream that can be fed back to the next iteration.
	 * 
	 */
	public DataStream<T> closeWith(DataStream<T> iterationResult) {
		return closeWith(iterationResult, null);
	}

	/**
	 * Closes the iteration. This method defines the end of the iterative
	 * program part. By default the DataStream represented by the parameter will
	 * be fed back to the iteration head, however the user can explicitly select
	 * which tuples should be iterated by {@code directTo(OutputSelector)}.
	 * Tuples directed to 'iterate' will be fed back to the iteration head.
	 * 
	 * @param iterationTail
	 *            The data stream that can be fed back to the next iteration.
	 * @param iterationName
	 *            Name of the iteration edge (backward edge to iteration head)
	 *            when used with directed emits
	 * 
	 */
	public <R> DataStream<T> closeWith(DataStream<T> iterationTail, String iterationName) {
		DataStream<R> returnStream = new DataStreamSink<R>(environment, "iterationSink");

		jobGraphBuilder.addIterationSink(returnStream.getId(), iterationTail.getId(),
				iterationID.toString(), iterationTail.getParallelism(), iterationName);

		jobGraphBuilder.setIterationSourceParallelism(iterationID.toString(),
				iterationTail.getParallelism());

		if (iterationTail instanceof ConnectedDataStream) {
			for (DataStream<T> stream : ((ConnectedDataStream<T>) iterationTail).connectedStreams) {
				String inputID = stream.getId();
				jobGraphBuilder.setEdge(inputID, returnStream.getId(), new ForwardPartitioner<T>(),
						0);
			}
		} else {
			jobGraphBuilder.setEdge(iterationTail.getId(), returnStream.getId(),
					new ForwardPartitioner<T>(), 0);
		}

		return iterationTail;
	}

	@Override
	protected IterativeDataStream<T> copy() {
		return new IterativeDataStream<T>(this, iterationID);
	}
}
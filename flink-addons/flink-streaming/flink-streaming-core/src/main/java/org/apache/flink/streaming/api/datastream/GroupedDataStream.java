/**
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
 */

package org.apache.flink.streaming.api.datastream;

import org.apache.flink.api.common.functions.GroupReduceFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.java.functions.RichGroupReduceFunction;
import org.apache.flink.api.java.functions.RichReduceFunction;
import org.apache.flink.streaming.api.function.aggregation.AggregationFunction;
import org.apache.flink.streaming.api.invokable.operator.GroupedBatchGroupReduceInvokable;
import org.apache.flink.streaming.api.invokable.operator.GroupReduceInvokable;
import org.apache.flink.streaming.api.invokable.operator.GroupedWindowGroupReduceInvokable;
import org.apache.flink.streaming.api.invokable.util.DefaultTimeStamp;
import org.apache.flink.streaming.api.invokable.util.TimeStamp;
import org.apache.flink.streaming.partitioner.StreamPartitioner;
import org.apache.flink.streaming.util.serialization.FunctionTypeWrapper;

/**
 * A GroupedDataStream represents a {@link DataStream} which has been
 * partitioned by the given key in the values. Operators like {@link #reduce},
 * {@link #batchReduce} etc. can be applied on the {@link GroupedDataStream} to
 * get additional functionality by the grouping.
 *
 * @param <OUT>
 *            The output type of the {@link GroupedDataStream}.
 */
public class GroupedDataStream<OUT> extends DataStream<OUT> {

	int keyPosition;

	protected GroupedDataStream(DataStream<OUT> dataStream, int keyPosition) {
		super(dataStream.partitionBy(keyPosition));
		this.keyPosition = keyPosition;
	}

	protected GroupedDataStream(GroupedDataStream<OUT> dataStream) {
		super(dataStream);
		this.keyPosition = dataStream.keyPosition;
	}

	/**
	 * Applies a reduce transformation on the grouped data stream grouped on by
	 * the given key position. The {@link ReduceFunction} will receive input
	 * values based on the key value. Only input values with the same key will
	 * go to the same reducer.The user can also extend
	 * {@link RichReduceFunction} to gain access to other features provided by
	 * the {@link RichFuntion} interface.
	 * 
	 * @param reducer
	 *            The {@link ReduceFunction} that will be called for every
	 *            element of the input values with the same key.
	 * @return The transformed DataStream.
	 */
	public SingleOutputStreamOperator<OUT, ?> reduce(ReduceFunction<OUT> reducer) {
		return addFunction("groupReduce", reducer, new FunctionTypeWrapper<OUT>(reducer,
				ReduceFunction.class, 0), new FunctionTypeWrapper<OUT>(reducer,
				ReduceFunction.class, 0), new GroupReduceInvokable<OUT>(reducer, keyPosition));
	}

	/**
	 * Applies a group reduce transformation on preset chunks of the grouped
	 * data stream. The {@link GroupReduceFunction} will receive input values
	 * based on the key value. Only input values with the same key will go to
	 * the same reducer.When the reducer has ran for all the values in the
	 * batch, the batch is slid forward.The user can also extend
	 * {@link RichGroupReduceFunction} to gain access to other features provided
	 * by the {@link RichFuntion} interface.
	 * 
	 * 
	 * @param reducer
	 *            The {@link GroupReduceFunction} that will be called for every
	 *            element of the input values with the same key.
	 * @param batchSize
	 *            The size of the data stream chunk (the number of values in the
	 *            batch).
	 * @return The transformed {@link DataStream}.
	 */
	public <R> SingleOutputStreamOperator<R, ?> batchReduce(GroupReduceFunction<OUT, R> reducer,
			int batchSize) {
		return batchReduce(reducer, batchSize, batchSize);
	}

	/**
	 * Applies a group reduce transformation on preset chunks of the grouped
	 * data stream in a sliding window fashion. The {@link GroupReduceFunction}
	 * will receive input values based on the key value. Only input values with
	 * the same key will go to the same reducer. When the reducer has ran for
	 * all the values in the batch, the batch is slid forward. The user can also
	 * extend {@link RichGroupReduceFunction} to gain access to other features
	 * provided by the {@link RichFuntion} interface.
	 * 
	 * @param reducer
	 *            The {@link GroupReduceFunction} that will be called for every
	 *            element of the input values with the same key.
	 * @param batchSize
	 *            The size of the data stream chunk (the number of values in the
	 *            batch).
	 * @param slideSize
	 *            The number of values the batch is slid by.
	 * @return The transformed {@link DataStream}.
	 */
	public <R> SingleOutputStreamOperator<R, ?> batchReduce(GroupReduceFunction<OUT, R> reducer,
			long batchSize, long slideSize) {

		return addFunction("batchReduce", reducer, new FunctionTypeWrapper<OUT>(reducer,
				GroupReduceFunction.class, 0), new FunctionTypeWrapper<R>(reducer,
				GroupReduceFunction.class, 1), new GroupedBatchGroupReduceInvokable<OUT, R>(reducer,
				batchSize, slideSize, keyPosition));
	}

	/**
	 * Applies a group reduce transformation on preset "time" chunks of the
	 * grouped data stream. The {@link GroupReduceFunction} will receive input
	 * values based on the key value. Only input values with the same key will
	 * go to the same reducer.When the reducer has ran for all the values in the
	 * batch, the window is shifted forward. The user can also extend
	 * {@link RichGroupReduceFunction} to gain access to other features provided
	 * by the {@link RichFuntion} interface.
	 * 
	 * 
	 * @param reducer
	 *            The GroupReduceFunction that is called for each time window.
	 * @param windowSize
	 *            SingleOutputStreamOperator The time window to run the reducer
	 *            on, in milliseconds.
	 * @return The transformed DataStream.
	 */
	public <R> SingleOutputStreamOperator<R, ?> windowReduce(GroupReduceFunction<OUT, R> reducer,
			long windowSize) {
		return windowReduce(reducer, windowSize, windowSize);
	}

	/**
	 * Applies a group reduce transformation on preset "time" chunks of the
	 * grouped data stream in a sliding window fashion. The
	 * {@link GroupReduceFunction} will receive input values based on the key
	 * value. Only input values with the same key will go to the same reducer.
	 * When the reducer has ran for all the values in the batch, the window is
	 * shifted forward. The user can also extend {@link RichGroupReduceFunction}
	 * to gain access to other features provided by the {@link RichFuntion}
	 * interface.
	 *
	 * @param reducer
	 *            The GroupReduceFunction that is called for each time window.
	 * @param windowSize
	 *            SingleOutputStreamOperator The time window to run the reducer
	 *            on, in milliseconds.
	 * @param slideInterval
	 *            The time interval the batch is slid by.
	 * @return The transformed DataStream.
	 */
	public <R> SingleOutputStreamOperator<R, ?> windowReduce(GroupReduceFunction<OUT, R> reducer,
			long windowSize, long slideInterval) {
		return windowReduce(reducer, windowSize, slideInterval, new DefaultTimeStamp<OUT>());
	}

	/**
	 * Applies a group reduce transformation on preset "time" chunks of the
	 * grouped data stream in a sliding window fashion. The
	 * {@link GroupReduceFunction} will receive input values based on the key
	 * value. Only input values with the same key will go to the same reducer.
	 * When the reducer has ran for all the values in the batch, the window is
	 * shifted forward. The time is determined by a user-defined timestamp. The
	 * user can also extend {@link RichGroupReduceFunction} to gain access to
	 * other features provided by the {@link RichFuntion} interface.
	 *
	 * @param reducer
	 *            The GroupReduceFunction that is called for each time window.
	 * @param windowSize
	 *            SingleOutputStreamOperator The time window to run the reducer
	 *            on, in milliseconds.
	 * @param slideInterval
	 *            The time interval the batch is slid by.
	 * @param timestamp
	 *            Timestamp function to retrieve a timestamp from an element.
	 * @return The transformed DataStream.
	 */
	public <R> SingleOutputStreamOperator<R, ?> windowReduce(GroupReduceFunction<OUT, R> reducer,
			long windowSize, long slideInterval, TimeStamp<OUT> timestamp) {
		return addFunction("batchReduce", reducer, new FunctionTypeWrapper<OUT>(reducer,
				GroupReduceFunction.class, 0), new FunctionTypeWrapper<R>(reducer,
				GroupReduceFunction.class, 1), new GroupedWindowGroupReduceInvokable<OUT, R>(reducer,
				windowSize, slideInterval, keyPosition, timestamp));
	}

	/**
	 * Applies an aggregation that sums the grouped data stream at the given
	 * position, grouped by the given key position. Input values with the same
	 * key will be summed.
	 * 
	 * @param positionToSum
	 *            The position in the data point to sum
	 * @return The transformed DataStream.
	 */
	public SingleOutputStreamOperator<OUT, ?> sum(final int positionToSum) {
		return super.sum(positionToSum);
	}

	/**
	 * Applies an aggregation that gives the minimum of the grouped data stream
	 * at the given position, grouped by the given key position. Input values
	 * with the same key will be minimized.
	 * 
	 * @param positionToMin
	 *            The position in the data point to minimize
	 * @return The transformed DataStream.
	 */
	public SingleOutputStreamOperator<OUT, ?> min(final int positionToMin) {
		return super.min(positionToMin);
	}

	/**
	 * Applies an aggregation that gives the maximum of the grouped data stream
	 * at the given position, grouped by the given key position. Input values
	 * with the same key will be maximized.
	 * 
	 * @param positionToMax
	 *            The position in the data point to maximize
	 * @return The transformed DataStream.
	 */
	public SingleOutputStreamOperator<OUT, ?> max(final int positionToMax) {
		return super.max(positionToMax);
	}

	@Override
	protected SingleOutputStreamOperator<OUT, ?> aggregate(AggregationFunction<OUT> aggregate) {

		GroupReduceInvokable<OUT> invokable = new GroupReduceInvokable<OUT>(aggregate, keyPosition);

		SingleOutputStreamOperator<OUT, ?> returnStream = addFunction("groupReduce", aggregate,
				null, null, invokable);

		this.jobGraphBuilder.setTypeWrappersFrom(getId(), returnStream.getId());
		return returnStream;
	}

	@Override
	protected DataStream<OUT> setConnectionType(StreamPartitioner<OUT> partitioner) {
		System.out.println("Setting the partitioning after groupBy can affect the grouping");
		return super.setConnectionType(partitioner);
	}

	@Override
	protected GroupedDataStream<OUT> copy() {
		return new GroupedDataStream<OUT>(this);
	}
}

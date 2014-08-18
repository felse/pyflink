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

package org.apache.flink.streaming.api;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.SerializationException;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.flink.api.common.functions.AbstractRichFunction;
import org.apache.flink.api.java.functions.RichFilterFunction;
import org.apache.flink.api.java.functions.RichFlatMapFunction;
import org.apache.flink.api.java.functions.RichGroupReduceFunction;
import org.apache.flink.api.java.functions.RichMapFunction;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.streaming.api.collector.OutputSelector;
import org.apache.flink.streaming.api.function.co.CoMapFunction;
import org.apache.flink.streaming.api.function.sink.PrintSinkFunction;
import org.apache.flink.streaming.api.function.sink.SinkFunction;
import org.apache.flink.streaming.api.function.sink.WriteFormatAsCsv;
import org.apache.flink.streaming.api.function.sink.WriteFormatAsText;
import org.apache.flink.streaming.api.function.sink.WriteSinkFunctionByBatches;
import org.apache.flink.streaming.api.function.sink.WriteSinkFunctionByMillis;
import org.apache.flink.streaming.api.invokable.SinkInvokable;
import org.apache.flink.streaming.api.invokable.UserTaskInvokable;
import org.apache.flink.streaming.api.invokable.operator.BatchReduceInvokable;
import org.apache.flink.streaming.api.invokable.operator.FilterInvokable;
import org.apache.flink.streaming.api.invokable.operator.FlatMapInvokable;
import org.apache.flink.streaming.api.invokable.operator.MapInvokable;
import org.apache.flink.streaming.api.invokable.operator.WindowReduceInvokable;
import org.apache.flink.streaming.api.invokable.operator.co.CoInvokable;
import org.apache.flink.streaming.api.invokable.operator.co.CoMapInvokable;
import org.apache.flink.streaming.partitioner.BroadcastPartitioner;
import org.apache.flink.streaming.partitioner.DistributePartitioner;
import org.apache.flink.streaming.partitioner.FieldsPartitioner;
import org.apache.flink.streaming.partitioner.ForwardPartitioner;
import org.apache.flink.streaming.partitioner.ShufflePartitioner;
import org.apache.flink.streaming.partitioner.StreamPartitioner;
import org.apache.flink.streaming.util.serialization.FunctionTypeWrapper;
import org.apache.flink.streaming.util.serialization.TypeSerializerWrapper;

/**
 * A DataStream represents a stream of elements of the same type. A DataStream
 * can be transformed into another DataStream by applying a transformation as
 * for example
 * <ul>
 * <li>{@link DataStream#map},</li>
 * <li>{@link DataStream#filter}, or</li>
 * <li>{@link DataStream#batchReduce}.</li>
 * </ul>
 * 
 * @param <T>
 *            The type of the DataStream, i.e., the type of the elements of the
 *            DataStream.
 */
public class DataStream<T> {

	protected static Integer counter = 0;
	protected final StreamExecutionEnvironment environment;
	protected String id;
	protected int degreeOfParallelism;
	protected String userDefinedName;
	protected StreamPartitioner<T> partitioner;
	protected List<DataStream<T>> connectedStreams;

	protected JobGraphBuilder jobGraphBuilder;

	/**
	 * Create a new {@link DataStream} in the given execution environment with
	 * partitioning set to forward by default.
	 * 
	 * @param environment
	 *            StreamExecutionEnvironment
	 * @param operatorType
	 *            The type of the operator in the component
	 */
	protected DataStream(StreamExecutionEnvironment environment, String operatorType) {
		if (environment == null) {
			throw new NullPointerException("context is null");
		}

		// TODO add name based on component number an preferable sequential id
		counter++;
		this.id = operatorType + "-" + counter.toString();
		this.environment = environment;
		this.degreeOfParallelism = environment.getDegreeOfParallelism();
		this.jobGraphBuilder = environment.getJobGraphBuilder();
		this.partitioner = new ForwardPartitioner<T>();
		this.connectedStreams = new ArrayList<DataStream<T>>();
		this.connectedStreams.add(this.copy());
	}

	/**
	 * Create a new DataStream by creating a copy of another DataStream
	 * 
	 * @param dataStream
	 *            The DataStream that will be copied.
	 */
	protected DataStream(DataStream<T> dataStream) {
		this.environment = dataStream.environment;
		this.id = dataStream.id;
		this.degreeOfParallelism = dataStream.degreeOfParallelism;
		this.userDefinedName = dataStream.userDefinedName;
		this.partitioner = dataStream.partitioner;
		this.jobGraphBuilder = dataStream.jobGraphBuilder;
		this.connectedStreams = new ArrayList<DataStream<T>>();
		for (DataStream<T> stream : dataStream.connectedStreams) {
			this.connectedStreams.add(stream.copy());
		}

	}

	/**
	 * Creates a copy of the DataStream
	 * 
	 * @return The copy
	 */
	protected DataStream<T> copy() {
		return new DataStream<T>(this);
	}

	/**
	 * Partitioning strategy on the stream.
	 */
	public static enum ConnectionType {
		SHUFFLE, BROADCAST, FIELD, FORWARD, DISTRIBUTE
	}

	/**
	 * Returns the ID of the {@link DataStream}.
	 * 
	 * @return ID of the DataStream
	 */
	public String getId() {
		return id;
	}

	/**
	 * Gets the degree of parallelism for this operator.
	 * 
	 * @return The parallelism set for this operator.
	 */
	public int getParallelism() {
		return this.degreeOfParallelism;
	}

	/**
	 * Sets the degree of parallelism for this operator. The degree must be 1 or
	 * more.
	 * 
	 * @param dop
	 *            The degree of parallelism for this operator.
	 * @return The operator with set degree of parallelism.
	 */
	public DataStream<T> setParallelism(int dop) {
		if (dop < 1) {
			throw new IllegalArgumentException("The parallelism of an operator must be at least 1.");
		}
		this.degreeOfParallelism = dop;
	
		jobGraphBuilder.setParallelism(id, degreeOfParallelism);
	
		return this;
	}

	/**
	 * Sets the mutability of the operator represented by the DataStream. If the
	 * operator is set to mutable, the tuples received in the user defined
	 * functions, will be reused after the function call. Setting an operator to
	 * mutable greatly reduces garbage collection overhead and thus scalability.
	 * 
	 * @param isMutable
	 *            The mutability of the operator.
	 * @return The DataStream with mutability set.
	 */
	public DataStream<T> setMutability(boolean isMutable) {
		jobGraphBuilder.setMutability(id, isMutable);
		return this;
	}

	/**
	 * Sets the maximum time frequency (ms) for the flushing of the output
	 * buffer. By default the output buffers flush only when they are full.
	 * 
	 * @param timeoutMillis
	 *            The maximum time between two output flushes.
	 * @return The DataStream with buffer timeout set.
	 */
	public DataStream<T> setBufferTimeout(long timeoutMillis) {
		jobGraphBuilder.setBufferTimeout(id, timeoutMillis);
		return this;
	}

	/**
	 * Connecting {@link DataStream} outputs with each other for applying joint
	 * operators on them. The DataStreams connected using this operator will be
	 * transformed simultaneously. It creates a joint output of the connected
	 * DataStreams.
	 * 
	 * @param streams
	 *            The DataStreams to connect output with.
	 * @return The connected DataStream.
	 */
	public DataStream<T> connectWith(DataStream<T>... streams) {
		DataStream<T> returnStream = this.copy();

		for (DataStream<T> stream : streams) {
			addConnection(returnStream, stream);
		}
		return returnStream;
	}

	/**
	 * Operator used for directing tuples to specific named outputs. Sets an
	 * {@link OutputSelector} for the vertex. The tuples emitted from this
	 * vertex will be sent to the output names selected by the OutputSelector.
	 * Unnamed outputs will not receive any tuples.
	 * 
	 * @param outputSelector
	 *            The user defined OutputSelector for directing the tuples.
	 * @return The {@link SplitDataStream}
	 */
	public SplitDataStream<T> split(OutputSelector<T> outputSelector) {
		try {
			for (DataStream<T> stream : connectedStreams) {
				jobGraphBuilder.setOutputSelector(stream.id,
						SerializationUtils.serialize(outputSelector));
			}
		} catch (SerializationException e) {
			throw new RuntimeException("Cannot serialize OutputSelector");
		}

		return new SplitDataStream<T>(this);
	}

	/**
	 * Sets the partitioning of the {@link DataStream} so that the output tuples
	 * are partitioned by their hashcode and are sent to only one component.
	 * 
	 * @param keyposition
	 *            The field used to compute the hashcode.
	 * @return The DataStream with field partitioning set.
	 */
	public DataStream<T> partitionBy(int keyposition) {
		if (keyposition < 0) {
			throw new IllegalArgumentException("The position of the field must be non-negative");
		}

		return setConnectionType(new FieldsPartitioner<T>(keyposition));
	}

	/**
	 * Sets the partitioning of the {@link DataStream} so that the output tuples
	 * are broadcasted to every parallel instance of the next component.
	 * 
	 * @return The DataStream with broadcast partitioning set.
	 */
	public DataStream<T> broadcast() {
		return setConnectionType(new BroadcastPartitioner<T>());
	}

	/**
	 * Sets the partitioning of the {@link DataStream} so that the output tuples
	 * are shuffled to the next component.
	 * 
	 * @return The DataStream with shuffle partitioning set.
	 */
	public DataStream<T> shuffle() {
		return setConnectionType(new ShufflePartitioner<T>());
	}

	/**
	 * Sets the partitioning of the {@link DataStream} so that the output tuples
	 * are forwarded to the local subtask of the next component. This is the
	 * default partitioner setting.
	 * 
	 * @return The DataStream with shuffle partitioning set.
	 */
	public DataStream<T> forward() {
		return setConnectionType(new ForwardPartitioner<T>());
	}

	/**
	 * Sets the partitioning of the {@link DataStream} so that the output tuples
	 * are distributed evenly to the next component.
	 * 
	 * @return The DataStream with shuffle partitioning set.
	 */
	public DataStream<T> distribute() {
		return setConnectionType(new DistributePartitioner<T>());
	}

	/**
	 * Applies a Map transformation on a {@link DataStream}. The transformation
	 * calls a {@link RichMapFunction} for each element of the DataStream. Each
	 * MapFunction call returns exactly one element.
	 * 
	 * @param mapper
	 *            The RichMapFunction that is called for each element of the
	 *            DataStream.
	 * @param <R>
	 *            output type
	 * @return The transformed DataStream.
	 */
	public <R> StreamOperator<T, R> map(RichMapFunction<T, R> mapper) {
		return addFunction("map", mapper, new FunctionTypeWrapper<T, Tuple, R>(mapper,
				RichMapFunction.class, 0, -1, 1), new MapInvokable<T, R>(mapper));
	}

	/**
	 * Applies a FlatMap transformation on a {@link DataStream}. The
	 * transformation calls a {@link RichFlatMapFunction} for each element of
	 * the DataStream. Each RichFlatMapFunction call can return any number of
	 * elements including none.
	 * 
	 * @param flatMapper
	 *            The RichFlatMapFunction that is called for each element of the
	 *            DataStream
	 * 
	 * @param <R>
	 *            output type
	 * @return The transformed DataStream.
	 */
	public <R> StreamOperator<T, R> flatMap(RichFlatMapFunction<T, R> flatMapper) {
		return addFunction("flatMap", flatMapper, new FunctionTypeWrapper<T, Tuple, R>(flatMapper,
				RichFlatMapFunction.class, 0, -1, 1), new FlatMapInvokable<T, R>(flatMapper));
	}

	/**
	 * Applies a CoMap transformation on two separate {@link DataStream}s. The
	 * transformation calls a {@link CoMapFunction#map1(Tuple)} for each element
	 * of the first DataStream (on which .coMapWith was called) and
	 * {@link CoMapFunction#map2(Tuple)} for each element of the second
	 * DataStream. Each CoMapFunction call returns exactly one element.
	 * 
	 * @param coMapper
	 *            The CoMapFunction used to jointly transform the two input
	 *            DataStreams
	 * @param otherStream
	 *            The DataStream that will be transformed with
	 *            {@link CoMapFunction#map2(Tuple)}
	 * @return The transformed DataStream
	 */
	public <T2, R> DataStream<R> coMapWith(CoMapFunction<T, T2, R> coMapper,
			DataStream<T2> otherStream) {
		return addCoFunction("coMap", this.copy(), otherStream.copy(), coMapper,
				new FunctionTypeWrapper<T, T2, R>(coMapper, CoMapFunction.class, 0, 1, 2),
				new CoMapInvokable<T, T2, R>(coMapper));
	}

	/**
	 * Applies a reduce transformation on preset chunks of the DataStream. The
	 * transformation calls a {@link RichGroupReduceFunction} for each tuple
	 * batch of the predefined size. Each RichGroupReduceFunction call can
	 * return any number of elements including none.
	 * 
	 * 
	 * @param reducer
	 *            The RichGroupReduceFunction that is called for each tuple
	 *            batch.
	 * @param batchSize
	 *            The number of tuples grouped together in the batch.
	 * @param <R>
	 *            output type
	 * @return The modified DataStream.
	 */
	public <R> StreamOperator<T, R> batchReduce(RichGroupReduceFunction<T, R> reducer, int batchSize) {
		return addFunction("batchReduce", reducer, new FunctionTypeWrapper<T, Tuple, R>(reducer,
				RichGroupReduceFunction.class, 0, -1, 1), new BatchReduceInvokable<T, R>(reducer,
				batchSize));
	}

	/**
	 * Applies a reduce transformation on preset "time" chunks of the
	 * DataStream. The transformation calls a {@link RichGroupReduceFunction} on
	 * records received during the predefined time window. The window shifted
	 * after each reduce call. Each RichGroupReduceFunction call can return any
	 * number of elements including none.
	 * 
	 * 
	 * @param reducer
	 *            The RichGroupReduceFunction that is called for each time
	 *            window.
	 * @param windowSize
	 *            The time window to run the reducer on, in milliseconds.
	 * @param <R>
	 *            output type
	 * @return The modified DataStream.
	 */
	public <R> StreamOperator<T, R> windowReduce(RichGroupReduceFunction<T, R> reducer,
			long windowSize) {
		return addFunction("batchReduce", reducer, new FunctionTypeWrapper<T, Tuple, R>(reducer,
				RichGroupReduceFunction.class, 0, -1, 1), new WindowReduceInvokable<T, R>(reducer,
				windowSize));
	}

	/**
	 * Applies a Filter transformation on a {@link DataStream}. The
	 * transformation calls a {@link RichFilterFunction} for each element of the
	 * DataStream and retains only those element for which the function returns
	 * true. Elements for which the function returns false are filtered.
	 * 
	 * @param filter
	 *            The RichFilterFunction that is called for each element of the
	 *            DataSet.
	 * @return The filtered DataStream.
	 */
	public StreamOperator<T, T> filter(RichFilterFunction<T> filter) {
		return addFunction("filter", filter, new FunctionTypeWrapper<T, Tuple, T>(filter,
				RichFilterFunction.class, 0, -1, 0), new FilterInvokable<T>(filter));
	}

	/**
	 * Writes a DataStream to the standard output stream (stdout). For each
	 * element of the DataStream the result of {@link Object#toString()} is
	 * written.
	 * 
	 * @return The closed DataStream.
	 */
	public DataStream<T> print() {
		DataStream<T> inputStream = this.copy();
		PrintSinkFunction<T> printFunction = new PrintSinkFunction<T>();
		DataStream<T> returnStream = addSink(inputStream, printFunction, null);

		jobGraphBuilder.setBytesFrom(inputStream.getId(), returnStream.getId());

		return returnStream;
	}

	/**
	 * Writes a DataStream to the file specified by path in text format. For
	 * every element of the DataStream the result of {@link Object#toString()}
	 * is written.
	 * 
	 * @param path
	 *            is the path to the location where the tuples are written
	 * 
	 * @return The closed DataStream
	 */
	public DataStream<T> writeAsText(String path) {
		return writeAsText(this, path, new WriteFormatAsText<T>(), 1, null);
	}

	/**
	 * Writes a DataStream to the file specified by path in text format. The
	 * writing is performed periodically, in every millis milliseconds. For
	 * every element of the DataStream the result of {@link Object#toString()}
	 * is written.
	 * 
	 * @param path
	 *            is the path to the location where the tuples are written
	 * @param millis
	 *            is the file update frequency
	 * 
	 * @return The closed DataStream
	 */
	public DataStream<T> writeAsText(String path, long millis) {
		return writeAsText(this, path, new WriteFormatAsText<T>(), millis, null);
	}

	/**
	 * Writes a DataStream to the file specified by path in text format. The
	 * writing is performed periodically in equally sized batches. For every
	 * element of the DataStream the result of {@link Object#toString()} is
	 * written.
	 * 
	 * @param path
	 *            is the path to the location where the tuples are written
	 * @param batchSize
	 *            is the size of the batches, i.e. the number of tuples written
	 *            to the file at a time
	 * 
	 * @return The closed DataStream
	 */
	public DataStream<T> writeAsText(String path, int batchSize) {
		return writeAsText(this, path, new WriteFormatAsText<T>(), batchSize, null);
	}

	/**
	 * Writes a DataStream to the file specified by path in text format. The
	 * writing is performed periodically, in every millis milliseconds. For
	 * every element of the DataStream the result of {@link Object#toString()}
	 * is written.
	 * 
	 * @param path
	 *            is the path to the location where the tuples are written
	 * @param millis
	 *            is the file update frequency
	 * @param endTuple
	 *            is a special tuple indicating the end of the stream. If an
	 *            endTuple is caught, the last pending batch of tuples will be
	 *            immediately appended to the target file regardless of the
	 *            system time.
	 * 
	 * @return The closed DataStream
	 */
	public DataStream<T> writeAsText(String path, long millis, T endTuple) {
		return writeAsText(this, path, new WriteFormatAsText<T>(), millis, endTuple);
	}

	/**
	 * Writes a DataStream to the file specified by path in text format. The
	 * writing is performed periodically in equally sized batches. For every
	 * element of the DataStream the result of {@link Object#toString()} is
	 * written.
	 * 
	 * @param path
	 *            is the path to the location where the tuples are written
	 * @param batchSize
	 *            is the size of the batches, i.e. the number of tuples written
	 *            to the file at a time
	 * @param endTuple
	 *            is a special tuple indicating the end of the stream. If an
	 *            endTuple is caught, the last pending batch of tuples will be
	 *            immediately appended to the target file regardless of the
	 *            batchSize.
	 * 
	 * @return The closed DataStream
	 */
	public DataStream<T> writeAsText(String path, int batchSize, T endTuple) {
		return writeAsText(this, path, new WriteFormatAsText<T>(), batchSize, endTuple);
	}

	/**
	 * Writes a DataStream to the file specified by path in text format. The
	 * writing is performed periodically, in every millis milliseconds. For
	 * every element of the DataStream the result of {@link Object#toString()}
	 * is written.
	 * 
	 * @param path
	 *            is the path to the location where the tuples are written
	 * @param millis
	 *            is the file update frequency
	 * @param endTuple
	 *            is a special tuple indicating the end of the stream. If an
	 *            endTuple is caught, the last pending batch of tuples will be
	 *            immediately appended to the target file regardless of the
	 *            system time.
	 * 
	 * @return the data stream constructed
	 */
	private DataStream<T> writeAsText(DataStream<T> inputStream, String path,
			WriteFormatAsText<T> format, long millis, T endTuple) {
		DataStream<T> returnStream = addSink(inputStream, new WriteSinkFunctionByMillis<T>(path,
				format, millis, endTuple), null);
		jobGraphBuilder.setBytesFrom(inputStream.getId(), returnStream.getId());
		jobGraphBuilder.setMutability(returnStream.getId(), false);
		return returnStream;
	}

	/**
	 * Writes a DataStream to the file specified by path in text format. The
	 * writing is performed periodically in equally sized batches. For every
	 * element of the DataStream the result of {@link Object#toString()} is
	 * written.
	 * 
	 * @param path
	 *            is the path to the location where the tuples are written
	 * @param batchSize
	 *            is the size of the batches, i.e. the number of tuples written
	 *            to the file at a time
	 * @param endTuple
	 *            is a special tuple indicating the end of the stream. If an
	 *            endTuple is caught, the last pending batch of tuples will be
	 *            immediately appended to the target file regardless of the
	 *            batchSize.
	 * 
	 * @return the data stream constructed
	 */
	private DataStream<T> writeAsText(DataStream<T> inputStream, String path,
			WriteFormatAsText<T> format, int batchSize, T endTuple) {
		DataStream<T> returnStream = addSink(inputStream, new WriteSinkFunctionByBatches<T>(path,
				format, batchSize, endTuple), null);
		jobGraphBuilder.setBytesFrom(inputStream.getId(), returnStream.getId());
		jobGraphBuilder.setMutability(returnStream.getId(), false);
		return returnStream;
	}

	/**
	 * Writes a DataStream to the file specified by path in text format. For
	 * every element of the DataStream the result of {@link Object#toString()}
	 * is written.
	 * 
	 * @param path
	 *            is the path to the location where the tuples are written
	 * 
	 * @return The closed DataStream
	 */
	public DataStream<T> writeAsCsv(String path) {
		return writeAsCsv(this, path, new WriteFormatAsCsv<T>(), 1, null);
	}

	/**
	 * Writes a DataStream to the file specified by path in text format. The
	 * writing is performed periodically, in every millis milliseconds. For
	 * every element of the DataStream the result of {@link Object#toString()}
	 * is written.
	 * 
	 * @param path
	 *            is the path to the location where the tuples are written
	 * @param millis
	 *            is the file update frequency
	 * 
	 * @return The closed DataStream
	 */
	public DataStream<T> writeAsCsv(String path, long millis) {
		return writeAsCsv(this, path, new WriteFormatAsCsv<T>(), millis, null);
	}

	/**
	 * Writes a DataStream to the file specified by path in text format. The
	 * writing is performed periodically in equally sized batches. For every
	 * element of the DataStream the result of {@link Object#toString()} is
	 * written.
	 * 
	 * @param path
	 *            is the path to the location where the tuples are written
	 * @param batchSize
	 *            is the size of the batches, i.e. the number of tuples written
	 *            to the file at a time
	 * 
	 * @return The closed DataStream
	 */
	public DataStream<T> writeAsCsv(String path, int batchSize) {
		return writeAsCsv(this, path, new WriteFormatAsCsv<T>(), batchSize, null);
	}

	/**
	 * Writes a DataStream to the file specified by path in text format. The
	 * writing is performed periodically, in every millis milliseconds. For
	 * every element of the DataStream the result of {@link Object#toString()}
	 * is written.
	 * 
	 * @param path
	 *            is the path to the location where the tuples are written
	 * @param millis
	 *            is the file update frequency
	 * @param endTuple
	 *            is a special tuple indicating the end of the stream. If an
	 *            endTuple is caught, the last pending batch of tuples will be
	 *            immediately appended to the target file regardless of the
	 *            system time.
	 * 
	 * @return The closed DataStream
	 */
	public DataStream<T> writeAsCsv(String path, long millis, T endTuple) {
		return writeAsCsv(this, path, new WriteFormatAsCsv<T>(), millis, endTuple);
	}

	/**
	 * Writes a DataStream to the file specified by path in text format. The
	 * writing is performed periodically in equally sized batches. For every
	 * element of the DataStream the result of {@link Object#toString()} is
	 * written.
	 * 
	 * @param path
	 *            is the path to the location where the tuples are written
	 * @param batchSize
	 *            is the size of the batches, i.e. the number of tuples written
	 *            to the file at a time
	 * @param endTuple
	 *            is a special tuple indicating the end of the stream. If an
	 *            endTuple is caught, the last pending batch of tuples will be
	 *            immediately appended to the target file regardless of the
	 *            batchSize.
	 * 
	 * @return The closed DataStream
	 */
	public DataStream<T> writeAsCsv(String path, int batchSize, T endTuple) {
		setMutability(false);
		return writeAsCsv(this, path, new WriteFormatAsCsv<T>(), batchSize, endTuple);
	}

	/**
	 * Writes a DataStream to the file specified by path in csv format. The
	 * writing is performed periodically, in every millis milliseconds. For
	 * every element of the DataStream the result of {@link Object#toString()}
	 * is written.
	 * 
	 * @param path
	 *            is the path to the location where the tuples are written
	 * @param millis
	 *            is the file update frequency
	 * @param endTuple
	 *            is a special tuple indicating the end of the stream. If an
	 *            endTuple is caught, the last pending batch of tuples will be
	 *            immediately appended to the target file regardless of the
	 *            system time.
	 * 
	 * @return the data stream constructed
	 */
	private DataStream<T> writeAsCsv(DataStream<T> inputStream, String path,
			WriteFormatAsCsv<T> format, long millis, T endTuple) {
		DataStream<T> returnStream = addSink(inputStream, new WriteSinkFunctionByMillis<T>(path,
				format, millis, endTuple));
		jobGraphBuilder.setBytesFrom(inputStream.getId(), returnStream.getId());
		jobGraphBuilder.setMutability(returnStream.getId(), false);
		return returnStream;
	}

	/**
	 * Writes a DataStream to the file specified by path in csv format. The
	 * writing is performed periodically in equally sized batches. For every
	 * element of the DataStream the result of {@link Object#toString()} is
	 * written.
	 * 
	 * @param path
	 *            is the path to the location where the tuples are written
	 * @param batchSize
	 *            is the size of the batches, i.e. the number of tuples written
	 *            to the file at a time
	 * @param endTuple
	 *            is a special tuple indicating the end of the stream. If an
	 *            endTuple is caught, the last pending batch of tuples will be
	 *            immediately appended to the target file regardless of the
	 *            batchSize.
	 * 
	 * @return the data stream constructed
	 */
	private DataStream<T> writeAsCsv(DataStream<T> inputStream, String path,
			WriteFormatAsCsv<T> format, int batchSize, T endTuple) {
		DataStream<T> returnStream = addSink(inputStream, new WriteSinkFunctionByBatches<T>(path,
				format, batchSize, endTuple), null);
		jobGraphBuilder.setBytesFrom(inputStream.getId(), returnStream.getId());
		jobGraphBuilder.setMutability(returnStream.getId(), false);
		return returnStream;
	}

	/**
	 * Initiates an iterative part of the program that executes multiple times
	 * and feeds back data streams. The iterative part needs to be closed by
	 * calling {@link IterativeDataStream#closeWith(DataStream)}. The data
	 * stream given to the {@code closeWith(DataStream)} method is the data
	 * stream that will be fed back and used as the input for the iteration
	 * head. Unlike in batch processing by default the output of the iteration
	 * stream is directed to both to the iteration head and the next component.
	 * To direct tuples to the iteration head or the output specifically one can
	 * use the {@code directTo(OutputSelector)} while referencing the iteration
	 * head as 'iterate'.
	 * 
	 * The iteration edge will be partitioned the same way as the first input of
	 * the iteration head.
	 * 
	 * @return The iterative data stream created.
	 */
	public IterativeDataStream<T> iterate() {
		return new IterativeDataStream<T>(this);
	}

	protected <R> DataStream<T> addIterationSource(String iterationID) {
		DataStream<R> returnStream = new DataStream<R>(environment, "iterationSource");
	
		jobGraphBuilder.addIterationSource(returnStream.getId(), this.getId(), iterationID,
				degreeOfParallelism);
	
		return this.copy();
	}

	/**
	 * Internal function for passing the user defined functions to the JobGraph
	 * of the job.
	 * 
	 * @param functionName
	 *            name of the function
	 * @param function
	 *            the user defined function
	 * @param functionInvokable
	 *            the wrapping JobVertex instance
	 * @param <T>
	 *            type of the input stream
	 * @param <R>
	 *            type of the return stream
	 * @return the data stream constructed
	 */
	private <R> StreamOperator<T, R> addFunction(String functionName,
			final AbstractRichFunction function, TypeSerializerWrapper<T, Tuple, R> typeWrapper,
			UserTaskInvokable<T, R> functionInvokable) {
	
		DataStream<T> inputStream = this.copy();
		StreamOperator<T, R> returnStream = new StreamOperator<T, R>(environment, functionName);
	
		try {
			jobGraphBuilder.addTask(returnStream.getId(), functionInvokable, typeWrapper,
					functionName, SerializationUtils.serialize(function), degreeOfParallelism);
		} catch (SerializationException e) {
			throw new RuntimeException("Cannot serialize user defined function");
		}
	
		connectGraph(inputStream, returnStream.getId(), 0);
	
		if (inputStream instanceof IterativeDataStream) {
			returnStream.addIterationSource(((IterativeDataStream<T>) inputStream).iterationID
					.toString());
		}
	
		if (inputStream instanceof NamedDataStream) {
			returnStream.name(inputStream.userDefinedName);
		}
	
		return returnStream;
	}

	protected <T1, T2, R> DataStream<R> addCoFunction(String functionName,
			DataStream<T1> inputStream1, DataStream<T2> inputStream2,
			final AbstractRichFunction function, TypeSerializerWrapper<T1, T2, R> typeWrapper,
			CoInvokable<T1, T2, R> functionInvokable) {
	
		DataStream<R> returnStream = new DataStream<R>(environment, functionName);
	
		try {
			jobGraphBuilder.addCoTask(returnStream.getId(), functionInvokable, typeWrapper,
					functionName, SerializationUtils.serialize(function), degreeOfParallelism);
		} catch (SerializationException e) {
			throw new RuntimeException("Cannot serialize user defined function");
		}
	
		connectGraph(inputStream1, returnStream.getId(), 1);
		connectGraph(inputStream2, returnStream.getId(), 2);
	
		if ((inputStream1 instanceof NamedDataStream) && (inputStream2 instanceof NamedDataStream)) {
			throw new RuntimeException("An operator cannot have two names");
		} else {
			if (inputStream1 instanceof NamedDataStream) {
				returnStream.name(inputStream1.userDefinedName);
			}
	
			if (inputStream2 instanceof NamedDataStream) {
				returnStream.name(inputStream2.userDefinedName);
			}
		}
		// TODO consider iteration
	
		return returnStream;
	}

	/**
	 * Gives the data transformation(vertex) a user defined name in order to use
	 * with directed outputs. The {@link OutputSelector} of the input vertex
	 * should use this name for directed emits.
	 * 
	 * @param name
	 *            The name to set
	 * @return The named DataStream.
	 */
	protected DataStream<T> name(String name) {
		// TODO copy DataStream?
		if (name == "") {
			throw new IllegalArgumentException("User defined name must not be empty string");
		}
	
		userDefinedName = name;
		jobGraphBuilder.setUserDefinedName(id, name);
	
		return this;
	}

	/**
	 * Connects two DataStreams
	 * 
	 * @param returnStream
	 *            The other DataStream will connected to this
	 * @param stream
	 *            This DataStream will be connected to returnStream
	 */
	private void addConnection(DataStream<T> returnStream, DataStream<T> stream) {
		if ((stream instanceof NamedDataStream) || (returnStream instanceof NamedDataStream)) {
			if (!returnStream.userDefinedName.equals(stream.userDefinedName)) {
				throw new RuntimeException("Error: Connected NamedDataStreams must have same names");
			}
		}
		returnStream.connectedStreams.add(stream.copy());
	}

	private DataStream<T> setConnectionType(StreamPartitioner<T> partitioner) {
		DataStream<T> returnStream = this.copy();
	
		for (DataStream<T> stream : returnStream.connectedStreams) {
			stream.partitioner = partitioner;
		}
	
		return returnStream;
	}

	/**
	 * Internal function for assembling the underlying
	 * {@link org.apache.flink.nephele.jobgraph.JobGraph} of the job. Connects
	 * the outputs of the given input stream to the specified output stream
	 * given by the outputID.
	 * 
	 * @param inputStream
	 *            input data stream
	 * @param outputID
	 *            ID of the output
	 * @param typeNumber
	 *            Number of the type (used at co-functions)
	 */
	private <X> void connectGraph(DataStream<X> inputStream, String outputID, int typeNumber) {
		for (DataStream<X> stream : inputStream.connectedStreams) {
			jobGraphBuilder.setEdge(stream.getId(), outputID, stream.partitioner, typeNumber);
		}
	}

	/**
	 * Adds the given sink to this DataStream. Only streams with sinks added
	 * will be executed once the {@link StreamExecutionEnvironment#execute()}
	 * method is called.
	 * 
	 * @param sinkFunction
	 *            The object containing the sink's invoke function.
	 * @return The closed DataStream.
	 */
	public DataStream<T> addSink(SinkFunction<T> sinkFunction) {
		return addSink(this.copy(), sinkFunction);
	}

	private DataStream<T> addSink(DataStream<T> inputStream, SinkFunction<T> sinkFunction) {
		return addSink(inputStream, sinkFunction, new FunctionTypeWrapper<T, Tuple, T>(
				sinkFunction, SinkFunction.class, 0, -1, 0));
	}

	private DataStream<T> addSink(DataStream<T> inputStream, SinkFunction<T> sinkFunction,
			TypeSerializerWrapper<T, Tuple, T> typeWrapper) {
		DataStream<T> returnStream = new DataStream<T>(environment, "sink");
	
		try {
			jobGraphBuilder.addSink(returnStream.getId(), new SinkInvokable<T>(sinkFunction),
					typeWrapper, "sink", SerializationUtils.serialize(sinkFunction),
					degreeOfParallelism);
		} catch (SerializationException e) {
			throw new RuntimeException("Cannot serialize SinkFunction");
		}
	
		inputStream.connectGraph(inputStream, returnStream.getId(), 0);
	
		if (this.copy() instanceof NamedDataStream) {
			returnStream.name(inputStream.userDefinedName);
		}
	
		return returnStream;
	}
}
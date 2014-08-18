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

import org.apache.flink.api.java.functions.FilterFunction;
import org.apache.flink.api.java.functions.FlatMapFunction;
import org.apache.flink.api.java.functions.GroupReduceFunction;
import org.apache.flink.api.java.functions.MapFunction;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.streaming.api.StreamExecutionEnvironment.ConnectionType;
import org.apache.flink.streaming.api.collector.OutputSelector;
import org.apache.flink.streaming.api.function.SinkFunction;
import org.apache.flink.streaming.api.invokable.operator.BatchReduceInvokable;
import org.apache.flink.streaming.api.invokable.operator.FilterInvokable;
import org.apache.flink.streaming.api.invokable.operator.FlatMapInvokable;
import org.apache.flink.streaming.api.invokable.operator.MapInvokable;
import org.apache.flink.types.TypeInformation;

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
public class DataStream<T extends Tuple> {

	protected static Integer counter = 0;
	protected final StreamExecutionEnvironment environment;
	protected TypeInformation<T> type;
	protected String id;
	protected int degreeOfParallelism;
	protected String userDefinedName;
	protected OutputSelector<T> outputSelector;
	protected List<String> connectIDs;
	protected List<ConnectionType> ctypes;
	protected List<Integer> cparams;
	protected boolean iterationflag;
	protected Integer iterationID;

	/**
	 * Create a new {@link DataStream} in the given execution environment
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
		initConnections();

	}

	/**
	 * Create a new {@link DataStream} in the given environment with the given
	 * id
	 * 
	 * @param environment
	 *            StreamExecutionEnvironment
	 * @param id
	 *            The id of the DataStream
	 */
	protected DataStream(StreamExecutionEnvironment environment, String operatorType, String id) {
		this.environment = environment;
		this.id = id;
		initConnections();
	}

	/**
	 * Initialize the connection and partitioning among the connected
	 * {@link DataStream}s.
	 */
	private void initConnections() {
		connectIDs = new ArrayList<String>();
		connectIDs.add(getId());
		ctypes = new ArrayList<StreamExecutionEnvironment.ConnectionType>();
		ctypes.add(ConnectionType.SHUFFLE);
		cparams = new ArrayList<Integer>();
		cparams.add(0);

	}

	/**
	 * Creates an identical {@link DataStream}.
	 * 
	 * @return The DataStream copy.
	 */
	public DataStream<T> copy() {
		DataStream<T> copiedStream = new DataStream<T>(environment, "", getId());
		copiedStream.type = this.type;

		copiedStream.connectIDs = new ArrayList<String>(this.connectIDs);
		copiedStream.userDefinedName = this.userDefinedName;
		copiedStream.ctypes = new ArrayList<StreamExecutionEnvironment.ConnectionType>(this.ctypes);
		copiedStream.cparams = new ArrayList<Integer>(this.cparams);
		copiedStream.degreeOfParallelism = this.degreeOfParallelism;
		copiedStream.iterationflag = this.iterationflag;
		copiedStream.iterationID = this.iterationID;
		return copiedStream;
	}

	/**
	 * Returns the ID of the {@link DataStream}.
	 * 
	 * @return ID of the datastream
	 */
	public String getId() {
		return id;
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

		environment.setOperatorParallelism(this);

		return this.copy();

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
	 * Gives the data transformation a user defined name in order to use at
	 * directed outputs
	 * 
	 * @param name
	 *            The name to set
	 * @return The named DataStream.
	 */
	public DataStream<T> name(String name) {
		// copy?
		if (name == "") {
			throw new IllegalArgumentException("User defined name must not be empty string");
		}

		userDefinedName = name;
		environment.setName(this, name);
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
		DataStream<T> returnStream = copy();

		for (DataStream<T> stream : streams) {
			addConnection(returnStream, stream);
		}
		return returnStream;
	}

	private DataStream<T> addConnection(DataStream<T> returnStream, DataStream<T> stream) {
		returnStream.connectIDs.addAll(stream.connectIDs);
		returnStream.ctypes.addAll(stream.ctypes);
		returnStream.cparams.addAll(stream.cparams);

		return returnStream;
	}

	public DataStream<T> directTo(OutputSelector<T> outputSelector) {
		this.outputSelector = outputSelector;
		environment.addDirectedEmit(id, outputSelector);
		return this;
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
		// TODO get type information
		// else if (type.getArity() <= keyposition) {
		// throw new IllegalArgumentException(
		// "The position of the field must be smaller than the number of fields in the Tuple");
		// }

		DataStream<T> returnStream = copy();

		for (int i = 0; i < returnStream.ctypes.size(); i++) {
			returnStream.ctypes.set(i, ConnectionType.FIELD);
			returnStream.cparams.set(i, keyposition);
		}
		return returnStream;
	}

	/**
	 * Sets the partitioning of the {@link DataStream} so that the output tuples
	 * are broadcasted to every parallel instance of the next component.
	 * 
	 * @return The DataStream with broadcast partitioning set.
	 */
	public DataStream<T> broadcast() {
		DataStream<T> returnStream = copy();

		for (int i = 0; i < returnStream.ctypes.size(); i++) {
			returnStream.ctypes.set(i, ConnectionType.BROADCAST);
		}
		return returnStream;
	}
	
	/**
	 * Sets the partitioning of the {@link DataStream} so that the output tuples
	 * are shuffled to the next component.
	 * 
	 * @return The DataStream with shuffle partitioning set.
	 */
	public DataStream<T> shuffle() {
		DataStream<T> returnStream = copy();

		for (int i = 0; i < returnStream.ctypes.size(); i++) {
			returnStream.ctypes.set(i, ConnectionType.SHUFFLE);
		}
		return returnStream;
	}
	
	/**
	 * Sets the partitioning of the {@link DataStream} so that the output tuples
	 * are forwarded to the local subtask of the next component.
	 * 
	 * @return The DataStream with shuffle partitioning set.
	 */
	public DataStream<T> forward() {
		DataStream<T> returnStream = copy();

		for (int i = 0; i < returnStream.ctypes.size(); i++) {
			returnStream.ctypes.set(i, ConnectionType.FORWARD);
		}
		return returnStream;
	}

	/**
	 * Applies a Map transformation on a {@link DataStream}. The transformation
	 * calls a {@link MapFunction} for each element of the DataStream. Each
	 * MapFunction call returns exactly one element.
	 * 
	 * @param mapper
	 *            The MapFunction that is called for each element of the
	 *            DataStream.
	 * @param <R>
	 *            output type
	 * @return The transformed DataStream.
	 */
	public <R extends Tuple> StreamOperator<T, R> map(MapFunction<T, R> mapper) {
		return environment.addFunction("map", this.copy(), mapper, new MapInvokable<T, R>(mapper));

	}

	/**
	 * Applies a FlatMap transformation on a {@link DataStream}. The
	 * transformation calls a FlatMapFunction for each element of the DataSet.
	 * Each FlatMapFunction call can return any number of elements including
	 * none.
	 * 
	 * @param flatMapper
	 *            The FlatMapFunction that is called for each element of the
	 *            DataStream
	 * 
	 * @param <R>
	 *            output type
	 * @return The transformed DataStream.
	 */
	public <R extends Tuple> StreamOperator<T, R> flatMap(FlatMapFunction<T, R> flatMapper) {
		return environment.addFunction("flatMap", this.copy(), flatMapper,
				new FlatMapInvokable<T, R>(flatMapper));
	}

	/**
	 * Applies a Filter transformation on a {@link DataStream}. The
	 * transformation calls a {@link FilterFunction} for each element of the
	 * DataStream and retains only those element for which the function returns
	 * true. Elements for which the function returns false are filtered.
	 * 
	 * @param filter
	 *            The FilterFunction that is called for each element of the
	 *            DataSet.
	 * @return The filtered DataStream.
	 */
	public StreamOperator<T, T> filter(FilterFunction<T> filter) {
		return environment.addFunction("filter", this.copy(), filter,
				new FilterInvokable<T>(filter));
	}

	/**
	 * Applies a reduce transformation on preset chunks of the DataStream. The
	 * transformation calls a {@link GroupReduceFunction} for each tuple batch
	 * of the predefined size. Each GroupReduceFunction call can return any
	 * number of elements including none.
	 * 
	 * 
	 * @param reducer
	 *            The GroupReduceFunction that is called for each tuple batch.
	 * @param batchSize
	 *            The number of tuples grouped together in the batch.
	 * @param <R>
	 *            output type
	 * @return The modified DataStream.
	 */
	public <R extends Tuple> StreamOperator<T, R> batchReduce(GroupReduceFunction<T, R> reducer,
			int batchSize) {
		return environment.addFunction("batchReduce", this.copy(), reducer,
				new BatchReduceInvokable<T, R>(reducer, batchSize));
	}

	/**
	 * Adds the given sink to this environment. Only streams with sinks added
	 * will be executed once the {@link StreamExecutionEnvironment#execute()}
	 * method is called.
	 * 
	 * @param sinkFunction
	 *            The object containing the sink's invoke function.
	 * @return The modified DataStream.
	 */
	public DataStream<T> addSink(SinkFunction<T> sinkFunction) {
		return environment.addSink(this.copy(), sinkFunction);
	}

	/**
	 * Writes a DataStream to the standard output stream (stdout). For each
	 * element of the DataStream the result of {@link Object#toString()} is
	 * written.
	 * 
	 * @return The closed DataStream.
	 */
	public DataStream<T> print() {
		return environment.print(this.copy());
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
	 * @return The iterative data stream created.
	 */
	public IterativeDataStream<T> iterate() {
		return new IterativeDataStream<T>(copy());
	}

	protected DataStream<T> addIterationSource(String iterationID) {
		environment.addIterationSource(this, iterationID);
		return this.copy();
	}

	/**
	 * Set the type parameter.
	 * 
	 * @param type
	 *            The type parameter.
	 */
	protected void setType(TypeInformation<T> type) {
		this.type = type;
	}

	/**
	 * Get the type information for this DataStream.
	 * 
	 * @return The type of the generic parameter.
	 */
	public TypeInformation<T> getType() {
		return this.type;
	}
}
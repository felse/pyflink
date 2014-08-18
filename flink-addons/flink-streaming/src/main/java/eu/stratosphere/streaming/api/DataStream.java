/***********************************************************************************************************************
 *
 * Copyright (C) 2010-2014 by the Stratosphere project (http://stratosphere.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 **********************************************************************************************************************/

package eu.stratosphere.streaming.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import eu.stratosphere.api.java.functions.FilterFunction;
import eu.stratosphere.api.java.functions.FlatMapFunction;
import eu.stratosphere.api.java.functions.GroupReduceFunction;
import eu.stratosphere.api.java.functions.MapFunction;
import eu.stratosphere.api.java.tuple.Tuple;
import eu.stratosphere.streaming.api.StreamExecutionEnvironment.ConnectionType;
import eu.stratosphere.types.TypeInformation;

public class DataStream<T extends Tuple> {

	private final StreamExecutionEnvironment context;
	private TypeInformation<T> type;
	private final Random random = new Random();
	private final String id;
	List<String> connectIDs;
	List<ConnectionType> ctypes;
	List<Integer> cparams;

	protected DataStream() {
		// TODO implement
		context = new StreamExecutionEnvironment();
		id = "source";
		initConnections();
	}

	protected DataStream(StreamExecutionEnvironment context) {
		if (context == null) {
			throw new NullPointerException("context is null");
		}

		// TODO add name based on component number an preferable sequential id
		this.id = Long.toHexString(random.nextLong()) + Long.toHexString(random.nextLong());
		this.context = context;
		initConnections();

	}

	private void initConnections() {
		connectIDs = new ArrayList<String>();
		connectIDs.add(getId());
		ctypes = new ArrayList<StreamExecutionEnvironment.ConnectionType>();
		ctypes.add(ConnectionType.SHUFFLE);
		cparams = new ArrayList<Integer>();
		cparams.add(0);
	}

	public String getId() {
		return id;
	}

	public DataStream<T> connectWith(DataStream<T> stream) {
		connectIDs.addAll(stream.connectIDs);
		ctypes.addAll(stream.ctypes);
		cparams.addAll(stream.cparams);
		return this;
	}

	public DataStream<T> partitionBy(int keyposition) {
		for (int i = 0; i < ctypes.size(); i++) {
			ctypes.set(i, ConnectionType.FIELD);
			cparams.set(i, keyposition);
		}
		return this;
	}

	public DataStream<T> broadcast() {
		for (int i = 0; i < ctypes.size(); i++) {
			ctypes.set(i, ConnectionType.BROADCAST);
		}
		return this;
	}

	public DataStream<T> batch(int batchSize) {
		return context.setBatchSize(this, batchSize);
	}
	
	public <R extends Tuple> DataStream<R> flatMap(FlatMapFunction<T, R> flatMapper) {
		return context.addFunction("flatMap", this, flatMapper, new FlatMapInvokable<T, R>(
				flatMapper));
	}

	public <R extends Tuple> DataStream<R> map(MapFunction<T, R> mapper) {
		return context.addFunction("map", this, mapper, new MapInvokable<T, R>(mapper));
	}

	public <R extends Tuple> DataStream<R> batchReduce(GroupReduceFunction<T, R> reducer) {
		return context.addFunction("batchReduce", this, reducer, new BatchReduceInvokable<T, R>(
				reducer));
	}

	public DataStream<T> filter(FilterFunction<T> filter) {
		return context.addFunction("filter", this, filter, new FilterInvokable<T>(filter));
	}

	public DataStream<T> addSink(SinkFunction<T> sinkFunction) {
		return context.addSink(this, sinkFunction);
	}

	public DataStream<T> addDummySink() {
		return context.addDummySink(this);
	}

	protected void setType(TypeInformation<T> type) {
		this.type = type;
	}

	public TypeInformation<T> getType() {
		return this.type;
	}
}
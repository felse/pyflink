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

package org.apache.flink.streaming.api.streamcomponent;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;

import org.apache.commons.lang.SerializationUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.flink.api.common.functions.AbstractFunction;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.api.java.functions.FilterFunction;
import org.apache.flink.api.java.functions.FlatMapFunction;
import org.apache.flink.api.java.functions.GroupReduceFunction;
import org.apache.flink.api.java.functions.MapFunction;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.api.java.typeutils.TupleTypeInfo;
import org.apache.flink.api.java.typeutils.TypeExtractor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.core.io.IOReadableWritable;
import org.apache.flink.runtime.io.network.api.MutableReader;
import org.apache.flink.runtime.io.network.api.MutableRecordReader;
import org.apache.flink.runtime.io.network.api.MutableUnionRecordReader;
import org.apache.flink.runtime.io.network.api.RecordWriter;
import org.apache.flink.runtime.jobgraph.tasks.AbstractInvokable;
import org.apache.flink.runtime.operators.util.ReaderIterator;
import org.apache.flink.runtime.plugable.DeserializationDelegate;
import org.apache.flink.runtime.plugable.SerializationDelegate;
import org.apache.flink.streaming.api.collector.DirectedStreamCollector;
import org.apache.flink.streaming.api.collector.OutputSelector;
import org.apache.flink.streaming.api.collector.StreamCollector;
import org.apache.flink.streaming.api.function.sink.SinkFunction;
import org.apache.flink.streaming.api.invokable.StreamComponentInvokable;
import org.apache.flink.streaming.api.invokable.UserSourceInvokable;
import org.apache.flink.streaming.api.streamrecord.StreamRecord;
import org.apache.flink.streaming.api.streamrecord.StreamRecordSerializer;
import org.apache.flink.streaming.partitioner.ShufflePartitioner;
import org.apache.flink.streaming.partitioner.StreamPartitioner;
import org.apache.flink.util.Collector;
import org.apache.flink.util.MutableObjectIterator;

public abstract class AbstractStreamComponent<IN extends Tuple, OUT extends Tuple> extends
		AbstractInvokable {
	private static final Log LOG = LogFactory.getLog(AbstractStreamComponent.class);

	protected TupleTypeInfo<IN> inTupleTypeInfo = null;
	protected StreamRecordSerializer<IN> inTupleSerializer = null;
	protected DeserializationDelegate<StreamRecord<IN>> inDeserializationDelegate = null;

	protected TupleTypeInfo<OUT> outTupleTypeInfo = null;
	protected StreamRecordSerializer<OUT> outTupleSerializer = null;
	protected SerializationDelegate<StreamRecord<OUT>> outSerializationDelegate = null;

	protected Configuration configuration;
	protected StreamCollector<OUT> collector;
	protected int instanceID;
	protected String name;
	private static int numComponents = 0;
	protected boolean isMutable;

	protected static int newComponent() {
		numComponents++;
		return numComponents;
	}

	protected void initialize() {
		configuration = getTaskConfiguration();
		name = configuration.getString("componentName", "MISSING_COMPONENT_NAME");
	}

	protected Collector<OUT> setCollector() {
		if (configuration.getBoolean("directedEmit", false)) {
			OutputSelector<OUT> outputSelector = null;
			try {
				outputSelector = deserializeObject(configuration.getBytes("outputSelector", null));
			} catch (Exception e) {
				throw new StreamComponentException(
						"Cannot deserialize and instantiate OutputSelector", e);
			}

			collector = new DirectedStreamCollector<OUT>(instanceID, outSerializationDelegate,
					outputSelector);
		} else {
			collector = new StreamCollector<OUT>(instanceID, outSerializationDelegate);
		}
		return collector;
	}

	protected void setSerializers() {
		byte[] operatorBytes = configuration.getBytes("operator", null);
		String operatorName = configuration.getString("operatorName", "");

		Object function = null;
		try {
			ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(operatorBytes));
			function = in.readObject();

			if (operatorName.equals("flatMap")) {
				setSerializerDeserializer(function, FlatMapFunction.class);
			} else if (operatorName.equals("map")) {
				setSerializerDeserializer(function, MapFunction.class);
			} else if (operatorName.equals("batchReduce")) {
				setSerializerDeserializer(function, GroupReduceFunction.class);
			} else if (operatorName.equals("filter")) {
				setDeserializer(function, FilterFunction.class);
				setSerializer(function, FilterFunction.class, 0);
			} else if (operatorName.equals("sink")) {
				setDeserializer(function, SinkFunction.class);
			} else if (operatorName.equals("source")) {
				setSerializer(function, UserSourceInvokable.class, 0);
			} else if (operatorName.equals("elements")) {
				outTupleTypeInfo = new TupleTypeInfo<OUT>(TypeExtractor.getForObject(function));

				outTupleSerializer = new StreamRecordSerializer<OUT>(outTupleTypeInfo.createSerializer());
				outSerializationDelegate = new SerializationDelegate<StreamRecord<OUT>>(
						outTupleSerializer);
			} else {
				throw new Exception("Wrong operator name!");
			}

		} catch (Exception e) {
			throw new StreamComponentException(e);
			// throw new StreamComponentException("Nonsupported object (named "
			// + operatorName
			// + ") passed as operator");
		}
	}

	private void setSerializerDeserializer(Object function, Class<? extends AbstractFunction> clazz) {
		setDeserializer(function, clazz);
		setSerializer(function, clazz, 1);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void setDeserializer(Object function, Class<? extends AbstractFunction> clazz) {
		inTupleTypeInfo = (TupleTypeInfo) TypeExtractor.createTypeInfo(clazz, function.getClass(),
				0, null, null);

		inTupleSerializer = new StreamRecordSerializer(inTupleTypeInfo.createSerializer());
		inDeserializationDelegate = new DeserializationDelegate<StreamRecord<IN>>(inTupleSerializer);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void setSerializer(Object function, Class<?> clazz, int typeParameter) {
		outTupleTypeInfo = (TupleTypeInfo) TypeExtractor.createTypeInfo(clazz, function.getClass(),
				typeParameter, null, null);

		outTupleSerializer = new StreamRecordSerializer(outTupleTypeInfo.createSerializer());
		outSerializationDelegate = new SerializationDelegate<StreamRecord<OUT>>(outTupleSerializer);
	}

	@SuppressWarnings("unchecked")
	protected void setSinkSerializer() {
		if (outSerializationDelegate != null) {
			inTupleTypeInfo = (TupleTypeInfo<IN>) outTupleTypeInfo;

			inTupleSerializer = new StreamRecordSerializer<IN>(inTupleTypeInfo.createSerializer());
			inDeserializationDelegate = new DeserializationDelegate<StreamRecord<IN>>(inTupleSerializer);
		}
	}

	@SuppressWarnings("unchecked")
	protected MutableReader<IOReadableWritable> getConfigInputs() throws StreamComponentException {
		int numberOfInputs = configuration.getInteger("numberOfInputs", 0);

		if (numberOfInputs < 2) {

			return new MutableRecordReader<IOReadableWritable>(this);

		} else {
			MutableRecordReader<IOReadableWritable>[] recordReaders = (MutableRecordReader<IOReadableWritable>[]) new MutableRecordReader<?>[numberOfInputs];

			for (int i = 0; i < numberOfInputs; i++) {
				recordReaders[i] = new MutableRecordReader<IOReadableWritable>(this);
			}
			return new MutableUnionRecordReader<IOReadableWritable>(recordReaders);
		}
	}

	protected MutableObjectIterator<StreamRecord<IN>> createInputIterator(MutableReader<?> inputReader,
			TypeSerializer<?> serializer) {

		// generic data type serialization
		@SuppressWarnings("unchecked")
		MutableReader<DeserializationDelegate<?>> reader = (MutableReader<DeserializationDelegate<?>>) inputReader;
		@SuppressWarnings({ "unchecked", "rawtypes" })
		final MutableObjectIterator<StreamRecord<IN>> iter = new ReaderIterator(reader, serializer);
		return iter;

	}

	protected void setConfigOutputs(List<RecordWriter<SerializationDelegate<StreamRecord<OUT>>>> outputs) {

		int numberOfOutputs = configuration.getInteger("numberOfOutputs", 0);

		for (int i = 0; i < numberOfOutputs; i++) {
			setPartitioner(i, outputs);
		}
	}

	private void setPartitioner(int outputNumber,
			List<RecordWriter<SerializationDelegate<StreamRecord<OUT>>>> outputs) {

		byte[] serializedPartitioner = configuration.getBytes("partitionerObject_" + outputNumber,
				SerializationUtils.serialize((new ShufflePartitioner<OUT>())));
		StreamPartitioner<OUT> outputPartitioner = null;

		try {
			outputPartitioner = deserializeObject(serializedPartitioner);

			RecordWriter<SerializationDelegate<StreamRecord<OUT>>> output = new RecordWriter<SerializationDelegate<StreamRecord<OUT>>>(
					this, outputPartitioner);
			outputs.add(output);
			String outputName = configuration.getString("outputName_" + outputNumber, null);

			if (collector != null) {
				collector.addOutput(output, outputName);
			}

			if (LOG.isTraceEnabled()) {
				LOG.trace("Partitioner set: " + outputPartitioner.getClass().getSimpleName()
						+ " with " + outputNumber + " outputs");
			}
		} catch (Exception e) {
			throw new StreamComponentException("Cannot deserialize partitioner "
					+ outputPartitioner.getClass().getSimpleName() + " with " + outputNumber
					+ " outputs");
		}
	}

	/**
	 * Reads and creates a StreamComponent from the config.
	 * 
	 * @param userFunctionClass
	 *            Class of the invokable function
	 * @return The StreamComponent object
	 */
	protected StreamComponentInvokable getInvokable(
			Class<? extends StreamComponentInvokable> userFunctionClass) {
		StreamComponentInvokable userFunction = null;

		byte[] userFunctionSerialized = configuration.getBytes("serializedudf", null);
		this.isMutable = configuration.getBoolean("isMutable", false);

		try {
			userFunction = deserializeObject(userFunctionSerialized);
		} catch (ClassNotFoundException e) {
			new StreamComponentException("Cannot instantiate user function: "
					+ userFunctionClass.getSimpleName());
		} catch (IOException e) {
			new StreamComponentException("Cannot instantiate user function: "
					+ userFunctionClass.getSimpleName());
		}

		return userFunction;
	}

	@SuppressWarnings("unchecked")
	private static <T> T deserializeObject(byte[] serializedObject) throws IOException,
			ClassNotFoundException {
		return (T) SerializationUtils.deserialize(serializedObject);
	}

	protected abstract void setInvokable();
}

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

package eu.stratosphere.streaming.api.streamcomponent;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ConcurrentModificationException;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import eu.stratosphere.api.java.functions.FlatMapFunction;
import eu.stratosphere.api.java.tuple.Tuple;
import eu.stratosphere.api.java.typeutils.TupleTypeInfo;
import eu.stratosphere.api.java.typeutils.TypeExtractor;
import eu.stratosphere.api.java.typeutils.runtime.TupleSerializer;
import eu.stratosphere.configuration.Configuration;
import eu.stratosphere.nephele.event.task.AbstractTaskEvent;
import eu.stratosphere.nephele.event.task.EventListener;
import eu.stratosphere.nephele.io.AbstractRecordReader;
import eu.stratosphere.nephele.io.ChannelSelector;
import eu.stratosphere.nephele.io.MutableRecordReader;
import eu.stratosphere.nephele.io.RecordWriter;
import eu.stratosphere.nephele.template.AbstractInvokable;
import eu.stratosphere.pact.runtime.plugable.DeserializationDelegate;
import eu.stratosphere.pact.runtime.plugable.SerializationDelegate;
import eu.stratosphere.streaming.api.StreamCollector;
import eu.stratosphere.streaming.api.invokable.DefaultSinkInvokable;
import eu.stratosphere.streaming.api.invokable.DefaultTaskInvokable;
import eu.stratosphere.streaming.api.invokable.RecordInvokable;
import eu.stratosphere.streaming.api.invokable.UserSinkInvokable;
import eu.stratosphere.streaming.api.invokable.UserSourceInvokable;
import eu.stratosphere.streaming.api.streamrecord.ArrayStreamRecord;
import eu.stratosphere.streaming.api.streamrecord.StreamRecord;
import eu.stratosphere.streaming.api.streamrecord.UID;
import eu.stratosphere.streaming.faulttolerance.AckEvent;
import eu.stratosphere.streaming.faulttolerance.AckEventListener;
import eu.stratosphere.streaming.faulttolerance.FailEvent;
import eu.stratosphere.streaming.faulttolerance.FailEventListener;
import eu.stratosphere.streaming.faulttolerance.FaultToleranceType;
import eu.stratosphere.streaming.faulttolerance.FaultToleranceUtil;
import eu.stratosphere.streaming.partitioner.DefaultPartitioner;
import eu.stratosphere.streaming.partitioner.FieldsPartitioner;

public final class StreamComponentHelper<T extends AbstractInvokable> {
	private static final Log log = LogFactory.getLog(StreamComponentHelper.class);
	private static int numComponents = 0;

	private TupleTypeInfo<Tuple> inTupleTypeInfo = null;
	private TupleSerializer<Tuple> inTupleSerializer = null;
	private DeserializationDelegate<Tuple> inDeserializationDelegate = null;

	private TupleTypeInfo<Tuple> outTupleTypeInfo = null;
	private TupleSerializer<Tuple> outTupleSerializer = null;
	private SerializationDelegate<Tuple> outSerializationDelegate = null;

	public StreamCollector<Tuple> collector;

	public static int newComponent() {
		numComponents++;
		return numComponents;
	}

	public RecordInvoker setFaultTolerance(FaultToleranceUtil util, FaultToleranceType type,
			Configuration config, List<RecordWriter<StreamRecord>> outputs, int taskInstanceID,
			String name, int[] numberOfOutputChannels) {
		type = FaultToleranceType.from(config.getInteger("faultToleranceType", 0));

		RecordInvoker invoker = getRecordInvoker(type);
		switch (type) {
		case AT_LEAST_ONCE:
		case EXACTLY_ONCE:
			util = new FaultToleranceUtil(type, outputs, taskInstanceID, name,
					numberOfOutputChannels);
			break;
		case NONE:
		default:
			util = null;
			break;
		}
		return invoker;
	}

	public RecordInvoker getRecordInvoker(FaultToleranceType type) {
		switch (type) {
		case AT_LEAST_ONCE:
		case EXACTLY_ONCE:
			return new InvokerWithFaultTolerance();
		case NONE:
		default:
			return new Invoker();
		}
	}

	public void setAckListener(FaultToleranceUtil recordBuffer, int sourceInstanceID,
			List<RecordWriter<StreamRecord>> outputs) {

		EventListener[] ackListeners = new EventListener[outputs.size()];

		for (int i = 0; i < outputs.size(); i++) {
			ackListeners[i] = new AckEventListener(sourceInstanceID, recordBuffer, i);
			outputs.get(i).subscribeToEvent(ackListeners[i], AckEvent.class);
		}

	}

	public void setFailListener(FaultToleranceUtil recordBuffer, int sourceInstanceID,
			List<RecordWriter<StreamRecord>> outputs) {

		EventListener[] failListeners = new EventListener[outputs.size()];

		for (int i = 0; i < outputs.size(); i++) {
			failListeners[i] = new FailEventListener(sourceInstanceID, recordBuffer, i);
			outputs.get(i).subscribeToEvent(failListeners[i], FailEvent.class);
		}

	}

	public void setSerializers(Configuration taskConfiguration) {
		byte[] operatorBytes = taskConfiguration.getBytes("operator", null);
		String operatorName = taskConfiguration.getString("operatorName", "");

		try {
			ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(operatorBytes));

			if (operatorName.equals("flatMap")) {

				FlatMapFunction<Tuple, Tuple> f = (FlatMapFunction<Tuple, Tuple>) in.readObject();

				inTupleTypeInfo = (TupleTypeInfo) TypeExtractor.createTypeInfo(
						FlatMapFunction.class, f.getClass(), 0, null, null);

				inTupleSerializer = inTupleTypeInfo.createSerializer();
				inDeserializationDelegate = new DeserializationDelegate<Tuple>(inTupleSerializer);

				outTupleTypeInfo = (TupleTypeInfo) TypeExtractor.createTypeInfo(
						FlatMapFunction.class, f.getClass(), 1, null, null);

				outTupleSerializer = outTupleTypeInfo.createSerializer();
				outSerializationDelegate = new SerializationDelegate<Tuple>(outTupleSerializer);

			} else if (operatorName.equals("sink")) {

				UserSinkInvokable<Tuple> f = (UserSinkInvokable<Tuple>) in.readObject();

				inTupleTypeInfo = (TupleTypeInfo) TypeExtractor.createTypeInfo(
						UserSinkInvokable.class, f.getClass(), 0, null, null);

				inTupleSerializer = inTupleTypeInfo.createSerializer();
				inDeserializationDelegate = new DeserializationDelegate<Tuple>(inTupleSerializer);
			} else if (operatorName.equals("source")) {

				UserSourceInvokable<Tuple> f = (UserSourceInvokable<Tuple>) in.readObject();

				outTupleTypeInfo = (TupleTypeInfo) TypeExtractor.createTypeInfo(
						UserSourceInvokable.class, f.getClass(), 0, null, null);

				outTupleSerializer = outTupleTypeInfo.createSerializer();
				outSerializationDelegate = new SerializationDelegate<Tuple>(outTupleSerializer);

			} else {

				throw new Exception();
			}
			collector = new StreamCollector<Tuple>(1, 1, outSerializationDelegate);

		} catch (Exception e) {
			throw new StreamComponentException("Nonsupported object passed as operator");

		}
	}

	public AbstractRecordReader getConfigInputs(T taskBase, Configuration taskConfiguration)
			throws StreamComponentException {
		int numberOfInputs = taskConfiguration.getInteger("numberOfInputs", 0);

		if (numberOfInputs < 2) {
			if (taskBase instanceof StreamTask) {
				return new StreamRecordReader((StreamTask) taskBase, ArrayStreamRecord.class,
						inDeserializationDelegate, inTupleSerializer);
			} else if (taskBase instanceof StreamSink) {
				return new StreamRecordReader((StreamSink) taskBase, ArrayStreamRecord.class,
						inDeserializationDelegate, inTupleSerializer);
			} else {
				throw new StreamComponentException("Nonsupported object passed to setConfigInputs");
			}
		} else {
			@SuppressWarnings("unchecked")
			MutableRecordReader<StreamRecord>[] recordReaders = (MutableRecordReader<StreamRecord>[]) new MutableRecordReader<?>[numberOfInputs];

			for (int i = 0; i < numberOfInputs; i++) {

				if (taskBase instanceof StreamTask) {
					recordReaders[i] = new MutableRecordReader<StreamRecord>((StreamTask) taskBase);
				} else if (taskBase instanceof StreamSink) {
					recordReaders[i] = new MutableRecordReader<StreamRecord>((StreamSink) taskBase);
				} else {
					throw new StreamComponentException(
							"Nonsupported object passed to setConfigInputs");
				}
			}
			return new UnionStreamRecordReader(recordReaders, ArrayStreamRecord.class,
					inDeserializationDelegate, inTupleSerializer);
		}
	}

	public void setConfigOutputs(T taskBase, Configuration taskConfiguration,
			List<RecordWriter<StreamRecord>> outputs,
			List<ChannelSelector<StreamRecord>> partitioners) throws StreamComponentException {

		if (taskBase instanceof StreamSource) {
			byte[] bytes = taskConfiguration.getBytes("operator", null);

			ObjectInputStream in;
			try {
				in = new ObjectInputStream(new ByteArrayInputStream(bytes));
				

				collector = new StreamCollector<Tuple>(1, 1, outSerializationDelegate);

			} catch (Exception e) {

			}
		}

		int numberOfOutputs = taskConfiguration.getInteger("numberOfOutputs", 0);
		for (int i = 0; i < numberOfOutputs; i++) {
			setPartitioner(taskConfiguration, i, partitioners);
		}
		for (ChannelSelector<StreamRecord> outputPartitioner : partitioners) {
			if (taskBase instanceof StreamTask) {
				outputs.add(new RecordWriter<StreamRecord>((StreamTask) taskBase,
						StreamRecord.class, outputPartitioner));
			} else if (taskBase instanceof StreamSource) {
				outputs.add(new RecordWriter<StreamRecord>((StreamSource) taskBase,
						StreamRecord.class, outputPartitioner));
			} else {
				throw new StreamComponentException("Nonsupported object passed to setConfigOutputs");
			}
		}
		collector.setOutputs(outputs);
	}

	public UserSinkInvokable getUserFunction(Configuration taskConfiguration) {

		Class<? extends UserSinkInvokable> userFunctionClass = taskConfiguration.getClass(
				"userfunction", DefaultSinkInvokable.class, UserSinkInvokable.class);
		UserSinkInvokable userFunction = null;

		byte[] userFunctionSerialized = taskConfiguration.getBytes("serializedudf", null);

		try {
			ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(
					userFunctionSerialized));
			userFunction = (UserSinkInvokable) ois.readObject();
		} catch (Exception e) {
			if (log.isErrorEnabled()) {
				log.error("Cannot instanciate user function: " + userFunctionClass.getSimpleName());
			}
		}

		return userFunction;
	}

	// TODO consider logging stack trace!
	@SuppressWarnings("unchecked")
	public StreamInvokableComponent getUserFunction(Configuration taskConfiguration,
			List<RecordWriter<StreamRecord>> outputs, int instanceID, String name,
			FaultToleranceUtil recordBuffer) {

		// Default value is a TaskInvokable even if it was called from a source
		Class<? extends StreamInvokableComponent> userFunctionClass = taskConfiguration.getClass(
				"userfunction", DefaultTaskInvokable.class, StreamInvokableComponent.class);
		StreamInvokableComponent userFunction = null;
		FaultToleranceType faultToleranceType = FaultToleranceType.from(taskConfiguration
				.getInteger("faultToleranceBuffer", 0));

		byte[] userFunctionSerialized = taskConfiguration.getBytes("serializedudf", null);

		try {
			ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(
					userFunctionSerialized));
			userFunction = (StreamInvokableComponent) ois.readObject();
			// userFunction.declareOutputs(outputs, instanceID, name,
			// recordBuffer,
			// faultToleranceType);
		} catch (Exception e) {
			if (log.isErrorEnabled()) {

				log.error("Cannot instanciate user function: " + userFunctionClass.getSimpleName());
			}
		}

		return userFunction;
	}

	// TODO find a better solution for this
	public void threadSafePublish(AbstractTaskEvent event, AbstractRecordReader inputs)
			throws InterruptedException, IOException {

		boolean concurrentModificationOccured = false;
		while (!concurrentModificationOccured) {
			try {
				inputs.publishEvent(event);
				concurrentModificationOccured = true;
			} catch (ConcurrentModificationException exeption) {
				if (log.isTraceEnabled()) {
					log.trace("Waiting to publish " + event.getClass());
				}
			}
		}
	}

	private void setPartitioner(Configuration taskConfiguration, int nrOutput,
			List<ChannelSelector<StreamRecord>> partitioners) {
		Class<? extends ChannelSelector<StreamRecord>> partitioner = taskConfiguration.getClass(
				"partitionerClass_" + nrOutput, DefaultPartitioner.class, ChannelSelector.class);

		try {
			if (partitioner.equals(FieldsPartitioner.class)) {
				int keyPosition = taskConfiguration
						.getInteger("partitionerIntParam_" + nrOutput, 1);

				partitioners.add(partitioner.getConstructor(int.class).newInstance(keyPosition));

			} else {
				partitioners.add(partitioner.newInstance());
			}
			if (log.isTraceEnabled()) {
				log.trace("Partitioner set: " + partitioner.getSimpleName() + " with " + nrOutput
						+ " outputs");
			}
		} catch (Exception e) {
			if (log.isErrorEnabled()) {
				log.error("Error while setting partitioner: " + partitioner.getSimpleName()
						+ " with " + nrOutput + " outputs", e);
			}
		}
	}

	public void invokeRecords(RecordInvoker invoker, RecordInvokable userFunction,
			AbstractRecordReader inputs, String name) throws Exception {
		if (inputs instanceof UnionStreamRecordReader) {
			UnionStreamRecordReader recordReader = (UnionStreamRecordReader) inputs;
			while (recordReader.hasNext()) {
				StreamRecord record = recordReader.next();
				invoker.call(name, userFunction, recordReader, record);
			}

		} else if (inputs instanceof StreamRecordReader) {
			StreamRecordReader recordReader = (StreamRecordReader) inputs;

			while (recordReader.hasNext()) {
				StreamRecord record = recordReader.next();
				invoker.call(name, userFunction, recordReader, record);
			}
		}
	}

	public static interface RecordInvoker {
		void call(String name, RecordInvokable userFunction, AbstractRecordReader inputs,
				StreamRecord record) throws Exception;
	}

	public class InvokerWithFaultTolerance implements RecordInvoker {

		@Override
		public void call(String name, RecordInvokable userFunction, AbstractRecordReader inputs,
				StreamRecord record) throws Exception {
			UID id = record.getId();
			userFunction.invoke(record, collector);
			// threadSafePublish(new AckEvent(id), inputs);
			// if (log.isDebugEnabled()) {
			// log.debug("ACK: " + id + " -- " + name);
			// }
		}
	}

	public class Invoker implements RecordInvoker {
		@Override
		public void call(String name, RecordInvokable userFunction, AbstractRecordReader inputs,
				StreamRecord record) throws Exception {
			userFunction.invoke(record, collector);
		}
	}

}
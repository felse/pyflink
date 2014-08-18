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

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.runtime.io.network.api.RecordWriter;
import org.apache.flink.runtime.plugable.SerializationDelegate;
import org.apache.flink.streaming.api.streamrecord.StreamRecord;

public class StreamIterationSource<OUT extends Tuple> extends
		SingleInputAbstractStreamComponent<Tuple, OUT> {

	private static final Log LOG = LogFactory.getLog(StreamIterationSource.class);

	private List<RecordWriter<SerializationDelegate<StreamRecord<OUT>>>> outputs;
	private static int numSources;
	private String iterationId;
	@SuppressWarnings("rawtypes")
	private BlockingQueue<StreamRecord> dataChannel;

	@SuppressWarnings("rawtypes")
	public StreamIterationSource() {

		outputs = new LinkedList<RecordWriter<SerializationDelegate<StreamRecord<OUT>>>>();
		numSources = newComponent();
		instanceID = numSources;
		dataChannel = new ArrayBlockingQueue<StreamRecord>(1);
	}

	@Override
	public void setInputsOutputs() {
		try {
			setConfigOutputs(outputs);
			setSinkSerializer();
		} catch (StreamComponentException e) {
			e.printStackTrace();
			throw new StreamComponentException("Cannot register outputs", e);
		}

		iterationId = configuration.getIterationId();
		try {
			BlockingQueueBroker.instance().handIn(iterationId, dataChannel);
		} catch (Exception e) {

		}

	}

	@Override
	public void invoke() throws Exception {
		if (LOG.isDebugEnabled()) {
			LOG.debug("SOURCE " + name + " invoked with instance id " + instanceID);
		}

		for (RecordWriter<SerializationDelegate<StreamRecord<OUT>>> output : outputs) {
			output.initializeSerializers();
		}

		while (true) {
			@SuppressWarnings("unchecked")
			StreamRecord<OUT> nextRecord = dataChannel.poll(3, TimeUnit.SECONDS);
			if (nextRecord == null) {
				break;
			}
			for (RecordWriter<SerializationDelegate<StreamRecord<OUT>>> output : outputs) {
				outSerializationDelegate.setInstance(nextRecord);
				output.emit(outSerializationDelegate);
			}
		}

		for (RecordWriter<SerializationDelegate<StreamRecord<OUT>>> output : outputs) {
			output.flush();
		}

	}

	@Override
	protected void setInvokable() {
	}
}

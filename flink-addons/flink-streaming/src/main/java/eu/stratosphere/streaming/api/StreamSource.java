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

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import eu.stratosphere.configuration.Configuration;
import eu.stratosphere.nephele.event.task.EventListener;
import eu.stratosphere.nephele.io.ChannelSelector;
import eu.stratosphere.nephele.io.RecordWriter;
import eu.stratosphere.nephele.template.AbstractInputTask;
import eu.stratosphere.streaming.api.invokable.DefaultSourceInvokable;
import eu.stratosphere.streaming.api.invokable.UserSourceInvokable;
import eu.stratosphere.streaming.partitioner.DefaultPartitioner;
import eu.stratosphere.streaming.partitioner.FieldsPartitioner;
import eu.stratosphere.streaming.test.RandIS;
import eu.stratosphere.types.Key;
import eu.stratosphere.types.Record;
import eu.stratosphere.types.StringValue;

public class StreamSource extends AbstractInputTask<RandIS> {

	private List<RecordWriter<Record>> outputs;
	private List<ChannelSelector<Record>> partitioners;
	private UserSourceInvokable userFunction;
	private int numberOfOutputs;
	
	private static int numSources = 0;
	private String sourceInstanceID;
	private Map<String, StreamRecord> recordBuffer;

	public StreamSource() {
		// TODO: Make configuration file visible and call setClassInputs() here
		outputs = new LinkedList<RecordWriter<Record>>();
		partitioners = new LinkedList<ChannelSelector<Record>>();
		userFunction = null;
		numberOfOutputs = 0;
		numSources++;
		sourceInstanceID = Integer.toString(numSources);
		recordBuffer = new TreeMap<String, StreamRecord>();
	}

	@Override
	public RandIS[] computeInputSplits(int requestedMinNumber) throws Exception {
		return null;
	}

	@Override
	public Class<RandIS> getInputSplitType() {
		return null;
	}

	private void setConfigInputs() {
		Configuration taskConfiguration = getTaskConfiguration();

		numberOfOutputs = taskConfiguration.getInteger("numberOfOutputs", 0);

		for (int i = 1; i <= numberOfOutputs; i++) {
			setPartitioner(taskConfiguration, i);
		}

		for (ChannelSelector<Record> outputPartitioner : partitioners) {
			outputs.add(new RecordWriter<Record>(this, Record.class,
					outputPartitioner));
		}

		setUserFunction(taskConfiguration);
		setAckListener();

	}

	public void setUserFunction(Configuration taskConfiguration) {

		Class<? extends UserSourceInvokable> userFunctionClass = taskConfiguration
				.getClass("userfunction", DefaultSourceInvokable.class,
						UserSourceInvokable.class);

		try {
			this.userFunction = userFunctionClass.newInstance();
			this.userFunction.declareOutputs(outputs, sourceInstanceID, recordBuffer);
		} catch (Exception e) {

		}

	}

	private void setPartitioner(Configuration taskConfiguration, int nrOutput) {
		Class<? extends ChannelSelector<Record>> partitioner = taskConfiguration
				.getClass("partitionerClass_" + nrOutput, DefaultPartitioner.class,
						ChannelSelector.class);

		try {
			if (partitioner.equals(FieldsPartitioner.class)) {
				int keyPosition = taskConfiguration.getInteger("partitionerIntParam_"
						+ nrOutput, 1);
				Class<? extends Key> keyClass = taskConfiguration.getClass(
						"partitionerClassParam_" + nrOutput, StringValue.class, Key.class);

				partitioners.add(partitioner.getConstructor(int.class, Class.class)
						.newInstance(keyPosition, keyClass));

			} else {
				partitioners.add(partitioner.newInstance());
			}
		} catch (Exception e) {
			System.out.println("partitioner error" + " " + "partitioner_" + nrOutput);
			System.out.println(e);
		}

	}

	public void setAckListener() {
		EventListener eventListener = new AckEventListener(sourceInstanceID,
				recordBuffer);
		for (RecordWriter output : outputs) {
			// TODO: separate outputs
			output.subscribeToEvent(eventListener, AckEvent.class);
		}
	}

	@Override
	public void registerInputOutput() {
		setConfigInputs();

	}

	@Override
	public void invoke() throws Exception {

		userFunction.invoke();
		System.out.println(this.getClass().getName() + "-" + sourceInstanceID);
		System.out.println(recordBuffer.toString());
		System.out.println("---------------------");

	}

}

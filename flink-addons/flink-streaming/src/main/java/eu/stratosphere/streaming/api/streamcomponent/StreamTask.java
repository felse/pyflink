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

import java.util.LinkedList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import eu.stratosphere.configuration.Configuration;
import eu.stratosphere.nephele.execution.Environment;
import eu.stratosphere.nephele.io.AbstractRecordReader;
import eu.stratosphere.nephele.io.ChannelSelector;
import eu.stratosphere.nephele.io.RecordWriter;
import eu.stratosphere.nephele.template.AbstractTask;
import eu.stratosphere.streaming.api.invokable.UserTaskInvokable;
import eu.stratosphere.streaming.api.streamcomponent.StreamComponentHelper.RecordInvoker;
import eu.stratosphere.streaming.api.streamrecord.StreamRecord;
import eu.stratosphere.streaming.faulttolerance.FaultToleranceType;
import eu.stratosphere.streaming.faulttolerance.FaultToleranceUtil;

public class StreamTask extends AbstractTask {

	private static final Log log = LogFactory.getLog(StreamTask.class);

	private AbstractRecordReader inputs;
	private List<RecordWriter<StreamRecord>> outputs;
	private List<ChannelSelector<StreamRecord>> partitioners;
	private UserTaskInvokable userFunction;
	private static int numTasks;
	private int taskInstanceID;
	private String name;
	private StreamComponentHelper<StreamTask> streamTaskHelper;
	private FaultToleranceType faultToleranceType;
	private RecordInvoker invoker;
	Configuration taskConfiguration;

	private FaultToleranceUtil recordBuffer;

	public StreamTask() {
		// TODO: Make configuration file visible and call setClassInputs() here
		outputs = new LinkedList<RecordWriter<StreamRecord>>();
		partitioners = new LinkedList<ChannelSelector<StreamRecord>>();
		userFunction = null;
		numTasks = StreamComponentHelper.newComponent();
		taskInstanceID = numTasks;
		streamTaskHelper = new StreamComponentHelper<StreamTask>();
	}

	@Override
	public void registerInputOutput() {
		Environment env=this.getEnvironment();
		taskConfiguration = getTaskConfiguration();
		name = taskConfiguration.getString("componentName", "MISSING_COMPONENT_NAME");

		try {
			streamTaskHelper.setSerializers(taskConfiguration);
			inputs = streamTaskHelper.getConfigInputs(this, taskConfiguration);
			streamTaskHelper.setConfigOutputs(this, taskConfiguration, outputs, partitioners);
		} catch (StreamComponentException e) {
			if (log.isErrorEnabled()) {
				log.error("Cannot register inputs/outputs for " + getClass().getSimpleName(), e);
			}
		}

		int[] numberOfOutputChannels = new int[outputs.size()];
		for (int i = 0; i < numberOfOutputChannels.length; i++) {
			numberOfOutputChannels[i] = taskConfiguration.getInteger("channels_" + i, 0);
		}

		invoker = streamTaskHelper.setFaultTolerance(recordBuffer, faultToleranceType,
				taskConfiguration, outputs, taskInstanceID, name, numberOfOutputChannels);

		userFunction = (UserTaskInvokable) streamTaskHelper.getUserFunction(taskConfiguration,
				outputs, taskInstanceID, name, recordBuffer);

		streamTaskHelper.setAckListener(recordBuffer, taskInstanceID, outputs);
		streamTaskHelper.setFailListener(recordBuffer, taskInstanceID, outputs);
	}

	@Override
	public void invoke() throws Exception {
		if (log.isDebugEnabled()) {
			log.debug("TASK " + name + " invoked with instance id " + taskInstanceID);
		}
		streamTaskHelper.invokeRecords(invoker, userFunction, inputs, name);

		// TODO print to file
		System.out.println(userFunction.getResult());
		if (log.isDebugEnabled()) {
			log.debug("TASK " + name + " invoke finished with instance id " + taskInstanceID);
		}
	}
}
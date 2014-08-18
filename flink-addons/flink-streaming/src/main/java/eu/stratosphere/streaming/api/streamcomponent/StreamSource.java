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
import eu.stratosphere.pact.runtime.task.DataSourceTask;
import eu.stratosphere.runtime.io.api.ChannelSelector;
import eu.stratosphere.runtime.io.api.RecordWriter;
import eu.stratosphere.streaming.api.invokable.UserSourceInvokable;
import eu.stratosphere.streaming.api.streamrecord.StreamRecord;
import eu.stratosphere.streaming.examples.DummyIS;
import eu.stratosphere.streaming.faulttolerance.FaultToleranceType;
import eu.stratosphere.streaming.faulttolerance.FaultToleranceUtil;

public class StreamSource extends DataSourceTask<DummyIS> {

	private static final Log log = LogFactory.getLog(StreamSource.class);

	private List<RecordWriter<StreamRecord>> outputs;
	private List<ChannelSelector<StreamRecord>> partitioners;
	private UserSourceInvokable userFunction;
	private static int numSources;
	private int sourceInstanceID;
	private String name;
	private FaultToleranceUtil recordBuffer;
	private FaultToleranceType faultToleranceType;
	StreamComponentHelper<StreamSource> streamSourceHelper;

	public StreamSource() {
		// TODO: Make configuration file visible and call setClassInputs() here
		outputs = new LinkedList<RecordWriter<StreamRecord>>();
		partitioners = new LinkedList<ChannelSelector<StreamRecord>>();
		userFunction = null;
		streamSourceHelper = new StreamComponentHelper<StreamSource>();
		numSources = StreamComponentHelper.newComponent();
		sourceInstanceID = numSources;
	}

	@Override
	public void registerInputOutput() {
		Configuration taskConfiguration = getTaskConfiguration();
		name = taskConfiguration.getString("componentName", "MISSING_COMPONENT_NAME");

		try {
			streamSourceHelper.setSerializers(taskConfiguration);
			streamSourceHelper.setConfigOutputs(this, taskConfiguration, outputs, partitioners);
			streamSourceHelper.setCollector(taskConfiguration, sourceInstanceID, outputs);
		} catch (StreamComponentException e) {
			if (log.isErrorEnabled()) {
				log.error("Cannot register outputs", e);
			}
		}

		int[] numberOfOutputChannels = new int[outputs.size()];
		for (int i = 0; i < numberOfOutputChannels.length; i++) {
			numberOfOutputChannels[i] = taskConfiguration.getInteger("channels_" + i, 0);
		}

		userFunction = (UserSourceInvokable) streamSourceHelper
				.getSourceInvokable(taskConfiguration);
		streamSourceHelper.setAckListener(recordBuffer, sourceInstanceID, outputs);
		streamSourceHelper.setFailListener(recordBuffer, sourceInstanceID, outputs);
	}

	@Override
	public void invoke() throws Exception {
		if (log.isDebugEnabled()) {
			log.debug("SOURCE " + name + " invoked with instance id " + sourceInstanceID);
		}
		userFunction.invoke(streamSourceHelper.collector);
	}

}

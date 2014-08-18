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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.runtime.io.network.api.AbstractRecordReader;
import org.apache.flink.streaming.api.streamrecord.StreamRecord;
import org.apache.flink.util.StringUtils;

public class StreamIterationSink<IN extends Tuple> extends AbstractStreamComponent<IN, IN> {

	private static final Log LOG = LogFactory.getLog(StreamIterationSink.class);

	private AbstractRecordReader inputs;
	private String iterationId;
	@SuppressWarnings("rawtypes")
	private BlockingQueue<StreamRecord> dataChannel;

	public StreamIterationSink() {
	}

	@Override
	public void registerInputOutput() {
		initialize();

		try {
			setSerializers();
			setSinkSerializer();
			inputs = getConfigInputs();
			iterationId = configuration.getString("iteration-id", "iteration-0");
			dataChannel = BlockingQueueBroker.instance().getAndRemove(iterationId);
		} catch (Exception e) {
			throw new StreamComponentException(String.format(
					"Cannot register inputs of StreamIterationSink %s", iterationId), e);
		}
	}

	@Override
	public void invoke() throws Exception {
		if (LOG.isDebugEnabled()) {
			LOG.debug("SINK " + name + " invoked");
		}

		forwardRecords(inputs);

		if (LOG.isDebugEnabled()) {
			LOG.debug("SINK " + name + " invoke finished");
		}
	}

	@SuppressWarnings("unchecked")
	protected void forwardRecords(AbstractRecordReader inputs) throws Exception {
		if (inputs instanceof UnionStreamRecordReader) {
			UnionStreamRecordReader<IN> recordReader = (UnionStreamRecordReader<IN>) inputs;
			while (recordReader.hasNext()) {
				StreamRecord<IN> record = recordReader.next();
				pushToQueue(record);
			}

		} else if (inputs instanceof StreamRecordReader) {
			StreamRecordReader<IN> recordReader = (StreamRecordReader<IN>) inputs;

			while (recordReader.hasNext()) {
				StreamRecord<IN> record = recordReader.next();
				pushToQueue(record);
			}
		}
	}

	private void pushToQueue(StreamRecord<IN> record) {
		try {
			dataChannel.offer(record, 5, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			if (LOG.isErrorEnabled()) {
				LOG.error(String.format("Pushing back record at iteration %s failed due to: %s",
						iterationId, StringUtils.stringifyException(e)));
			}
		}
	}

	@Override
	protected void setInvokable() {

	}
}

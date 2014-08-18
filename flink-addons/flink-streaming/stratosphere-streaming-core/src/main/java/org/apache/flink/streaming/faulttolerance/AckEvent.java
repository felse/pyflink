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

package org.apache.flink.streaming.faulttolerance;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.apache.flink.streaming.api.streamrecord.UID;

import org.apache.flink.nephele.event.task.AbstractTaskEvent;

/**
 * TaskEvent for sending record acknowledgements to the input's fault tolerance
 * buffer
 */
public class AckEvent extends AbstractTaskEvent {

	private UID recordId;

	/**
	 * Creates a new event to acknowledge the record with the given ID
	 * 
	 * @param recordId
	 *            ID of the record to be acknowledged
	 */
	public AckEvent(UID recordId) {
		this.recordId = recordId;
	}

	public AckEvent() {
		recordId = new UID();
	}

	@Override
	public void write(DataOutput out) throws IOException {
		recordId.write(out);
	}

	@Override
	public void read(DataInput in) throws IOException {
		recordId = new UID();
		recordId.read(in);
	}

	public UID getRecordId() {
		return recordId;
	}

}
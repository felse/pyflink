/***********************************************************************************************************************
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
 **********************************************************************************************************************/

package org.apache.flink.streaming.faulttolerance;

import java.util.LinkedList;
import java.util.List;

import org.apache.flink.streaming.api.streamrecord.StreamRecord;
import org.apache.flink.streaming.faulttolerance.FaultToleranceType;
import org.apache.flink.streaming.faulttolerance.FaultToleranceUtil;
import org.junit.Before;
import org.junit.Test;

import org.apache.flink.runtime.io.network.api.RecordWriter;

public class FaultToleranceUtilTest {

	FaultToleranceUtil faultTolerancyBuffer;
	List<RecordWriter<StreamRecord>> outputs;

	@Before
	public void setFaultTolerancyBuffer() {
		outputs = new LinkedList<RecordWriter<StreamRecord>>();
		int[] numOfOutputchannels = { 1, 2 };
		faultTolerancyBuffer = new FaultToleranceUtil(FaultToleranceType.EXACTLY_ONCE, outputs, 1, numOfOutputchannels);
	}

	@Test
	public void testFaultTolerancyBuffer() {
		
	}

	@Test
	public void testAddRecord() {
		
	}

	@Test
	public void testAddTimestamp() {
		
	}

	@Test
	public void testPopRecord() {
		
	}

	@Test
	public void testRemoveRecord() {
		
	}

	@Test
	public void testAckRecord() {
		
	}

	@Test
	public void testFailRecord() {
		
	}

	// TODO: create more tests for this method
	@Test
	public void testTimeOutRecords() {
		
	}
}

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

package org.apache.flink.streaming.partitioner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apache.flink.streaming.api.streamrecord.ArrayStreamRecord;
import org.apache.flink.streaming.api.streamrecord.StreamRecord;
import org.apache.flink.streaming.partitioner.DefaultPartitioner;
import org.junit.Before;
import org.junit.Test;

//Currently implemented as a ShufflePartitioner
public class DefaultPartitionerTest {

	private DefaultPartitioner defaultPartitioner;
	private StreamRecord streamRecord = new ArrayStreamRecord();

	@Before
	public void setPartitioner() {
		defaultPartitioner = new DefaultPartitioner();
	}

	@Test
	public void testSelectChannelsLength() {
		assertEquals(1,
				defaultPartitioner.selectChannels(streamRecord, 1).length);
		assertEquals(1,
				defaultPartitioner.selectChannels(streamRecord, 2).length);
		assertEquals(1,
				defaultPartitioner.selectChannels(streamRecord, 1024).length);
	}

	@Test
	public void testSelectChannelsInterval() {
		assertEquals(0, defaultPartitioner.selectChannels(streamRecord, 1)[0]);

		assertTrue(0 <= defaultPartitioner.selectChannels(streamRecord, 2)[0]);
		assertTrue(2 > defaultPartitioner.selectChannels(streamRecord, 2)[0]);

		assertTrue(0 <= defaultPartitioner.selectChannels(streamRecord, 1024)[0]);
		assertTrue(1024 > defaultPartitioner.selectChannels(streamRecord, 1024)[0]);
	}
}
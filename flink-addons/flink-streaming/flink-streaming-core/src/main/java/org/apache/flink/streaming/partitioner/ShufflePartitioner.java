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

package org.apache.flink.streaming.partitioner;

import java.util.Random;

import org.apache.flink.streaming.api.streamrecord.StreamRecord;

import org.apache.flink.runtime.io.network.api.ChannelSelector;

//Randomly group, to distribute equally
public class ShufflePartitioner implements ChannelSelector<StreamRecord> {

	private Random random = new Random();
	
	private int[] returnArray;
	
	public ShufflePartitioner(){
		this.random = new Random();
		this.returnArray = new int[1];
	}

	@Override
	public int[] selectChannels(StreamRecord record, int numberOfOutputChannels) {
		returnArray[0] = random.nextInt(numberOfOutputChannels);
		return returnArray;
	}
}

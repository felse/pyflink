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

package org.apache.flink.streaming.state;

import java.io.Serializable;
import java.util.ArrayList;

import org.apache.commons.collections.buffer.CircularFifoBuffer;

import org.apache.flink.api.java.tuple.Tuple;

/**
 * The window state for window operator. To be general enough, this class
 * implements a count based window operator. It is possible for the user to
 * compose time based window operator by extending this class by splitting the
 * stream into multiple mini batches.
 */
public class SlidingWindowState<InTuple extends Tuple> implements Serializable {
	private static final long serialVersionUID = -2376149970115888901L;
	private int currentRecordCount;
	private int fullRecordCount;
	private int slideRecordCount;

	CircularFifoBuffer buffer;

	public SlidingWindowState(int windowSize, int slidingStep, int computeGranularity) {
		this.currentRecordCount = 0;
		// here we assume that windowSize and slidingStep is divisible by
		// computeGranularity.
		this.fullRecordCount = windowSize / computeGranularity;
		this.slideRecordCount = slidingStep / computeGranularity;
		this.buffer = new CircularFifoBuffer(fullRecordCount);
	}

	public void pushBack(ArrayList<InTuple> tupleArray) {
		buffer.add(tupleArray);
		currentRecordCount += 1;
	}

	@SuppressWarnings("unchecked")
	public ArrayList<InTuple> popFront() {
		ArrayList<InTuple> frontRecord = (ArrayList<InTuple>) buffer.get();
		buffer.remove();
		return frontRecord;
	}

	public boolean isFull() {
		return currentRecordCount >= fullRecordCount;
	}

	public boolean isEmittable() {
		if (currentRecordCount == fullRecordCount + slideRecordCount) {
			currentRecordCount -= slideRecordCount;
			return true;
		}
		return false;
	}

}

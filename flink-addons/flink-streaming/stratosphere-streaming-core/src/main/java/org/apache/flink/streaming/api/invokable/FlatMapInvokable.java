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

package org.apache.flink.streaming.api.invokable;

import org.apache.flink.streaming.api.streamrecord.StreamRecord;

import org.apache.flink.api.java.functions.FlatMapFunction;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.util.Collector;

public class FlatMapInvokable<T extends Tuple, R extends Tuple> extends UserTaskInvokable<T, R> {
	private static final long serialVersionUID = 1L;

	private FlatMapFunction<T, R> flatMapper;
	public FlatMapInvokable(FlatMapFunction<T, R> flatMapper) {
		this.flatMapper = flatMapper;
	}
	
	@Override
	public void invoke(StreamRecord record, Collector<R> collector) throws Exception {
		int batchSize = record.getBatchSize();
		for (int i = 0; i < batchSize; i++) {
			@SuppressWarnings("unchecked")
			T tuple = (T) record.getTuple(i);
			flatMapper.flatMap(tuple, collector);
		}
	}		
}

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

package org.apache.flink.streaming.api.invokable;

import org.apache.flink.streaming.api.streamrecord.StreamRecord;

import org.apache.flink.api.java.functions.FilterFunction;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.util.Collector;

public class FilterInvokable<IN extends Tuple> extends UserTaskInvokable<IN, IN> {

	private static final long serialVersionUID = 1L;

	FilterFunction<IN> filterFunction;

	public FilterInvokable(FilterFunction<IN> filterFunction) {
		this.filterFunction = filterFunction;
	}

	@Override
	public void invoke(StreamRecord record, Collector<IN> collector) throws Exception {
		for (int i = 0; i < record.getBatchSize(); i++) {
			@SuppressWarnings("unchecked")
			IN tuple = (IN) record.getTuple(i);
			if (filterFunction.filter(tuple)) {
				collector.collect(tuple);
			}
		}
	}
}

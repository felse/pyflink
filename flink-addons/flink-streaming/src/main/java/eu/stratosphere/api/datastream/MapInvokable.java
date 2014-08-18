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

package eu.stratosphere.api.datastream;

import eu.stratosphere.api.java.functions.MapFunction;
import eu.stratosphere.api.java.tuple.Tuple;
import eu.stratosphere.streaming.api.StreamCollector;
import eu.stratosphere.streaming.api.invokable.UserTaskInvokable;
import eu.stratosphere.streaming.api.streamrecord.StreamRecord;

public class MapInvokable<T extends Tuple, R extends Tuple> extends UserTaskInvokable<T, R> {
	private static final long serialVersionUID = 1L;

	private MapFunction<T, R> mapper;
	public MapInvokable(MapFunction<T, R> mapper) {
		this.mapper = mapper;
	}
	
	@Override
	public void invoke(StreamRecord record, StreamCollector<R> collector) throws Exception {
		int batchSize = record.getBatchSize();
		for (int i = 0; i < batchSize; i++) {
			@SuppressWarnings("unchecked")
			T tuple = (T) record.getTuple(i);
			collector.collect(mapper.map(tuple));
		}
	}		
}

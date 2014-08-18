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

package org.apache.flink.streaming.api.invokable;

import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.streaming.api.streamrecord.StreamRecord;
import org.apache.flink.streaming.api.streamrecord.StreamRecordSerializer;
import org.apache.flink.util.Collector;
import org.apache.flink.util.MutableObjectIterator;

public abstract class StreamRecordInvokable<IN extends Tuple, OUT extends Tuple> extends
		StreamComponentInvokable {

	private static final long serialVersionUID = 1L;

	protected Collector<OUT> collector;
	protected MutableObjectIterator<StreamRecord<IN>> recordIterator;
	StreamRecordSerializer<IN> serializer;
	protected StreamRecord<IN> reuse;
	protected boolean isMutable;

	public void initialize(Collector<OUT> collector,
			MutableObjectIterator<StreamRecord<IN>> recordIterator,
			StreamRecordSerializer<IN> serializer, boolean isMutable) {
		this.collector = collector;
		this.recordIterator = recordIterator;
		this.serializer = serializer;
		this.reuse = serializer.createInstance();
		this.isMutable = isMutable;
	}

	protected void resetReuse() {
		this.reuse = serializer.createInstance();
	}

	public abstract void invoke() throws Exception;
}

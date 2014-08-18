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

package org.apache.flink.streaming.api.collector;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

import org.apache.flink.api.java.functions.MapFunction;
import org.apache.flink.api.java.tuple.Tuple1;
import org.apache.flink.streaming.api.DataStream;
import org.apache.flink.streaming.api.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.collector.OutputSelector;
import org.apache.flink.streaming.api.function.SinkFunction;
import org.junit.Test;

public class DirectedOutputTest {

	static HashSet<Long> evenSet = new HashSet<Long>();
	static HashSet<Long> oddSet = new HashSet<Long>();
	
	private static class PlusTwo extends MapFunction<Tuple1<Long>, Tuple1<Long>> {
	
		private static final long serialVersionUID = 1L;

		@Override
		public Tuple1<Long> map(Tuple1<Long> arg0) throws Exception {
			arg0.f0 += 2;
			return arg0;
		}
	}

	private static class EvenSink extends SinkFunction<Tuple1<Long>> {

		private static final long serialVersionUID = 1L;

		@Override
		public void invoke(Tuple1<Long> tuple) {
			evenSet.add(tuple.f0);
		}
	}
	
	private static class OddSink extends SinkFunction<Tuple1<Long>> {

		private static final long serialVersionUID = 1L;

		@Override
		public void invoke(Tuple1<Long> tuple) {
			oddSet.add(tuple.f0);
		}
	}
	
	
	private static class MySelector extends OutputSelector<Tuple1<Long>> {
		
		private static final long serialVersionUID = 1L;

		@Override
		public void select(Tuple1<Long> tuple, Collection<String> outputs) {
			int mod = (int) (tuple.f0 % 2);
			switch (mod) {
				case 0:
					outputs.add("ds1");
					break;
				case 1:
					outputs.add("ds2");
					break;
			}
		}
	}
	

	@SuppressWarnings("unused")
	@Test
	public void directOutputTest() {
		StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment(1);
		DataStream<Tuple1<Long>> s = env.generateSequence(1, 6).directTo(new MySelector());
		DataStream<Tuple1<Long>> ds1 = s.map(new PlusTwo()).name("ds1").addSink(new EvenSink());
		DataStream<Tuple1<Long>> ds2 = s.map(new PlusTwo()).name("ds2").addSink(new OddSink());
		DataStream<Tuple1<Long>> ds3 = s.map(new PlusTwo()).addSink(new OddSink());

		env.execute();
		
		HashSet<Long> expectedEven = new HashSet<Long>(Arrays.asList(4L, 6L, 8L));
		HashSet<Long> expectedOdd = new HashSet<Long>(Arrays.asList(3L, 5L, 7L));
		
		assertEquals(expectedEven, evenSet);
		assertEquals(expectedOdd, oddSet);
	}
	
}

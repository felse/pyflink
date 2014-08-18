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

package org.apache.flink.streaming.api;

import static org.junit.Assert.assertEquals;

import org.apache.flink.streaming.api.DataStream;
import org.apache.flink.streaming.api.IterativeDataStream;
import org.apache.flink.streaming.api.LocalStreamEnvironment;
import org.apache.flink.streaming.api.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.function.SinkFunction;
import org.junit.Test;

import org.apache.flink.api.java.functions.FlatMapFunction;
import org.apache.flink.api.java.tuple.Tuple1;
import org.apache.flink.util.Collector;

public class IterateTest {

	private static final long MEMORYSIZE = 32;
	private static int iterationTimes = 0;
	private static int iterationResult = 0;

	public static final class Increment extends
			FlatMapFunction<Tuple1<Integer>, Tuple1<Integer>> {

		private static final long serialVersionUID = 1L;

		@Override
		public void flatMap(Tuple1<Integer> value,
				Collector<Tuple1<Integer>> out) throws Exception {
			if (value.f0 < 5) {
				out.collect(new Tuple1<Integer>(value.f0 + 1));
			}

		}

	}

	public static final class Forward extends
			FlatMapFunction<Tuple1<Integer>, Tuple1<Integer>> {

		private static final long serialVersionUID = 1L;

		@Override
		public void flatMap(Tuple1<Integer> value,
				Collector<Tuple1<Integer>> out) throws Exception {
			out.collect(value);

		}

	}
	
	public static final class MySink extends SinkFunction<Tuple1<Integer>> {
		
		private static final long serialVersionUID = 1L;

		@Override
		public void invoke(Tuple1<Integer> tuple) {
			iterationTimes++;
			iterationResult += tuple.f0;
		}
		
	}
	@Test
	public void test() throws Exception {

		LocalStreamEnvironment env = StreamExecutionEnvironment
				.createLocalEnvironment(1);

		IterativeDataStream<Tuple1<Integer>> source = env.fromElements(1).flatMap(new Forward()).
				iterate();
		
		DataStream<Tuple1<Integer>> increment = source.flatMap(new Increment());
		
		source.closeWith(increment).addSink(new MySink());

		env.executeTest(MEMORYSIZE);
		
		assertEquals(4, iterationTimes);
		assertEquals(14, iterationResult);

	}

}

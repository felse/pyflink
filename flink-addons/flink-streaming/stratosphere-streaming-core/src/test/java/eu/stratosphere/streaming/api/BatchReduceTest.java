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

package eu.stratosphere.streaming.api;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Iterator;

import org.junit.Test;

import eu.stratosphere.api.java.functions.GroupReduceFunction;
import eu.stratosphere.api.java.tuple.Tuple1;
import eu.stratosphere.util.Collector;

public class BatchReduceTest {

	private static ArrayList<Double> avgs = new ArrayList<Double>();
	private static final int BATCH_SIZE = 5;
	private static final int PARALELISM = 1;

	public static final class MyBatchReduce extends
			GroupReduceFunction<Tuple1<Double>, Tuple1<Double>> {
		private static final long serialVersionUID = 1L;

		@Override
		public void reduce(Iterator<Tuple1<Double>> values, Collector<Tuple1<Double>> out)
				throws Exception {

			Double sum = 0.;
			Double count = 0.;
			while (values.hasNext()) {
				sum += values.next().f0;
				count++;
			}

			out.collect(new Tuple1<Double>(sum / count));
		}
	}

	public static final class MySink extends SinkFunction<Tuple1<Double>> {
		private static final long serialVersionUID = 1L;

		@Override
		public void invoke(Tuple1<Double> tuple) {
			avgs.add(tuple.f0);
		}

	}

	public static final class MySource extends SourceFunction<Tuple1<Double>> {
		private static final long serialVersionUID = 1L;

		@Override
		public void invoke(Collector<Tuple1<Double>> collector) {
			for (Double i = 1.; i <= 100; i++) {
				collector.collect(new Tuple1<Double>(i));
			}
		}
	}

	@Test
	public void test() throws Exception {
		StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();
		DataStream<Tuple1<Double>> dataStream0 = env.addSource(new MySource(),1)
				.batchReduce(new MyBatchReduce(), BATCH_SIZE, PARALELISM).addSink(new MySink());

		env.execute();

		for (int i = 0; i < avgs.size(); i++) {
			assertEquals(3.0 + i * BATCH_SIZE, avgs.get(i), 0);
		}
	}
}

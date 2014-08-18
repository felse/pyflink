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
package eu.stratosphere.streaming.examples.basictopology;

import eu.stratosphere.api.java.functions.MapFunction;
import eu.stratosphere.api.java.tuple.Tuple1;
import eu.stratosphere.streaming.api.DataStream;
import eu.stratosphere.streaming.api.StreamExecutionEnvironment;
import eu.stratosphere.streaming.api.function.SourceFunction;
import eu.stratosphere.util.Collector;

public class BasicTopology {

	public static class BasicSource extends SourceFunction<Tuple1<String>> {

		private static final long serialVersionUID = 1L;
		Tuple1<String> tuple = new Tuple1<String>("streaming");

		@Override
		public void invoke(Collector<Tuple1<String>> out) throws Exception {
			//  continuously emit a tuple
			while (true) {
				out.collect(tuple);
			}
		}
	}

	public static class BasicMap extends MapFunction<Tuple1<String>, Tuple1<String>> {
		private static final long serialVersionUID = 1L;

		// map to the same tuple
		@Override
		public Tuple1<String> map(Tuple1<String> value) throws Exception {
			return value;
		}

	}

	private static final int PARALELISM = 1;
	private static final int SOURCE_PARALELISM = 1;

	public static void main(String[] args) {
		StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();

		DataStream<Tuple1<String>> stream = env.addSource(new BasicSource(), SOURCE_PARALELISM)
				.map(new BasicMap(), PARALELISM);
		
		stream.print();

		env.execute();
	}
}

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

package org.apache.flink.streaming.api.invokable.operator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.function.sink.SinkFunction;
import org.apache.flink.streaming.api.function.source.SourceFunction;
import org.apache.flink.streaming.util.LogUtils;
import org.apache.flink.util.Collector;
import org.apache.log4j.Level;
import org.junit.Test;

public class MapTest {

	public static final class MySource implements SourceFunction<Integer> {
		private static final long serialVersionUID = 1L;

		@Override
		public void invoke(Collector<Integer> collector) throws Exception {
			for (int i = 0; i < 10; i++) {
				collector.collect(i);
			}
		}
	}

	public static final class MySource1 implements SourceFunction<Integer> {
		private static final long serialVersionUID = 1L;

		@Override
		public void invoke(Collector<Integer> collector) throws Exception {
			for (int i = 0; i < 5; i++) {
				collector.collect(i);
			}
		}
	}

	public static final class MySource2 implements SourceFunction<Integer> {
		private static final long serialVersionUID = 1L;

		@Override
		public void invoke(Collector<Integer> collector) throws Exception {
			for (int i = 5; i < 10; i++) {
				collector.collect(i);
			}
		}
	}

	public static final class MySource3 implements SourceFunction<Integer> {
		private static final long serialVersionUID = 1L;

		@Override
		public void invoke(Collector<Integer> collector) throws Exception {
			for (int i = 10; i < 15; i++) {
				collector.collect(new Integer(i));
			}
		}
	}

	public static final class MyMap implements MapFunction<Integer, Integer> {
		private static final long serialVersionUID = 1L;

		@Override
		public Integer map(Integer value) throws Exception {
			map++;
			return value * value;
		}
	}

	public static final class MySingleJoinMap implements MapFunction<Integer, Integer> {
		private static final long serialVersionUID = 1L;

		@Override
		public Integer map(Integer value) throws Exception {
			singleJoinSetResult.add(value);
			return value;
		}
	}

	public static final class MyMultipleJoinMap implements MapFunction<Integer, Integer> {
		private static final long serialVersionUID = 1L;

		@Override
		public Integer map(Integer value) throws Exception {
			multipleJoinSetResult.add(value);
			return value;
		}
	}

	public static final class MyFieldsMap implements MapFunction<Integer, Integer> {
		private static final long serialVersionUID = 1L;

		private int counter = 0;

		@Override
		public Integer map(Integer value) throws Exception {
			counter++;
			if (counter == MAXSOURCE)
				allInOne = true;
			return value * value;
		}
	}

	public static final class MyDiffFieldsMap implements MapFunction<Integer, Integer> {
		private static final long serialVersionUID = 1L;

		private int counter = 0;

		@Override
		public Integer map(Integer value) throws Exception {
			counter++;
			if (counter > 3)
				threeInAll = false;
			return value * value;
		}
	}

	public static final class MySink implements SinkFunction<Integer> {
		private static final long serialVersionUID = 1L;

		@Override
		public void invoke(Integer tuple) {
			result.add(tuple);
		}
	}

	public static final class MyBroadcastSink implements SinkFunction<Integer> {
		private static final long serialVersionUID = 1L;

		@Override
		public void invoke(Integer tuple) {
			broadcastResult++;
		}
	}

	public static final class MyShufflesSink implements SinkFunction<Integer> {
		private static final long serialVersionUID = 1L;

		@Override
		public void invoke(Integer tuple) {
			shuffleResult++;
		}
	}

	public static final class MyFieldsSink implements SinkFunction<Integer> {
		private static final long serialVersionUID = 1L;

		@Override
		public void invoke(Integer tuple) {
			fieldsResult++;
		}
	}

	public static final class MyDiffFieldsSink implements SinkFunction<Integer> {
		private static final long serialVersionUID = 1L;

		@Override
		public void invoke(Integer tuple) {
			diffFieldsResult++;
		}
	}

	public static final class MyGraphSink implements SinkFunction<Integer> {
		private static final long serialVersionUID = 1L;

		@Override
		public void invoke(Integer tuple) {
			graphResult++;
		}
	}

	public static final class JoinSink implements SinkFunction<Integer> {
		private static final long serialVersionUID = 1L;

		@Override
		public void invoke(Integer tuple) {
		}
	}

	private static Set<Integer> expected = new HashSet<Integer>();
	private static Set<Integer> result = new HashSet<Integer>();
	private static int broadcastResult = 0;
	private static int shuffleResult = 0;
	@SuppressWarnings("unused")
	private static int fieldsResult = 0;
	private static int diffFieldsResult = 0;
	@SuppressWarnings("unused")
	private static int graphResult = 0;
	@SuppressWarnings("unused")
	private static int map = 0;
	@SuppressWarnings("unused")
	private static final int PARALLELISM = 1;
	private static final long MEMORYSIZE = 32;
	private static final int MAXSOURCE = 10;
	private static boolean allInOne = false;
	private static boolean threeInAll = true;
	private static Set<Integer> fromCollectionSet = new HashSet<Integer>();
	private static List<Integer> fromCollectionFields = new ArrayList<Integer>();
	private static Set<Integer> fromCollectionDiffFieldsSet = new HashSet<Integer>();
	private static Set<Integer> singleJoinSetExpected = new HashSet<Integer>();
	private static Set<Integer> multipleJoinSetExpected = new HashSet<Integer>();
	private static Set<Integer> singleJoinSetResult = new HashSet<Integer>();
	private static Set<Integer> multipleJoinSetResult = new HashSet<Integer>();

	private static void fillExpectedList() {
		for (int i = 0; i < 10; i++) {
			expected.add(i * i);
		}
	}

	private static void fillFromCollectionSet() {
		if (fromCollectionSet.isEmpty()) {
			for (int i = 0; i < 10; i++) {
				fromCollectionSet.add(i);
			}
		}
	}

	private static void fillFromCollectionFieldsSet() {
		if (fromCollectionFields.isEmpty()) {
			for (int i = 0; i < MAXSOURCE; i++) {

				fromCollectionFields.add(5);
			}
		}
	}

	private static void fillFromCollectionDiffFieldsSet() {
		if (fromCollectionDiffFieldsSet.isEmpty()) {
			for (int i = 0; i < 9; i++) {
				fromCollectionDiffFieldsSet.add(i);
			}
		}
	}

	private static void fillSingleJoinSet() {
		for (int i = 0; i < 10; i++) {
			singleJoinSetExpected.add(i);
		}
	}

	private static void fillMultipleJoinSet() {
		for (int i = 0; i < 15; i++) {
			multipleJoinSetExpected.add(i);
		}
	}

	@Test
	public void mapTest() throws Exception {
		LogUtils.initializeDefaultConsoleLogger(Level.OFF, Level.OFF);
		// mapTest
		LocalStreamEnvironment env = StreamExecutionEnvironment.createLocalEnvironment(3);

		fillFromCollectionSet();

		@SuppressWarnings("unused")
		DataStream<Integer> dataStream = env.fromCollection(fromCollectionSet).map(new MyMap())
				.addSink(new MySink());

		fillExpectedList();

		// broadcastSinkTest
		fillFromCollectionSet();

		@SuppressWarnings("unused")
		DataStream<Integer> dataStream1 = env.fromCollection(fromCollectionSet).broadcast()
				.map(new MyMap()).addSink(new MyBroadcastSink());

		// shuffleSinkTest
		fillFromCollectionSet();

		@SuppressWarnings("unused")
		DataStream<Integer> dataStream2 = env.fromCollection(fromCollectionSet).map(new MyMap())
				.setParallelism(3).addSink(new MyShufflesSink());

		// fieldsMapTest
		fillFromCollectionFieldsSet();

		@SuppressWarnings("unused")
		DataStream<Integer> dataStream3 = env.fromCollection(fromCollectionFields).partitionBy(0)
				.map(new MyFieldsMap()).addSink(new MyFieldsSink());

		// diffFieldsMapTest
		fillFromCollectionDiffFieldsSet();

		@SuppressWarnings("unused")
		DataStream<Integer> dataStream4 = env.fromCollection(fromCollectionDiffFieldsSet)
				.partitionBy(0).map(new MyDiffFieldsMap()).addSink(new MyDiffFieldsSink());

		// singleConnectWithTest
		DataStream<Integer> source1 = env.addSource(new MySource1(), 1);

		@SuppressWarnings({ "unused", "unchecked" })
		DataStream<Integer> source2 = env.addSource(new MySource2(), 1).connectWith(source1)
				.partitionBy(0).map(new MySingleJoinMap()).setParallelism(1)
				.addSink(new JoinSink());

		fillSingleJoinSet();

		// multipleConnectWithTest
		DataStream<Integer> source3 = env.addSource(new MySource1(), 1);

		DataStream<Integer> source4 = env.addSource(new MySource2(), 1);

		@SuppressWarnings({ "unused", "unchecked" })
		DataStream<Integer> source5 = env.addSource(new MySource3(), 1)
				.connectWith(source3, source4).partitionBy(0).map(new MyMultipleJoinMap())
				.setParallelism(1).addSink(new JoinSink());

		env.executeTest(MEMORYSIZE);

		fillMultipleJoinSet();

		assertTrue(expected.equals(result));
		assertEquals(30, broadcastResult);
		assertEquals(10, shuffleResult);
		assertTrue(allInOne);
		assertTrue(threeInAll);
		assertEquals(9, diffFieldsResult);
		assertEquals(singleJoinSetExpected, singleJoinSetResult);
		assertEquals(multipleJoinSetExpected, multipleJoinSetResult);

	}

}

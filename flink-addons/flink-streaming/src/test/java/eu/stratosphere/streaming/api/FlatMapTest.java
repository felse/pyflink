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

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import eu.stratosphere.api.java.functions.FlatMapFunction;
import eu.stratosphere.api.java.tuple.Tuple;
import eu.stratosphere.api.java.tuple.Tuple1;
import eu.stratosphere.api.java.tuple.Tuple2;
import eu.stratosphere.api.java.typeutils.TupleTypeInfo;
import eu.stratosphere.api.java.typeutils.TypeExtractor;
import eu.stratosphere.configuration.Configuration;
import eu.stratosphere.nephele.jobgraph.AbstractJobVertex;
import eu.stratosphere.nephele.jobgraph.JobInputVertex;
import eu.stratosphere.nephele.jobgraph.JobOutputVertex;
import eu.stratosphere.nephele.jobgraph.JobTaskVertex;
import eu.stratosphere.streaming.api.MapTest.MyMap;
import eu.stratosphere.streaming.api.MapTest.MySink;
import eu.stratosphere.streaming.api.PrintTest.MyFlatMap;
import eu.stratosphere.streaming.api.invokable.UserSinkInvokable;
import eu.stratosphere.streaming.api.invokable.UserSourceInvokable;
import eu.stratosphere.streaming.api.invokable.UserTaskInvokable;
import eu.stratosphere.util.Collector;

public class FlatMapTest {

	public static final class MyFlatMap extends FlatMapFunction<Tuple1<Integer>, Tuple1<Integer>> {

		@Override
		public void flatMap(Tuple1<Integer> value, Collector<Tuple1<Integer>> out) throws Exception {
			out.collect(new Tuple1<Integer>(value.f0 * value.f0));

		}

	}

	public static final class ParallelFlatMap extends
			FlatMapFunction<Tuple1<Integer>, Tuple1<Integer>> {

		@Override
		public void flatMap(Tuple1<Integer> value, Collector<Tuple1<Integer>> out) throws Exception {
			numberOfElements++;

		}

	}

	public static final class GenerateSequenceFlatMap extends
			FlatMapFunction<Tuple1<Long>, Tuple1<Long>> {

		@Override
		public void flatMap(Tuple1<Long> value, Collector<Tuple1<Long>> out) throws Exception {
			out.collect(new Tuple1<Long>(value.f0 * value.f0));

		}

	}

	public static final class MySink extends SinkFunction<Tuple1<Integer>> {

		@Override
		public void invoke(Tuple1<Integer> tuple) {
			result.add(tuple.f0);
		}

	}

	public static final class FromElementsSink extends SinkFunction<Tuple1<Integer>> {

		@Override
		public void invoke(Tuple1<Integer> tuple) {
			fromElementsResult.add(tuple.f0);
		}

	}

	public static final class FromCollectionSink extends SinkFunction<Tuple1<Integer>> {

		@Override
		public void invoke(Tuple1<Integer> tuple) {
			fromCollectionResult.add(tuple.f0);
		}

	}

	public static final class GenerateSequenceSink extends SinkFunction<Tuple1<Long>> {

		@Override
		public void invoke(Tuple1<Long> tuple) {
			generateSequenceResult.add(tuple.f0);
		}

	}

	public static final class MySource extends SourceFunction<Tuple1<Integer>> {

		@Override
		public void invoke(Collector<Tuple1<Integer>> collector) throws Exception {
			for (int i = 0; i < 10; i++) {
				collector.collect(new Tuple1<Integer>(i));
			}
		}
	}

	private static void fillExpectedList() {
		for (int i = 0; i < 10; i++) {
			expected.add(i * i);
		}
	}

	private static void fillFromElementsExpected() {
		fromElementsExpected.add(4);
		fromElementsExpected.add(25);
		fromElementsExpected.add(81);
	}

	private static void fillSequenceSet() {
		for (int i = 0; i < 10; i++) {
			sequenceExpected.add(i * i);
		}
	}
	
	private static void fillLongSequenceSet() {
		for (int i = 0; i < 10; i++) {
			sequenceLongExpected.add((long)(i * i));
		}
	}

	private static void fillFromCollectionSet() {
		for (int i = 0; i < 10; i++) {
			fromCollectionSet.add(i);
		}
	}

	private static final int PARALELISM = 1;
	private static int numberOfElements = 0;
	private static Set<Integer> expected = new HashSet<Integer>();
	private static Set<Integer> result = new HashSet<Integer>();
	private static Set<Integer> fromElementsExpected = new HashSet<Integer>();
	private static Set<Integer> fromElementsResult = new HashSet<Integer>();
	private static Set<Integer> fromCollectionSet = new HashSet<Integer>();
	private static Set<Integer> sequenceExpected = new HashSet<Integer>();
	private static Set<Long> sequenceLongExpected = new HashSet<Long>();
	private static Set<Integer> fromCollectionResult = new HashSet<Integer>();
	private static Set<Long> generateSequenceResult = new HashSet<Long>();

	@Test
	public void test() throws Exception {

		StreamExecutionEnvironment env = new StreamExecutionEnvironment(2, 1000);
		DataStream<Tuple1<Integer>> dataStream = env.addSource(new MySource(), 1)
				.flatMap(new MyFlatMap(), PARALELISM).addSink(new MySink());

		env.execute();

		fillExpectedList();

		assertTrue(expected.equals(result));

	}

	@Test
	public void parallelShuffleconnectTest() throws Exception {
		StreamExecutionEnvironment env = new StreamExecutionEnvironment();
		DataStream<Tuple1<Integer>> source = env.addSource(new MySource(), 1);
		DataStream<Tuple1<Integer>> map = source.flatMap(new ParallelFlatMap(), 1).addSink(
				new MySink());
		DataStream<Tuple1<Integer>> map2 = source.flatMap(new ParallelFlatMap(), 1).addSink(
				new MySink());

		env.execute();

		assertEquals(20, numberOfElements);

	}

	@Test
	public void fromElementsTest() throws Exception {
		StreamExecutionEnvironment env = new StreamExecutionEnvironment();
		DataStream<Tuple1<Integer>> map = env.fromElements(2, 5, 9).flatMap(new MyFlatMap(), 1);
		DataStream<Tuple1<Integer>> sink = map.addSink(new FromElementsSink());

		fillFromElementsExpected();

		env.execute();
		assertEquals(fromElementsExpected, fromElementsResult);

	}

	@Test
	public void fromCollectionTest() throws Exception {
		StreamExecutionEnvironment env = new StreamExecutionEnvironment();

		fillFromCollectionSet();

		DataStream<Tuple1<Integer>> map = env.fromCollection(fromCollectionSet).flatMap(
				new MyFlatMap(), 1);
		DataStream<Tuple1<Integer>> sink = map.addSink(new FromCollectionSink());

		fillSequenceSet();

		env.execute();
		assertEquals(sequenceExpected, fromCollectionResult);

	}

	@Test
	public void generateSequenceTest() throws Exception {
		StreamExecutionEnvironment env = new StreamExecutionEnvironment();

		DataStream<Tuple1<Long>> map = env.generateSequence(0, 9).flatMap(
				new GenerateSequenceFlatMap(), 1);
		DataStream<Tuple1<Long>> sink = map.addSink(new GenerateSequenceSink());

		fillLongSequenceSet();

		env.execute();
		assertEquals(sequenceLongExpected, generateSequenceResult);
	}
}

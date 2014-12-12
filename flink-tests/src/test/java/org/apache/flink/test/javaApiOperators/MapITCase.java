/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.test.javaApiOperators;

import java.util.Collection;

import org.apache.flink.test.util.MultipleProgramsTestBase;
import org.junit.After;
import org.junit.Assert;

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.test.javaApiOperators.util.CollectionDataSets;
import org.apache.flink.test.javaApiOperators.util.CollectionDataSets.CustomType;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;

@RunWith(Parameterized.class)
public class MapITCase extends MultipleProgramsTestBase {

	public MapITCase(MultipleProgramsTestBase.ExecutionMode mode){
		super(mode);
	}

	private String resultPath;
	private String expected;

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	@Before
	public void before() throws Exception{
		resultPath = tempFolder.newFile().toURI().toString();
	}

	@After
	public void after() throws Exception{
		compareResultsByLinesInMemory(expected, resultPath);
	}

	@Test
	public void testIdentityMapWithBasicType() throws Exception {
		/*
		 * Test identity map with basic type
		 */

		final ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();

		DataSet<String> ds = CollectionDataSets.getStringDataSet(env);
		DataSet<String> identityMapDs = ds.
				map(new Mapper1());

		identityMapDs.writeAsText(resultPath);
		env.execute();

		expected = "Hi\n" +
				"Hello\n" +
				"Hello world\n" +
				"Hello world, how are you?\n" +
				"I am fine.\n" +
				"Luke Skywalker\n" +
				"Random comment\n" +
				"LOL\n";
	}

	public static class Mapper1 implements MapFunction<String, String> {
		private static final long serialVersionUID = 1L;

		@Override
		public String map(String value) throws Exception {
			return value;
		}
	}

	@Test
	public void testIdentityMapWithTuple() throws Exception {
		/*
		 * Test identity map with a tuple
		 */

		final ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();

		DataSet<Tuple3<Integer, Long, String>> ds = CollectionDataSets.get3TupleDataSet(env);
		DataSet<Tuple3<Integer, Long, String>> identityMapDs = ds.
				map(new Mapper2());

		identityMapDs.writeAsCsv(resultPath);
		env.execute();

		expected = "1,1,Hi\n" +
				"2,2,Hello\n" +
				"3,2,Hello world\n" +
				"4,3,Hello world, how are you?\n" +
				"5,3,I am fine.\n" +
				"6,3,Luke Skywalker\n" +
				"7,4,Comment#1\n" +
				"8,4,Comment#2\n" +
				"9,4,Comment#3\n" +
				"10,4,Comment#4\n" +
				"11,5,Comment#5\n" +
				"12,5,Comment#6\n" +
				"13,5,Comment#7\n" +
				"14,5,Comment#8\n" +
				"15,5,Comment#9\n" +
				"16,6,Comment#10\n" +
				"17,6,Comment#11\n" +
				"18,6,Comment#12\n" +
				"19,6,Comment#13\n" +
				"20,6,Comment#14\n" +
				"21,6,Comment#15\n";
	}

	public static class Mapper2 implements MapFunction<Tuple3<Integer, Long, String>, Tuple3<Integer, Long, String>> {
		private static final long serialVersionUID = 1L;

		@Override
		public Tuple3<Integer, Long, String> map(Tuple3<Integer, Long, String> value)
		throws Exception {
			return value;
		}
	}

	@Test
	public void testTypeConversionMapperCustomToTuple() throws Exception {
		/*
		 * Test type conversion mapper (Custom -> Tuple)
		 */

		final ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();

		DataSet<CustomType> ds = CollectionDataSets.getCustomTypeDataSet(env);
		DataSet<Tuple3<Integer, Long, String>> typeConversionMapDs = ds.
				map(new Mapper3());

		typeConversionMapDs.writeAsCsv(resultPath);
		env.execute();

		expected = "1,0,Hi\n" +
				"2,1,Hello\n" +
				"2,2,Hello world\n" +
				"3,3,Hello world, how are you?\n" +
				"3,4,I am fine.\n" +
				"3,5,Luke Skywalker\n" +
				"4,6,Comment#1\n" +
				"4,7,Comment#2\n" +
				"4,8,Comment#3\n" +
				"4,9,Comment#4\n" +
				"5,10,Comment#5\n" +
				"5,11,Comment#6\n" +
				"5,12,Comment#7\n" +
				"5,13,Comment#8\n" +
				"5,14,Comment#9\n" +
				"6,15,Comment#10\n" +
				"6,16,Comment#11\n" +
				"6,17,Comment#12\n" +
				"6,18,Comment#13\n" +
				"6,19,Comment#14\n" +
				"6,20,Comment#15\n";
	}

	public static class Mapper3 implements MapFunction<CustomType, Tuple3<Integer, Long, String>> {
		private static final long serialVersionUID = 1L;
		private final Tuple3<Integer, Long, String> out = new Tuple3<Integer, Long, String>();

		@Override
		public Tuple3<Integer, Long, String> map(CustomType value) throws Exception {
			out.setField(value.myInt, 0);
			out.setField(value.myLong, 1);
			out.setField(value.myString, 2);
			return out;
		}
	}

	@Test
	public void testTypeConversionMapperTupleToBasic() throws Exception {
		/*
		 * Test type conversion mapper (Tuple -> Basic)
		 */

		final ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();

		DataSet<Tuple3<Integer, Long, String>> ds = CollectionDataSets.get3TupleDataSet(env);
		DataSet<String> typeConversionMapDs = ds.
				map(new Mapper4());

		typeConversionMapDs.writeAsText(resultPath);
		env.execute();

		expected = "Hi\n" + "Hello\n" + "Hello world\n" +
				"Hello world, how are you?\n" +
				"I am fine.\n" + "Luke Skywalker\n" +
				"Comment#1\n" +	"Comment#2\n" +
				"Comment#3\n" +	"Comment#4\n" +
				"Comment#5\n" +	"Comment#6\n" +
				"Comment#7\n" + "Comment#8\n" +
				"Comment#9\n" +	"Comment#10\n" +
				"Comment#11\n" + "Comment#12\n" +
				"Comment#13\n" + "Comment#14\n" +
				"Comment#15\n";
	}

	public static class Mapper4 implements MapFunction<Tuple3<Integer, Long, String>, String> {
		private static final long serialVersionUID = 1L;

		@Override
		public String map(Tuple3<Integer, Long, String> value) throws Exception {
			return value.getField(2);
		}
	}

	@Test
	public void testMapperOnTupleIncrementIntegerFieldReorderSecondAndThirdFields() throws
			Exception {
		/*
		 * Test mapper on tuple - Increment Integer field, reorder second and third fields
		 */

		final ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();

		DataSet<Tuple3<Integer, Long, String>> ds = CollectionDataSets.get3TupleDataSet(env);
		DataSet<Tuple3<Integer, String, Long>> tupleMapDs = ds.
				map(new Mapper5());

		tupleMapDs.writeAsCsv(resultPath);
		env.execute();

		expected = "2,Hi,1\n" +
				"3,Hello,2\n" +
				"4,Hello world,2\n" +
				"5,Hello world, how are you?,3\n" +
				"6,I am fine.,3\n" +
				"7,Luke Skywalker,3\n" +
				"8,Comment#1,4\n" +
				"9,Comment#2,4\n" +
				"10,Comment#3,4\n" +
				"11,Comment#4,4\n" +
				"12,Comment#5,5\n" +
				"13,Comment#6,5\n" +
				"14,Comment#7,5\n" +
				"15,Comment#8,5\n" +
				"16,Comment#9,5\n" +
				"17,Comment#10,6\n" +
				"18,Comment#11,6\n" +
				"19,Comment#12,6\n" +
				"20,Comment#13,6\n" +
				"21,Comment#14,6\n" +
				"22,Comment#15,6\n";
	}

	public static class Mapper5 implements MapFunction<Tuple3<Integer, Long, String>, Tuple3<Integer, String, Long>> {
		private static final long serialVersionUID = 1L;
		private final Tuple3<Integer, String, Long> out = new Tuple3<Integer, String, Long>();

		@Override
		public Tuple3<Integer, String, Long> map(Tuple3<Integer, Long, String> value)
		throws Exception {
			Integer incr = Integer.valueOf(value.f0.intValue() + 1);
			out.setFields(incr, value.f2, value.f1);
			return out;
		}
	}

	@Test
	public void testMapperOnCustomLowercaseString() throws Exception {
		/*
		 * Test mapper on Custom - lowercase myString
		 */

		final ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();

		DataSet<CustomType> ds = CollectionDataSets.getCustomTypeDataSet(env);
		DataSet<CustomType> customMapDs = ds.
				map(new Mapper6());

		customMapDs.writeAsText(resultPath);
		env.execute();

		expected = "1,0,hi\n" +
				"2,1,hello\n" +
				"2,2,hello world\n" +
				"3,3,hello world, how are you?\n" +
				"3,4,i am fine.\n" +
				"3,5,luke skywalker\n" +
				"4,6,comment#1\n" +
				"4,7,comment#2\n" +
				"4,8,comment#3\n" +
				"4,9,comment#4\n" +
				"5,10,comment#5\n" +
				"5,11,comment#6\n" +
				"5,12,comment#7\n" +
				"5,13,comment#8\n" +
				"5,14,comment#9\n" +
				"6,15,comment#10\n" +
				"6,16,comment#11\n" +
				"6,17,comment#12\n" +
				"6,18,comment#13\n" +
				"6,19,comment#14\n" +
				"6,20,comment#15\n";
	}

	public static class Mapper6 implements MapFunction<CustomType, CustomType> {
		private static final long serialVersionUID = 1L;
		private final CustomType out = new CustomType();

		@Override
		public CustomType map(CustomType value) throws Exception {
			out.myInt = value.myInt;
			out.myLong = value.myLong;
			out.myString = value.myString.toLowerCase();
			return out;
		}
	}

	@Test
	public void test() throws Exception {
		/*
		 * Test mapper if UDF returns input object - increment first field of a tuple
		 */

		final ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();

		DataSet<Tuple3<Integer, Long, String>> ds = CollectionDataSets.get3TupleDataSet(env);
		DataSet<Tuple3<Integer, Long, String>> inputObjMapDs = ds.
				map(new Mapper7());

		inputObjMapDs.writeAsCsv(resultPath);
		env.execute();

		expected = "2,1,Hi\n" +
				"3,2,Hello\n" +
				"4,2,Hello world\n" +
				"5,3,Hello world, how are you?\n" +
				"6,3,I am fine.\n" +
				"7,3,Luke Skywalker\n" +
				"8,4,Comment#1\n" +
				"9,4,Comment#2\n" +
				"10,4,Comment#3\n" +
				"11,4,Comment#4\n" +
				"12,5,Comment#5\n" +
				"13,5,Comment#6\n" +
				"14,5,Comment#7\n" +
				"15,5,Comment#8\n" +
				"16,5,Comment#9\n" +
				"17,6,Comment#10\n" +
				"18,6,Comment#11\n" +
				"19,6,Comment#12\n" +
				"20,6,Comment#13\n" +
				"21,6,Comment#14\n" +
				"22,6,Comment#15\n";
	}

	public static class Mapper7 implements MapFunction<Tuple3<Integer, Long, String>, Tuple3<Integer, Long, String>> {
		private static final long serialVersionUID = 1L;

		@Override
		public Tuple3<Integer, Long, String> map(Tuple3<Integer, Long, String> value)
		throws Exception {
			Integer incr = Integer.valueOf(value.f0.intValue() + 1);
			value.setField(incr, 0);
			return value;
		}
	}

	@Test
	public void testMapWithBroadcastSet() throws Exception {
		/*
		 * Test map with broadcast set
		 */

		final ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();

		DataSet<Integer> ints = CollectionDataSets.getIntegerDataSet(env);

		DataSet<Tuple3<Integer, Long, String>> ds = CollectionDataSets.get3TupleDataSet(env);
		DataSet<Tuple3<Integer, Long, String>> bcMapDs = ds.
				map(new RichMapper1()).withBroadcastSet(ints, "ints");
		bcMapDs.writeAsCsv(resultPath);
		env.execute();

		expected = "55,1,Hi\n" +
				"55,2,Hello\n" +
				"55,2,Hello world\n" +
				"55,3,Hello world, how are you?\n" +
				"55,3,I am fine.\n" +
				"55,3,Luke Skywalker\n" +
				"55,4,Comment#1\n" +
				"55,4,Comment#2\n" +
				"55,4,Comment#3\n" +
				"55,4,Comment#4\n" +
				"55,5,Comment#5\n" +
				"55,5,Comment#6\n" +
				"55,5,Comment#7\n" +
				"55,5,Comment#8\n" +
				"55,5,Comment#9\n" +
				"55,6,Comment#10\n" +
				"55,6,Comment#11\n" +
				"55,6,Comment#12\n" +
				"55,6,Comment#13\n" +
				"55,6,Comment#14\n" +
				"55,6,Comment#15\n";
	}

	public static class RichMapper1 extends RichMapFunction<Tuple3<Integer,Long,String>,
	Tuple3<Integer,	Long,String>> {
		private static final long serialVersionUID = 1L;
		private final Tuple3<Integer, Long, String> out = new Tuple3<Integer, Long, String>();
		private Integer f2Replace = 0;

		@Override
		public void open(Configuration config) {
			Collection<Integer> ints = this.getRuntimeContext().getBroadcastVariable("ints");
			int sum = 0;
			for(Integer i : ints) {
				sum += i;
			}
			f2Replace = sum;
		}

		@Override
		public Tuple3<Integer, Long, String> map(Tuple3<Integer, Long, String> value)
		throws Exception {
			out.setFields(f2Replace, value.f1, value.f2);
			return out;
		}
	}

	static final String testKey = "testVariable";
	static final int testValue = 666;

	@Test
	public void testPassingConfigurationObject() throws Exception {
		/*
		 * Test passing configuration object.
		 */

		final ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();

		DataSet<Tuple3<Integer, Long, String>> ds = CollectionDataSets.getSmall3TupleDataSet(env);
		Configuration conf = new Configuration();
		conf.setInteger(testKey, testValue);
		DataSet<Tuple3<Integer, Long, String>> bcMapDs = ds.
				map(new RichMapper2()).withParameters(conf);
		bcMapDs.writeAsCsv(resultPath);
		env.execute();

		expected = "1,1,Hi\n"
				+ "2,2,Hello\n"
				+ "3,2,Hello world";
	}

	public static class RichMapper2 extends RichMapFunction<Tuple3<Integer,Long,String>,
	Tuple3<Integer,	Long,String>> {
		private static final long serialVersionUID = 1L;

		@Override
		public void open(Configuration config) {
			int val = config.getInteger(testKey, -1);
			Assert.assertEquals(testValue, val);
		}

		@Override
		public Tuple3<Integer, Long, String> map(Tuple3<Integer, Long, String> value) {
			return value;
		}
	}
}

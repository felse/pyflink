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

package org.apache.flink.api.java.expressions.test;

import org.apache.flink.api.expressions.ExpressionException;
import org.apache.flink.api.expressions.ExpressionOperation;
import org.apache.flink.api.expressions.Row;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.expressions.ExpressionUtil;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.api.scala.expressions.JavaBatchTranslator;
import org.apache.flink.core.fs.FileSystem;
import org.apache.flink.test.javaApiOperators.util.CollectionDataSets;
import org.apache.flink.test.util.MultipleProgramsTestBase;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class GroupedAggregationsITCase extends MultipleProgramsTestBase {


	public GroupedAggregationsITCase(TestExecutionMode mode){
		super(mode);
	}

	private String resultPath;
	private String expected = "";

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

	@Test(expected = ExpressionException.class)
	public void testGroupingOnNonExistentField() throws Exception {
		ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
		DataSet<Tuple3<Integer, Long, String>> input = CollectionDataSets.get3TupleDataSet(env);

		ExpressionOperation<JavaBatchTranslator> expressionOperation =
				ExpressionUtil.from(input, "a, b, c");

		ExpressionOperation<JavaBatchTranslator> result = expressionOperation
				.groupBy("foo").select("a.avg");

		DataSet<Row> ds = ExpressionUtil.toSet(result, Row.class);
		ds.writeAsText(resultPath, FileSystem.WriteMode.OVERWRITE);

		env.execute();

		expected = "";
	}

	@Test
	public void testGroupedAggregate() throws Exception {

		ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
		DataSet<Tuple3<Integer, Long, String>> input = CollectionDataSets.get3TupleDataSet(env);

		ExpressionOperation<JavaBatchTranslator> expressionOperation =
				ExpressionUtil.from(input, "a, b, c");

		ExpressionOperation<JavaBatchTranslator> result = expressionOperation
				.groupBy("b").select("b, a.sum");

		DataSet<Row> ds = ExpressionUtil.toSet(result, Row.class);
		ds.writeAsText(resultPath, FileSystem.WriteMode.OVERWRITE);

		env.execute();

		expected = "1,1\n" + "2,5\n" + "3,15\n" + "4,34\n" + "5,65\n" + "6,111\n";
	}

	@Test
	public void testGroupingKeyForwardIfNotUsed() throws Exception {

		// the grouping key needs to be forwarded to the intermediate DataSet, even
		// if we don't want the key in the output

		ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
		DataSet<Tuple3<Integer, Long, String>> input = CollectionDataSets.get3TupleDataSet(env);

		ExpressionOperation<JavaBatchTranslator> expressionOperation =
				ExpressionUtil.from(input, "a, b, c");

		ExpressionOperation<JavaBatchTranslator> result = expressionOperation
				.groupBy("b").select("a.sum");

		DataSet<Row> ds = ExpressionUtil.toSet(result, Row.class);
		ds.writeAsText(resultPath, FileSystem.WriteMode.OVERWRITE);

		env.execute();

		expected = "1\n" + "5\n" + "15\n" + "34\n" + "65\n" + "111\n";
	}
}


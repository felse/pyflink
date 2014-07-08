/***********************************************************************************************************************
*
* Copyright (C) 2013 by the Stratosphere project (http://stratosphere.eu)
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

package eu.stratosphere.pact.compiler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import eu.stratosphere.api.common.Plan;
import eu.stratosphere.api.common.operators.base.GenericDataSourceBase;
import eu.stratosphere.api.java.DataSet;
import eu.stratosphere.api.java.ExecutionEnvironment;
import eu.stratosphere.api.java.IterativeDataSet;
import eu.stratosphere.api.java.functions.JoinFunction;
import eu.stratosphere.api.java.tuple.Tuple3;
import eu.stratosphere.compiler.PactCompiler;
import eu.stratosphere.compiler.plan.DualInputPlanNode;
import eu.stratosphere.compiler.plan.OptimizedPlan;
import eu.stratosphere.compiler.plantranslate.NepheleJobGraphGenerator;
import eu.stratosphere.configuration.Configuration;
import eu.stratosphere.pact.runtime.task.DriverStrategy;

/**
* Tests that validate optimizer choice when using hash joins inside of iterations
*/
@SuppressWarnings("serial")
public class CachedMatchStrategyCompilerTest extends CompilerTestBase {

	/**
	 * This tests whether a HYBRIDHASH_BUILD_SECOND is correctly transformed to a HYBRIDHASH_BUILD_SECOND_CACHED
	 * when inside of an iteration an on the static path
	 */
	@Test
	public void testRightSide() {
		try {
			
			Plan plan = getTestPlanRightStatic(PactCompiler.HINT_LOCAL_STRATEGY_HASH_BUILD_SECOND);
			
			OptimizedPlan oPlan = compileNoStats(plan);
	
			OptimizerPlanNodeResolver resolver = getOptimizerPlanNodeResolver(oPlan);
			DualInputPlanNode innerJoin = resolver.getNode("DummyJoiner");
			
			// verify correct join strategy
			assertEquals(DriverStrategy.HYBRIDHASH_BUILD_SECOND_CACHED, innerJoin.getDriverStrategy()); 
		
			new NepheleJobGraphGenerator().compileJobGraph(oPlan);
		}
		catch (Exception e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
			fail("Test errored: " + e.getMessage());
		}
	}
	
	/**
	 * This test makes sure that only a HYBRIDHASH on the static path is transformed to the cached variant
	 */
	@Test
	public void testRightSideCountercheck() {
		try {
			
			Plan plan = getTestPlanRightStatic(PactCompiler.HINT_LOCAL_STRATEGY_HASH_BUILD_FIRST);
			
			OptimizedPlan oPlan = compileNoStats(plan);
	
			OptimizerPlanNodeResolver resolver = getOptimizerPlanNodeResolver(oPlan);
			DualInputPlanNode innerJoin = resolver.getNode("DummyJoiner");
			
			// verify correct join strategy
			assertEquals(DriverStrategy.HYBRIDHASH_BUILD_FIRST, innerJoin.getDriverStrategy()); 
		
			new NepheleJobGraphGenerator().compileJobGraph(oPlan);
		}
		catch (Exception e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
			fail("Test errored: " + e.getMessage());
		}
	}
	
	/**
	 * This tests whether a HYBRIDHASH_BUILD_FIRST is correctly transformed to a HYBRIDHASH_BUILD_FIRST_CACHED
	 * when inside of an iteration an on the static path
	 */
	@Test
	public void testLeftSide() {
		try {
			
			Plan plan = getTestPlanLeftStatic(PactCompiler.HINT_LOCAL_STRATEGY_HASH_BUILD_FIRST);
			
			OptimizedPlan oPlan = compileNoStats(plan);
	
			OptimizerPlanNodeResolver resolver = getOptimizerPlanNodeResolver(oPlan);
			DualInputPlanNode innerJoin = resolver.getNode("DummyJoiner");
			
			// verify correct join strategy
			assertEquals(DriverStrategy.HYBRIDHASH_BUILD_FIRST_CACHED, innerJoin.getDriverStrategy()); 
		
			new NepheleJobGraphGenerator().compileJobGraph(oPlan);
		}
		catch (Exception e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
			fail("Test errored: " + e.getMessage());
		}
	}
	
	/**
	 * This test makes sure that only a HYBRIDHASH on the static path is transformed to the cached variant
	 */
	@Test
	public void testLeftSideCountercheck() {
		try {
			
			Plan plan = getTestPlanLeftStatic(PactCompiler.HINT_LOCAL_STRATEGY_HASH_BUILD_SECOND);
			
			OptimizedPlan oPlan = compileNoStats(plan);
	
			OptimizerPlanNodeResolver resolver = getOptimizerPlanNodeResolver(oPlan);
			DualInputPlanNode innerJoin = resolver.getNode("DummyJoiner");
			
			// verify correct join strategy
			assertEquals(DriverStrategy.HYBRIDHASH_BUILD_SECOND, innerJoin.getDriverStrategy()); 
		
			new NepheleJobGraphGenerator().compileJobGraph(oPlan);
		}
		catch (Exception e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
			fail("Test errored: " + e.getMessage());
		}
	}
	
	/**
	 * This test simulates a join of a big left side with a small right side inside of an iteration, where the small side is on a static path.
	 * Currently the best execution plan is a HYBRIDHASH_BUILD_SECOND_CACHED, where the small side is hashed and cached.
	 * This test also makes sure that all relevant plans are correctly enumerated by the optimizer.
	 */
	@Test
	public void testCorrectChoosing() {
		try {
			
			Plan plan = getTestPlanRightStatic("");
			
			SourceCollectorVisitor sourceCollector = new SourceCollectorVisitor();
			plan.accept(sourceCollector);
			
			for(GenericDataSourceBase<?, ?> s : sourceCollector.getSources()) {
				if(s.getName().equals("bigFile")) {
					this.setSourceStatistics(s, 10000000, 1000);
				}
				else if(s.getName().equals("smallFile")) {
					this.setSourceStatistics(s, 100, 100);
				}
			}
			
			OptimizedPlan oPlan = compileNoStats(plan);
	
			OptimizerPlanNodeResolver resolver = getOptimizerPlanNodeResolver(oPlan);
			DualInputPlanNode innerJoin = resolver.getNode("DummyJoiner");
			
			// verify correct join strategy
			assertEquals(DriverStrategy.HYBRIDHASH_BUILD_SECOND_CACHED, innerJoin.getDriverStrategy()); 
		
			new NepheleJobGraphGenerator().compileJobGraph(oPlan);
		}
		catch (Exception e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
			fail("Test errored: " + e.getMessage());
		}
	}
	
	private Plan getTestPlanRightStatic(String strategy) {
		
		ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
		env.setDegreeOfParallelism(DEFAULT_PARALLELISM);
		
		DataSet<Tuple3<Long, Long, Long>> bigInput = env.readCsvFile("file://bigFile").types(Long.class, Long.class, Long.class).name("bigFile");
		
		DataSet<Tuple3<Long, Long, Long>> smallInput = env.readCsvFile("file://smallFile").types(Long.class, Long.class, Long.class).name("smallFile");
		
		IterativeDataSet<Tuple3<Long, Long, Long>> iteration = bigInput.iterate(10);
		
		DataSet<Tuple3<Long, Long, Long>> inner;
		
		if(strategy != "") {
			Configuration joinStrategy = new Configuration();
			joinStrategy.setString(PactCompiler.HINT_LOCAL_STRATEGY, strategy);
			inner = iteration.join(smallInput).where(0).equalTo(0).with(new DummyJoiner()).name("DummyJoiner").withParameters(joinStrategy);
		}
		else {
			inner = iteration.join(smallInput).where(0).equalTo(0).with(new DummyJoiner()).name("DummyJoiner");
		}

		DataSet<Tuple3<Long, Long, Long>> output = iteration.closeWith(inner);
		
		output.print();
		
		return env.createProgramPlan();
		
	}
	
	private Plan getTestPlanLeftStatic(String strategy) {
		
		ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
		env.setDegreeOfParallelism(DEFAULT_PARALLELISM);
		
		@SuppressWarnings("unchecked")
		DataSet<Tuple3<Long, Long, Long>> bigInput = env.fromElements(new Tuple3<Long, Long, Long>(1L, 2L, 3L),
				new Tuple3<Long, Long, Long>(1L, 2L, 3L),new Tuple3<Long, Long, Long>(1L, 2L, 3L)).name("Big");
		
		@SuppressWarnings("unchecked")
		DataSet<Tuple3<Long, Long, Long>> smallInput = env.fromElements(new Tuple3<Long, Long, Long>(1L, 2L, 3L)).name("Small");
		
		IterativeDataSet<Tuple3<Long, Long, Long>> iteration = bigInput.iterate(10);
		
		Configuration joinStrategy = new Configuration();
		joinStrategy.setString(PactCompiler.HINT_LOCAL_STRATEGY, strategy);
		
		DataSet<Tuple3<Long, Long, Long>> inner = smallInput.join(iteration).where(0).equalTo(0).with(new DummyJoiner()).name("DummyJoiner").withParameters(joinStrategy);

		DataSet<Tuple3<Long, Long, Long>> output = iteration.closeWith(inner);
		
		output.print();
		
		return env.createProgramPlan();
		
	}
	
	private static class DummyJoiner extends JoinFunction<Tuple3<Long, Long, Long>, Tuple3<Long, Long, Long>, Tuple3<Long, Long, Long>> {

		@Override
		public Tuple3<Long, Long, Long> join(Tuple3<Long, Long, Long> first,
				Tuple3<Long, Long, Long> second) throws Exception {

			return first;
		}
	}
}


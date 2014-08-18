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

package eu.stratosphere.streaming.examples.join;

import java.util.ArrayList;
import java.util.HashMap;

import eu.stratosphere.api.java.functions.FlatMapFunction;
import eu.stratosphere.api.java.tuple.Tuple3;
import eu.stratosphere.util.Collector;

public class JoinTask extends
		FlatMapFunction<Tuple3<String, String, Integer>, Tuple3<String, Integer, Integer>> {
	private static final long serialVersionUID = 749913336259789039L;

	private HashMap<String, ArrayList<Integer>> gradeHashmap;
	private HashMap<String, ArrayList<Integer>> salaryHashmap;

	public JoinTask() {
		gradeHashmap = new HashMap<String, ArrayList<Integer>>();
		salaryHashmap = new HashMap<String, ArrayList<Integer>>();
	}

	@Override
	public void flatMap(Tuple3<String, String, Integer> value,
			Collector<Tuple3<String, Integer, Integer>> out) throws Exception {
		String streamId = value.f0;
		String name = value.f1;
		;
		if (streamId.equals("grade")) {
			if (salaryHashmap.containsKey(name)) {
				for (Integer salary : salaryHashmap.get(name)) {
					Tuple3<String, Integer, Integer> outputTuple = new Tuple3<String, Integer, Integer>(
							name, value.f2, salary);
					out.collect(outputTuple);
				}
			}
			if (!gradeHashmap.containsKey(name)) {
				gradeHashmap.put(name, new ArrayList<Integer>());
			}
			gradeHashmap.get(name).add(value.f2);
		} else {
			if (gradeHashmap.containsKey(name)) {
				for (Integer grade : gradeHashmap.get(name)) {
					Tuple3<String, Integer, Integer> outputTuple = new Tuple3<String, Integer, Integer>(
							name, grade, value.f2);
					out.collect(outputTuple);
				}
			}
			if (!salaryHashmap.containsKey(name)) {
				salaryHashmap.put(name, new ArrayList<Integer>());
			}
			salaryHashmap.get(name).add(value.f2);
		}
	}
}

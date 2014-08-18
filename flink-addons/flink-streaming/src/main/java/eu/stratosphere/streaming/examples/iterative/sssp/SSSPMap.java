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

package eu.stratosphere.streaming.examples.iterative.sssp;

import eu.stratosphere.api.java.tuple.Tuple1;
import eu.stratosphere.streaming.api.invokable.UserTaskInvokable;
import eu.stratosphere.streaming.api.streamrecord.StreamRecord;
import eu.stratosphere.streaming.state.GraphState;

//public class SSSPMap extends UserTaskInvokable {
//
//	private static final long serialVersionUID = 1L;
//	private StreamRecord outRecord = new StreamRecord(new Tuple1<String>());
//	private GraphState linkGraph = new GraphState();
//	
//	@Override
//	public void invoke(StreamRecord record) throws Exception {
//		Integer sourceNode = record.getInteger(0, 0);
//		Integer targetNode = record.getInteger(0, 1);
//		// set the input graph.
//		linkGraph.insertDirectedEdge(sourceNode, targetNode);
//		
//		//outRecord.setString(0, line);
//	}
//
//}

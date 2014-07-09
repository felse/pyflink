/***********************************************************************************************************************
 *
 * Copyright (C) 2010-2013 by the Apache Flink project (http://flink.incubator.apache.org)
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
package eu.stratosphere.test.exampleJavaPrograms;

import org.apache.flink.example.java.graph.EnumTrianglesBasic;
import org.apache.flink.test.testdata.EnumTriangleData;
import org.apache.flink.test.util.JavaProgramTestBase;

public class EnumTriangleBasicITCase extends JavaProgramTestBase {
	
	protected String edgePath;
	protected String resultPath;
	
	@Override
	protected void preSubmit() throws Exception {
		edgePath = createTempFile("edges", EnumTriangleData.EDGES);
		resultPath = getTempDirPath("triangles");
	}

	@Override
	protected void postSubmit() throws Exception {
		compareResultsByLinesInMemory(EnumTriangleData.TRIANGLES_BY_ID, resultPath);
	}
	
	@Override
	protected void testProgram() throws Exception {
		EnumTrianglesBasic.main(new String[] { edgePath, resultPath });
	}

}

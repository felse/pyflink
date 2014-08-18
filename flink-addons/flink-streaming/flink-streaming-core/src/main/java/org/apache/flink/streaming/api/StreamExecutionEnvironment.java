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

package org.apache.flink.streaming.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Collection;

import org.apache.flink.api.common.functions.AbstractFunction;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.api.java.tuple.Tuple1;
import org.apache.flink.streaming.api.collector.OutputSelector;
import org.apache.flink.streaming.api.function.FileSourceFunction;
import org.apache.flink.streaming.api.function.FileStreamFunction;
import org.apache.flink.streaming.api.function.FromElementsFunction;
import org.apache.flink.streaming.api.function.GenSequenceFunction;
import org.apache.flink.streaming.api.function.PrintSinkFunction;
import org.apache.flink.streaming.api.function.SinkFunction;
import org.apache.flink.streaming.api.function.SourceFunction;
import org.apache.flink.streaming.api.invokable.SinkInvokable;
import org.apache.flink.streaming.api.invokable.UserTaskInvokable;

/**
 * {@link ExecutionEnvironment} for streaming jobs. An instance of it is necessary to
 * construct streaming topologies.
 * 
 */
public abstract class StreamExecutionEnvironment {

	/**
	 * The environment of the context (local by default, cluster if invoked
	 * through command line)
	 */
	private static StreamExecutionEnvironment contextEnvironment;

	/** flag to disable local executor when using the ContextEnvironment */
	private static boolean allowLocalExecution = true;

	private static int defaultLocalDop = Runtime.getRuntime().availableProcessors();

	private int degreeOfParallelism = 1;

	private int executionParallelism = -1;

	protected JobGraphBuilder jobGraphBuilder;

	/**
	 * Partitioning strategy on the stream.
	 */
	public static enum ConnectionType {
		SHUFFLE, BROADCAST, FIELD
	}

	// --------------------------------------------------------------------------------------------
	// Constructor and Properties
	// --------------------------------------------------------------------------------------------

	/**
	 * Constructor for creating StreamExecutionEnvironment
	 */
	protected StreamExecutionEnvironment() {
		jobGraphBuilder = new JobGraphBuilder("jobGraph");
	}

	public int getExecutionParallelism() {
		return executionParallelism == -1 ? degreeOfParallelism : executionParallelism;
	}

	/**
	 * Gets the degree of parallelism with which operation are executed by
	 * default. Operations can individually override this value to use a
	 * specific degree of parallelism via {@link DataStream#setParallelism}.
	 * 
	 * @return The degree of parallelism used by operations, unless they
	 *         override that value.
	 */
	public int getDegreeOfParallelism() {
		return this.degreeOfParallelism;
	}

	/**
	 * Sets the degree of parallelism (DOP) for operations executed through this
	 * environment. Setting a DOP of x here will cause all operators (such as
	 * map, batchReduce) to run with x parallel instances. This method overrides
	 * the default parallelism for this environment. The
	 * {@link LocalStreamEnvironment} uses by default a value equal to the
	 * number of hardware contexts (CPU cores / threads). When executing the
	 * program via the command line client from a JAR file, the default degree
	 * of parallelism is the one configured for that setup.
	 * 
	 * @param degreeOfParallelism
	 *            The degree of parallelism
	 */
	protected void setDegreeOfParallelism(int degreeOfParallelism) {
		if (degreeOfParallelism < 1)
			throw new IllegalArgumentException("Degree of parallelism must be at least one.");

		this.degreeOfParallelism = degreeOfParallelism;
	}

	/**
	 * Sets the number of hardware contexts (CPU cores / threads) used when
	 * executed in {@link LocalStreamEnvironment}.
	 * 
	 * @param degreeOfParallelism
	 *            The degree of parallelism in local environment
	 */
	public void setExecutionParallelism(int degreeOfParallelism) {
		if (degreeOfParallelism < 1)
			throw new IllegalArgumentException("Degree of parallelism must be at least one.");

		this.executionParallelism = degreeOfParallelism;
	}

	// --------------------------------------------------------------------------------------------
	// Data stream creations
	// --------------------------------------------------------------------------------------------

	/**
	 * Creates a DataStream that represents the Strings produced by reading the
	 * given file line wise. The file will be read with the system's default
	 * character set.
	 * 
	 * @param filePath
	 *            The path of the file, as a URI (e.g.,
	 *            "file:///some/local/file" or "hdfs://host:port/file/path").
	 * @return The DataStream representing the text file.
	 */
	public DataStream<Tuple1<String>> readTextFile(String filePath) {
		return addSource(new FileSourceFunction(filePath), 1);
	}

	public DataStream<Tuple1<String>> readTextFile(String filePath, int parallelism) {
		return addSource(new FileSourceFunction(filePath), parallelism);
	}

	/**
	 * Creates a DataStream that represents the Strings produced by reading the
	 * given file line wise multiple times(infinite). The file will be read with
	 * the system's default character set.
	 * 
	 * @param filePath
	 *            The path of the file, as a URI (e.g.,
	 *            "file:///some/local/file" or "hdfs://host:port/file/path").
	 * @return The DataStream representing the text file.
	 */
	public DataStream<Tuple1<String>> readTextStream(String filePath) {
		return addSource(new FileStreamFunction(filePath), 1);
	}

	public DataStream<Tuple1<String>> readTextStream(String filePath, int parallelism) {
		return addSource(new FileStreamFunction(filePath), parallelism);
	}

	/**
	 * Creates a new DataStream that contains the given elements. The elements
	 * must all be of the same type, for example, all of the String or Integer.
	 * The sequence of elements must not be empty. Furthermore, the elements
	 * must be serializable (as defined in java.io.Serializable), because the
	 * execution environment may ship the elements into the cluster.
	 * 
	 * @param data
	 *            The collection of elements to create the DataStream from.
	 * @param <X>
	 *            type of the returned stream
	 * @return The DataStream representing the elements.
	 */
	public <X> DataStream<Tuple1<X>> fromElements(@SuppressWarnings("unchecked") X... data) {
		DataStream<Tuple1<X>> returnStream = new DataStream<Tuple1<X>>(this, "elements");

		jobGraphBuilder.addSource(returnStream.getId(), new FromElementsFunction<X>(data),
				"elements", serializeToByteArray(data[0]), 1);

		return returnStream.copy();
	}

	/**
	 * Creates a DataStream from the given non-empty collection. The type of the
	 * DataStream is that of the elements in the collection. The elements need
	 * to be serializable (as defined by java.io.Serializable), because the
	 * framework may move the elements into the cluster if needed.
	 * 
	 * @param data
	 *            The collection of elements to create the DataStream from.
	 * @param <X>
	 *            type of the returned stream
	 * @return The DataStream representing the elements.
	 */
	public <X> DataStream<Tuple1<X>> fromCollection(Collection<X> data) {
		DataStream<Tuple1<X>> returnStream = new DataStream<Tuple1<X>>(this, "elements");

		jobGraphBuilder.addSource(returnStream.getId(), new FromElementsFunction<X>(data),
				"elements", serializeToByteArray(data.toArray()[0]), 1);

		return returnStream.copy();
	}

	/**
	 * Creates a new DataStream that contains a sequence of numbers.
	 * 
	 * @param from
	 *            The number to start at (inclusive).
	 * @param to
	 *            The number to stop at (inclusive)
	 * @return A DataStrean, containing all number in the [from, to] interval.
	 */
	public DataStream<Tuple1<Long>> generateSequence(long from, long to) {
		return addSource(new GenSequenceFunction(from, to), 1);
	}
	
	/**
	 * Ads a data source thus opening a {@link DataStream}.
	 * 
	 * @param sourceFunction
	 *            the user defined function
	 * @param parallelism
	 *            number of parallel instances of the function
	 * @param <T>
	 *            type of the returned stream
	 * @return the data stream constructed
	 */
	public <T extends Tuple> DataStream<T> addSource(SourceFunction<T> sourceFunction,
			int parallelism) {
		DataStream<T> returnStream = new DataStream<T>(this, "source");

		jobGraphBuilder.addSource(returnStream.getId(), sourceFunction, "source",
				serializeToByteArray(sourceFunction), parallelism);

		return returnStream.copy();
	}

	public <T extends Tuple> DataStream<T> addSource(SourceFunction<T> sourceFunction) {
		return addSource(sourceFunction, 1);
	}

	// --------------------------------------------------------------------------------------------
	// Data stream operators and sinks
	// --------------------------------------------------------------------------------------------

	/**
	 * Internal function for passing the user defined functions to the JobGraph
	 * of the job.
	 * 
	 * @param functionName
	 *            name of the function
	 * @param inputStream
	 *            input data stream
	 * @param function
	 *            the user defined function
	 * @param functionInvokable
	 *            the wrapping JobVertex instance
	 * @param <T>
	 *            type of the input stream
	 * @param <R>
	 *            type of the return stream
	 * @return the data stream constructed
	 */
	protected <T extends Tuple, R extends Tuple> DataStream<R> addFunction(String functionName,
			DataStream<T> inputStream, final AbstractFunction function,
			UserTaskInvokable<T, R> functionInvokable) {
		DataStream<R> returnStream = new DataStream<R>(this, functionName);

		jobGraphBuilder.addTask(returnStream.getId(), functionInvokable, functionName,
				serializeToByteArray(function), degreeOfParallelism);

		connectGraph(inputStream, returnStream.getId());

		return returnStream;
	}

	protected <T extends Tuple, R extends Tuple> void addIterationSource(DataStream<T> inputStream) {
		DataStream<R> returnStream = new DataStream<R>(this, "iterationHead");

		jobGraphBuilder.addIterationSource(returnStream.getId(), inputStream.getId(),
				degreeOfParallelism);

		jobGraphBuilder.shuffleConnect(returnStream.getId(), inputStream.getId());
	}

	protected <T extends Tuple, R extends Tuple> void addIterationSink(DataStream<T> inputStream) {
		DataStream<R> returnStream = new DataStream<R>(this, "iterationTail");

		jobGraphBuilder.addIterationSink(returnStream.getId(), inputStream.getId(),
				degreeOfParallelism, "iterate");

		for (int i = 0; i < inputStream.connectIDs.size(); i++) {
			String input = inputStream.connectIDs.get(i);
			jobGraphBuilder.shuffleConnect(input, returnStream.getId());

		}
	}

	/**
	 * Adds the given sink to this environment. Only streams with sinks added
	 * will be executed once the {@link #execute()} method is called.
	 * 
	 * @param inputStream
	 *            input data stream
	 * @param sinkFunction
	 *            the user defined function
	 * @param <T>
	 *            type of the returned stream
	 * @return the data stream constructed
	 */
	protected <T extends Tuple> DataStream<T> addSink(DataStream<T> inputStream,
			SinkFunction<T> sinkFunction) {
		DataStream<T> returnStream = new DataStream<T>(this, "sink");

		jobGraphBuilder.addSink(returnStream.getId(), new SinkInvokable<T>(sinkFunction), "sink",
				serializeToByteArray(sinkFunction), degreeOfParallelism);

		connectGraph(inputStream, returnStream.getId());

		return returnStream;
	}

	<T extends Tuple> void addDirectedEmit(String id, OutputSelector<T> outputSelector) {
		jobGraphBuilder.setOutputSelector(id, serializeToByteArray(outputSelector));
	}

	/**
	 * Writes a DataStream to the standard output stream (stdout). For each
	 * element of the DataStream the result of {@link Object#toString()} is
	 * written.
	 * 
	 * @param inputStream
	 *            the input data stream
	 * 
	 * @param <T>
	 *            type of the returned stream
	 * @return the data stream constructed
	 */
	protected <T extends Tuple> DataStream<T> print(DataStream<T> inputStream) {
		DataStream<T> returnStream = addSink(inputStream, new PrintSinkFunction<T>());

		jobGraphBuilder.setBytesFrom(inputStream.getId(), returnStream.getId());

		return returnStream;
	}

	/**
	 * Internal function for assembling the underlying
	 * {@link org.apache.flink.nephele.jobgraph.JobGraph} of the job. Connects
	 * the outputs of the given input stream to the specified output stream
	 * given by the outputID.
	 * 
	 * @param inputStream
	 *            input data stream
	 * @param outputID
	 *            ID of the output
	 * @param <T>
	 *            type of the input stream
	 */
	private <T extends Tuple> void connectGraph(DataStream<T> inputStream, String outputID) {

		for (int i = 0; i < inputStream.connectIDs.size(); i++) {
			ConnectionType type = inputStream.ctypes.get(i);
			String input = inputStream.connectIDs.get(i);
			int param = inputStream.cparams.get(i);

			switch (type) {
			case SHUFFLE:
				jobGraphBuilder.shuffleConnect(input, outputID);
				break;
			case BROADCAST:
				jobGraphBuilder.broadcastConnect(input, outputID);
				break;
			case FIELD:
				jobGraphBuilder.fieldsConnect(input, outputID, param);
				break;
			}

		}

	}

	protected <T extends Tuple> void setName(DataStream<T> stream, String name) {
		jobGraphBuilder.setUserDefinedName(stream.getId(), name);
	}

	/**
	 * Sets the proper parallelism for the given operator in the JobGraph
	 * 
	 * @param inputStream
	 *            DataStream corresponding to the operator
	 * @param <T>
	 *            type of the operator
	 */
	protected <T extends Tuple> void setOperatorParallelism(DataStream<T> inputStream) {
		jobGraphBuilder.setParallelism(inputStream.getId(), inputStream.degreeOfParallelism);
	}

	/**
	 * Converts object to byte array using default java serialization
	 * 
	 * @param object
	 *            Object to be serialized
	 * @return Serialized object
	 */
	private static byte[] serializeToByteArray(Object object) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ObjectOutputStream oos;
		try {
			oos = new ObjectOutputStream(baos);
			oos.writeObject(object);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return baos.toByteArray();
	}

	// --------------------------------------------------------------------------------------------
	// Instantiation of Execution Contexts
	// --------------------------------------------------------------------------------------------

	/**
	 * Creates an execution environment that represents the context in which the
	 * program is currently executed. If the program is invoked standalone, this
	 * method returns a local execution environment, as returned by
	 * {@link #createLocalEnvironment()}.
	 * 
	 * @return The execution environment of the context in which the program is
	 *         executed.
	 */
	public static StreamExecutionEnvironment getExecutionEnvironment() {
		return contextEnvironment == null ? createLocalEnvironment() : contextEnvironment;
	}

	/**
	 * Creates a {@link LocalStreamEnvironment}. The local execution environment
	 * will run the program in a multi-threaded fashion in the same JVM as the
	 * environment was created in. The default degree of parallelism of the
	 * local environment is the number of hardware contexts (CPU cores /
	 * threads), unless it was specified differently by
	 * {@link #setDegreeOfParallelism(int)}.
	 * 
	 * @return A local execution environment.
	 */
	public static LocalStreamEnvironment createLocalEnvironment() {
		return createLocalEnvironment(defaultLocalDop);
	}

	/**
	 * Creates a {@link LocalStreamEnvironment}. The local execution environment
	 * will run the program in a multi-threaded fashion in the same JVM as the
	 * environment was created in. It will use the degree of parallelism
	 * specified in the parameter.
	 * 
	 * @param degreeOfParallelism
	 *            The degree of parallelism for the local environment.
	 * @return A local execution environment with the specified degree of
	 *         parallelism.
	 */
	public static LocalStreamEnvironment createLocalEnvironment(int degreeOfParallelism) {
		LocalStreamEnvironment lee = new LocalStreamEnvironment();
		lee.setDegreeOfParallelism(degreeOfParallelism);
		return lee;
	}

	// TODO:fix cluster default parallelism
	/**
	 * Creates a {@link RemoteStreamEnvironment}. The remote environment sends
	 * (parts of) the program to a cluster for execution. Note that all file
	 * paths used in the program must be accessible from the cluster. The
	 * execution will use no parallelism, unless the parallelism is set
	 * explicitly via {@link #setDegreeOfParallelism}.
	 * 
	 * @param host
	 *            The host name or address of the master (JobManager), where the
	 *            program should be executed.
	 * @param port
	 *            The port of the master (JobManager), where the program should
	 *            be executed.
	 * @param jarFiles
	 *            The JAR files with code that needs to be shipped to the
	 *            cluster. If the program uses user-defined functions,
	 *            user-defined input formats, or any libraries, those must be
	 *            provided in the JAR files.
	 * @return A remote environment that executes the program on a cluster.
	 */
	public static StreamExecutionEnvironment createRemoteEnvironment(String host, int port,
			String... jarFiles) {
		return new RemoteStreamEnvironment(host, port, jarFiles);
	}

	/**
	 * Creates a {@link RemoteStreamEnvironment}. The remote environment sends
	 * (parts of) the program to a cluster for execution. Note that all file
	 * paths used in the program must be accessible from the cluster. The
	 * execution will use the specified degree of parallelism.
	 * 
	 * @param host
	 *            The host name or address of the master (JobManager), where the
	 *            program should be executed.
	 * @param port
	 *            The port of the master (JobManager), where the program should
	 *            be executed.
	 * @param degreeOfParallelism
	 *            The degree of parallelism to use during the execution.
	 * @param jarFiles
	 *            The JAR files with code that needs to be shipped to the
	 *            cluster. If the program uses user-defined functions,
	 *            user-defined input formats, or any libraries, those must be
	 *            provided in the JAR files.
	 * @return A remote environment that executes the program on a cluster.
	 */
	public static StreamExecutionEnvironment createRemoteEnvironment(String host, int port,
			int degreeOfParallelism, String... jarFiles) {
		RemoteStreamEnvironment rec = new RemoteStreamEnvironment(host, port, jarFiles);
		rec.setDegreeOfParallelism(degreeOfParallelism);
		return rec;
	}

	// --------------------------------------------------------------------------------------------
	// Methods to control the context and local environments for execution from
	// packaged programs
	// --------------------------------------------------------------------------------------------

	protected static void initializeContextEnvironment(StreamExecutionEnvironment ctx) {
		contextEnvironment = ctx;
	}

	protected static boolean isContextEnvironmentSet() {
		return contextEnvironment != null;
	}

	protected static void disableLocalExecution() {
		allowLocalExecution = false;
	}

	public static boolean localExecutionIsAllowed() {
		return allowLocalExecution;
	}

	/**
	 * Triggers the program execution. The environment will execute all parts of
	 * the program that have resulted in a "sink" operation. Sink operations are
	 * for example printing results or forwarding them to a message queue.
	 * <p>
	 * The program execution will be logged and displayed with a generated
	 * default name.
	 **/
	public abstract void execute();

	/**
	 * Getter of the {@link JobGraphBuilder} of the streaming job.
	 * 
	 * @return jobgraph
	 */
	public JobGraphBuilder jobGB() {
		return jobGraphBuilder;
	}

}

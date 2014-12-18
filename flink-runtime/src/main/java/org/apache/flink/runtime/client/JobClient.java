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

package org.apache.flink.runtime.client;

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.Map;

import akka.actor.ActorRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.flink.api.common.JobExecutionResult;
import org.apache.flink.api.common.accumulators.AccumulatorHelper;
import org.apache.flink.configuration.ConfigConstants;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.accumulators.AccumulatorEvent;
import org.apache.flink.runtime.akka.AkkaUtils;
import org.apache.flink.runtime.event.job.AbstractEvent;
import org.apache.flink.runtime.event.job.JobEvent;
import org.apache.flink.runtime.jobgraph.JobGraph;
import org.apache.flink.runtime.jobgraph.JobStatus;
import org.apache.flink.runtime.jobmanager.JobManager;
import org.apache.flink.runtime.messages.EventCollectorMessages;
import org.apache.flink.runtime.messages.JobManagerMessages;
import org.apache.flink.runtime.messages.JobResult;
import org.apache.flink.runtime.messages.JobResult.JobCancelResult;
import org.apache.flink.runtime.messages.JobResult.JobSubmissionResult;
import org.apache.flink.runtime.messages.JobResult.JobProgressResult;
import org.apache.flink.util.StringUtils;

/**
 * The job client is able to submit, control, and abort jobs.
 */
public class JobClient {

	/** The logging object used for debugging. */
	private static final Logger LOG = LoggerFactory.getLogger(JobClient.class);

	private final ActorRef jobManager;
	/**
	private final JobManagementProtocol jobSubmitClient;


	/** The job graph assigned with this job client. */
	private final JobGraph jobGraph;

	/**
	 * The configuration assigned with this job client.
	 */
	private final Configuration configuration;

	private final ClassLoader userCodeClassLoader;

	/**
	 * The sequence number of the last processed event received from the job manager.
	 */
	private long lastProcessedEventSequenceNumber = -1;

	private PrintStream console;

	/**
	 * Constructs a new job client object and instantiates a local
	 * RPC proxy for the JobSubmissionProtocol
	 * 
	 * @param jobGraph
	 *        the job graph to run
	 * @throws IOException
	 *         thrown on error while initializing the RPC connection to the job manager
	 */
	public JobClient(JobGraph jobGraph, ClassLoader userCodeClassLoader) throws IOException {
		this(jobGraph, new Configuration(), userCodeClassLoader);
	}

	/**
	 * Constructs a new job client object and instantiates a local
	 * RPC proxy for the JobSubmissionProtocol
	 * 
	 * @param jobGraph
	 *        the job graph to run
	 * @param configuration
	 *        configuration object which can include special configuration settings for the job client
	 * @throws IOException
	 *         thrown on error while initializing the RPC connection to the job manager
	 */
	public JobClient(JobGraph jobGraph, Configuration configuration, ClassLoader userCodeClassLoader) throws IOException {

		final String address = configuration.getString(ConfigConstants.JOB_MANAGER_IPC_ADDRESS_KEY, null);
		final int port = configuration.getInteger(ConfigConstants.JOB_MANAGER_IPC_PORT_KEY,
			ConfigConstants.DEFAULT_JOB_MANAGER_IPC_PORT);

		final InetSocketAddress inetaddr = new InetSocketAddress(address, port);
		this.jobManager = JobManager.getJobManager(inetaddr);
		this.jobGraph = jobGraph;
		this.configuration = configuration;
		this.userCodeClassLoader = userCodeClassLoader;
	}

	/**
	 * Returns the {@link Configuration} object which can include special configuration settings for the job client.
	 * 
	 * @return the {@link Configuration} object which can include special configuration settings for the job client
	 */
	public Configuration getConfiguration() {

		return this.configuration;
	}

	/**
	 * Submits the job assigned to this job client to the job manager.
	 * 
	 * @return a <code>JobSubmissionResult</code> object encapsulating the results of the job submission
	 * @throws IOException
	 *         thrown in case of submission errors while transmitting the data to the job manager
	 */
	public JobSubmissionResult submitJob() throws IOException {
			// Get port of BLOB server
			final int port = this.jobSubmitClient.getBlobServerPort();
			if (port == -1) {
				throw new IOException("Unable to upload user jars: BLOB server not running");
			}

			// We submit the required files with the BLOB manager before the submission of the actual job graph
			final String jobManagerAddress = configuration.getString(ConfigConstants.JOB_MANAGER_IPC_ADDRESS_KEY, null);

			if(jobManagerAddress == null){
				throw new IOException("Unable to find job manager address from configuration.");
			}

			final InetSocketAddress blobManagerAddress = new InetSocketAddress(jobManagerAddress,
					port);

			this.jobGraph.uploadRequiredJarFiles(blobManagerAddress);
			
			try{
				return AkkaUtils.ask(jobManager, new JobManagerMessages.SubmitJob(jobGraph));
			}catch(IOException ioe) {
				throw ioe;
			}
		}
	}

	/**
	 * Cancels the job assigned to this job client.
	 * 
	 * @return a <code>JobCancelResult</code> object encapsulating the result of the job cancel request
	 * @throws IOException
	 *         thrown if an error occurred while transmitting the request to the job manager
	 */
	public JobCancelResult cancelJob() throws IOException {
		try{
			return AkkaUtils.ask(jobManager, new JobManagerMessages.CancelJob(jobGraph.getJobID()));
		}catch(IOException ioe){
			throw ioe;
		}
	}

	/**
	 * Retrieves the current status of the job assigned to this job client.
	 * 
	 * @return a <code>JobProgressResult</code> object including the current job progress
	 * @throws IOException
	 *         thrown if an error occurred while transmitting the request
	 */
	public JobProgressResult getJobProgress() throws IOException {
			return AkkaUtils.ask(jobManager, new EventCollectorMessages.RequestJobProgress(jobGraph.getJobID()));
	}

	/**
	 * Submits the job assigned to this job client to the job manager and queries the job manager
	 * about the progress of the job until it is either finished or aborted.
	 * 
	 * @return the duration of the job execution in milliseconds
	 * @throws IOException
	 *         thrown if an error occurred while transmitting the request
	 * @throws JobExecutionException
	 *         thrown if the job has been aborted either by the user or as a result of an error
	 */
	public JobExecutionResult submitJobAndWait() throws IOException, JobExecutionException {


		final JobSubmissionResult submissionResult = submitJob();
		if (submissionResult.returnCode() == JobResult.ERROR()) {
			LOG.error("ERROR: " + submissionResult.description());
			throw new JobExecutionException(submissionResult.description(), false);
		}

		long sleep = 0;
		final int interval = AkkaUtils.<Integer>ask(jobManager, JobManagerMessages.RequestPollingInterval$
				.MODULE$);
		sleep = interval * 1000;

		try {
			Thread.sleep(sleep / 2);
		} catch (InterruptedException e) {
			logErrorAndRethrow(StringUtils.stringifyException(e));
		}

		long startTimestamp = -1;

		while (true) {

			if (Thread.interrupted()) {
				logErrorAndRethrow("Job client has been interrupted");
			}

			JobResult.JobProgressResult jobProgressResult = null;
			jobProgressResult = getJobProgress();

			if (jobProgressResult == null) {
				logErrorAndRethrow("Returned job progress is unexpectedly null!");
			}

			if (jobProgressResult.returnCode() == JobResult.ERROR()) {
				logErrorAndRethrow("Could not retrieve job progress: " + jobProgressResult.description());
			}

			final Iterator<AbstractEvent> it = jobProgressResult.asJavaList().iterator();
			while (it.hasNext()) {

				final AbstractEvent event = it.next();

				// Did we already process that event?
				if (this.lastProcessedEventSequenceNumber >= event.getSequenceNumber()) {
					continue;
				}

				LOG.info(event.toString());
				if (this.console != null) {
					this.console.println(event.toString());
				}

				this.lastProcessedEventSequenceNumber = event.getSequenceNumber();

				// Check if we can exit the loop
				if (event instanceof JobEvent) {
					final JobEvent jobEvent = (JobEvent) event;
					final JobStatus jobStatus = jobEvent.getCurrentJobStatus();
					if (jobStatus == JobStatus.RUNNING) {
						startTimestamp = jobEvent.getTimestamp();
					}
					if (jobStatus == JobStatus.FINISHED) {
						final long jobDuration = jobEvent.getTimestamp() - startTimestamp;

						// Request accumulators
						Map<String, Object> accumulators = null;
						accumulators = AccumulatorHelper.toResultMap(getAccumulators().getAccumulators(this.userCodeClassLoader));
						return new JobExecutionResult(jobDuration, accumulators);

					} else if (jobStatus == JobStatus.CANCELED || jobStatus == JobStatus.FAILED) {
						LOG.info(jobEvent.getOptionalMessage());
						if (jobStatus == JobStatus.CANCELED) {
							throw new JobExecutionException(jobEvent.getOptionalMessage(), true);
						} else {
							throw new JobExecutionException(jobEvent.getOptionalMessage(), false);
						}
					}
				}
			}

			try {
				Thread.sleep(sleep);
			} catch (InterruptedException e) {
				logErrorAndRethrow(StringUtils.stringifyException(e));
			}
		}
	}

	/**
	 * Writes the given error message to the log and throws it in an {@link IOException}.
	 * 
	 * @param errorMessage
	 *        the error message to write to the log
	 * @throws IOException
	 *         thrown after the error message is written to the log
	 */
	private void logErrorAndRethrow(final String errorMessage) throws IOException {
		LOG.error(errorMessage);
		throw new IOException(errorMessage);
	}

	public void setConsoleStreamForReporting(PrintStream stream) {
		this.console = stream;
	}

	private AccumulatorEvent getAccumulators() throws IOException {
		return AkkaUtils.ask(jobManager, new JobManagerMessages.RequestAccumulatorResult(jobGraph.getJobID()));
	}
}

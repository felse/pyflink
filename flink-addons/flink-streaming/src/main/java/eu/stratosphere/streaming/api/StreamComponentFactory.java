package eu.stratosphere.streaming.api;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import eu.stratosphere.configuration.Configuration;
import eu.stratosphere.nephele.event.task.EventListener;
import eu.stratosphere.nephele.io.ChannelSelector;
import eu.stratosphere.nephele.io.RecordReader;
import eu.stratosphere.nephele.io.RecordWriter;
import eu.stratosphere.nephele.template.AbstractTask;
import eu.stratosphere.streaming.partitioner.DefaultPartitioner;
import eu.stratosphere.streaming.partitioner.FieldsPartitioner;
import eu.stratosphere.types.Key;
import eu.stratosphere.types.Record;
import eu.stratosphere.types.StringValue;

public final class StreamComponentFactory {
	// TODO: put setConfigInputs here

	public static void setAckListener(Map<String, StreamRecord> recordBuffer,
			String sourceInstanceID, List<RecordWriter<Record>> outputs) {
		EventListener eventListener = new AckEventListener(sourceInstanceID,
				recordBuffer);
		for (RecordWriter<Record> output : outputs) {
			// TODO: separate outputs
			output.subscribeToEvent(eventListener, AckEvent.class);
		}
	}

	public static int setConfigInputs(StreamTask taskBase,
			Configuration taskConfiguration, List<RecordReader<Record>> inputs) {
		int numberOfInputs = taskConfiguration.getInteger("numberOfInputs", 0);
		for (int i = 0; i < numberOfInputs; i++) {
			inputs.add(new RecordReader<Record>(taskBase, Record.class));
		}
		return numberOfInputs;
	}

	// this function can be removed as duplication of the above function if
	// modification on kernel is allowed.
	public static int setConfigInputs(StreamSink taskBase,
			Configuration taskConfiguration, List<RecordReader<Record>> inputs) {
		int numberOfInputs = taskConfiguration.getInteger("numberOfInputs", 0);
		for (int i = 0; i < numberOfInputs; i++) {
			inputs.add(new RecordReader<Record>(taskBase, Record.class));
		}
		return numberOfInputs;
	}

	public static int setConfigOutputs(StreamTask taskBase,
			Configuration taskConfiguration,
			List<RecordWriter<Record>> outputs,
			List<ChannelSelector<Record>> partitioners) {
		int numberOfOutputs = taskConfiguration
				.getInteger("numberOfOutputs", 0);
		for (int i = 1; i <= numberOfOutputs; i++) {
			StreamComponentFactory.setPartitioner(taskConfiguration, i,
					partitioners);
		}
		for (ChannelSelector<Record> outputPartitioner : partitioners) {
			outputs.add(new RecordWriter<Record>(taskBase, Record.class,
					outputPartitioner));
		}
		return numberOfOutputs;
	}
	
	// this function can be removed as duplication of the above function if
	// modification on kernel is allowed.
	public static int setConfigOutputs(StreamSource taskBase,
			Configuration taskConfiguration,
			List<RecordWriter<Record>> outputs,
			List<ChannelSelector<Record>> partitioners) {
		int numberOfOutputs = taskConfiguration
				.getInteger("numberOfOutputs", 0);
		for (int i = 1; i <= numberOfOutputs; i++) {
			StreamComponentFactory.setPartitioner(taskConfiguration, i,
					partitioners);
		}
		for (ChannelSelector<Record> outputPartitioner : partitioners) {
			outputs.add(new RecordWriter<Record>(taskBase, Record.class,
					outputPartitioner));
		}
		return numberOfOutputs;
	}

	public static void setPartitioner(Configuration taskConfiguration,
			int nrOutput, List<ChannelSelector<Record>> partitioners) {
		Class<? extends ChannelSelector<Record>> partitioner = taskConfiguration
				.getClass("partitionerClass_" + nrOutput,
						DefaultPartitioner.class, ChannelSelector.class);

		try {
			if (partitioner.equals(FieldsPartitioner.class)) {
				int keyPosition = taskConfiguration.getInteger(
						"partitionerIntParam_" + nrOutput, 1);
				Class<? extends Key> keyClass = taskConfiguration.getClass(
						"partitionerClassParam_" + nrOutput, StringValue.class,
						Key.class);

				partitioners.add(partitioner.getConstructor(int.class,
						Class.class).newInstance(keyPosition, keyClass));

			} else {
				partitioners.add(partitioner.newInstance());
			}
		} catch (Exception e) {
			System.out.println("partitioner error" + " " + "partitioner_"
					+ nrOutput);
			System.out.println(e);
		}
	}
}
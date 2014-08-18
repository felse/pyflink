package eu.stratosphere.streaming.api;

import eu.stratosphere.api.java.tuple.Tuple;
import eu.stratosphere.streaming.api.streamrecord.ArrayStreamRecord;
import eu.stratosphere.streaming.api.streamrecord.StreamRecord;
import eu.stratosphere.util.Collector;

public class StreamCollector implements Collector<Tuple> {

	protected ArrayStreamRecord streamRecord;
	protected int batchSize;
	protected int counter = 0;
	protected int channelID;

	public StreamCollector(int batchSize, int channelID) {
		this.batchSize = batchSize;
		this.streamRecord = new ArrayStreamRecord(batchSize);
		this.channelID = channelID;
	}

	@Override
	public void collect(Tuple tuple) {
		streamRecord.setTuple(counter, StreamRecord.copyTuple(tuple));
		counter++;
		if (counter >= batchSize) {
			counter = 0;
			streamRecord.setId(channelID);
			emit(streamRecord);
		}
	}

	private void emit(ArrayStreamRecord streamRecord) {
		System.out.println(streamRecord);
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub

	}

}

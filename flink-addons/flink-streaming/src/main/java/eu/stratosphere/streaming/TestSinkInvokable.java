package eu.stratosphere.streaming;

import eu.stratosphere.nephele.io.RecordReader;
import eu.stratosphere.types.Record;
import eu.stratosphere.types.StringValue;

public class TestSinkInvokable implements UserSinkInvokable {

	@Override
	public void invoke(Record record, RecordReader<Record> input)
			throws Exception {
		
	    StringValue value = new StringValue("");
	    record.getFieldInto(0, value);
	    System.out.println(value.getValue());
	}

}

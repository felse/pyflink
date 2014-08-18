package eu.stratosphere.streaming;

import eu.stratosphere.core.io.IOReadableWritable;
import eu.stratosphere.nephele.io.RecordWriter;
import eu.stratosphere.types.Record;

public interface UserSourceInvokable {
  public void invoke(RecordWriter<Record> output) throws Exception ;
}

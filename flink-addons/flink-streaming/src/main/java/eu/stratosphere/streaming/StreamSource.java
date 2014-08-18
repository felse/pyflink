package eu.stratosphere.streaming;

import eu.stratosphere.nephele.io.ChannelSelector;
import eu.stratosphere.nephele.io.RecordWriter;
import eu.stratosphere.nephele.template.AbstractInputTask;
import eu.stratosphere.types.Record;

public class StreamSource extends AbstractInputTask<RandIS> {

	private RecordWriter<Record> output;
	private Class<? extends ChannelSelector<Record>> Partitioner;
	ChannelSelector<Record> partitioner;
	private Class<? extends UserSourceInvokable> UserFunction;
	private UserSourceInvokable userFunction;

	public StreamSource() {
		Partitioner = null;
		UserFunction = null;
		partitioner = null;
		userFunction = null;
	}

	@Override
	public RandIS[] computeInputSplits(int requestedMinNumber) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Class<RandIS> getInputSplitType() {
		// TODO Auto-generated method stub
		return null;
	}

	private void setClassInputs() {
		Partitioner = getTaskConfiguration().getClass("partitioner",
				DefaultPartitioner.class, ChannelSelector.class);
		try {
			partitioner = Partitioner.newInstance();
		} catch (Exception e) {

		}
		UserFunction = getTaskConfiguration().getClass("userfunction",
				TestSourceInvokable.class, UserSourceInvokable.class);
		try {
			userFunction = UserFunction.newInstance();
		} catch (Exception e) {

		}
	}

	@Override
	public void registerInputOutput() {
		setClassInputs();
		output = new RecordWriter<Record>(this, Record.class, this.partitioner);
	}

	@Override
	public void invoke() throws Exception {
		userFunction.invoke(output);
	}

}

package eu.stratosphere.streaming.api;

import eu.stratosphere.nephele.event.task.AbstractTaskEvent;
import eu.stratosphere.nephele.event.task.EventListener;

public class AckEventListener implements EventListener {

	private String taskInstanceID;
	private FaultToleranceBuffer recordBuffer;

	public AckEventListener(String taskInstanceID,
			FaultToleranceBuffer recordBuffer) {
		this.taskInstanceID = taskInstanceID;
		this.recordBuffer = recordBuffer;
	}

	public void eventOccurred(AbstractTaskEvent event) {

		AckEvent ackEvent = (AckEvent) event;
		String recordId = ackEvent.getRecordId();
		String ackCID = recordId.split("-", 2)[0];
		if (ackCID.equals(taskInstanceID)) {

			Long nt= System.nanoTime();
			recordBuffer.ackRecord(ackEvent.getRecordId());
			System.out.println("Ack recieved " + ackEvent.getRecordId()+"\nAck exec. time(ns): "+(System.nanoTime()-nt));
			System.out.println("--------------");
		}

	}
}

package eu.stratosphere.streaming.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import eu.stratosphere.types.IntValue;
import eu.stratosphere.types.StringValue;

public class StreamRecordTest {

	@Test
	public void singleRecordSetGetTest() {
		StreamRecord record = new StreamRecord(new StringValue("Stratosphere"), new IntValue(1));
		
		assertEquals(2, record.getNumOfFields());
		assertEquals(1, record.getNumOfRecords());
		assertEquals("Stratosphere", ((StringValue) record.getField(0)).getValue());
		assertEquals(1, ((IntValue) record.getField(1)).getValue());
		
		record.setField(1, new StringValue("Big Data"));
		assertEquals("Big Data", ((StringValue) record.getField(1)).getValue());
		
		record.setRecord(new IntValue(2), new StringValue("Big Data looks tiny from here."));
		assertEquals(2, record.getNumOfFields());
		assertEquals(1, record.getNumOfRecords());
		assertEquals(2, ((IntValue) record.getField(0)).getValue());
	}
	
	@Test
	public void batchRecordSetGetTest() {
		StreamRecord record = new StreamRecord(1, 2);
		record.addRecord(new StringValue("1"));
		record.addRecord(new IntValue(2));
		record.addRecord(new StringValue("three"));
		
		try {
			record.addRecord(new StringValue("4"), new IntValue(5));
			fail();
		} catch (RecordSizeMismatchException e) {
		}
			
		assertEquals(1, record.getNumOfFields());
		assertEquals(3, record.getNumOfRecords());
		assertEquals("1", ((StringValue) record.getField(0,0)).getValue());
		assertEquals(2, ((IntValue) record.getField(1,0)).getValue());
		assertEquals("three", ((StringValue) record.getField(2,0)).getValue());
		
		record.setRecord(1, new StringValue("2"));
		assertEquals("2", ((StringValue) record.getField(1,0)).getValue());
		
		record.addRecord(new StringValue("4"));
		assertEquals(1, record.getNumOfFields());
		assertEquals(4, record.getNumOfRecords());
	}
	
	@Test
	public void copyTest() {
		StreamRecord a = new StreamRecord(new StringValue("Big"));
		StreamRecord b = a.copy();
		assertTrue(((StringValue) a.getField(0)).getValue().equals(((StringValue) b.getField(0)).getValue()));
		b.setRecord(new StringValue("Data"));
		assertFalse(((StringValue) a.getField(0)).getValue().equals(((StringValue) b.getField(0)).getValue()));
	}
	
	@Test
	public void exceptionTest() {
		StreamRecord a = new StreamRecord(new StringValue("Big"));
		try {
			a.setRecord(4, new StringValue("Data"));
			fail();
		}
		catch (NoSuchRecordException e) {
		}
		
		try {
			a.setRecord(new StringValue("Data"), new StringValue("Stratosphere"));
			fail();
		}
		catch (RecordSizeMismatchException e) {
		}
		
		try {
			a.getField(3);
			fail();
		}
		catch (NoSuchFieldException e) {
		}
	}

}

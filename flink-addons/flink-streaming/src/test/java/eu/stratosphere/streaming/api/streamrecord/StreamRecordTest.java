/***********************************************************************************************************************
 *
 * Copyright (C) 2010-2014 by the Stratosphere project (http://stratosphere.eu)
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

package eu.stratosphere.streaming.api.streamrecord;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.junit.Test;

import eu.stratosphere.api.java.tuple.Tuple;
import eu.stratosphere.api.java.tuple.Tuple1;
import eu.stratosphere.api.java.tuple.Tuple2;
import eu.stratosphere.api.java.tuple.Tuple3;
import eu.stratosphere.api.java.tuple.Tuple5;
import eu.stratosphere.api.java.tuple.Tuple9;
import eu.stratosphere.api.java.typeutils.TupleTypeInfo;
import eu.stratosphere.api.java.typeutils.TypeExtractor;
import eu.stratosphere.api.java.typeutils.TypeInformation;
import eu.stratosphere.api.java.typeutils.runtime.TupleSerializer;
import eu.stratosphere.pact.runtime.plugable.DeserializationDelegate;
import eu.stratosphere.pact.runtime.plugable.SerializationDelegate;
import eu.stratosphere.types.StringValue;

public class StreamRecordTest {

	@Test
	public void singleRecordSetGetTest() {
		StreamRecord record = new StreamRecord(
				new Tuple9<String, Integer, Long, Boolean, Double, Byte, Character, Float, Short>(
						"Stratosphere", 1, 2L, true, 3.5, (byte) 0xa, 'a', 0.1f, (short) 42));

		assertEquals(9, record.getNumOfFields());
		assertEquals(1, record.getNumOfTuples());

		assertEquals("Stratosphere", record.getString(0));
		assertEquals((Integer) 1, record.getInteger(1));
		assertEquals((Long) 2L, record.getLong(2));
		assertEquals(true, record.getBoolean(3));
		assertEquals((Double) 3.5, record.getDouble(4));
		assertEquals((Byte) (byte) 0xa, record.getByte(5));
		assertEquals((Character) 'a', record.getCharacter(6));
		assertEquals((Float) 0.1f, record.getFloat(7));
		assertEquals((Short) (short) 42, record.getShort(8));

		Tuple9<String, Integer, Long, Boolean, Double, Byte, Character, Float, Short> tuple = new Tuple9<String, Integer, Long, Boolean, Double, Byte, Character, Float, Short>();

		record.getTupleInto(tuple);

		assertEquals("Stratosphere", tuple.getField(0));
		assertEquals((Integer) 1, tuple.getField(1));
		assertEquals((Long) 2L, tuple.getField(2));
		assertEquals(true, tuple.getField(3));
		assertEquals((Double) 3.5, tuple.getField(4));
		assertEquals((Byte) (byte) 0xa, tuple.getField(5));
		assertEquals((Character) 'a', tuple.getField(6));
		assertEquals((Float) 0.1f, tuple.getField(7));
		assertEquals((Short) (short) 42, tuple.getField(8));

		record.setString(0, "Streaming");
		record.setInteger(1, 2);
		record.setLong(2, 3L);
		record.setBoolean(3, false);
		record.setDouble(4, 4.5);
		record.setByte(5, (byte) 0xb);
		record.setCharacter(6, 'b');
		record.setFloat(7, 0.2f);
		record.setShort(8, (short) 69);

		assertEquals("Streaming", record.getString(0));
		assertEquals((Integer) 2, record.getInteger(1));
		assertEquals((Long) 3L, record.getLong(2));
		assertEquals(false, record.getBoolean(3));
		assertEquals((Double) 4.5, record.getDouble(4));
		assertEquals((Byte) (byte) 0xb, record.getByte(5));
		assertEquals((Character) 'b', record.getCharacter(6));
		assertEquals((Float) 0.2f, record.getFloat(7));
		assertEquals((Short) (short) 69, record.getShort(8));

		record.setString(0, 0, "");
		record.setInteger(0, 1, 0);
		record.setLong(0, 2, 0L);
		record.setBoolean(0, 3, false);
		record.setDouble(0, 4, 0.);
		record.setByte(0, 5, (byte) 0x0);
		record.setCharacter(0, 6, '\0');
		record.setFloat(0, 7, 0.f);
		record.setShort(0, 8, (short) 0);

		assertEquals("", record.getString(0, 0));
		assertEquals((Integer) 0, record.getInteger(0, 1));
		assertEquals((Long) 0L, record.getLong(0, 2));
		assertEquals(false, record.getBoolean(0, 3));
		assertEquals((Double) 0., record.getDouble(0, 4));
		assertEquals((Byte) (byte) 0x0, record.getByte(0, 5));
		assertEquals((Character) '\0', record.getCharacter(0, 6));
		assertEquals((Float) 0.f, record.getFloat(0, 7));
		assertEquals((Short) (short) 0, record.getShort(0, 8));

	}

	@Test
	public void batchRecordSetGetTest() {
		StreamRecord record = new StreamRecord(5, 2);

		Tuple5<String, Integer, Long, Boolean, Double> tuple = new Tuple5<String, Integer, Long, Boolean, Double>(
				"Stratosphere", 1, 2L, true, 3.5);

		record.addTuple(tuple);

		tuple.setField("", 0);
		tuple.setField(0, 1);
		tuple.setField(0L, 2);
		tuple.setField(false, 3);
		tuple.setField(0., 4);

		record.addTuple(tuple);
		try {
			record.addTuple(new Tuple1<String>("4"));
			fail();
		} catch (TupleSizeMismatchException e) {
		}

		assertEquals(5, record.getNumOfFields());
		assertEquals(2, record.getNumOfTuples());

		assertEquals("Stratosphere", record.getString(0, 0));
		assertEquals((Integer) 1, record.getInteger(0, 1));
		assertEquals((Long) 2L, record.getLong(0, 2));
		assertEquals(true, record.getBoolean(0, 3));
		assertEquals((Double) 3.5, record.getDouble(0, 4));

		assertEquals("", record.getString(1, 0));
		assertEquals((Integer) 0, record.getInteger(1, 1));
		assertEquals((Long) 0L, record.getLong(1, 2));
		assertEquals(false, record.getBoolean(1, 3));
		assertEquals((Double) 0., record.getDouble(1, 4));

		record.setTuple(new Tuple5<String, Integer, Long, Boolean, Double>("", 0, 0L, false, 0.));

		assertEquals(5, record.getNumOfFields());
		assertEquals(2, record.getNumOfTuples());

		assertEquals("", record.getString(0, 0));
		assertEquals((Integer) 0, record.getInteger(0, 1));
		assertEquals((Long) 0L, record.getLong(0, 2));
		assertEquals(false, record.getBoolean(0, 3));
		assertEquals((Double) 0., record.getDouble(0, 4));

		record.setTuple(1, new Tuple5<String, Integer, Long, Boolean, Double>("Stratosphere", 1,
				2L, true, 3.5));

		assertEquals("Stratosphere", record.getString(1, 0));
		assertEquals((Integer) 1, record.getInteger(1, 1));
		assertEquals((Long) 2L, record.getLong(1, 2));
		assertEquals(true, record.getBoolean(1, 3));
		assertEquals((Double) 3.5, record.getDouble(1, 4));

		record.removeTuple(1);

		assertEquals(1, record.getNumOfTuples());

		assertEquals("", record.getString(0, 0));
		assertEquals((Integer) 0, record.getInteger(0, 1));
		assertEquals((Long) 0L, record.getLong(0, 2));
		assertEquals(false, record.getBoolean(0, 3));
		assertEquals((Double) 0., record.getDouble(0, 4));

		record.addTuple(0, new Tuple5<String, Integer, Long, Boolean, Double>("Stratosphere", 1,
				2L, true, 3.5));

		assertEquals(2, record.getNumOfTuples());

		assertEquals("Stratosphere", record.getString(0, 0));
		assertEquals((Integer) 1, record.getInteger(0, 1));
		assertEquals((Long) 2L, record.getLong(0, 2));
		assertEquals(true, record.getBoolean(0, 3));
		assertEquals((Double) 3.5, record.getDouble(0, 4));

	}

	@Test
	public void copyTest() {
		StreamRecord a = new StreamRecord(new Tuple1<String>("Big"));
		a.setId(0);
		StreamRecord b = a.copy();
		assertTrue(a.getField(0).equals(b.getField(0)));
		assertTrue(a.getId().equals(b.getId()));
		b.setId(2);
		b.setTuple(new Tuple1<String>("Data"));
		assertFalse(a.getId().equals(b.getId()));
		assertFalse(a.getField(0).equals(b.getField(0)));
		final int ITERATION = 10000;

		StreamRecord c = new StreamRecord(new Tuple1<String>("Big"));

		long t = System.nanoTime();
		for (int i = 0; i < ITERATION; i++) {
			c.copySerialized();
		}
		long t2 = System.nanoTime() - t;
		System.out.println("Serialized copy:\t" + t2 + " ns");

		t = System.nanoTime();
		for (int i = 0; i < ITERATION; i++) {
			c.copy();
		}
		t2 = System.nanoTime() - t;
		System.out.println("Copy:\t" + t2 + " ns");

	}

	// @Test
	// public void getFieldSpeedTest() {
	//
	// final int ITERATION = 10000;
	//
	// StreamRecord record = new StreamRecord(new Tuple4<Integer, Long, String,
	// String>(0, 42L, "Stratosphere",
	// "Streaming"));
	//
	// long t = System.nanoTime();
	// for (int i = 0; i < ITERATION; i++) {
	// record.getField(0, i % 4);
	// }
	// long t2 = System.nanoTime() - t;
	// System.out.println("Tuple5");
	// System.out.println("getField:\t" + t2 + " ns");
	//
	// t = System.nanoTime();
	// for (int i = 0; i < ITERATION; i++) {
	// record.getFieldFast(0, i % 4);
	// }
	// t2 = System.nanoTime() - t;
	// System.out.println("getFieldFast:\t" + t2 + " ns");
	//
	// StreamRecord record20 = new StreamRecord(
	// new Tuple20<Integer, Long, String, String, String, String, String,
	// String, String, String, String, String, String, String, String, String,
	// String, String, String, String>(
	// 0, 42L, "Stratosphere", "Streaming", "Stratosphere", "Stratosphere",
	// "Streaming",
	// "Stratosphere", "Streaming", "Streaming", "Stratosphere", "Streaming",
	// "Stratosphere",
	// "Streaming", "Streaming", "Stratosphere", "Streaming", "Stratosphere",
	// "Streaming", "Streaming"));
	//
	// t = System.nanoTime();
	// for (int i = 0; i < ITERATION; i++) {
	// record20.getField(0, i % 20);
	// }
	// t2 = System.nanoTime() - t;
	// System.out.println("Tuple20");
	// System.out.println("getField:\t" + t2 + " ns");
	//
	// t = System.nanoTime();
	// for (int i = 0; i < ITERATION; i++) {
	// record20.getFieldFast(0, i % 20);
	// }
	// t2 = System.nanoTime() - t;
	// System.out.println("getFieldFast:\t" + t2 + " ns");
	//
	// }

	@Test
	public void exceptionTest() {
		StreamRecord a = new StreamRecord(new Tuple1<String>("Big"));
		try {
			a.setTuple(4, new Tuple1<String>("Data"));
			fail();
		} catch (NoSuchTupleException e) {
		}

		try {
			a.setTuple(new Tuple2<String, String>("Data", "Stratosphere"));
			fail();
		} catch (TupleSizeMismatchException e) {
		}

		StreamRecord b = new StreamRecord();
		try {
			b.addTuple(new Tuple2<String, String>("Data", "Stratosphere"));
			fail();
		} catch (TupleSizeMismatchException e) {
		}

		try {
			a.getField(3);
			fail();
		} catch (NoSuchFieldException e) {
		}
	}

	@Test
	public void writeReadTest() {
		ByteArrayOutputStream buff = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(buff);

		int num = 42;
		String str = "above clouds";
		Integer[] intArray = new Integer[] { 1, 2 };
		StreamRecord rec = new StreamRecord(new Tuple3<Integer, String, Integer[]>(num, str,
				intArray));

		try {
			rec.write(out);
			DataInputStream in = new DataInputStream(new ByteArrayInputStream(buff.toByteArray()));

			StreamRecord newRec = new StreamRecord();
			newRec.read(in);
			@SuppressWarnings("unchecked")
			Tuple3<Integer, String, Integer[]> tupleOut = (Tuple3<Integer, String, Integer[]>) newRec
					.getTuple(0);

			assertEquals(tupleOut.getField(0), 42);
			assertEquals(str, tupleOut.getField(1));
			assertArrayEquals(intArray, (Integer[]) tupleOut.getField(2));
		} catch (IOException e) {
			fail();
			e.printStackTrace();
		}

	}

	@Test
	public void tupleCopyTest() {
		Tuple2<String, Integer> t1 = new Tuple2<String, Integer>("a", 1);

		@SuppressWarnings("rawtypes")
		Tuple2 t2 = (Tuple2) StreamRecord.copyTuple(t1);

		assertEquals("a", t2.getField(0));
		assertEquals(1, t2.getField(1));

		t1.setField(2, 1);
		assertEquals(1, t2.getField(1));
		assertEquals(2, t1.getField(1));

		assertEquals(t1.getField(0).getClass(), t2.getField(0).getClass());
		assertEquals(t1.getField(1).getClass(), t2.getField(1).getClass());

	}

	//TODO:measure performance of different serialization logics
	@Test
	public void typeCopyTest() throws NoSuchTupleException, IOException {
		StreamRecord rec = new StreamRecord(
				new Tuple9<Boolean, Byte, Character, Double, Float, Integer, Long, Short, String>(
						(Boolean) true, (Byte) (byte) 12, (Character) 'a', (Double) 12.5,
						(Float) (float) 13.5, (Integer) 1234, (Long) 12345678900l,
						(Short) (short) 12345, "something"));
		@SuppressWarnings({ "rawtypes", "unused" })
		Class[] types = new Class[9];
		assertArrayEquals(new Class[] { Boolean.class, Byte.class, Character.class, Double.class,
				Float.class, Integer.class, Long.class, Short.class, String.class },
				rec.tupleBasicTypesFromByteArray(rec.tupleBasicTypesToByteArray(rec.getTuple()), 9));

		ByteArrayOutputStream buff3 = new ByteArrayOutputStream();
		DataOutputStream out3 = new DataOutputStream(buff3);
		Long start = System.nanoTime();
		for (int i = 0; i < 1000; i++) {
			out3.write(rec.tupleBasicTypesToByteArray(rec.getTuple()));
		}
		DataInputStream in3 = new DataInputStream(new ByteArrayInputStream(buff3.toByteArray()));
		for (int i = 0; i < 1000; i++) {
			byte[] byteTypes = new byte[9];
			in3.read(byteTypes);
			String types2 = StreamRecord.typeStringFromByteArray(byteTypes, 9);
		}
		System.out.println("Type copy with ByteArray:\t" + (System.nanoTime() - start) + " ns");
	}
	
}

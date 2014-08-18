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

package eu.stratosphere.streaming.test.cellinfo;

import java.util.Random;

import eu.stratosphere.streaming.api.invokable.UserSourceInvokable;
import eu.stratosphere.streaming.api.streamrecord.StreamRecord;
import eu.stratosphere.types.IntValue;
import eu.stratosphere.types.LongValue;

public class QuerySourceInvokable extends UserSourceInvokable {

	Random _rand = new Random();
	int _cellNumber = 10;

	private IntValue cellId = new IntValue(5);
	private LongValue timeStamp = new LongValue(500);
	private IntValue lastMillis = new IntValue(100);
	private StreamRecord record = new StreamRecord(cellId, timeStamp, lastMillis);

	@Override
	public void invoke() throws Exception {
		for (int i = 0; i < 10000; i++) {
			Thread.sleep(1);
			cellId.setValue(_rand.nextInt(_cellNumber));
			timeStamp.setValue(System.currentTimeMillis());

			record.setRecord(cellId, timeStamp, lastMillis);

			emit(record);
		}
	}

}

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

package eu.stratosphere.streaming.examples.wordcount;

import eu.stratosphere.streaming.api.invokable.UserTaskInvokable;
import eu.stratosphere.streaming.api.streamrecord.StreamRecord;
import eu.stratosphere.types.StringValue;

public class WordCountSplitter extends UserTaskInvokable {

	private StringValue sentence = new StringValue();
	private String[] words = new String[] {};
	private StringValue wordValue = new StringValue("");
	private int i = 0;
	private StreamRecord outputRecord = new StreamRecord(wordValue);
	private long time;
	private long prevTime = System.currentTimeMillis();

	@Override
	public void invoke(StreamRecord record) throws Exception {
		i++;
		if (i % 50000 == 0) {
			time = System.currentTimeMillis();
			System.out.println("Splitter:\t" + i + "\t----Time: " + (time - prevTime));
			prevTime = time;
		}
		sentence = (StringValue) record.getRecord(0)[0];
		words = sentence.getValue().split(" ");
		for (CharSequence word : words) {
			wordValue.setValue(word);
			outputRecord.setRecord(wordValue);
			emit(outputRecord);
			// emit(new StreamRecord(wordValue));
		}
	}
}
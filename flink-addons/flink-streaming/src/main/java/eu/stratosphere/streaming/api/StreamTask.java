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

package eu.stratosphere.streaming.api;

import java.util.LinkedList;
import java.util.List;

import eu.stratosphere.nephele.io.ChannelSelector;
import eu.stratosphere.nephele.io.RecordReader;
import eu.stratosphere.nephele.io.RecordWriter;
import eu.stratosphere.nephele.template.AbstractTask;
import eu.stratosphere.streaming.api.invokable.DefaultTaskInvokable;
import eu.stratosphere.streaming.api.invokable.UserTaskInvokable;
import eu.stratosphere.streaming.partitioner.DefaultPartitioner;
import eu.stratosphere.types.Record;

public class StreamTask extends AbstractTask {

  private List<RecordReader<Record>> inputs;
  private List<RecordWriter<Record>> outputs;
  private List<ChannelSelector<Record>> partitioners;
  private UserTaskInvokable userFunction;

  private int numberOfInputs;
  private int numberOfOutputs;

  public StreamTask() {
    // TODO: Make configuration file visible and call setClassInputs() here
    inputs = new LinkedList<RecordReader<Record>>();
    outputs = new LinkedList<RecordWriter<Record>>();
    partitioners = new LinkedList<ChannelSelector<Record>>();
    userFunction = null;
    numberOfInputs = 0;
    numberOfOutputs = 0;
  }

  private void setConfigInputs() {

    numberOfInputs = getTaskConfiguration().getInteger("numberOfInputs", 0);
    for (int i = 0; i < numberOfInputs; i++) {
      inputs.add(new RecordReader<Record>(this, Record.class));
    }

    numberOfOutputs = getTaskConfiguration().getInteger("numberOfOutputs", 0);
    Class<? extends ChannelSelector<Record>> partitioner;

    for (int i = 1; i <= numberOfOutputs; i++) {
      partitioner = getTaskConfiguration().getClass("partitioner_" + i,
          DefaultPartitioner.class, ChannelSelector.class);
      try {
        partitioners.add(partitioner.newInstance());
      } catch (Exception e) {

      }
    }

    Class<? extends UserTaskInvokable> userFunctionClass;
    userFunctionClass = getTaskConfiguration().getClass("userfunction",
        DefaultTaskInvokable.class, UserTaskInvokable.class);
    try {
      userFunction = userFunctionClass.newInstance();
    } catch (Exception e) {

    }
  }

  @Override
  public void registerInputOutput() {
    setConfigInputs();
    for (ChannelSelector<Record> partitioner : partitioners) {
      outputs.add(new RecordWriter<Record>(this, Record.class, partitioner));
    }
  }

  // TODO: Performance with multiple outputs
  @Override
  public void invoke() throws Exception {
    boolean hasInput = true;
    while (hasInput) {
      hasInput = false;
      for (RecordReader<Record> input : inputs) {
        if (input.hasNext()) {
          hasInput = true;
          for (RecordWriter<Record> output : outputs) {
            userFunction.invoke(input.next(), output);
          }
        }
      }
    }
  }

}

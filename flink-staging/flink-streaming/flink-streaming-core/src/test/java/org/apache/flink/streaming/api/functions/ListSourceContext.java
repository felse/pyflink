/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.streaming.api.functions;

import org.apache.flink.streaming.api.functions.source.SourceFunction;

import java.util.List;

/**
 * Mock context that collects elements in a List.
 * 
 * @param <T> Type of the collected elements.
 */
public class ListSourceContext<T> implements SourceFunction.SourceContext<T> {
	
	private final Object lock = new Object();
	
	private final List<T> target;

	
	public ListSourceContext(List<T> target) {
		this.target = target;
	}

	@Override
	public void collect(T element) {
		target.add(element);
	}

	@Override
	public Object getCheckpointLock() {
		return lock;
	}
}

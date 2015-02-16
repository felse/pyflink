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

package org.apache.flink.api.java.typeutils;

import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.typeinfo.AtomicType;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeutils.TypeComparator;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.api.java.typeutils.runtime.GenericTypeComparator;
import org.apache.flink.api.java.typeutils.runtime.KryoSerializer;

public class GenericTypeInfo<T> extends TypeInformation<T> implements AtomicType<T> {

	private static final long serialVersionUID = -7959114120287706504L;
	
	private final Class<T> typeClass;

	public GenericTypeInfo(Class<T> typeClass) {
		this.typeClass = typeClass;
	}

	@Override
	public boolean isBasicType() {
		return false;
	}

	@Override
	public boolean isTupleType() {
		return false;
	}

	@Override
	public int getArity() {
		return 1;
	}
	
	@Override
	public int getTotalFields() {
		return 1;
	}

	@Override
	public Class<T> getTypeClass() {
		return typeClass;
	}
	
	@Override
	public boolean isKeyType() {
		return Comparable.class.isAssignableFrom(typeClass);
	}

	@Override
	public TypeSerializer<T> createSerializer(ExecutionConfig config) {
		return new KryoSerializer<T>(this.typeClass, config);
	}

	@SuppressWarnings("unchecked")
	@Override
	public TypeComparator<T> createComparator(boolean sortOrderAscending, ExecutionConfig executionConfig) {
		if (isKeyType()) {
			@SuppressWarnings("rawtypes")
			GenericTypeComparator comparator = new GenericTypeComparator(sortOrderAscending, createSerializer(executionConfig), this.typeClass);
			return (TypeComparator<T>) comparator;
		}

		throw new UnsupportedOperationException("Types that do not implement java.lang.Comparable cannot be used as keys.");
	}

	// --------------------------------------------------------------------------------------------

	@Override
	public int hashCode() {
		return typeClass.hashCode() ^ 0x165667b1;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj.getClass() == GenericTypeInfo.class) {
			return typeClass == ((GenericTypeInfo<?>) obj).typeClass;
		} else {
			return false;
		}
	}
	
	@Override
	public String toString() {
		return "GenericType<" + typeClass.getCanonicalName() + ">";
	}
}

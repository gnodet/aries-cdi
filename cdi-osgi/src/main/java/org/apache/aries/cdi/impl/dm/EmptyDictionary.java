/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.aries.cdi.impl.dm;

import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;

public class EmptyDictionary<K, V> extends Dictionary<K, V> {

    private static EmptyDictionary INSTANCE = new EmptyDictionary();

    @SuppressWarnings("unchecked")
    public static <K, V> Dictionary<K, V> emptyDictionary() {
        return (Dictionary) INSTANCE;
    }

    EmptyDictionary() {}

    @Override
    public int size() {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public Enumeration<K> keys() {
        return Collections.emptyEnumeration();
    }

    @Override
    public Enumeration<V> elements() {
        return Collections.emptyEnumeration();
    }

    @Override
    public V get(Object key) {
        return null;
    }

    @Override
    public V put(K key, V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V remove(Object key) {
        throw new UnsupportedOperationException();
    }
}

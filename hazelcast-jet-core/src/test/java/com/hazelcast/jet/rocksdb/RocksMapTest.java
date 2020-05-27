/*
 * Copyright (c) 2008-2020, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.jet.rocksdb;

import com.hazelcast.internal.serialization.InternalSerializationService;
import com.hazelcast.jet.JetException;
import com.hazelcast.jet.core.JetTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import static java.util.Collections.emptyMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class RocksMapTest extends JetTestSupport {
    private RocksDBStateBackend rocksDBStateBackend;
    private InternalSerializationService serializationService;
    private RocksMap<String, Integer> rocksMap;

    @Before
    public void init() {
        serializationService = getJetService(createJetMember())
                .createSerializationService(emptyMap());
        rocksDBStateBackend = new RocksDBStateBackend(serializationService);
        rocksMap = rocksDBStateBackend.getMap();
    }

    @After
    public void cleanup() {
        rocksDBStateBackend.deleteKeyValueStore();
        serializationService.dispose();
    }

    @Test
    public void when_putKeyValue_then_getKeyReturnsValue() {
        //Given
        String key = "key1";
        Integer value = 1;

        //When
        rocksMap.put(key, value);

        //Then
        assertEquals("rocksMap.get() doesn't return the value used in rocksMap.put()", value, rocksMap.get(key));
    }

    @Test
    public void when_updateKeyValue_then_getKeyReturnsNewValue() {
        //Given
        String key = "key1";
        Integer value1 = 1;
        Integer value2 = 2;

        //When
        rocksMap.put(key, value1);
        rocksMap.put(key, value2);

        //Then
        assertEquals("rocksMap.get() doesn't return the updated value used in rocksMap.put()", value2, rocksMap.get(key));
    }

    @Test
    public void when_putAllHashMap_then_getKeyReturnsValue() {
        //Given
        String key1 = "key1";
        String key2 = "key2";
        Integer value1 = 1;
        Integer value2 = 2;
        Map<String, Integer> map = new HashMap<>();
        map.put(key1, value1);
        map.put(key2, value2);

        //When
        rocksMap.putAll(map);

        //Then
        assertEquals("rocksMap.get() doesn't return the value used in rocksMap.put()", value1, rocksMap.get(key1));
        assertEquals("rocksMap.get() doesn't return the value used in rocksMap.put()", value2, rocksMap.get(key2));
    }

    @Test
    public void when_putKeyValues_then_getAllReturnsAllValues() {
        //Given
        String key1 = "key1";
        String key2 = "key2";
        Integer value1 = 1;
        Integer value2 = 2;

        //When
        rocksMap.put(key1, value1);
        rocksMap.put(key2, value2);
        Map<String, Integer> map = new HashMap<>(rocksMap.getAll());

        assertEquals("rocksMap.getAll() doesn't return the value used in rocksMap.putAll()", value1, map.get(key1));
        assertEquals("rocksMap.getAll() doesn't return the value used in rocksMap.putAll()", value2, map.get(key2));
    }

    //even after the column family is dropped you can still use it to get its contents
    //but you can't modify it
    @Test
    public void when_releaseRocksMap_then_putKeyValueThrowsException() {
        //Given
        String key = "key1";
        Integer value1 = 1;
        Integer value2 = 2;

        //When
        rocksMap.put(key, value1);
        rocksDBStateBackend.releaseMap(rocksMap);

        //Then
        rocksMap.get(key);
        assertThrows(JetException.class, () -> rocksMap.put(key, value2));
    }

    @Test
    public void whenCreateIterator_then_rocksMapCreatesSnapshot() {
        //Given
        String key1 = "key1";
        String key2 = "key2";
        Integer value1 = 1;
        Integer value2 = 2;
        Integer value3 = 3;
        Map<String, Integer> map = new HashMap<>();
        Entry<String, Integer> e;

        //When
        rocksMap.put(key1, value1);
        rocksMap.put(key2, value2);
        Iterator<Entry<String, Integer>> iterator = rocksMap.iterator();
        rocksMap.put(key1, value3);

        //Then
        assertTrue(iterator.hasNext());
        e = iterator.next();
        map.put(e.getKey(), e.getValue());
        assertTrue((iterator.hasNext()));
        e = iterator.next();
        map.put(e.getKey(), e.getValue());
        assertFalse(iterator.hasNext());
        assertNotEquals("iterator.next() returns the new value used in rocksMap.put()", value3, map.get(key1));
        assertEquals("iterator.next() doesn't return the value used in rocksMap.put()", value1, map.get(key1));
        assertEquals("iterator.next() doesn't return the value used in rocksMap.put()", value1, map.get(key1));
    }
}
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

package com.hazelcast.jet.impl.processor;

import com.hazelcast.function.FunctionEx;
import com.hazelcast.jet.Traverser;
import com.hazelcast.jet.aggregate.AggregateOperation;
import com.hazelcast.jet.aggregate.AggregateOperation1;
import com.hazelcast.jet.core.AbstractProcessor;
import com.hazelcast.jet.datamodel.Tuple2;
import com.hazelcast.jet.rocksdb.PrefixRocksMap;
import com.hazelcast.jet.rocksdb.PrefixRocksMap.Cursor;

import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.hazelcast.internal.util.Preconditions.checkTrue;
import static com.hazelcast.jet.datamodel.Tuple2.tuple2;
import static java.util.Collections.singletonList;

/**
 * A Variant of {@link GroupP} that uses RocksDB state backend to store state instead of in-memory maps.
 * The difference between this processor and {@link GroupWithPersistenceAndUnboundedStateP} is that
 * the state that this processor holds per key is unbounded.
 * Each key is associated with a list of values that can grow very large.
 */
public class GroupWithPersistenceAndUnboundedStateP<K, A, R, OUT> extends AbstractProcessor {
    @Nonnull private final List<FunctionEx<?, ? extends K>> groupKeyFns;
    @Nonnull private final AggregateOperation<A, R> aggrOp;
    private final BiFunction<? super K, ? super R, OUT> mapToOutputFn;
    private Traverser<OUT> resultTraverser;
    private PrefixRocksMap<K, Entry<Integer, Object>> keyToOrdinalAndAcc;

    public GroupWithPersistenceAndUnboundedStateP(
            @Nonnull List<FunctionEx<?, ? extends K>> groupKeyFns,
            @Nonnull AggregateOperation<A, R> aggrOp,
            @Nonnull BiFunction<? super K, ? super R, OUT> mapToOutputFn
    ) {
        checkTrue(groupKeyFns.size() == aggrOp.arity(), groupKeyFns.size() + " key functions " +
                "provided for " + aggrOp.arity() + "-arity aggregate operation");
        this.groupKeyFns = groupKeyFns;
        this.aggrOp = aggrOp;
        this.mapToOutputFn = mapToOutputFn;
    }

    public <T> GroupWithPersistenceAndUnboundedStateP(
            @Nonnull FunctionEx<? super T, ? extends K> groupKeyFn,
            @Nonnull AggregateOperation1<? super T, A, R> aggrOp,
            @Nonnull BiFunction<? super K, ? super R, OUT> mapToOutputFn
    ) {
        this(singletonList(groupKeyFn), aggrOp, mapToOutputFn);
    }

    @Override
    protected void init(@Nonnull Context context) throws Exception {
        keyToOrdinalAndAcc = context.prefixStateBackend().getPrefixMap();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected boolean tryProcess(int ordinal, @Nonnull Object item) {
        Function<Object, ? extends K> keyFn = (Function<Object, ? extends K>) groupKeyFns.get(ordinal);
        K key = keyFn.apply(item);
        return keyToOrdinalAndAcc.add(key, tuple2(ordinal, item));
    }

    @Override
    public boolean complete() {
        if (resultTraverser == null) {
            keyToOrdinalAndAcc.compact();
            resultTraverser = new ResultTraverser()
                    // reuse null filtering done by map()
                    .map(e -> mapToOutputFn.apply(e.getKey(), aggrOp.finishFn().apply(e.getValue())));
        }
        return emitFromTraverser(resultTraverser);
    }

    private class ResultTraverser implements Traverser<Entry<K, A>> {
        private final Cursor cursor = keyToOrdinalAndAcc.cursor();

        @Override
        public Entry<K, A> next() {
            if (!cursor.advance()) {
                cursor.close();
                return null;
            }
            Entry<K, Iterator<Entry<Integer, Object>>> e = cursor.getValues();
            K key = e.getKey();
            A acc = aggrOp.createFn().get();
            Iterator<Entry<Integer, Object>> values = e.getValue();
            while (values.hasNext()) {
                Entry<Integer, Object> ordinalAndAcc = values.next();
                aggrOp.accumulateFn(ordinalAndAcc.getKey()).accept(acc, ordinalAndAcc.getValue());
            }
            return tuple2(key, acc);
        }
    }
}


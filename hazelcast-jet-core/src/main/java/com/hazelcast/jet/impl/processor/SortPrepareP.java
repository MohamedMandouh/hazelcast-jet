package com.hazelcast.jet.impl.processor;

import com.hazelcast.function.FunctionEx;
import com.hazelcast.jet.Traverser;
import com.hazelcast.jet.core.AbstractProcessor;

import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public class SortPrepareP<V> extends AbstractProcessor {
    private TreeMap<Long, V> map = new TreeMap<>();
    private final FunctionEx<? super V, ? extends Long> keyFn;
    private ResultTraverser resultTraverser;


    public SortPrepareP(FunctionEx<? super V, ? extends Long> keyFn) {
        this.keyFn = keyFn;
    }

    protected boolean tryProcess0(@Nonnull Object item) {
        Long key = keyFn.apply((V) item);
        map.put(key, (V) item);
        return true;
    }

    @Override
    public boolean complete() {
        if (resultTraverser == null) {
            resultTraverser = new ResultTraverser();
        }
        return emitFromTraverser(resultTraverser);
    }

    private class ResultTraverser implements Traverser<V> {
        private Iterator<Map.Entry<Long, V>> iterator = map.entrySet().iterator();

        @Override
        public V next() {
            if(!iterator.hasNext()) {
                return null;
            }
            try {
                return iterator.next().getValue();
            } finally {
                iterator.remove();
            }
        }
    }

    }

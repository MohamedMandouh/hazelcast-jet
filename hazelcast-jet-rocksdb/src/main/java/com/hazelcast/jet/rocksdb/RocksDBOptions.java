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

import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.BloomFilter;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.LRUCache;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.WriteOptions;

import javax.annotation.Nonnull;
import java.io.Serializable;

/**
 * General RocksDB Configurations.
 * see {@link RocksMap}, {@link RocksDBStateBackend}
 */
public class RocksDBOptions implements Serializable {

    private static final int MEMTABLE_SIZE = 64 * 1024 * 1024;
    private static final int MEMTABLE_NUMBER = 2;
    private static final int BLOOM_FILTER_BITS = 10;
    private static final int CACHE_SIZE = 128 * 1024 * 1024;

    private int memtableSize;
    private int memtableNumber;
    private int bloomFilterBits;
    private int cacheSize;

    static {
        RocksDB.loadLibrary();
    }

    /**
     * Creates a new RocksDBOptions instance with default options.
     */
    public RocksDBOptions() {
        memtableSize = MEMTABLE_SIZE;
        memtableNumber = MEMTABLE_NUMBER;
        bloomFilterBits = BLOOM_FILTER_BITS;
        cacheSize = CACHE_SIZE;
    }

    RocksDBOptions(@Nonnull RocksDBOptions options) {
        memtableSize = options.memtableSize;
        memtableNumber = options.memtableNumber;
        bloomFilterBits = options.bloomFilterBits;
        cacheSize = options.cacheSize;
    }

    /**
     * Sets RocksDB options using the {@link RocksDBOptionsBuilder} instance the user provided through
     * {@code JobConfig.setRocksDBOptions()}
     */
    public RocksDBOptions setOptions(@Nonnull RocksDBOptionsBuilder options) {
        if (options.memtableSize != null) {
            memtableSize = options.memtableSize;
        }
        if (options.memtableNumber != null) {
            memtableNumber = options.memtableNumber;
        }
        if (options.bloomFilterBits != null) {
            bloomFilterBits = options.bloomFilterBits;
        }
        if (options.cacheSize != null) {
            cacheSize = options.cacheSize;
        }
        return this;
    }

    Options options() {
        return new Options().setCreateIfMissing(true);
    }

    ColumnFamilyOptions columnFamilyOptions() {
        return new ColumnFamilyOptions()
                .setWriteBufferSize(memtableSize)
                .setMaxWriteBufferNumber(memtableNumber)
                .setTableFormatConfig(new BlockBasedTableConfig()
                        .setBlockCache(new LRUCache(cacheSize))
                        .setPinL0FilterAndIndexBlocksInCache(true)
                        .setFilter(new BloomFilter(bloomFilterBits, false)));
    }

    WriteOptions writeOptions() {
        return new WriteOptions().setDisableWAL(true);
    }

    ReadOptions readOptions() {
        return new ReadOptions();
    }
}
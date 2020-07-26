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

package com.hazelcast.jet.impl.pipeline.transform;

import com.hazelcast.function.SupplierEx;
import com.hazelcast.jet.aggregate.AggregateOperation;
import com.hazelcast.jet.core.Processor;
import com.hazelcast.jet.core.Vertex;
import com.hazelcast.jet.impl.pipeline.Planner;
import com.hazelcast.jet.impl.pipeline.Planner.PlannerVertex;

import javax.annotation.Nonnull;
import java.util.List;

import static com.hazelcast.jet.core.Edge.between;
import static com.hazelcast.jet.core.processor.Processors.accumulateP;
import static com.hazelcast.jet.core.processor.Processors.accumulateWithPersistenceAndUnboundedStateP;
import static com.hazelcast.jet.core.processor.Processors.accumulateWithPersistenceP;
import static com.hazelcast.jet.core.processor.Processors.aggregateP;
import static com.hazelcast.jet.core.processor.Processors.aggregateWithPersistenceAndUnboundedStateP;
import static com.hazelcast.jet.core.processor.Processors.aggregateWithPersistenceP;
import static com.hazelcast.jet.core.processor.Processors.combineP;
import static com.hazelcast.jet.core.processor.Processors.combineWithPersistenceAndUnboundedStateP;
import static com.hazelcast.jet.core.processor.Processors.combineWithPersistenceP;

public class AggregateTransform<A, R> extends AbstractTransform {
    public static final String FIRST_STAGE_VERTEX_NAME_SUFFIX = "-prepare";

    @Nonnull
    private final AggregateOperation<A, ? extends R> aggrOp;
    private final boolean usePersistence;

    public AggregateTransform(
            @Nonnull List<Transform> upstream,
            @Nonnull AggregateOperation<A, ? extends R> aggrOp,
            boolean usePersistence
    ) {
        super(createName(upstream), upstream);
        this.aggrOp = aggrOp;
        this.usePersistence = usePersistence;
    }

    private static String createName(@Nonnull List<Transform> upstream) {
        return upstream.size() == 1
                ? "aggregate"
                : upstream.size() + "-way co-aggregate";
    }

    @Override
    public void addToDag(Planner p) {
        if (aggrOp.combineFn() == null) {
            addToDagSingleStage(p);
        } else {
            addToDagTwoStage(p);
        }
    }

    //               ---------       ---------
    //              | source0 | ... | sourceN |
    //               ---------       ---------
    //                   |              |
    //              distributed    distributed
    //              all-to-one      all-to-one
    //                   \              /
    //                    ---\    /-----
    //                        v  v
    //                   ----------------
    //                  |   aggregateP   | local parallelism = 1
    //                   ----------------
    private void addToDagSingleStage(Planner p) {
        SupplierEx<Processor> agg;

        if (usePersistence && aggrOp.hasUnboundedState()) {
            agg = aggregateWithPersistenceAndUnboundedStateP(aggrOp);
        } else if (usePersistence) {
            agg = aggregateWithPersistenceP(aggrOp);
        } else {
            agg = aggregateP(aggrOp);
        }
        PlannerVertex pv = p.addVertex(this, name(), 1, agg);
        p.addEdges(this, pv.v, edge -> edge.distributed().allToOne(name().hashCode()));
    }

    //               ---------       ---------
    //              | source0 | ... | sourceN |
    //               ---------       ---------
    //                   |              |
    //                 local          local
    //                unicast        unicast
    //                   v              v
    //                  -------------------
    //                 |    accumulateP    |
    //                  -------------------
    //                           |
    //                      distributed
    //                       all-to-one
    //                           v
    //                   ----------------
    //                  |    combineP    | local parallelism = 1
    //                   ----------------
    private void addToDagTwoStage(Planner p) {
        String vertexName = name();
        SupplierEx<Processor> acc;
        SupplierEx<Processor> comb;
        if (usePersistence && aggrOp.hasUnboundedState()) {
            acc = accumulateWithPersistenceAndUnboundedStateP(aggrOp);
            comb = combineWithPersistenceAndUnboundedStateP(aggrOp);
        } else if (usePersistence) {
            acc = accumulateWithPersistenceP(aggrOp);
            comb = combineWithPersistenceP(aggrOp);
        } else {
            acc = accumulateP(aggrOp);
            comb = combineP(aggrOp);
        }
        Vertex v1 = p.dag.newVertex(vertexName + FIRST_STAGE_VERTEX_NAME_SUFFIX, acc)
                         .localParallelism(localParallelism());
        PlannerVertex pv2 = p.addVertex(this, vertexName, 1, comb);
        p.addEdges(this, v1);
        p.dag.edge(between(v1, pv2.v).distributed().allToOne(name().hashCode()));
    }
}

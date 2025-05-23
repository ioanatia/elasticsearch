/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.compute.aggregation;

$if(BytesRef)$
import org.apache.lucene.util.BytesRef;
$endif$
import org.apache.lucene.util.RamUsageEstimator;
import org.elasticsearch.common.util.BigArrays;
$if(BytesRef)$
import org.elasticsearch.common.util.BytesRefHash;
$else$
import org.elasticsearch.common.util.LongHash;
$endif$
$if(long||double||BytesRef)$
import org.elasticsearch.common.util.LongLongHash;
$endif$
$if(BytesRef)$
import org.elasticsearch.compute.aggregation.blockhash.BlockHash;
$endif$
import org.elasticsearch.compute.ann.Aggregator;
import org.elasticsearch.compute.ann.GroupingAggregator;
import org.elasticsearch.compute.ann.IntermediateState;
import org.elasticsearch.compute.data.Block;
import org.elasticsearch.compute.data.BlockFactory;
$if(int||double||float)$
import org.elasticsearch.compute.data.$Type$Block;
$elseif(BytesRef)$
import org.elasticsearch.compute.data.BytesRefBlock;
import org.elasticsearch.compute.data.BytesRefVector;
import org.elasticsearch.compute.data.IntBlock;
$endif$
import org.elasticsearch.compute.data.IntVector;
$if(long)$
import org.elasticsearch.compute.data.LongBlock;
$endif$
$if(BytesRef)$
import org.elasticsearch.compute.data.OrdinalBytesRefBlock;
$endif$
import org.elasticsearch.compute.operator.DriverContext;
$if(BytesRef)$
import org.elasticsearch.core.Releasables;

$else$

$endif$
/**
 * Aggregates field values for $type$.
 * This class is generated. Edit @{code X-ValuesAggregator.java.st} instead
 * of this file.
 */
@Aggregator({ @IntermediateState(name = "values", type = "$TYPE$_BLOCK") })
@GroupingAggregator
class Values$Type$Aggregator {
    public static SingleState initSingle(BigArrays bigArrays) {
        return new SingleState(bigArrays);
    }

    public static void combine(SingleState state, $type$ v) {
$if(float)$
        state.values.add(Float.floatToIntBits(v));
$elseif(double)$
        state.values.add(Double.doubleToLongBits(v));
$else$
        state.values.add(v);
$endif$
    }

    public static void combineIntermediate(SingleState state, $Type$Block values) {
$if(BytesRef)$
        BytesRef scratch = new BytesRef();
$endif$
        int start = values.getFirstValueIndex(0);
        int end = start + values.getValueCount(0);
        for (int i = start; i < end; i++) {
$if(BytesRef)$
            combine(state, values.getBytesRef(i, scratch));
$else$
            combine(state, values.get$Type$(i));
$endif$
        }
    }

    public static Block evaluateFinal(SingleState state, DriverContext driverContext) {
        return state.toBlock(driverContext.blockFactory());
    }

    public static GroupingState initGrouping(BigArrays bigArrays) {
        return new GroupingState(bigArrays);
    }

$if(BytesRef)$
    public static GroupingAggregatorFunction.AddInput wrapAddInput(
        GroupingAggregatorFunction.AddInput delegate,
        GroupingState state,
        BytesRefBlock values
    ) {
        return ValuesBytesRefAggregators.wrapAddInput(delegate, state, values);
    }

    public static GroupingAggregatorFunction.AddInput wrapAddInput(
        GroupingAggregatorFunction.AddInput delegate,
        GroupingState state,
        BytesRefVector values
    ) {
        return ValuesBytesRefAggregators.wrapAddInput(delegate, state, values);
    }
$endif$

    public static void combine(GroupingState state, int groupId, $type$ v) {
$if(long)$
        state.values.add(groupId, v);
$elseif(double)$
        state.values.add(groupId, Double.doubleToLongBits(v));
$elseif(BytesRef)$
        state.values.add(groupId, BlockHash.hashOrdToGroup(state.bytes.add(v)));
$elseif(int)$
        /*
         * Encode the groupId and value into a single long -
         * the top 32 bits for the group, the bottom 32 for the value.
         */
        state.values.add((((long) groupId) << Integer.SIZE) | (v & 0xFFFFFFFFL));
$elseif(float)$
        /*
         * Encode the groupId and value into a single long -
         * the top 32 bits for the group, the bottom 32 for the value.
         */
        state.values.add((((long) groupId) << Float.SIZE) | (Float.floatToIntBits(v) & 0xFFFFFFFFL));
$endif$
    }

    public static void combineIntermediate(GroupingState state, int groupId, $Type$Block values, int valuesPosition) {
$if(BytesRef)$
        BytesRef scratch = new BytesRef();
$endif$
        int start = values.getFirstValueIndex(valuesPosition);
        int end = start + values.getValueCount(valuesPosition);
        for (int i = start; i < end; i++) {
$if(BytesRef)$
            combine(state, groupId, values.getBytesRef(i, scratch));
$else$
            combine(state, groupId, values.get$Type$(i));
$endif$
        }
    }

    public static void combineStates(GroupingState current, int currentGroupId, GroupingState state, int statePosition) {
$if(BytesRef)$
        BytesRef scratch = new BytesRef();
$endif$
        for (int id = 0; id < state.values.size(); id++) {
$if(long||BytesRef)$
            if (state.values.getKey1(id) == statePosition) {
                long value = state.values.getKey2(id);
$elseif(double)$
            if (state.values.getKey1(id) == statePosition) {
                double value = Double.longBitsToDouble(state.values.getKey2(id));
$elseif(int)$
            long both = state.values.get(id);
            int group = (int) (both >>> Integer.SIZE);
            if (group == statePosition) {
                int value = (int) both;
$elseif(float)$
            long both = state.values.get(id);
            int group = (int) (both >>> Float.SIZE);
            if (group == statePosition) {
                float value = Float.intBitsToFloat((int) both);
$endif$
                combine(current, currentGroupId, $if(BytesRef)$state.bytes.get(value, scratch)$else$value$endif$);
            }
        }
    }

    public static Block evaluateFinal(GroupingState state, IntVector selected, DriverContext driverContext) {
        return state.toBlock(driverContext.blockFactory(), selected);
    }

    public static class SingleState implements AggregatorState {
$if(BytesRef)$
        private final BytesRefHash values;

$else$
        private final LongHash values;

$endif$
        private SingleState(BigArrays bigArrays) {
$if(BytesRef)$
            values = new BytesRefHash(1, bigArrays);
$else$
            values = new LongHash(1, bigArrays);
$endif$
        }

        @Override
        public void toIntermediate(Block[] blocks, int offset, DriverContext driverContext) {
            blocks[offset] = toBlock(driverContext.blockFactory());
        }

        Block toBlock(BlockFactory blockFactory) {
            if (values.size() == 0) {
                return blockFactory.newConstantNullBlock(1);
            }
$if(BytesRef)$
            BytesRef scratch = new BytesRef();
$endif$
            if (values.size() == 1) {
$if(long)$
                return blockFactory.newConstantLongBlockWith(values.get(0), 1);
$elseif(float)$
                return blockFactory.newConstantFloatBlockWith(Float.intBitsToFloat((int) values.get(0)), 1);
$elseif(double)$
                return blockFactory.newConstantDoubleBlockWith(Double.longBitsToDouble(values.get(0)), 1);
$elseif(int)$
                return blockFactory.newConstantIntBlockWith((int) values.get(0), 1);
$elseif(BytesRef)$
                return blockFactory.newConstantBytesRefBlockWith(BytesRef.deepCopyOf(values.get(0, scratch)), 1);
$endif$
            }
            try ($Type$Block.Builder builder = blockFactory.new$Type$BlockBuilder((int) values.size())) {
                builder.beginPositionEntry();
                for (int id = 0; id < values.size(); id++) {
$if(long)$
                    builder.appendLong(values.get(id));
$elseif(float)$
                    builder.appendFloat(Float.intBitsToFloat((int) values.get(id)));
$elseif(double)$
                    builder.appendDouble(Double.longBitsToDouble(values.get(id)));
$elseif(int)$
                    builder.appendInt((int) values.get(id));
$elseif(BytesRef)$
                    builder.appendBytesRef(values.get(id, scratch));
$endif$
                }
                builder.endPositionEntry();
                return builder.build();
            }
        }

        @Override
        public void close() {
            values.close();
        }
    }

    /**
     * State for a grouped {@code VALUES} aggregation. This implementation
     * emphasizes collect-time performance over the performance of rendering
     * results. That's good, but it's a pretty intensive emphasis, requiring
     * an {@code O(n^2)} operation for collection to support a {@code O(1)}
     * collector operation. But at least it's fairly simple.
     */
    public static class GroupingState implements GroupingAggregatorState {
$if(long||double)$
        private final LongLongHash values;

$elseif(BytesRef)$
        final LongLongHash values;
        BytesRefHash bytes;

$elseif(int||float)$
        private final LongHash values;

$endif$
        private GroupingState(BigArrays bigArrays) {
$if(long||double)$
            values = new LongLongHash(1, bigArrays);
$elseif(BytesRef)$
            LongLongHash _values = null;
            BytesRefHash _bytes = null;
            try {
                _values = new LongLongHash(1, bigArrays);
                _bytes = new BytesRefHash(1, bigArrays);

                values = _values;
                bytes = _bytes;

                _values = null;
                _bytes = null;
            } finally {
                Releasables.closeExpectNoException(_values, _bytes);
            }
$elseif(int||float)$
            values = new LongHash(1, bigArrays);
$endif$
        }

        @Override
        public void toIntermediate(Block[] blocks, int offset, IntVector selected, DriverContext driverContext) {
            blocks[offset] = toBlock(driverContext.blockFactory(), selected);
        }

        /**
         * Builds a {@link Block} with the unique values collected for the {@code #selected}
         * groups. This is the implementation of the final and intermediate results of the agg.
         */
        Block toBlock(BlockFactory blockFactory, IntVector selected) {
            if (values.size() == 0) {
                return blockFactory.newConstantNullBlock(selected.getPositionCount());
            }

            long selectedCountsSize = 0;
            long idsSize = 0;
            try {
                /*
                 * Get a count of all groups less than the maximum selected group. Count
                 * *downwards* so that we can flip the sign on all of the actually selected
                 * groups. Negative values in this array are always unselected groups.
                 */
                int selectedCountsLen = selected.max() + 1;
                long adjust = RamUsageEstimator.alignObjectSize(
                    RamUsageEstimator.NUM_BYTES_ARRAY_HEADER + selectedCountsLen * Integer.BYTES
                );
                blockFactory.adjustBreaker(adjust);
                selectedCountsSize = adjust;
                int[] selectedCounts = new int[selectedCountsLen];
                for (int id = 0; id < values.size(); id++) {
$if(long||BytesRef||double)$
                    int group = (int) values.getKey1(id);
$elseif(float||int)$
                    long both = values.get(id);
                    int group = (int) (both >>> Float.SIZE);
$endif$
                    if (group < selectedCounts.length) {
                        selectedCounts[group]--;
                    }
                }

                /*
                 * Total the selected groups and turn the counts into the start index into a sort-of
                 * off-by-one running count. It's really the number of values that have been inserted
                 * into the results before starting on this group. Unselected groups will still
                 * have negative counts.
                 *
                 * For example, if
                 * | Group | Value Count | Selected |
                 * |-------|-------------|----------|
                 * |     0 | 3           | <-       |
                 * |     1 | 1           | <-       |
                 * |     2 | 2           |          |
                 * |     3 | 1           | <-       |
                 * |     4 | 4           | <-       |
                 *
                 * Then the total is 9 and the counts array will contain 0, 3, -2, 4, 5
                 */
                int total = 0;
                for (int s = 0; s < selected.getPositionCount(); s++) {
                    int group = selected.getInt(s);
                    int count = -selectedCounts[group];
                    selectedCounts[group] = total;
                    total += count;
                }

                /*
                 * Build a list of ids to insert in order *and* convert the running
                 * count in selectedCounts[group] into the end index (exclusive) in
                 * ids for each group.
                 * Here we use the negative counts to signal that a group hasn't been
                 * selected and the id containing values for that group is ignored.
                 *
                 * For example, if
                 * | Group | Value Count | Selected |
                 * |-------|-------------|----------|
                 * |     0 | 3           | <-       |
                 * |     1 | 1           | <-       |
                 * |     2 | 2           |          |
                 * |     3 | 1           | <-       |
                 * |     4 | 4           | <-       |
                 *
                 * Then the total is 9 and the counts array will start with 0, 3, -2, 4, 5.
                 * The counts will end with 3, 4, -2, 5, 9.
                 */
                adjust = RamUsageEstimator.alignObjectSize(RamUsageEstimator.NUM_BYTES_ARRAY_HEADER + total * Integer.BYTES);
                blockFactory.adjustBreaker(adjust);
                idsSize = adjust;
                int[] ids = new int[total];
                for (int id = 0; id < values.size(); id++) {
$if(long||BytesRef||double)$
                    int group = (int) values.getKey1(id);
$elseif(float||int)$
                    long both = values.get(id);
                    int group = (int) (both >>> Float.SIZE);
$endif$
                    if (group < selectedCounts.length && selectedCounts[group] >= 0) {
                        ids[selectedCounts[group]++] = id;
                    }
                }
$if(BytesRef)$
                if (OrdinalBytesRefBlock.isDense(selected.getPositionCount(), Math.toIntExact(values.size()))) {
                    return buildOrdinalOutputBlock(blockFactory, selected, selectedCounts, ids);
                } else {
                    return buildOutputBlock(blockFactory, selected, selectedCounts, ids);
                }
$else$
                return buildOutputBlock(blockFactory, selected, selectedCounts, ids);
$endif$
            } finally {
                blockFactory.adjustBreaker(-selectedCountsSize - idsSize);
            }
        }

        Block buildOutputBlock(BlockFactory blockFactory, IntVector selected, int[] selectedCounts, int[] ids) {
            /*
             * Insert the ids in order.
             */
$if(BytesRef)$
            BytesRef scratch = new BytesRef();
$endif$
            try ($Type$Block.Builder builder = blockFactory.new$Type$BlockBuilder(selected.getPositionCount())) {
                int start = 0;
                for (int s = 0; s < selected.getPositionCount(); s++) {
                    int group = selected.getInt(s);
                    int end = selectedCounts[group];
                    int count = end - start;
                    switch (count) {
                        case 0 -> builder.appendNull();
                        case 1 -> append(builder, ids[start]$if(BytesRef)$, scratch$endif$);
                        default -> {
                            builder.beginPositionEntry();
                            for (int i = start; i < end; i++) {
                                append(builder, ids[i]$if(BytesRef)$, scratch$endif$);
                            }
                            builder.endPositionEntry();
                        }
                    }
                    start = end;
                }
                return builder.build();
            }
        }

$if(BytesRef)$
        Block buildOrdinalOutputBlock(BlockFactory blockFactory, IntVector selected, int[] selectedCounts, int[] ids) {
            BytesRefVector dict = null;
            IntBlock ordinals = null;
            BytesRefBlock result = null;
            var dictArray = bytes.takeBytesRefsOwnership();
            bytes = null; // transfer ownership to dictArray
            try (var builder = blockFactory.newIntBlockBuilder(selected.getPositionCount())) {
                int start = 0;
                for (int s = 0; s < selected.getPositionCount(); s++) {
                    int group = selected.getInt(s);
                    int end = selectedCounts[group];
                    int count = end - start;
                    switch (count) {
                        case 0 -> builder.appendNull();
                        case 1 -> builder.appendInt(Math.toIntExact(values.getKey2(ids[start])));
                        default -> {
                            builder.beginPositionEntry();
                            for (int i = start; i < end; i++) {
                                builder.appendInt(Math.toIntExact(values.getKey2(ids[i])));
                            }
                            builder.endPositionEntry();
                        }
                    }
                    start = end;
                }
                ordinals = builder.build();
                dict = blockFactory.newBytesRefArrayVector(dictArray, Math.toIntExact(dictArray.size()));
                dictArray = null; // transfer ownership to dict
                result = new OrdinalBytesRefBlock(ordinals, dict);
                return result;
            } finally {
                if (result == null) {
                    Releasables.close(dictArray, dict, ordinals);
                }
            }
        }

        private void append($Type$Block.Builder builder, int id, BytesRef scratch) {
            BytesRef value = bytes.get(values.getKey2(id), scratch);
            builder.appendBytesRef(value);
        }

$else$
        private void append($Type$Block.Builder builder, int id) {
$if(long)$
            long value = values.getKey2(id);
$elseif(double)$
            double value = Double.longBitsToDouble(values.getKey2(id));
$elseif(float)$
            long both = values.get(id);
            float value = Float.intBitsToFloat((int) both);
$elseif(int)$
            long both = values.get(id);
            int value = (int) both;
$endif$
            builder.append$Type$(value);
        }

$endif$
        @Override
        public void enableGroupIdTracking(SeenGroupIds seen) {
            // we figure out seen values from nulls on the values block
        }

        @Override
        public void close() {
$if(BytesRef)$
            Releasables.closeExpectNoException(values, bytes);
$else$
            values.close();
$endif$
        }
    }
}

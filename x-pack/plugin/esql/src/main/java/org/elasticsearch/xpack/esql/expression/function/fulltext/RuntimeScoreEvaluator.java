/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.expression.function.fulltext;

import org.elasticsearch.compute.data.BlockFactory;
import org.elasticsearch.compute.data.BytesRefBlock;
import org.elasticsearch.compute.data.DoubleBlock;
import org.elasticsearch.compute.data.Page;
import org.elasticsearch.compute.operator.ScoreOperator;

public abstract class RuntimeScoreEvaluator implements ScoreOperator.ExpressionScorer {
    private final BlockFactory blockFactory;
    private final int position;

    public RuntimeScoreEvaluator(BlockFactory blockFactory, int position) {
        this.blockFactory = blockFactory;
        this.position = position;
    }

    @Override
    public DoubleBlock score(Page page) {
        int positionCount = page.getPositionCount();
        BytesRefBlock fieldBlock = page.getBlock(position);
        try (DoubleBlock.Builder result = blockFactory.newDoubleBlockBuilder(positionCount)) {
            for (int p = 0; p < positionCount; p++) {
                result.appendDouble(scoreRow(fieldBlock, position));
            }
            return result.build();
        }
    }

    protected abstract double scoreRow(BytesRefBlock  fieldBlock, int position);

    @Override
    public void close() {

    }
}

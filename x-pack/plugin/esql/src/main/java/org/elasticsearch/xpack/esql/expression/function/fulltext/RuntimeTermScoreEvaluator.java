/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.expression.function.fulltext;

import org.elasticsearch.compute.data.BooleanBlock;
import org.elasticsearch.compute.data.DoubleBlock;
import org.elasticsearch.compute.data.Page;
import org.elasticsearch.compute.expression.ExpressionEvaluator;
import org.elasticsearch.compute.operator.DriverContext;
import org.elasticsearch.compute.operator.ScoreOperator;


public class RuntimeTermScoreEvaluator implements ScoreOperator.ExpressionScorer {
    private final ExpressionEvaluator expressionEvaluator;
    private final DriverContext context;

    public RuntimeTermScoreEvaluator(DriverContext context, ExpressionEvaluator expressionEvaluator) {
        this.context = context;
        this.expressionEvaluator = expressionEvaluator;
    }

    @Override
    public DoubleBlock score(Page page) {
        BooleanBlock booleanBlock = (BooleanBlock) expressionEvaluator.eval(page);

        try (DoubleBlock.Builder result = context.blockFactory().newDoubleBlockBuilder(page.getPositionCount())) {
            for (int p = 0; p < page.getPositionCount(); p++) {
                boolean isMatch = booleanBlock.getBoolean(p);
                result.appendDouble(isMatch ? 1 : 0);
            }
            return result.build();
        }
    }

    @Override
    public void close() {
        expressionEvaluator.close();
    }

    public record Factory(ExpressionEvaluator.Factory expressionEvaluatorFactory) implements ScoreOperator.ExpressionScorer.Factory {
        @Override
        public ScoreOperator.ExpressionScorer get(DriverContext context) {
            return new RuntimeTermScoreEvaluator(context, expressionEvaluatorFactory.get(context));
        }
    }
}

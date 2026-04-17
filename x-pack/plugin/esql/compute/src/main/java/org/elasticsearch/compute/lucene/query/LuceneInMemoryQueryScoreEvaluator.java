/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.compute.lucene.query;

import org.elasticsearch.compute.data.Block;
import org.elasticsearch.compute.data.DoubleBlock;
import org.elasticsearch.compute.data.Page;
import org.elasticsearch.compute.operator.DriverContext;
import org.elasticsearch.compute.operator.ScoreOperator;

public class LuceneInMemoryQueryScoreEvaluator implements ScoreOperator.ExpressionScorer{
    private final DriverContext context;
    private final String queryString;

    public record Factory(String queryString) implements ScoreOperator.ExpressionScorer.Factory {

        @Override
        public ScoreOperator.ExpressionScorer get(DriverContext context) {
            return new LuceneInMemoryQueryScoreEvaluator(context, queryString);
        }
    }

    public LuceneInMemoryQueryScoreEvaluator(DriverContext context, String queryString) {
        this.context = context;
        this.queryString = queryString;
    }

    @Override
    public DoubleBlock score(Page page) {
        return context.blockFactory().newConstantDoubleBlockWith(1.0, page.getPositionCount());
    }

    @Override
    public void close() {

    }
}

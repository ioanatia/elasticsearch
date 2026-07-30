/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.expression.function.fulltext;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BooleanSimilarity;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.compute.data.BytesRefBlock;
import org.elasticsearch.compute.expression.ExpressionEvaluator;
import org.elasticsearch.compute.operator.DriverContext;
import org.elasticsearch.compute.operator.ScoreOperator;

import java.io.IOException;
import java.util.function.Function;

public class RuntimeLuceneQueryScoreEvaluator extends RuntimeScoreEvaluator {

    private final Query query;
    private final Analyzer analyzer;
    private BytesRef scratch;

    public RuntimeLuceneQueryScoreEvaluator(DriverContext context, int channel, Query query, Analyzer analyzer, BytesRef scratch) {
        super(context.blockFactory(), channel);
        this.query = query;
        this.analyzer = analyzer;
        this.scratch = scratch;
    }

    @Override
    protected double scoreRow(BytesRefBlock fieldBlock, int position) {
        if (fieldBlock == null) {
            return 0;
        }

        final var valueCount = fieldBlock.getValueCount(position);
        final var startIndex = fieldBlock.getFirstValueIndex(position);

        MemoryIndex index = new MemoryIndex();
        for (int valueIndex = startIndex; valueIndex < startIndex + valueCount; valueIndex++) {

            scratch = fieldBlock.getBytesRef(valueIndex, scratch);
            index.addField(RuntimeSearch.CONTENT_FIELD, scratch.utf8ToString(), analyzer);
        }

        IndexSearcher searcher = index.createSearcher();
        // TODO: Do we need to create BooleanSimilarity every time?
        searcher.setSimilarity(new BooleanSimilarity());
        TopDocs topDocs = null;
        try {
            topDocs = searcher.search(query, 1);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // TODO: check if scoreDocs[0].score is max score
        if (topDocs.scoreDocs.length > 0) {
            return topDocs.scoreDocs[0].score;
        }
        return 0;
    }

    public record Factory(Query query, int channel, Analyzer analyzer, Function<DriverContext, BytesRef> scratch) implements ScoreOperator.ExpressionScorer.Factory {

        @Override
        public ScoreOperator.ExpressionScorer get(DriverContext context, ExpressionEvaluator.Factory toEvaluator) {
            return new RuntimeLuceneQueryScoreEvaluator(context, channel, query, analyzer, scratch.apply(context));
        }
    }
}

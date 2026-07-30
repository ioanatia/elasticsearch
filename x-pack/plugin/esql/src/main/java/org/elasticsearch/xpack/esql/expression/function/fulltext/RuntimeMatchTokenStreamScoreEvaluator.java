/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.expression.function.fulltext;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.compute.data.BlockFactory;
import org.elasticsearch.compute.data.BytesRefBlock;
import org.elasticsearch.compute.expression.ExpressionEvaluator;
import org.elasticsearch.compute.operator.DriverContext;
import org.elasticsearch.compute.operator.ScoreOperator;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class RuntimeMatchTokenStreamScoreEvaluator extends RuntimeScoreEvaluator {

    private final Map<BytesRef, Integer> terms;
    private final Analyzer analyzer;
    private final int maxScore;
    private BytesRef scratch;

    public RuntimeMatchTokenStreamScoreEvaluator(BlockFactory blockFactory, int channel, Map<BytesRef, Integer> terms, Analyzer analyzer, BytesRef scratch) {
        super(blockFactory, channel);
        this.terms = terms;
        this.analyzer = analyzer;
        this.scratch = scratch;
        this.maxScore = maxScoreFromTerms(terms);
    }

    @Override
    protected double scoreRow(BytesRefBlock fieldBlock, int position) {
        if (fieldBlock == null) {
            return 0;
        }
        if (maxScore == 0) {
            return 0;
        }

        final var valueCount = fieldBlock.getValueCount(position);
        final var startIndex = fieldBlock.getFirstValueIndex(position);
        int maxScoreRow = 0;
        for (int valueIndex = startIndex; valueIndex < startIndex + valueCount; valueIndex++) {
            scratch = fieldBlock.getBytesRef(valueIndex, scratch);
            int score = 0;
            try (TokenStream stream = analyzer.tokenStream(RuntimeSearch.CONTENT_FIELD, scratch.utf8ToString())) {
                stream.reset();

                Set<BytesRef> foundTerms = new HashSet<>();

                TermToBytesRefAttribute term = stream.addAttribute(TermToBytesRefAttribute.class);
                while (stream.incrementToken()) {
                    if (foundTerms.contains(term.getBytesRef()) == false && terms.containsKey(term.getBytesRef())) {
                        foundTerms.add(term.getBytesRef());
                        score += terms.get(term.getBytesRef());
                        if (score >= maxScore) {
                            break;
                        }
                    }
                }

                stream.end();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            if (score >= maxScore) {
                return maxScore;
            }
            if (score > maxScoreRow) {
                maxScoreRow = score;
            }
        }

        return maxScoreRow;
    }

    private int maxScoreFromTerms(Map<BytesRef, Integer> terms) {
        return terms.values().stream().reduce(0, Integer::sum);
    }

    public record Factory(int channel, Map<BytesRef, Integer> terms, Analyzer analyzer, Function<DriverContext, BytesRef> scratch) implements ScoreOperator.ExpressionScorer.Factory {
        @Override
        public ScoreOperator.ExpressionScorer get(DriverContext context, ExpressionEvaluator.Factory toEvaluator) {
            return new RuntimeMatchTokenStreamScoreEvaluator(context.blockFactory(), channel, terms, analyzer, scratch.apply(context));
        }
    }
}

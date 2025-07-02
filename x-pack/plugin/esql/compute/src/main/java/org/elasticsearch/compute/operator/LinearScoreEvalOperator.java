/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.compute.operator;

import org.apache.lucene.util.BytesRef;
import org.elasticsearch.compute.data.Block;
import org.elasticsearch.compute.data.BytesRefBlock;
import org.elasticsearch.compute.data.DoubleVector;
import org.elasticsearch.compute.data.DoubleVectorBlock;
import org.elasticsearch.compute.data.Page;
import org.elasticsearch.index.engine.Engine;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class LinearScoreEvalOperator implements Operator {
    public record Factory(int scorePosition, int discriminatorPosition, Map<String, Double> weights, String normalizer) implements OperatorFactory {

        @Override
        public Operator get(DriverContext driverContext) {
            return new LinearScoreEvalOperator(scorePosition, discriminatorPosition, weights, normalizer);
        }

        @Override
        public String describe() {
            return "LinearScoreEvalOperator";
        }
    }

    private final int scorePosition;
    private final int discriminatorPosition;
    private final Map<String, Double> weights;
    private final String normalizer;

    private final Deque<Page> inputPages;
    private final Deque<Page> outputPages;
    private boolean finished;
    private Warnings warnings;

    private Map<String, Double> minScores = new HashMap<>();
    private Map<String, Double> maxScores = new HashMap<>();
    private Map<String, Double> l2Norms = new HashMap<>();

    public LinearScoreEvalOperator(int scorePosition, int discriminatorPosition, Map<String, Double> weights, String normalizer) {
        this.scorePosition = scorePosition;
        this.discriminatorPosition = discriminatorPosition;
        this.weights = weights;
        this.normalizer = normalizer;

        finished = false;
        inputPages = new ArrayDeque<>();
        outputPages = new ArrayDeque<>();
        warnings = null;
    }

    @Override
    public boolean needsInput() {
        return finished == false;
    }

    @Override
    public void addInput(Page page) {
        inputPages.add(page);
    }

    @Override
    public void finish() {
        if (finished == false) {
            finished = true;
            createOutputPages();
        }
    }

    private void createOutputPages() {
        preprocessInputPages();

        while (inputPages.isEmpty() == false) {
            Page inputPage = inputPages.peek();

            BytesRefBlock discriminatorBlock = (BytesRefBlock) inputPage.getBlock(discriminatorPosition);
            DoubleVectorBlock initialScoreBlock = (DoubleVectorBlock) inputPage.getBlock(scorePosition);

            DoubleVector.Builder scores = discriminatorBlock.blockFactory().newDoubleVectorBuilder(discriminatorBlock.getPositionCount());

            for (int i = 0; i < inputPage.getPositionCount(); i++) {
                String discriminator = discriminatorBlock.getBytesRef(i, new BytesRef()).utf8ToString();

                var weight = weights.get(discriminator) == null ? 1.0 : weights.get(discriminator);

                Double score = initialScoreBlock.getDouble(i);

                if (normalizer.equals("minmax")) {
                    var minScore = minScores.get(discriminator);
                    var maxScore = maxScores.get(discriminator);
                    score = minScore == maxScore ? 0.0 : (score - minScore) / (maxScore - minScore);
                } else if (normalizer.equals("l2_norm")) {
                    var l2Norm = l2Norms.get(discriminator);
                    score = l2Norm == 0.0 ? 0.0 : score / l2Norm;
                }
                scores.appendDouble(weight * score);
            }
            Block scoreBlock = scores.build().asBlock();
            inputPage = inputPage.appendBlock(scoreBlock);

            int[] projections = new int[inputPage.getBlockCount() - 1];

            for (int i = 0; i < inputPage.getBlockCount() - 1; i++) {
                projections[i] = i == scorePosition ? inputPage.getBlockCount() - 1 : i;
            }
            inputPages.removeFirst();
            outputPages.add(inputPage.projectBlocks(projections));
            inputPage.releaseBlocks();
        }
    }

    private void preprocessInputPages() {
        for (Page inputPage : inputPages) {
            DoubleVectorBlock scoreBlock = inputPage.getBlock(scorePosition);
            BytesRefBlock discriminatorBlock = inputPage.getBlock(discriminatorPosition);

            for (int i = 0; i < inputPage.getPositionCount(); i++) {
                double score = scoreBlock.getDouble(i);
                String discriminator = discriminatorBlock.getBytesRef(i, new BytesRef()).utf8ToString();

                l2Norms.computeIfAbsent(discriminator, k -> 0.0);
                l2Norms.computeIfPresent(discriminator, (k, v) -> v + score * score);

                maxScores.computeIfAbsent(discriminator, k -> score);
                maxScores.computeIfPresent(discriminator, (k, v) -> Math.max(v, score));

                minScores.computeIfAbsent(discriminator, k -> score);
                minScores.computeIfPresent(discriminator, (k, v) -> Math.min(v, score));
            }
        }

        l2Norms.replaceAll((k, v) -> Math.sqrt(v));
    }


    @Override
    public boolean isFinished() {
        return finished && outputPages.isEmpty();
    }

    @Override
    public Page getOutput() {
        if (finished == false || outputPages.isEmpty()) {
            return null;
        }
        return outputPages.removeFirst();
    }

    @Override
    public void close() {
        for (Page page : inputPages) {
            page.releaseBlocks();
        }
        for (Page page : outputPages) {
            page.releaseBlocks();
        }
    }

    @Override
    public String toString() {
        return "LinearScoreEvalOperator[" + scorePosition + ", " + discriminatorPosition + "]";
    }
}

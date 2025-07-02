/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.plan.physical;

import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.xpack.esql.core.expression.Attribute;
import org.elasticsearch.xpack.esql.core.expression.Expression;
import org.elasticsearch.xpack.esql.core.expression.FoldContext;
import org.elasticsearch.xpack.esql.core.expression.MapExpression;
import org.elasticsearch.xpack.esql.core.tree.NodeInfo;
import org.elasticsearch.xpack.esql.core.tree.Source;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class LinearScoreEvalExec extends UnaryExec {
    private final Attribute score;
    private final Attribute discriminator;
    private final MapExpression options;

    public LinearScoreEvalExec(Source source, PhysicalPlan child, Attribute score, Attribute discriminator, MapExpression options) {
        super(source, child);
        this.score = score;
        this.discriminator = discriminator;
        this.options = options;
    }

    @Override
    public UnaryExec replaceChild(PhysicalPlan newChild) {
        return new LinearScoreEvalExec(source(), newChild, score, discriminator, options);
    }

    @Override
    protected NodeInfo<? extends PhysicalPlan> info() {
        return NodeInfo.create(this, LinearScoreEvalExec::new, child(), score, discriminator, options);
    }

    @Override
    public String getWriteableName() {
        throw new UnsupportedOperationException("not serialized");
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        throw new UnsupportedOperationException("not serialized");
    }

    public Attribute score() {
        return score;
    }

    public Attribute discriminator() {
        return discriminator;
    }

    public Map<String, Double> weights() {
        Map<String, Double> result = new HashMap<>();
        if (options == null) {
            return result;
        }

        MapExpression weights = (MapExpression) options.get("weights");
        if (weights == null) {
            return result;
        }

        weights.keyFoldedMap().forEach((k, v) -> { result.put(k, (double) v.fold(FoldContext.small())); });
        return result;
    }

    public String normalizer() {
        if (options == null) {
            return "none";
        }

        Expression normalizer = options.get("normalizer");
        if (normalizer == null) {
            return "none";
        }
        BytesRef normalizerBytes = (BytesRef) normalizer.fold(FoldContext.small());

        return normalizerBytes.utf8ToString();
    }
}

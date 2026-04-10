/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.plan.physical.inference;

import org.elasticsearch.xpack.esql.core.expression.Attribute;
import org.elasticsearch.xpack.esql.core.expression.AttributeSet;
import org.elasticsearch.xpack.esql.core.expression.Expression;
import org.elasticsearch.xpack.esql.core.tree.NodeInfo;
import org.elasticsearch.xpack.esql.core.tree.Source;
import org.elasticsearch.xpack.esql.plan.physical.PhysicalPlan;
import org.elasticsearch.xpack.esql.plan.physical.UnaryExec;

import java.util.List;

import static org.elasticsearch.xpack.esql.expression.NamedExpressions.mergeOutputAttributes;

public class GenerateEmbeddingsExec extends InferenceExec {
    private final Expression input;
    private final Attribute targetField;

    public GenerateEmbeddingsExec(Source source, PhysicalPlan child, Expression inferenceId, Expression input, Attribute targetField) {
        super(source, child, inferenceId);
        this.input = input;
        this.targetField = targetField;
    }

    @Override
    public UnaryExec replaceChild(PhysicalPlan newChild) {
        return new GenerateEmbeddingsExec(source(), newChild, inferenceId(), input, targetField);
    }

    @Override
    protected NodeInfo<? extends PhysicalPlan> info() {
        return NodeInfo.create(this, GenerateEmbeddingsExec::new, child(), inferenceId(), input, targetField);
    }

    @Override
    protected AttributeSet computeReferences() {
        return input.references();
    }

    @Override
    public String getWriteableName() {
        return "";
    }

    public Attribute targetField() {
        return targetField;
    }

    public Expression input() {
        return input;
    }

    private List<Attribute> lazyOutput;

    @Override
    public List<Attribute> output() {
        if (lazyOutput == null) {
            lazyOutput = mergeOutputAttributes(List.of(targetField), child().output());
        }

        return lazyOutput;
    }
}

/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.plan.logical.inference;

import org.elasticsearch.inference.TaskType;
import org.elasticsearch.xpack.esql.capabilities.TelemetryAware;
import org.elasticsearch.xpack.esql.core.expression.Attribute;
import org.elasticsearch.xpack.esql.core.expression.AttributeSet;
import org.elasticsearch.xpack.esql.core.expression.Expression;
import org.elasticsearch.xpack.esql.core.expression.NameId;
import org.elasticsearch.xpack.esql.core.tree.NodeInfo;
import org.elasticsearch.xpack.esql.core.tree.Source;
import org.elasticsearch.xpack.esql.plan.logical.LogicalPlan;
import org.elasticsearch.xpack.esql.plan.logical.UnaryPlan;

import java.util.List;

import static org.elasticsearch.xpack.esql.expression.NamedExpressions.mergeOutputAttributes;

public class GenerateEmbeddings extends InferencePlan<GenerateEmbeddings> implements TelemetryAware {

    private final Expression input;
    private final Attribute targetField;
    private List<Attribute> lazyOutput;

    public GenerateEmbeddings(
        Source source,
        LogicalPlan child,
        Expression inferenceId,
        Expression rowLimit,
        Expression input,
        Attribute targetField
    ) {
        super(source, child, inferenceId, rowLimit);
        this.input = input;
        this.targetField = targetField;
    }

    @Override
    public TaskType taskType() {
        return TaskType.TEXT_EMBEDDING;
    }

    @Override
    public GenerateEmbeddings withInferenceId(Expression newInferenceId) {
        return new GenerateEmbeddings(source(), child(), newInferenceId, rowLimit(), input, targetField);
    }

    @Override
    public boolean isFoldable() {
        return false;
    }

    @Override
    public List<Attribute> generatedAttributes() {
        return List.of(targetField);
    }

    @Override
    public InferencePlan<GenerateEmbeddings> withGeneratedNames(List<String> newNames) {
        checkNumberOfNewNames(newNames);
        return new GenerateEmbeddings(source(), child(), inferenceId(), rowLimit(), input, renameTargetField(newNames.getFirst()));
    }

    private Attribute renameTargetField(String newName) {
        if (newName.equals(targetField.name())) {
            return targetField;
        }

        return targetField.withName(newName).withId(new NameId());
    }

    @Override
    public UnaryPlan replaceChild(LogicalPlan newChild) {
        return new GenerateEmbeddings(source(), newChild, inferenceId(), rowLimit(), input, targetField);
    }

    @Override
    protected NodeInfo<? extends LogicalPlan> info() {
        return NodeInfo.create(this, GenerateEmbeddings::new, child(), inferenceId(), rowLimit(), input, targetField);
    }

    @Override
    public boolean expressionsResolved() {
        return super.expressionsResolved() && input.resolved() && targetField.resolved();
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

    @Override
    protected AttributeSet computeReferences() {
        return input.references();
    }

    @Override
    public List<Attribute> output() {
        if (lazyOutput == null) {
            lazyOutput = mergeOutputAttributes(List.of(targetField), child().output());
        }

        return lazyOutput;
    }
}

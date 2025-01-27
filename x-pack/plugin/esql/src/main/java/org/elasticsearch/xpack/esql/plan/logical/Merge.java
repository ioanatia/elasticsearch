/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.plan.logical;

import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.xpack.esql.core.expression.Attribute;
import org.elasticsearch.xpack.esql.core.expression.AttributeSet;
import org.elasticsearch.xpack.esql.core.tree.NodeInfo;
import org.elasticsearch.xpack.esql.core.tree.Source;
import org.elasticsearch.xpack.esql.io.stream.PlanStreamInput;
import org.elasticsearch.xpack.esql.plan.logical.local.LocalRelation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Merge extends LeafPlan {
    public static final NamedWriteableRegistry.Entry ENTRY = new NamedWriteableRegistry.Entry(LogicalPlan.class, "Merge", Merge::new);
    private final LogicalPlan left;
    private final LogicalPlan right;

    public Merge(Source source, LogicalPlan left, LogicalPlan right) {
        super(source);
        this.left = left;
        this.right = right;
    }

    public Merge(StreamInput in) throws IOException {
        this(
            Source.readFrom((PlanStreamInput) in),
            in.readNamedWriteable(LogicalPlan.class),
            in.readNamedWriteable(LogicalPlan.class)
        );
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        Source.EMPTY.writeTo(out);
        out.writeNamedWriteable(left);
        out.writeNamedWriteable(right);
    }

    @Override
    public String getWriteableName() {
        return ENTRY.name;
    }

    @Override
    public String commandName() {
        return "MERGE";
    }

    @Override
    public boolean expressionsResolved() {
        return left.expressionsResolved() && right.expressionsResolved();
    }

    @Override
    public int hashCode() {
        return Objects.hash(source().hashCode(), left, right);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Merge other = (Merge) o;

        return Objects.equals(source(), other.source())
            && Objects.equals(left, other.left)
            && Objects.equals(right, other.right);
    }

    @Override
    protected NodeInfo<? extends LogicalPlan> info() {
        return NodeInfo.create(this, Merge::new, left, right);
    }

    public static LogicalPlan subPlanData(Merge target, LocalRelation data) {
        return new Merge(target.source(), target.left(), data);
    }

    @Override
    public List<Attribute> output() {
        return left.output();
    }

    public LogicalPlan left() { return left; }
    public LogicalPlan right() { return right; }
 }

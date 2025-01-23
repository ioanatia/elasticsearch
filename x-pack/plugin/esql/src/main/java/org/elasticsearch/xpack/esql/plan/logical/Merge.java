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

public class Merge extends BinaryPlan {
    public static final NamedWriteableRegistry.Entry ENTRY = new NamedWriteableRegistry.Entry(LogicalPlan.class, "Merge", Merge::new);

    private final Attribute discriminator;

    protected Merge(Source source, LogicalPlan left, LogicalPlan right, Attribute discriminator) {
        super(source, left, right);
        this.discriminator = discriminator;
    }

    public Merge(StreamInput in) throws IOException {
        this(
            Source.readFrom((PlanStreamInput) in),
            in.readNamedWriteable(LogicalPlan.class),
            in.readNamedWriteable(LogicalPlan.class),
            in.readNamedWriteable(Attribute.class)
        );
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        Source.EMPTY.writeTo(out);
        out.writeNamedWriteable(left());
        out.writeNamedWriteable(right());
        out.writeNamedWriteable(discriminator);
    }

    @Override
    public String getWriteableName() {
        return ENTRY.name;
    }

    @Override
    public AttributeSet leftReferences() {
        return left().outputSet();
    }

    @Override
    public AttributeSet rightReferences() {
        return right().outputSet();
    }

    @Override
    public BinaryPlan replaceChildren(LogicalPlan left, LogicalPlan right) {
        return new Merge(source(), left, right, discriminator);
    }

    @Override
    public String commandName() {
        return "MERGE";
    }

    @Override
    public boolean expressionsResolved() {
        return left().expressionsResolved();
    }

    @Override
    public List<Attribute> output() {
        List<Attribute> output = new ArrayList<>();

        for (Attribute ra : right().output()) {
            for (Attribute la : left().output()) {
                if (la.name().equals(ra.name()) && la.dataType() == ra.dataType()) {
                    output.add(la);
                }
            }
        }
        output.add(discriminator);

        return output;
    }

    @Override
    protected NodeInfo<? extends LogicalPlan> info() {
        return NodeInfo.create(this, Merge::new, left(), right(), discriminator);
    }

    public static LogicalPlan subPlanData(Merge target, LocalRelation data) {
        return target.replaceRight(data);
    }
}

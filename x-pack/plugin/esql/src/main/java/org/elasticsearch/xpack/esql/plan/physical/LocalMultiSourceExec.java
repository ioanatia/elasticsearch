/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.plan.physical;

import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.xpack.esql.core.expression.Attribute;
import org.elasticsearch.xpack.esql.core.expression.AttributeSet;
import org.elasticsearch.xpack.esql.core.expression.Expressions;
import org.elasticsearch.xpack.esql.core.tree.NodeInfo;
import org.elasticsearch.xpack.esql.core.tree.Source;
import org.elasticsearch.xpack.esql.io.stream.PlanStreamInput;
import org.elasticsearch.xpack.esql.plan.logical.local.LocalSupplier;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class LocalMultiSourceExec extends UnaryExec {
        public static final NamedWriteableRegistry.Entry ENTRY = new NamedWriteableRegistry.Entry(
        PhysicalPlan.class,
        "LocalMultiSourceExec",
        LocalMultiSourceExec::new
    );
    private final PhysicalPlan right;
    private final List<Attribute> output;

    public LocalMultiSourceExec(Source source,  PhysicalPlan left, PhysicalPlan right, List<Attribute> output) {
        super(source, left);
        this.right = right;
        this.output = output;
    }

    public LocalMultiSourceExec(StreamInput in) throws IOException {
        super(Source.readFrom((PlanStreamInput) in), in.readNamedWriteable(PhysicalPlan.class));
        this.right = in.readNamedWriteable(PhysicalPlan.class);
        this.output = in.readNamedWriteableCollectionAsList(Attribute.class);
    }

    public List<LocalSupplier> suppliers() {
        if (right instanceof LocalSourceExec lse) {
            return List.of(lse.supplier());
        }
        return List.of();
    }

    @Override
    public String getWriteableName() {
        return ENTRY.name;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        Source.EMPTY.writeTo(out);
        out.writeNamedWriteable(child());
        out.writeNamedWriteable(right);
        out.writeNamedWriteableCollection(output);
    }

    @Override
    protected NodeInfo<LocalMultiSourceExec> info() {
        return NodeInfo.create(this, LocalMultiSourceExec::new, child(), right, output);
    }


    @Override
    public UnaryExec replaceChild(PhysicalPlan newChild) {
        return new LocalMultiSourceExec(source(), newChild, right, output);
    }

    @Override
    public AttributeSet references() {
        return Expressions.references(output);
    }

    @Override
    public List<Attribute> output() {
        // return Stream.concat(child().output().stream(), output.stream()).distinct().toList();
        return output;
    }


    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode());
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (super.equals(o) == false) {
            return false;
        }
        return true;
    }
}

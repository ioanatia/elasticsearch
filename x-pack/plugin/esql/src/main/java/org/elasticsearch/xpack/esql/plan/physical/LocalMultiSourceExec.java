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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class LocalMultiSourceExec extends LeafExec {
    public static final NamedWriteableRegistry.Entry ENTRY = new NamedWriteableRegistry.Entry(
        PhysicalPlan.class,
        "LocalMultiSourceExec",
        LocalMultiSourceExec::new
    );
    private final PhysicalPlan right;

    private final PhysicalPlan left;
    private final List<Attribute> output;

    public LocalMultiSourceExec(Source source, PhysicalPlan left, PhysicalPlan right, List<Attribute> output) {
        super(source);
        this.left = left;
        this.right = right;
        this.output = output;
    }

    public LocalMultiSourceExec(StreamInput in) throws IOException {
        super(Source.readFrom((PlanStreamInput) in));
        this.left = in.readNamedWriteable(PhysicalPlan.class);
        this.right = in.readNamedWriteable(PhysicalPlan.class);
        this.output = in.readNamedWriteableCollectionAsList(Attribute.class);
    }

    public List<LocalSupplier> suppliers() {
        List<LocalSupplier> suppliers = new ArrayList<>();

        if (left instanceof LocalSourceExec lse) {
            suppliers.add(lse.supplier());
        }
        if (right instanceof LocalSourceExec lse) {
            suppliers.add(lse.supplier());
        }

        return suppliers;
    }

    @Override
    public String getWriteableName() {
        return ENTRY.name;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        Source.EMPTY.writeTo(out);
        out.writeNamedWriteable(left);
        out.writeNamedWriteable(right);
        out.writeNamedWriteableCollection(output);
    }

    @Override
    protected NodeInfo<LocalMultiSourceExec> info() {
        return NodeInfo.create(this, LocalMultiSourceExec::new, left, right, output);
    }

    @Override
    public AttributeSet references() {
        return Expressions.references(List.of());
        // return Expressions.references(output.stream().filter(at -> at.name().equals("_fork") == false).toList());
    }

    @Override
    public List<Attribute> output() {
        // return Stream.concat(child().output().stream(), output.stream()).distinct().toList();
        return output;
    }

    public PhysicalPlan left() { return left; }

    public PhysicalPlan right() { return right; }

    @Override
    public int hashCode() {
        return Objects.hash(source().hashCode(), left, right);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocalMultiSourceExec other = (LocalMultiSourceExec) o;
        return Objects.equals(this.source(), other.source())
            && Objects.equals(this.left, other.left)
            && Objects.equals(this.right, other.right);
    }
}

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

public class LocalMultiSourceExec extends BinaryExec {
        public static final NamedWriteableRegistry.Entry ENTRY = new NamedWriteableRegistry.Entry(
        PhysicalPlan.class,
        "LocalMultiSourceExec",
        LocalMultiSourceExec::new
    );
    private final List<Attribute> output;
    private final List<LocalSupplier> suppliers;

    public LocalMultiSourceExec(Source source,  PhysicalPlan left, PhysicalPlan right, List<Attribute> output, List<LocalSupplier> suppliers) {
        super(source, left, right);
        this.output = output;
        this.suppliers = suppliers;
    }

    public LocalMultiSourceExec(StreamInput in) throws IOException {
        super(Source.readFrom((PlanStreamInput) in), in.readNamedWriteable(PhysicalPlan.class), in.readNamedWriteable(PhysicalPlan.class));
        this.output = in.readNamedWriteableCollectionAsList(Attribute.class);
        this.suppliers = in.readCollectionAsList(LocalSupplier::readFromStreamInput);
    }

    public List<LocalSupplier> suppliers() { return this.suppliers; }

    @Override
    public String getWriteableName() {
        return ENTRY.name;
    }

    @Override
    protected BinaryExec replaceChildren(PhysicalPlan newLeft, PhysicalPlan newRight) {
        return new LocalMultiSourceExec(source(), newLeft, newRight, output, suppliers);
    }

    @Override
    public AttributeSet leftReferences() {
        return Expressions.references(output);
    }

    @Override
    public AttributeSet rightReferences() {
        return Expressions.references(output);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeNamedWriteableCollection(output);
        out.writeCollection(suppliers, StreamOutput::writeWriteable);
    }

    @Override
    protected NodeInfo<LocalMultiSourceExec> info() {
        return NodeInfo.create(this, LocalMultiSourceExec::new, left(), right(), output, suppliers);
    }


    @Override
    public List<Attribute> output() {
        return output;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), output, suppliers);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        var other = (LocalMultiSourceExec) o;
        return Objects.equals(suppliers, other.suppliers) && Objects.equals(output, other.output);
    }
}

/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.plan.logical;

import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.lucene.BytesRefs;
import org.elasticsearch.xpack.esql.core.capabilities.Resolvables;
import org.elasticsearch.xpack.esql.core.expression.Expression;
import org.elasticsearch.xpack.esql.core.expression.Literal;
import org.elasticsearch.xpack.esql.core.tree.NodeInfo;
import org.elasticsearch.xpack.esql.core.tree.Source;
import org.elasticsearch.xpack.esql.core.type.DataType;
import org.elasticsearch.xpack.esql.expression.Order;
import org.elasticsearch.xpack.esql.io.stream.PlanStreamInput;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static org.elasticsearch.xpack.esql.type.EsqlDataTypeConverter.stringToInt;

public class TopN extends UnaryPlan implements PipelineBreaker, ExecutesOn {
    public static final NamedWriteableRegistry.Entry ENTRY = new NamedWriteableRegistry.Entry(LogicalPlan.class, "TopN", TopN::new);

    private final List<Order> order;
    private final Expression limit;
    private final Expression offset;
    /**
     * Local topn is not a pipeline breaker, and is applied only to the local node's data.
     * It should always end up inside a fragment.
     */
    private final transient boolean local;

    public TopN(Source source, LogicalPlan child, List<Order> order, Expression limit, Expression offset, boolean local) {
        super(source, child);
        this.order = order;
        this.limit = limit;
        this.local = local;
        this.offset = offset;
    }

    private TopN(StreamInput in) throws IOException {
        this(
            Source.readFrom((PlanStreamInput) in),
            in.readNamedWriteable(LogicalPlan.class),
            in.readCollectionAsList(Order::new),
            in.readNamedWriteable(Expression.class),
            null,
            false
        );
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        Source.EMPTY.writeTo(out);
        out.writeNamedWriteable(child());
        out.writeCollection(order);
        out.writeNamedWriteable(limit);
    }

    @Override
    public String getWriteableName() {
        return ENTRY.name;
    }

    @Override
    public boolean expressionsResolved() {
        return limit.resolved() && Resolvables.resolved(order);
    }

    @Override
    protected NodeInfo<TopN> info() {
        return NodeInfo.create(this, TopN::new, child(), order, limit, offset, local);
    }

    @Override
    public TopN replaceChild(LogicalPlan newChild) {
        return new TopN(source(), newChild, order, limit, offset, local);
    }

    public TopN withLocal(boolean local) {
        return new TopN(source(), child(), order, limit, offset, local);
    }

    public boolean local() {
        return local;
    }

    public Expression limit() {
        return limit;
    }

    public Expression offset() {
        return offset;
    }

    public List<Order> order() {
        return order;
    }

    public int limitValue() {
        if (limit instanceof Literal literal) {
            Object val = literal.value() instanceof BytesRef br ? BytesRefs.toString(br) : literal.value();
            return stringToInt(val.toString());
        }
        throw new IllegalArgumentException("TopNExec limit must be a literal");
    }

    public int offsetValue() {
        if (offset == null) {
            return 0;
        }
        if (offset instanceof Literal literal) {
            Object val = literal.value() instanceof BytesRef br ? BytesRefs.toString(br) : literal.value();
            return stringToInt(val.toString());
        }
        throw new IllegalArgumentException("TopNExec offset must be a literal");
    }

    public TopN withOffsetFoldedIntoLimit() {
        Literal newLimit = new Literal(source(), limitValue() + offsetValue(), DataType.INTEGER);
        return new TopN(source(), child(), order, newLimit, null, local);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), order, limit, offset, local);
    }

    @Override
    public boolean equals(Object obj) {
        if (super.equals(obj)) {
            var other = (TopN) obj;
            return Objects.equals(order, other.order)
                && Objects.equals(limit, other.limit)
                && local == other.local
                && Objects.equals(offset, other.offset);
        }
        return false;
    }

    @Override
    public ExecuteLocation executesOn() {
        return local ? ExecuteLocation.ANY : ExecuteLocation.COORDINATOR;
    }
}

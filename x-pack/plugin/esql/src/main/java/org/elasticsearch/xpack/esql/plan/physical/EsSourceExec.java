/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.plan.physical;

import org.elasticsearch.index.IndexMode;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.vectors.KnnVectorQueryBuilder;
import org.elasticsearch.xpack.esql.core.expression.FieldAttribute;
import org.elasticsearch.xpack.esql.core.expression.predicate.fulltext.MatchQueryPredicate;
import org.elasticsearch.xpack.esql.core.type.EsField;
import org.elasticsearch.xpack.esql.core.expression.Attribute;
import org.elasticsearch.xpack.esql.core.index.EsIndex;
import org.elasticsearch.xpack.esql.core.tree.NodeInfo;
import org.elasticsearch.xpack.esql.core.tree.NodeUtils;
import org.elasticsearch.xpack.esql.core.tree.Source;
import org.elasticsearch.xpack.esql.plan.logical.EsRelationWithFilter;
import org.elasticsearch.xpack.esql.plan.logical.EsRelation;

import java.util.List;
import java.util.Objects;

import static java.util.Collections.emptyMap;
import static org.elasticsearch.xpack.esql.optimizer.LocalPhysicalPlanOptimizer.TRANSLATOR_HANDLER;
import static org.elasticsearch.xpack.esql.core.type.DataTypes.TEXT;

public class EsSourceExec extends LeafExec {

    private final EsIndex index;
    private final List<Attribute> attributes;
    private QueryBuilder query;
    private final IndexMode indexMode;

    private boolean withScores = false;

    public EsSourceExec(EsRelation relation) {
        this(relation.source(), relation.index(), relation.output(), null, relation.indexMode());

        if (relation instanceof EsRelationWithFilter) {
            String fieldName = ((EsRelationWithFilter) relation).getFieldName();
            String queryString = ((EsRelationWithFilter) relation).getQueryString();
            if (fieldName != null && queryString != null) {
                float[] queryVector;
                if ((queryVector = asVector(queryString)) != null) {
                    Integer numCands = 10;
                    Float similarity = 0.9f;
                    this.query = new KnnVectorQueryBuilder(fieldName, queryVector, numCands, similarity);
                    this.withScores = true;
                } else {
                    FieldAttribute fa = new FieldAttribute(Source.EMPTY, fieldName, new EsField(fieldName, TEXT, emptyMap(), true));
                    MatchQueryPredicate mmqp = new MatchQueryPredicate(relation.source(), fa, queryString, "");
                    this.query = TRANSLATOR_HANDLER.asQuery(mmqp).asBuilder();
                    this.withScores = true;
                }
            }
        }
    }

    private float[] asVector(String queryString) {
        float[] vector = null;
        try {
            String[] valuesArray = queryString
                .replaceFirst("\\[", "")
                .replaceFirst("]", "")
                .split("\\s*,\\s*");
            vector = new float[valuesArray.length];
            for (int i = 0; i < valuesArray.length; i++) {
                vector[i] = Float.parseFloat(valuesArray[i]);
            }
        } catch (Throwable t) {
           return null;
        }
        return vector;
    }

    public EsSourceExec(Source source, EsIndex index, List<Attribute> attributes, QueryBuilder query, IndexMode indexMode) {
        super(source);
        this.index = index;
        this.attributes = attributes;
        this.query = query;
        this.indexMode = indexMode;
    }

    public EsIndex index() {
        return index;
    }

    public QueryBuilder query() {
        return query;
    }

    public IndexMode indexMode() {
        return indexMode;
    }

    @Override
    public List<Attribute> output() {
        return attributes;
    }

    public boolean withScores() {
        return withScores;
    }

    @Override
    protected NodeInfo<? extends PhysicalPlan> info() {
        return NodeInfo.create(this, EsSourceExec::new, index, attributes, query, indexMode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        EsSourceExec other = (EsSourceExec) obj;
        return Objects.equals(index, other.index) && Objects.equals(query, other.query);
    }

    @Override
    public String nodeString() {
        return nodeName() + "[" + index + "]" + NodeUtils.limitedToString(attributes);
    }
}

/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.plan.logical;

import org.elasticsearch.index.IndexMode;
import org.elasticsearch.xpack.esql.core.expression.Attribute;
import org.elasticsearch.xpack.esql.core.index.EsIndex;
import org.elasticsearch.xpack.esql.core.tree.Source;

import java.util.List;

public class EsRelationWithFilter extends EsRelation {
    private final String fieldName;
    private final String queryString;

    public EsRelationWithFilter(
        Source source,
        EsIndex index,
        List<Attribute> attributes,
        IndexMode indexMode,
        String fieldName,
        String queryString
        ) {
        super(
            source,
            index,
            attributes,
            indexMode,
            false
        );

        this.fieldName = fieldName;
        this.queryString = queryString;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getQueryString() {
        return queryString;
    }
}

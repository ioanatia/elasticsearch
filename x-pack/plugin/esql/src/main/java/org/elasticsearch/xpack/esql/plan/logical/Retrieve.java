/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.plan.logical;

import org.elasticsearch.index.IndexMode;
import org.elasticsearch.xpack.esql.core.capabilities.Unresolvable;
import org.elasticsearch.xpack.esql.core.expression.Attribute;
import org.elasticsearch.xpack.esql.core.plan.TableIdentifier;
import org.elasticsearch.xpack.esql.core.plan.logical.LeafPlan;
import org.elasticsearch.xpack.esql.core.tree.NodeInfo;
import org.elasticsearch.xpack.esql.core.tree.Source;
import java.util.List;

public class Retrieve extends UnresolvedRelation {
    private final List<Attribute> metadataFields;
    private final String fieldName;
    private final String queryString;

    private final IndexMode indexMode;

    public Retrieve(
        Source source,
        TableIdentifier table,
        List<Attribute> metadataFields,
        IndexMode indexMode,
        String fieldName,
        String queryString,
        String unresolvedMessage
    ) {
        super(source, table, "", false, unresolvedMessage);
        this.metadataFields = metadataFields;
        this.fieldName = fieldName;
        this.queryString = queryString;
        this.indexMode = indexMode;
    }

    public Retrieve(Source source, TableIdentifier table, List<Attribute> metadataFields,  IndexMode indexMode, String unresolvedMessage) {
        this(source, table, metadataFields, indexMode, null, null, unresolvedMessage);
    }

    public List<Attribute> metadataFields() {
        return metadataFields;
    }

    public String getFieldName() { return fieldName; }

    public String getQueryString() { return queryString; }

    public IndexMode indexMode() {
        return indexMode;
    }

    @Override
    protected NodeInfo<UnresolvedRelation> info() {
        return NodeInfo.create(this, Retrieve::new, table(), metadataFields(), indexMode(), unresolvedMessage());
    }
}

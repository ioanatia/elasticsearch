/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.plan.logical;

import org.elasticsearch.common.io.stream.NamedWriteable;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;

import java.io.IOException;

public class RankDoc implements Comparable<RankDoc>, NamedWriteable {
    public final String id;
    public final String index;
    public final float score;
    public Integer rank;

    public RankDoc(String id, String index, float score) {
        this.id = id;
        this.index = index;
        this.score = score;
        this.rank = null;
    }

    @Override
    public int compareTo(RankDoc other) {
        if (score != other.score) {
            return score < other.score ? 1 : -1;
        }
        if (index.equals(other.index) == false) {
            return index.compareTo(other.index);
        }
        return id.compareTo(other.id);
    }

    public static final NamedWriteableRegistry.Entry ENTRY = new NamedWriteableRegistry.Entry(RankDoc.class, "RankDoc", RankDoc::new);

    public RankDoc(StreamInput in) throws IOException {
        this.id = in.readString();
        this.index = in.readString();
        this.score = in.readFloat();
        this.rank = in.readInt();
    }

    @Override
    public String getWriteableName() {
        return ENTRY.name;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(id);
        out.writeString(index);
        out.writeFloat(score);
        out.writeInt(rank);
    }
}

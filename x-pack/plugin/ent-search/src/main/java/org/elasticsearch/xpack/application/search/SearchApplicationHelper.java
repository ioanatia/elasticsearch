/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.application.search;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.xcontent.ConstructingObjectParser;
import org.elasticsearch.xcontent.ParseField;
import org.elasticsearch.xcontent.ToXContentObject;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentParser;

import java.io.IOException;
import java.util.Objects;

import static org.elasticsearch.xcontent.ConstructingObjectParser.optionalConstructorArg;

public class SearchApplicationHelper implements ToXContentObject, Writeable {
    public static ParseField SCRIPT_FIELD = new ParseField("script");

    private final String script;

    private static final ConstructingObjectParser<SearchApplicationHelper, Void> PARSER = new ConstructingObjectParser<>(
        "search_application_helper",
        p -> new SearchApplicationHelper((String) p[0])
    );

    public SearchApplicationHelper(String script) {
        this.script = script;
    }

    public SearchApplicationHelper(StreamInput in) throws IOException {
        this.script = in.readString();
    }

    public static Object parse(XContentParser p) {
        return PARSER.apply(p, null);
    }

    static {
        PARSER.declareString(optionalConstructorArg(), SCRIPT_FIELD);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(SCRIPT_FIELD.getPreferredName(), script);
        builder.endObject();
        return builder;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(script);
    }

    public String script() { return script; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SearchApplicationHelper helper = (SearchApplicationHelper) o;
        return script.equals(helper.script);
    }

    @Override
    public int hashCode() {
        return Objects.hash(script);
    }
}

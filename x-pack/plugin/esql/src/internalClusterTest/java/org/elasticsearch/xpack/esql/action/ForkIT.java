/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.action;

import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.test.junit.annotations.TestLogging;
import org.junit.Before;

import java.util.List;

import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;

@TestLogging(value = "org.elasticsearch.xpack.esql:TRACE,org.elasticsearch.compute:TRACE", reason = "debug")
public class ForkIT extends AbstractEsqlIntegTestCase {

    @Before
    public void setupIndex() {
        createAndPopulateIndex();
    }

    public void testSimple() {
        var query = """
            FROM test
            | WHERE id > 2
            | FORK
               [WHERE content:"fox" ],
               [WHERE content:"dog" ]
            """;
        try (var resp = run(query)) {
            System.out.println("response=" + resp);
            assertColumnNames(resp.columns(), List.of("content", "id",  "_fork"));
            assertColumnTypes(resp.columns(), List.of( "text", "integer", "keyword"));
            //assertValues(resp.values(), ...));
        }
    }

    public void testSimpleWithKeepAndSort() {
        var query = """
            FROM test
            | WHERE id > 2
            | FORK
               [WHERE content:"fox" ],
               [WHERE content:"dog" ]
            | KEEP id, content
            | SORT id
            """;

        try (var resp = run(query)) {
            System.out.println("response=" + resp);
            //assertColumnNames(resp.columns(), List.of("id", "content", "_fork"));
            //assertColumnTypes(resp.columns(), List.of("integer", "text", "keyword"));
            //assertValues(resp.values(), List.of(List.of(1), List.of(6)));
        }
    }

    private void createAndPopulateIndex() {
        var indexName = "test";
        var client = client().admin().indices();
        var CreateRequest = client.prepareCreate(indexName)
            .setSettings(Settings.builder().put("index.number_of_shards", 1))
            .setMapping("id", "type=integer", "content", "type=text");
        assertAcked(CreateRequest);
        client().prepareBulk()
            .add(new IndexRequest(indexName).id("1").source("id", 1, "content", "This is a brown fox"))
            .add(new IndexRequest(indexName).id("2").source("id", 2, "content", "This is a brown dog"))
            .add(new IndexRequest(indexName).id("3").source("id", 3, "content", "This dog is really brown"))
            .add(new IndexRequest(indexName).id("4").source("id", 4, "content", "The dog is brown but this document is very very long"))
            .add(new IndexRequest(indexName).id("5").source("id", 5, "content", "There is also a white cat"))
            .add(new IndexRequest(indexName).id("6").source("id", 6, "content", "The quick brown fox jumps over the lazy dog"))
            .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
            .get();
        ensureYellow(indexName);
    }
}

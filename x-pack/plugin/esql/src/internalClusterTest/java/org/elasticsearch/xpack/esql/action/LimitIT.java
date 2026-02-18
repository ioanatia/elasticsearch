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
import org.junit.Before;

import java.util.List;
import java.util.Map;

import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;

// @TestLogging(value = "org.elasticsearch.xpack.esql:TRACE,org.elasticsearch.compute:TRACE", reason = "debug")
public class LimitIT extends AbstractEsqlIntegTestCase {
    @Before
    public void setupIndex() {
        createAndPopulateIndices();
    }

    public void testSortAndLimit() {
        var query = """
            FROM test
            | SORT id
            | LIMIT 2 OFFSET 1
            """;

        try (var resp = run(query)) {
            assertColumnNames(resp.columns(), List.of("content", "id"));
            assertColumnTypes(resp.columns(), List.of("text", "integer"));

            Iterable<Iterable<Object>> expectedValues = List.of(List.of("This is a brown dog", 2), List.of("This dog is really brown", 3));
            assertValues(resp.values(), expectedValues);
        }
    }

    public void testLimit() {
        var query = """
            FROM test
            | EVAL id = 1
            | KEEP id
            | LIMIT 2
            """;

        try (var resp = run(query)) {
            assertColumnNames(resp.columns(), List.of("id"));
            assertColumnTypes(resp.columns(), List.of("integer"));

            Iterable<Iterable<Object>> expectedValues = List.of(List.of(1));
            assertValues(resp.values(), expectedValues);
        }
    }

    public void testWithRow() {
        var query = """
            ROW a = [1, 2, 3, 4, 5]
            | MV_EXPAND a
            """;

        try (var resp = run(query)) {
            assertColumnNames(resp.columns(), List.of("a"));
            assertColumnTypes(resp.columns(), List.of("integer"));

            Iterable<Iterable<Object>> expectedValues = List.of(List.of(1));
            assertValues(resp.values(), expectedValues);
        }
    }

    private void createAndPopulateIndices() {
        var indexName = "test";
        var client = client().admin().indices();
        var createRequest = client.prepareCreate(indexName)
            .setSettings(Settings.builder().put("index.number_of_shards", 1))
            .setMapping("id", "type=integer", "content", "type=text");
        assertAcked(createRequest);
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

        var lookupIndex = "test-lookup";
        createRequest = client.prepareCreate(lookupIndex)
            .setSettings(Settings.builder().put("index.number_of_shards", 1).put("index.mode", "lookup"))
            .setMapping("id", "type=integer", "animal", "type=keyword");
        assertAcked(createRequest);

        client().prepareBulk()
            .add(new IndexRequest(lookupIndex).id("1").source("id", 1, "animal", "fox"))
            .add(new IndexRequest(lookupIndex).id("2").source("id", 2, "animal", "dog"))
            .add(new IndexRequest(lookupIndex).id("3").source("id", 3, "animal", "dog"))
            .add(new IndexRequest(lookupIndex).id("4").source("id", 4, "animal", "dog"))
            .add(new IndexRequest(lookupIndex).id("5").source("id", 5, "animal", "cat"))
            .add(new IndexRequest(lookupIndex).id("6").source("id", 6, "animal", List.of("fox", "dog")))
            .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
            .get();
        ensureYellow(lookupIndex);

        var otherTestIndex = "test-other";

        createRequest = client.prepareCreate(otherTestIndex)
            .setSettings(Settings.builder().put("index.number_of_shards", 1))
            .setMapping("id", "type=keyword", "content", "type=keyword", "embedding", "type=sparse_vector");
        assertAcked(createRequest);
        client().prepareBulk()
            .add(
                new IndexRequest(otherTestIndex).id("1")
                    .source("id", "1", "content", "This is a brown fox", "embedding", Map.of("abc", 1.0))
            )
            .add(
                new IndexRequest(otherTestIndex).id("2")
                    .source("id", "2", "content", "This is a brown dog", "embedding", Map.of("def", 2.0))
            )
            .add(
                new IndexRequest(otherTestIndex).id("3")
                    .source("id", "3", "content", "This dog is really brown", "embedding", Map.of("ghi", 1.0))
            )
            .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
            .get();
        ensureYellow(indexName);
    }
}

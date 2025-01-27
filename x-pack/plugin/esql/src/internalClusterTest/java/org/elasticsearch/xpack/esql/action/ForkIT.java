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
import static org.elasticsearch.xpack.esql.EsqlTestUtils.getValuesList;
import static org.hamcrest.Matchers.equalTo;

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
               [WHERE content:"fox" ]
               [WHERE content:"dog" ]
            | KEEP id, _fork, content
            | SORT id, _fork
            """;
        try (var resp = run(query)) {
            assertColumnNames(resp.columns(), List.of("id", "_fork", "content"));
            assertColumnTypes(resp.columns(), List.of("integer", "keyword", "text"));
            Iterable<Iterable<Object>> expectedValues = List.of(
                List.of(3, "fork1", "This dog is really brown"),
                List.of(4, "fork1", "The dog is brown but this document is very very long"),
                List.of(6, "fork0", "The quick brown fox jumps over the lazy dog"),
                List.of(6, "fork1", "The quick brown fox jumps over the lazy dog")
            );
            assertValues(resp.values(), expectedValues);
        }
    }

    public void testSortAndLimitInFirstSubQuery() {
        var query = """
            FROM test
            | WHERE id > 0
            | FORK
               [WHERE content:"fox" | SORT id DESC | LIMIT 1 ]
               [WHERE content:"dog" ]
            | KEEP id, _fork, content
            | SORT id, _fork
            """;
        try (var resp = run(query)) {
            assertColumnNames(resp.columns(), List.of("id", "_fork", "content"));
            assertColumnTypes(resp.columns(), List.of("integer", "keyword", "text"));
            Iterable<Iterable<Object>> expectedValues = List.of(
                List.of(2, "fork1", "This is a brown dog"),
                List.of(3, "fork1", "This dog is really brown"),
                List.of(4, "fork1", "The dog is brown but this document is very very long"),
                List.of(6, "fork0", "The quick brown fox jumps over the lazy dog"),
                List.of(6, "fork1", "The quick brown fox jumps over the lazy dog")
            );
            assertValues(resp.values(), expectedValues);
        }
    }

    public void testSortAndLimitInFirstSubQueryASC() {
        var query = """
            FROM test
            | WHERE id > 0
            | FORK
               [WHERE content:"fox" | SORT id ASC | LIMIT 1 ]
               [WHERE content:"dog" ]
            | KEEP id, _fork, content
            | SORT id, _fork
            """;
        try (var resp = run(query)) {
            assertColumnNames(resp.columns(), List.of("id", "_fork", "content"));
            assertColumnTypes(resp.columns(), List.of("integer", "keyword", "text"));
            Iterable<Iterable<Object>> expectedValues = List.of(
                List.of(1, "fork0", "This is a brown fox"),
                List.of(2, "fork1", "This is a brown dog"),
                List.of(3, "fork1", "This dog is really brown"),
                List.of(4, "fork1", "The dog is brown but this document is very very long"),
                List.of(6, "fork1", "The quick brown fox jumps over the lazy dog")
            );
            assertValues(resp.values(), expectedValues);
        }
    }

    public void testSortAndLimitInSecondSubQuery() {
        var query = """
            FROM test
            | WHERE id > 2
            | FORK
               [WHERE content:"fox" ]
               [WHERE content:"dog" | SORT id DESC | LIMIT 2]
            | KEEP _fork, id, content
            | SORT _fork, id
            """;
        try (var resp = run(query)) {
            assertColumnNames(resp.columns(), List.of("_fork", "id", "content"));
            assertColumnTypes(resp.columns(), List.of("keyword", "integer", "text"));
            Iterable<Iterable<Object>> expectedValues = List.of(
                List.of("fork0", 6, "The quick brown fox jumps over the lazy dog"),
                List.of("fork1", 4, "The dog is brown but this document is very very long"),
                List.of("fork1", 6, "The quick brown fox jumps over the lazy dog")
            );
            assertValues(resp.values(), expectedValues);
        }
    }

    public void testSortAndLimitInBothSubQueries() {
        var query = """
            FROM test
            | WHERE id > 0
            | FORK
               [WHERE content:"fox" | SORT id | LIMIT 1]
               [WHERE content:"dog" | SORT id | LIMIT 1]
            | KEEP id, _fork, content
            | SORT id, _fork
            """;
        try (var resp = run(query)) {
            System.out.println("response=" + resp);
            assertColumnNames(resp.columns(), List.of("id", "_fork", "content"));
            assertColumnTypes(resp.columns(), List.of("integer", "keyword", "text"));
            Iterable<Iterable<Object>> expectedValues = List.of(
                List.of(1, "fork0", "This is a brown fox"),
                List.of(2, "fork1", "This is a brown dog")
            );
            assertValues(resp.values(), expectedValues);
        }
    }

    public void testWhereWhere() {
        var query = """
            FROM test
            | FORK
               [WHERE id < 2 | WHERE content:"fox"]
               [WHERE id > 2 | WHERE content:"dog"]
            | SORT _fork, id
            | KEEP _fork, id, content
            """;
        try (var resp = run(query)) {
            System.out.println("response=" + resp);
            assertColumnNames(resp.columns(), List.of("_fork", "id", "content"));
            assertColumnTypes(resp.columns(), List.of("keyword", "integer", "text"));
            Iterable<Iterable<Object>> expectedValues = List.of(
                List.of("fork0", 1, "This is a brown fox"),
                List.of("fork1", 3, "This dog is really brown"),
                List.of("fork1", 4, "The dog is brown but this document is very very long"),
                List.of("fork1", 6, "The quick brown fox jumps over the lazy dog")
            );
            assertValues(resp.values(), expectedValues);
        }
    }

    public void testWhereSort() {
        var query = """
            FROM test
            | FORK
               [WHERE content:"fox" | SORT id ]
               [WHERE content:"dog" | SORT id ]
            | SORT _fork, id
            | KEEP _fork, id, content
            """;
        try (var resp = run(query)) {
            System.out.println("response=" + resp);
            assertColumnNames(resp.columns(), List.of("_fork", "id", "content"));
            assertColumnTypes(resp.columns(), List.of("keyword", "integer", "text"));
            Iterable<Iterable<Object>> expectedValues = List.of(
                List.of("fork0", 1, "This is a brown fox"),
                List.of("fork0", 6, "The quick brown fox jumps over the lazy dog"),
                List.of("fork1", 2, "This is a brown dog"),
                List.of("fork1", 3, "This dog is really brown"),
                List.of("fork1", 4, "The dog is brown but this document is very very long"),
                List.of("fork1", 6, "The quick brown fox jumps over the lazy dog")
            );
            assertValues(resp.values(), expectedValues);
        }
    }

    public void testWhereSortOnlyInFork() {
        var query = """
            FROM test
            | FORK
               [WHERE content:"fox" | SORT id ]
               [WHERE content:"dog" | SORT id ]
            | KEEP _fork, id, content
            """;
        try (var resp = run(query)) {
            System.out.println("response=" + resp);
            assertColumnNames(resp.columns(), List.of("_fork", "id", "content"));
            assertColumnTypes(resp.columns(), List.of("keyword", "integer", "text"));
            Iterable<Iterable<Object>> expectedValues = List.of(
                List.of("fork0", 1, "This is a brown fox"),
                List.of("fork0", 6, "The quick brown fox jumps over the lazy dog"),
                List.of("fork1", 2, "This is a brown dog"),
                List.of("fork1", 3, "This dog is really brown"),
                List.of("fork1", 4, "The dog is brown but this document is very very long"),
                List.of("fork1", 6, "The quick brown fox jumps over the lazy dog")
            );
            assertValues(resp.values(), expectedValues);
        }
    }

    public void testLimitOnlyInSecondSubQuery() {
        var query = """
            FROM test
            | FORK
               [ WHERE content:"fox" ]
               [ LIMIT 3 ]
            | SORT _fork, id
            | KEEP _fork, id, content
            """;
        try (var resp = run(query)) {
            System.out.println("response=" + resp);
            assertColumnNames(resp.columns(), List.of("_fork", "id", "content"));
            assertColumnTypes(resp.columns(), List.of("keyword", "integer", "text"));
            Iterable<Iterable<Object>> expectedValues = List.of(
                List.of("fork0", 1, "This is a brown fox"),
                List.of("fork0", 6, "The quick brown fox jumps over the lazy dog"),
                List.of("fork1", 1, "This is a brown fox"),
                List.of("fork1", 2, "This is a brown dog"),
                List.of("fork1", 3, "This dog is really brown")
            );
            assertValues(resp.values(), expectedValues);
        }
    }

    public void testScoringKeepAndSort() {
        var query = """
            FROM test METADATA _score
            | WHERE id > 2
            | FORK
               [ WHERE content:"fox" ]
               [ WHERE content:"dog" ]
            | KEEP id, content, _fork, _score
            | SORT id
            """;
        try (var resp = run(query)) {
            System.out.println("response=" + resp);
            assertColumnNames(resp.columns(), List.of("id", "content", "_fork", "_score"));
            assertColumnTypes(resp.columns(), List.of("integer", "text", "keyword", "double"));
            assertThat(getValuesList(resp.values()).size(), equalTo(4)); // just assert that the expected number of results
        }
    }

    // TODO: this just verifies that the grammar accepts 3 subqueries
    public void testThreeSubQueries() {
        var query = """
            FROM test
            | WHERE id > 2
            | FORK
               [WHERE content:"fox" ]
               [WHERE content:"dog" ]
               [WHERE content:"cat" ]
            | KEEP id, _fork, content
            | SORT id, _fork
            """;
        try (var resp = run(query)) {
            assertColumnNames(resp.columns(), List.of("id", "_fork", "content"));
            assertColumnTypes(resp.columns(), List.of("integer", "keyword", "text"));
            // TODO ...
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

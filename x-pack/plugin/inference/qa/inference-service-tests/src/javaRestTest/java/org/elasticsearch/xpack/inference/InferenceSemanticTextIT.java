/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference;

import org.elasticsearch.client.Request;
import org.elasticsearch.common.settings.Settings;
import org.junit.Before;

import java.io.IOException;

public class InferenceSemanticTextIT extends BaseMockEISAuthServerTest {

    @Before
    public void setUp() throws Exception {
        super.setUp();
        // Ensure the mock EIS server has an authorized response ready before each test because each test will
        // use the services API which makes a call to EIS
        mockEISServer.enqueueAuthorizeAllModelsResponse();
    }

    public void testDefaultInferenceIDForSemanticText() throws IOException, IOException {
        var settings = Settings.builder().build();

        String mapping = """
                "properties": {
                  "semantic_text_field": {
                   "type": "semantic_text"
                  }
                }
            """;

        String indexName = "my-index";

        createIndex(indexName, settings, mapping);

        var a = entityAsMap(client().performRequest(new Request("GET", "/_inference")));
        var indexMapping = getIndexMappingAsMap(indexName);


    }

}

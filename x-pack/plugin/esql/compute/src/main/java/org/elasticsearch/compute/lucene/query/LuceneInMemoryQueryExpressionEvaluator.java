/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.compute.lucene.query;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.QueryBuilder;
import org.apache.lucene.util.RamUsageEstimator;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.compute.data.Block;
import org.elasticsearch.compute.data.BooleanBlock;
import org.elasticsearch.compute.data.BytesRefBlock;
import org.elasticsearch.compute.data.Page;
import org.elasticsearch.compute.expression.ExpressionEvaluator;
import org.elasticsearch.compute.operator.DriverContext;
import org.elasticsearch.core.Releasables;
import org.elasticsearch.xpack.core.common.chunks.ScoredChunk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LuceneInMemoryQueryExpressionEvaluator implements ExpressionEvaluator {
    private static final long BASE_RAM_BYTES_USED = RamUsageEstimator.shallowSizeOfInstance(LuceneInMemoryQueryExpressionEvaluator.class);

    private final ExpressionEvaluator field;
    private final String queryString;
    private final DriverContext driverContext;

    public static class Factory implements ExpressionEvaluator.Factory {
        private final ExpressionEvaluator.Factory field;
        private final String query;

        public Factory(
            ExpressionEvaluator.Factory field,
            String query
        ) {
            this.field = field;
            this.query = query;
        }


        @Override
        public ExpressionEvaluator get(DriverContext context) {
            return new LuceneInMemoryQueryExpressionEvaluator(context, field.get(context), query);
        }
    }


    public LuceneInMemoryQueryExpressionEvaluator(
        DriverContext driverContext,
        ExpressionEvaluator field,
        String query
    ) {
        this.driverContext = driverContext;
        this.field = field;
        this.queryString = query;
    }

    @Override
    public Block eval(Page page) {
        StandardAnalyzer analyzer = new StandardAnalyzer();


        try (BytesRefBlock fieldBlock = (BytesRefBlock) field.eval(page)) {
            try (BooleanBlock.Builder result = driverContext.blockFactory().newBooleanBlockBuilder(page.getPositionCount())) {
                try (Directory directory = new ByteBuffersDirectory()) {
                    IndexWriterConfig config = new IndexWriterConfig(analyzer);
                    try (IndexWriter writer = new IndexWriter(directory, config)) {
                        BytesRef scratch = new BytesRef();

                        for (int p = 0; p < fieldBlock.getPositionCount(); p++) {
                            int valueCount = fieldBlock.getValueCount(p);
                            if (valueCount == 0) { continue; }

                            int firstValueIndex = fieldBlock.getFirstValueIndex(p);
                            List<String> values = new ArrayList<>();
                            for (int i = 0; i < valueCount; i++) {
                                String value = fieldBlock.getBytesRef(firstValueIndex + i, scratch).utf8ToString();
                                values.add(value);
                            }

                            Document doc = new Document();
                            // this is probably wrong :-)
                            doc.add(new TextField("field", String.join(" ", values), Field.Store.YES));
                            doc.add(new IntField("id", p, Field.Store.YES));
                            writer.addDocument(doc);

                        }

                        writer.commit();
                    }

                    try (DirectoryReader reader = DirectoryReader.open(directory)) {
                        IndexSearcher searcher = new IndexSearcher(reader);

                        org.apache.lucene.util.QueryBuilder qb = new QueryBuilder(analyzer);

                        Query query = qb.createBooleanQuery("field", queryString, BooleanClause.Occur.SHOULD);

                        TopDocs topDocs = searcher.search(query, page.getPositionCount());

                        Set<Integer> matchingPositions = new HashSet<>();
                        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                            Document doc = reader.storedFields().document(scoreDoc.doc);
                            int id = Integer.parseInt(doc.get("id"));
                            matchingPositions.add(id);
                        }


                        for (int p = 0; p < page.getPositionCount(); p++) {
                            result.appendBoolean(matchingPositions.contains(p));
                        }
                    }

                } catch (IOException e) {
                    // probably should handle this better
                    throw new ElasticsearchException("Failed to score chunks", e);
                }


                return result.build();
            }
        }
    }

    @Override
    public long baseRamBytesUsed() {
        long baseRamBytesUsed = BASE_RAM_BYTES_USED;
        baseRamBytesUsed += field.baseRamBytesUsed();
        return baseRamBytesUsed;
    }

    @Override
    public void close() {
        Releasables.closeExpectNoException(field);
    }
}

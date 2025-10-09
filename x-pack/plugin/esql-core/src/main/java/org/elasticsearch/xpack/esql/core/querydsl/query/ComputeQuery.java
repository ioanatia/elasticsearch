/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.core.querydsl.query;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.ConstantScoreScorer;
import org.apache.lucene.search.ConstantScoreWeight;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.ScorerSupplier;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.search.Weight;
import org.elasticsearch.TransportVersion;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.SearchExecutionContext;
import org.elasticsearch.script.DocValuesDocReader;
import org.elasticsearch.search.lookup.SearchLookup;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xpack.esql.core.expression.Expression;
import org.elasticsearch.xpack.esql.core.expression.FieldAttribute;
import org.elasticsearch.xpack.esql.core.expression.FoldContext;
import org.elasticsearch.xpack.esql.core.expression.Literal;
import org.elasticsearch.xpack.esql.core.tree.Source;
import org.elasticsearch.xpack.esql.core.type.DataType;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Function;

public class ComputeQuery extends org.elasticsearch.xpack.esql.core.querydsl.query.Query {
    private final Expression expression;

    public ComputeQuery(Source source, Expression expression) {
        super(source);
        this.expression = expression;
    }

    @Override
    protected QueryBuilder asBuilder() {
        return new ComputeQueryBuilder(expression);
    }

    @Override
    protected String innerToString() {
        return "ComputeQuery";
    }

    @Override
    public boolean containsPlan() {
        return false;
    }

    static class ComputeQueryBuilder implements QueryBuilder {
        private final Expression expression;

        public ComputeQueryBuilder(Expression expression) {
            this.expression = expression;
        }

        @Override
        public org.apache.lucene.search.Query toQuery(SearchExecutionContext context) throws IOException {
            SearchLookup lookup = context.lookup();

            return new ComputeLuceneQuery(expression, lookup);
        }

        @Override
        public QueryBuilder queryName(String queryName) {
            return this;
        }

        @Override
        public String queryName() {
            return "ComputeQueryBuilder";
        }

        @Override
        public float boost() {
            return 0;
        }

        @Override
        public QueryBuilder boost(float boost) {
            return this;
        }

        @Override
        public String getName() {
            return "";
        }

        @Override
        public String getWriteableName() {
            return "";
        }

        @Override
        public TransportVersion getMinimalSupportedVersion() {
            return null;
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {

        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            return null;
        }
    }

    static class ComputeLuceneQuery extends org.apache.lucene.search.Query {

        private final Expression expression;
        private final SearchLookup lookup;

        public ComputeLuceneQuery(Expression expression, SearchLookup lookup) {
            this.expression = expression;
            this.lookup = lookup;
        }

        private boolean evaluateExpression(org.elasticsearch.search.lookup.Source docSource) {
            FoldContext foldContext = FoldContext.small();

            Expression transformed = expression.transformDown(FieldAttribute.class, fieldAttribute -> {
                String name = fieldAttribute.name();

                Object value = docSource.extractValue(name, null);

                DataType type = fieldAttribute.dataType();
                if (DataType.isString(type)) {
                    return Literal.keyword(fieldAttribute.source(), (String) value);
                }

                return new Literal(fieldAttribute.source(), value, type);
            });

            return (boolean) transformed.fold(foldContext);
        }

        @Override
        public String toString(String field) {
            return "";
        }

        @Override
        public void visit(QueryVisitor visitor) {
            visitor.visitLeaf(this);
        }

        @Override
        public boolean equals(Object obj) {
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(expression);
        }

        @Override
        public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
            return new ConstantScoreWeight(this, 0) {
                @Override
                public boolean isCacheable(LeafReaderContext ctx) {
                    return false;
                }

                @Override
                public ScorerSupplier scorerSupplier(LeafReaderContext context) throws IOException {
                    DocIdSetIterator approximation = DocIdSetIterator.all(context.reader().maxDoc());
                    final DocValuesDocReader reader = new DocValuesDocReader(lookup, context);
                    final SearchLookup finalLookup = lookup;
                    final Function<org.elasticsearch.search.lookup.Source, Boolean> expressionEvaluator = (docSource) -> evaluateExpression(
                        docSource
                    );

                    TwoPhaseIterator twoPhase = new TwoPhaseIterator(approximation) {

                        @Override
                        public boolean matches() throws IOException {
                            var docSource = finalLookup.getSource(reader.getLeafReaderContext(), approximation.docID());
                            return expressionEvaluator.apply(docSource);
                        }

                        @Override
                        public float matchCost() {
                            return 1000f;
                        }
                    };
                    Scorer scorer = new ConstantScoreScorer(score(), scoreMode, twoPhase);
                    return new DefaultScorerSupplier(scorer);
                }
            };
        }
    }
}

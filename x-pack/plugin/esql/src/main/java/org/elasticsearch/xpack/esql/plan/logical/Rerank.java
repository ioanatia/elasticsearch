/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.plan.logical;

import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.PriorityQueue;
import org.elasticsearch.common.io.stream.NamedWriteable;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.compute.data.BlockUtils;
import org.elasticsearch.compute.data.Page;
import org.elasticsearch.core.Tuple;
import org.elasticsearch.xpack.esql.core.capabilities.Resolvables;
import org.elasticsearch.xpack.esql.core.expression.Attribute;
import org.elasticsearch.xpack.esql.core.expression.Expression;
import org.elasticsearch.xpack.esql.core.expression.Literal;
import org.elasticsearch.xpack.esql.core.tree.NodeInfo;
import org.elasticsearch.xpack.esql.core.tree.Source;
import org.elasticsearch.xpack.esql.io.stream.PlanStreamInput;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Rerank extends UnaryPlan implements NamedWriteable, Phased {
    public static final NamedWriteableRegistry.Entry ENTRY = new NamedWriteableRegistry.Entry(LogicalPlan.class, "Rerank", Rerank::new);
    private final Expression limit;
    private final Filter firstQuery;
    private final Filter secondQuery;

    private final static float RANK_CONSTANT = 60;

    public Rerank(Source source, LogicalPlan child, Expression limit, Filter firstQuery, Filter secondQuery) {
        super(source, child);
        this.limit = limit;
        this.firstQuery = firstQuery;
        this.secondQuery = secondQuery;
    }

    public Rerank(StreamInput in) throws IOException {
        this(
            Source.readFrom((PlanStreamInput) in),
            in.readNamedWriteable(LogicalPlan.class),
            in.readNamedWriteable(Expression.class),
            in.readNamedWriteable(Filter.class),
            in.readNamedWriteable(Filter.class)
        );
    }

    @Override
    public LogicalPlan firstPhase() {
        return null;
    }

    @Override
    public List<LogicalPlan> firstPhases() {
        return List.of(
            // new Limit(source(), limit, new Filter(source(), child(), firstQuery.condition())),
            // new Limit(source(), limit, new Filter(source(), child(), secondQuery.condition()))
            new Limit(source(), limit, firstQuery),
            new Limit(source(), limit, secondQuery)
        );
    }

    @Override
    public LogicalPlan nextPhase(List<Tuple<List<Attribute>, List<Page>>> firstPhaseResults) {
        List<RankDoc> rankedDocuments = applyRRF(firstPhaseResults);

        LogicalPlan nextPhase = child().transformUp(EsRelation.class, esRelation -> {
            return new EsRelation(
                esRelation.source(),
                esRelation.index(),
                esRelation.output(),
                esRelation.indexMode(),
                esRelation.frozen(),
                rankedDocuments
            );
        });

        return nextPhase;
    }

    public List<RankDoc> applyRRF(List<Tuple<List<Attribute>, List<Page>>> firstPhaseResults) {
        Map<Tuple<String, String>, Map<Integer, Integer>> combined = new HashMap<>();

        Integer phaseCounter = 0;
        for (var phase : firstPhaseResults) {
            Integer counter = 0;
            for (Tuple<String, String> doc : phaseDocuments(phase.v1(), phase.v2())) {
                combined.putIfAbsent(doc, new HashMap<>());
                combined.get(doc).put(phaseCounter, counter);

                counter++;
            }
            phaseCounter++;
        }

        int windowSize = (Integer) ((Literal) limit).value();
        // put the actual limit here!
        PriorityQueue<RankDoc> queue = new PriorityQueue<RankDoc>(firstPhaseResults.size() * windowSize) {
            @Override
            protected boolean lessThan(RankDoc a, RankDoc b) {
                return a.compareTo(b) < 0;
            }
        };

        for (Tuple<String, String> doc : combined.keySet()) {
            Map<Integer, Integer> positions = combined.get(doc);
            float score = 0;

            for (Integer position : positions.keySet()) {
                score += 1.0f / (RANK_CONSTANT + position + 1);
            }
            queue.add(new RankDoc(doc.v1(), doc.v2(), score));
        }

        // return the actual list of docs after RRF!!!

        List<RankDoc> results = new ArrayList<>();
        for (int rank = 1; rank <= windowSize && queue.size() > 0; rank++) {
            RankDoc rankDoc = queue.pop();
            rankDoc.rank = rank;
            results.add(rankDoc);
        }
        return results;
    }

    private List<Tuple<String, String>> phaseDocuments(List<Attribute> attributes, List<Page> pages) {
        List<Tuple<String, String>> documents = new ArrayList<>();

        Integer idPosition = 0, indexPosition = 0;
        for (Integer counter = 0; counter < attributes.size(); counter++) {
            Attribute atr = attributes.get(counter);
            if (atr.name().equals("_id")) {
                idPosition = counter;
            }
            if (atr.name().equals("_index")) {
                indexPosition = counter;
            }
        }

        for (Page p : pages) {
            var idBlock = p.getBlock(idPosition);
            var indexBlock = p.getBlock(indexPosition);

            for (Integer counter = 0; counter < p.getPositionCount(); counter++) {
                documents.add(
                    new Tuple<>(
                        ((BytesRef) BlockUtils.toJavaObject(idBlock, counter)).utf8ToString(),
                        ((BytesRef) BlockUtils.toJavaObject(indexBlock, counter)).utf8ToString()
                    )
                );
            }
        }
        return documents;
    }

    @Override
    public String getWriteableName() {
        return ENTRY.name;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        source().writeTo(out);
        out.writeNamedWriteable(child());
        out.writeNamedWriteable(limit);
        out.writeNamedWriteable(firstQuery);
        out.writeNamedWriteable(secondQuery);
    }

    @Override
    protected NodeInfo<? extends LogicalPlan> info() {
        return NodeInfo.create(this, Rerank::new, child(), limit, firstQuery, secondQuery);
    }

    @Override
    public String commandName() {
        return "RERANK";
    }

    @Override
    public boolean expressionsResolved() {
        return Resolvables.resolved(firstQuery.expressions()) && Resolvables.resolved(secondQuery.expressions());
    }

    @Override
    public UnaryPlan replaceChild(LogicalPlan newChild) {
        return new Rerank(source(), newChild, limit, firstQuery, secondQuery);
    }

    public Expression limit() {
        return limit;
    }

    public Filter firstQuery() {
        return firstQuery;
    }

    public Filter secondQuery() {
        return secondQuery;
    }
}

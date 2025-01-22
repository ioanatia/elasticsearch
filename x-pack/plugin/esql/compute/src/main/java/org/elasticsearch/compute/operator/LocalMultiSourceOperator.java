/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.compute.operator;

import org.apache.lucene.util.BytesRef;
import org.elasticsearch.compute.data.Block;
import org.elasticsearch.compute.data.BlockFactory;
import org.elasticsearch.compute.data.DocBlock;
import org.elasticsearch.compute.data.Page;
import org.elasticsearch.core.Releasables;

import java.util.List;
import java.util.ListIterator;
import java.util.function.Supplier;
import java.util.stream.IntStream;

// TODO - name this to MergeOperator
// Also I am using AbstractPageMappingOperator which gives the input as a single page
// but maybe that's not needed.
public class LocalMultiSourceOperator implements Operator {

    private final BlockFactory blockFactory;
    private boolean finished;
    private Page prev;
    private ListIterator<Block[]> subPlanBlocks;

    public record LocalMultiSourceFactory(BlockSuppliers suppliers) implements OperatorFactory {
        @Override
        public String describe() {
            return "LocalMultiSourceOperator[suppliers=" + suppliers + "]";
        }

        @Override
        public Operator get(DriverContext driverContext) {
            return new LocalMultiSourceOperator(driverContext.blockFactory(), suppliers);
        }
    }

    private final BlockSuppliers suppliers;

    public LocalMultiSourceOperator(BlockFactory blockFactory, BlockSuppliers suppliers) {
        super();
        this.blockFactory = blockFactory;
        this.suppliers = suppliers;
        this.finished = false;
        this.prev = null;
        this.subPlanBlocks = null;
    }

    public interface BlockSuppliers extends Supplier<List<Block[]>> {};

    @Override
    public boolean needsInput() {
        return prev == null;
    }

    @Override
    public void addInput(Page page) {
        prev = page;
    }

    @Override
    public void finish() {
        finished = true;
    }

    @Override
    public boolean isFinished() {
        return finished && prev == null && subPlanBlocks.hasNext() == false;
    }

    @Override
    public Page getOutput() {
        if (subPlanBlocks == null) {
            subPlanBlocks = suppliers.get().listIterator();
        }
        if (subPlanBlocks.hasNext()) {
            int counter = subPlanBlocks.nextIndex() + 1;
            return addDiscriminatorColumn(new Page(subPlanBlocks.next()), "fork" + counter);
        }
        if (prev == null) {
            return null;
        }
        Page page = prev;
        prev = null;
        page = maybeDropDocBlock(page);
        page = addDiscriminatorColumn(page, "fork0");
        return page;
    }

    private static Page maybeDropDocBlock(Page page) {
        if (page.getBlock(0) instanceof DocBlock) {
            int[] projections = IntStream.range(1, page.getBlockCount()).toArray();
            try {
                return page.projectBlocks(projections);
            } finally {
                page.releaseBlocks();
            }
        }
        return page;
    }

    private Page addDiscriminatorColumn(Page page, String value) {
        Block discriminatorBlock = blockFactory.newConstantBytesRefBlockWith(new BytesRef(value), page.getPositionCount());
        return page.appendBlock(discriminatorBlock);
    }

    @Override
    public void close() {
        if (prev != null) {
            prev.releaseBlocks();
        }
        // The blocks are closable by the other exchange? so we have to copy them
//        if (subPlanBlocks != null) {
//            while (subPlanBlocks.hasNext()) {
//                Releasables.close(subPlanBlocks.next());
//            }
//        }
    }

    @Override
    public String toString() {
        return "LocalMultiSourceOperator[subPlanBlocks=" + subPlanBlocks + ", prev=" + prev + "]";
    }
}

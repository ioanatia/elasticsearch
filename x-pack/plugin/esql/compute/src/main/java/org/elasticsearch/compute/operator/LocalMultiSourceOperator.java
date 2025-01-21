/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.compute.operator;

import org.elasticsearch.compute.data.Block;
import org.elasticsearch.compute.data.BlockFactory;
import org.elasticsearch.compute.data.Page;

import java.util.List;
import java.util.function.Supplier;

public class LocalMultiSourceOperator extends AbstractPageMappingOperator {

    private final BlockFactory blockFactory;

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
    private boolean finished;
    private Page lastInput;

    public LocalMultiSourceOperator(BlockFactory blockFactory, BlockSuppliers suppliers) {
        super();
        this.blockFactory = blockFactory;
        this.suppliers = suppliers;
        this.finished = false;
        this.lastInput = null;
    }

    public interface BlockSuppliers extends Supplier<List<Block[]>> {};

    @Override
    protected Page process(Page page) {

        return page;
    }

    @Override
    public String toString() {
        return null;
    }

//    @Override
//    public boolean needsInput() {
//        return true;
//    }
//
//    @Override
//    public void addInput(Page page) {
//        // TODO: add input from suppliers
//        lastInput = page;
//        return;
//    }
//
//    @Override
//    public void finish() {
//        finished = true;
//    }
//
//    @Override
//    public boolean isFinished() {
//        return finished;
//    }
//
//    @Override
//    public Page getOutput() {
//        return lastInput;
//    }

    @Override
    public void close() {
    }
}

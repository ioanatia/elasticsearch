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

public class LocalMultiSourceOperator implements Operator {

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

    public LocalMultiSourceOperator(BlockFactory blockFactory, BlockSuppliers suppliers) {
        super();
        this.blockFactory = blockFactory;
        this.suppliers = suppliers;
    }

    public interface BlockSuppliers extends Supplier<List<Block[]>> {};

    @Override
    public boolean needsInput() {
        return true;
    }

    @Override
    public void addInput(Page page) {
        // TODO: add input from suppliers

        return;
    }

    @Override
    public void finish() {

    }

    @Override
    public boolean isFinished() {
        return false;
    }

    @Override
    public Page getOutput() {
        return null;
    }

    @Override
    public void close() {

    }
}

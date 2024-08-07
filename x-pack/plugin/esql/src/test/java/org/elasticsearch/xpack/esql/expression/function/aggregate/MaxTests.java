/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.expression.function.aggregate;

import com.carrotsearch.randomizedtesting.annotations.Name;
import com.carrotsearch.randomizedtesting.annotations.ParametersFactory;

import org.elasticsearch.xpack.esql.core.expression.Expression;
import org.elasticsearch.xpack.esql.core.tree.Source;
import org.elasticsearch.xpack.esql.core.type.DataType;
import org.elasticsearch.xpack.esql.expression.function.AbstractAggregationTestCase;
import org.elasticsearch.xpack.esql.expression.function.MultiRowTestCaseSupplier;
import org.elasticsearch.xpack.esql.expression.function.TestCaseSupplier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.equalTo;

public class MaxTests extends AbstractAggregationTestCase {
    public MaxTests(@Name("TestCase") Supplier<TestCaseSupplier.TestCase> testCaseSupplier) {
        this.testCase = testCaseSupplier.get();
    }

    @ParametersFactory
    public static Iterable<Object[]> parameters() {
        var suppliers = new ArrayList<TestCaseSupplier>();

        Stream.of(
            MultiRowTestCaseSupplier.intCases(1, 1000, Integer.MIN_VALUE, Integer.MAX_VALUE, true),
            MultiRowTestCaseSupplier.longCases(1, 1000, Long.MIN_VALUE, Long.MAX_VALUE, true),
            MultiRowTestCaseSupplier.doubleCases(1, 1000, -Double.MAX_VALUE, Double.MAX_VALUE, true),
            MultiRowTestCaseSupplier.dateCases(1, 1000),
            MultiRowTestCaseSupplier.booleanCases(1, 1000)
        ).flatMap(List::stream).map(MaxTests::makeSupplier).collect(Collectors.toCollection(() -> suppliers));

        suppliers.addAll(
            List.of(
                // Surrogates
                new TestCaseSupplier(
                    List.of(DataType.INTEGER),
                    () -> new TestCaseSupplier.TestCase(
                        List.of(TestCaseSupplier.TypedData.multiRow(List.of(5, 8, -2, 0, 200), DataType.INTEGER, "field")),
                        "Max[field=Attribute[channel=0]]",
                        DataType.INTEGER,
                        equalTo(200)
                    )
                ),
                new TestCaseSupplier(
                    List.of(DataType.LONG),
                    () -> new TestCaseSupplier.TestCase(
                        List.of(TestCaseSupplier.TypedData.multiRow(List.of(5L, 8L, -2L, 0L, 200L), DataType.LONG, "field")),
                        "Max[field=Attribute[channel=0]]",
                        DataType.LONG,
                        equalTo(200L)
                    )
                ),
                new TestCaseSupplier(
                    List.of(DataType.DOUBLE),
                    () -> new TestCaseSupplier.TestCase(
                        List.of(TestCaseSupplier.TypedData.multiRow(List.of(5., 8., -2., 0., 200.), DataType.DOUBLE, "field")),
                        "Max[field=Attribute[channel=0]]",
                        DataType.DOUBLE,
                        equalTo(200.)
                    )
                ),
                new TestCaseSupplier(
                    List.of(DataType.DATETIME),
                    () -> new TestCaseSupplier.TestCase(
                        List.of(TestCaseSupplier.TypedData.multiRow(List.of(5L, 8L, 2L, 0L, 200L), DataType.DATETIME, "field")),
                        "Max[field=Attribute[channel=0]]",
                        DataType.DATETIME,
                        equalTo(200L)
                    )
                ),
                new TestCaseSupplier(
                    List.of(DataType.BOOLEAN),
                    () -> new TestCaseSupplier.TestCase(
                        List.of(TestCaseSupplier.TypedData.multiRow(List.of(true, false, false, true), DataType.BOOLEAN, "field")),
                        "Max[field=Attribute[channel=0]]",
                        DataType.BOOLEAN,
                        equalTo(true)
                    )
                ),

                // Folding
                new TestCaseSupplier(
                    List.of(DataType.INTEGER),
                    () -> new TestCaseSupplier.TestCase(
                        List.of(TestCaseSupplier.TypedData.multiRow(List.of(200), DataType.INTEGER, "field")),
                        "Max[field=Attribute[channel=0]]",
                        DataType.INTEGER,
                        equalTo(200)
                    )
                ),
                new TestCaseSupplier(
                    List.of(DataType.LONG),
                    () -> new TestCaseSupplier.TestCase(
                        List.of(TestCaseSupplier.TypedData.multiRow(List.of(200L), DataType.LONG, "field")),
                        "Max[field=Attribute[channel=0]]",
                        DataType.LONG,
                        equalTo(200L)
                    )
                ),
                new TestCaseSupplier(
                    List.of(DataType.DOUBLE),
                    () -> new TestCaseSupplier.TestCase(
                        List.of(TestCaseSupplier.TypedData.multiRow(List.of(200.), DataType.DOUBLE, "field")),
                        "Max[field=Attribute[channel=0]]",
                        DataType.DOUBLE,
                        equalTo(200.)
                    )
                ),
                new TestCaseSupplier(
                    List.of(DataType.DATETIME),
                    () -> new TestCaseSupplier.TestCase(
                        List.of(TestCaseSupplier.TypedData.multiRow(List.of(200L), DataType.DATETIME, "field")),
                        "Max[field=Attribute[channel=0]]",
                        DataType.DATETIME,
                        equalTo(200L)
                    )
                ),
                new TestCaseSupplier(
                    List.of(DataType.BOOLEAN),
                    () -> new TestCaseSupplier.TestCase(
                        List.of(TestCaseSupplier.TypedData.multiRow(List.of(true), DataType.BOOLEAN, "field")),
                        "Max[field=Attribute[channel=0]]",
                        DataType.BOOLEAN,
                        equalTo(true)
                    )
                )
            )
        );

        return parameterSuppliersFromTypedDataWithDefaultChecks(suppliers);
    }

    @Override
    protected Expression build(Source source, List<Expression> args) {
        return new Max(source, args.get(0));
    }

    @SuppressWarnings("unchecked")
    private static TestCaseSupplier makeSupplier(TestCaseSupplier.TypedDataSupplier fieldSupplier) {
        return new TestCaseSupplier(fieldSupplier.name(), List.of(fieldSupplier.type()), () -> {
            var fieldTypedData = fieldSupplier.get();
            var expected = fieldTypedData.multiRowData()
                .stream()
                .map(v -> (Comparable<? super Comparable<?>>) v)
                .max(Comparator.naturalOrder())
                .orElse(null);

            return new TestCaseSupplier.TestCase(
                List.of(fieldTypedData),
                "Max[field=Attribute[channel=0]]",
                fieldSupplier.type(),
                equalTo(expected)
            );
        });
    }
}

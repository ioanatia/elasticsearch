/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.optimizer.rules.logical;

import org.elasticsearch.common.lucene.BytesRefs;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.IndexMode;
import org.elasticsearch.xpack.esql.analysis.Analyzer;
import org.elasticsearch.xpack.esql.core.expression.Alias;
import org.elasticsearch.xpack.esql.core.expression.Attribute;
import org.elasticsearch.xpack.esql.core.expression.FieldAttribute;
import org.elasticsearch.xpack.esql.core.expression.Literal;
import org.elasticsearch.xpack.esql.expression.Order;
import org.elasticsearch.xpack.esql.expression.function.EsqlFunctionRegistry;
import org.elasticsearch.xpack.esql.expression.function.scalar.convert.ToInteger;
import org.elasticsearch.xpack.esql.expression.predicate.operator.comparison.Equals;
import org.elasticsearch.xpack.esql.index.EsIndex;
import org.elasticsearch.xpack.esql.index.EsIndexGenerator;
import org.elasticsearch.xpack.esql.optimizer.AbstractLogicalPlanOptimizerTests;
import org.elasticsearch.xpack.esql.plan.logical.Enrich;
import org.elasticsearch.xpack.esql.plan.logical.EsRelation;
import org.elasticsearch.xpack.esql.plan.logical.Eval;
import org.elasticsearch.xpack.esql.plan.logical.Filter;
import org.elasticsearch.xpack.esql.plan.logical.Fork;
import org.elasticsearch.xpack.esql.plan.logical.Limit;
import org.elasticsearch.xpack.esql.plan.logical.LogicalPlan;
import org.elasticsearch.xpack.esql.plan.logical.MvExpand;
import org.elasticsearch.xpack.esql.plan.logical.OrderBy;
import org.elasticsearch.xpack.esql.plan.logical.Project;
import org.elasticsearch.xpack.esql.plan.logical.UnaryPlan;
import org.elasticsearch.xpack.esql.plan.logical.inference.Completion;
import org.elasticsearch.xpack.esql.plan.logical.inference.Rerank;
import org.elasticsearch.xpack.esql.plan.logical.join.Join;
import org.elasticsearch.xpack.esql.plan.logical.join.JoinConfig;
import org.elasticsearch.xpack.esql.plan.logical.join.JoinTypes;
import org.elasticsearch.xpack.esql.plugin.QueryPragmas;
import org.junit.BeforeClass;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import static org.elasticsearch.xpack.esql.EsqlTestUtils.TEST_VERIFIER;
import static org.elasticsearch.xpack.esql.EsqlTestUtils.as;
import static org.elasticsearch.xpack.esql.EsqlTestUtils.configuration;
import static org.elasticsearch.xpack.esql.EsqlTestUtils.emptyInferenceResolution;
import static org.elasticsearch.xpack.esql.EsqlTestUtils.getFieldAttribute;
import static org.elasticsearch.xpack.esql.EsqlTestUtils.randomLiteral;
import static org.elasticsearch.xpack.esql.EsqlTestUtils.testAnalyzerContext;
import static org.elasticsearch.xpack.esql.EsqlTestUtils.unboundLogicalOptimizerContext;
import static org.elasticsearch.xpack.esql.analysis.AnalyzerTestUtils.defaultLookupResolution;
import static org.elasticsearch.xpack.esql.analysis.AnalyzerTestUtils.indexResolutions;
import static org.elasticsearch.xpack.esql.core.tree.Source.EMPTY;
import static org.elasticsearch.xpack.esql.core.type.DataType.INTEGER;
import static org.elasticsearch.xpack.esql.core.type.DataType.KEYWORD;
import static org.elasticsearch.xpack.esql.core.type.DataType.TEXT;
import static org.elasticsearch.xpack.esql.optimizer.LocalLogicalPlanOptimizerTests.relation;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;

public class PushDownAndCombineLimitsTests extends AbstractLogicalPlanOptimizerTests {

    private static class PushDownLimitTestCase<PlanType extends LogicalPlan> {
        private final Class<PlanType> clazz;
        private final BiFunction<LogicalPlan, Attribute, PlanType> planBuilder;
        private final BiConsumer<PlanType, PlanType> planChecker;

        PushDownLimitTestCase(
            Class<PlanType> clazz,
            BiFunction<LogicalPlan, Attribute, PlanType> planBuilder,
            BiConsumer<PlanType, PlanType> planChecker
        ) {
            this.clazz = clazz;
            this.planBuilder = planBuilder;
            this.planChecker = planChecker;
        }

        public PlanType buildPlan(LogicalPlan child, Attribute attr) {
            return planBuilder.apply(child, attr);
        }

        public void checkOptimizedPlan(LogicalPlan basePlan, LogicalPlan optimizedPlan) {
            planChecker.accept(as(basePlan, clazz), as(optimizedPlan, clazz));
        }
    }

    private static final List<PushDownLimitTestCase<? extends UnaryPlan>> PUSHABLE_LIMIT_TEST_CASES = List.of(
        new PushDownLimitTestCase<>(
            Eval.class,
            (plan, attr) -> new Eval(EMPTY, plan, List.of(new Alias(EMPTY, "y", new ToInteger(EMPTY, attr)))),
            (basePlan, optimizedPlan) -> {
                assertEquals(basePlan.source(), optimizedPlan.source());
                assertEquals(basePlan.fields(), optimizedPlan.fields());
            }
        ),
        new PushDownLimitTestCase<>(
            Completion.class,
            (plan, attr) -> new Completion(EMPTY, plan, randomLiteral(KEYWORD), randomLiteral(KEYWORD), attr),
            (basePlan, optimizedPlan) -> {
                assertEquals(basePlan.source(), optimizedPlan.source());
                assertEquals(basePlan.inferenceId(), optimizedPlan.inferenceId());
                assertEquals(basePlan.prompt(), optimizedPlan.prompt());
                assertEquals(basePlan.targetField(), optimizedPlan.targetField());
            }
        ),
        new PushDownLimitTestCase<>(
            Rerank.class,
            (plan, attr) -> new Rerank(
                EMPTY,
                plan,
                randomLiteral(KEYWORD),
                randomLiteral(KEYWORD),
                randomList(1, 10, () -> new Alias(EMPTY, randomIdentifier(), randomLiteral(KEYWORD))),
                attr
            ),
            (basePlan, optimizedPlan) -> {
                assertEquals(basePlan.source(), optimizedPlan.source());
                assertEquals(basePlan.inferenceId(), optimizedPlan.inferenceId());
                assertEquals(basePlan.queryText(), optimizedPlan.queryText());
                assertEquals(basePlan.rerankFields(), optimizedPlan.rerankFields());
                assertEquals(basePlan.scoreAttribute(), optimizedPlan.scoreAttribute());
            }
        ),
        new PushDownLimitTestCase<>(
            Enrich.class,
            (plan, attr) -> new Enrich(
                EMPTY,
                plan,
                randomFrom(Enrich.Mode.ANY, Enrich.Mode.COORDINATOR),
                randomLiteral(KEYWORD),
                attr,
                null,
                Map.of(),
                List.of()
            ),
            (basePlan, optimizedPlan) -> {
                assertEquals(basePlan.source(), optimizedPlan.source());
                assertEquals(basePlan.mode(), optimizedPlan.mode());
                assertEquals(basePlan.policyName(), optimizedPlan.policyName());
                assertEquals(basePlan.matchField(), optimizedPlan.matchField());
            }
        )
    );

    private static final List<PushDownLimitTestCase<? extends UnaryPlan>> NON_PUSHABLE_LIMIT_TEST_CASES = List.of(
        new PushDownLimitTestCase<>(
            Filter.class,
            (plan, attr) -> new Filter(EMPTY, plan, new Equals(EMPTY, attr, new Literal(EMPTY, BytesRefs.toBytesRef("right"), TEXT))),
            (basePlan, optimizedPlan) -> {
                assertEquals(basePlan.source(), optimizedPlan.source());
                assertEquals(basePlan.condition(), optimizedPlan.condition());
            }
        ),
        new PushDownLimitTestCase<>(
            OrderBy.class,
            (plan, attr) -> new OrderBy(EMPTY, plan, List.of(new Order(EMPTY, attr, Order.OrderDirection.DESC, null))),
            (basePlan, optimizedPlan) -> {
                assertEquals(basePlan.source(), optimizedPlan.source());
                assertEquals(basePlan.order(), optimizedPlan.order());
            }
        )
    );
    protected static Analyzer analyzerWithoutForkImplicitLimit;

    @BeforeClass
    public static void initCustomAnalyzer() throws Exception {
        EsIndex test = EsIndexGenerator.esIndex("test", mapping, Map.of("test", IndexMode.STANDARD));
        EsIndex employees = EsIndexGenerator.esIndex("employees", mapping, Map.of("employees", IndexMode.STANDARD));

        var config = configuration(new QueryPragmas(
            Settings.builder()
                .put(QueryPragmas.FORK_IMPLICIT_LIMIT.getKey().toLowerCase(Locale.ROOT), false)
                .build()
        ));

        analyzerWithoutForkImplicitLimit = new Analyzer(
            testAnalyzerContext(
                config,
                new EsqlFunctionRegistry(),
                indexResolutions(test, employees),
                defaultLookupResolution(),
                enrichResolution,
                emptyInferenceResolution()
            ),
            TEST_VERIFIER
        );
    }

    protected LogicalPlan planWithoutForkImplicitLimit(String query) {
        var analyzed = analyzerWithoutForkImplicitLimit.analyze(parser.parseQuery(query));
        return logicalOptimizer.optimize(analyzed);
    }

    public void testPushableLimit() {
        FieldAttribute a = getFieldAttribute("a");
        FieldAttribute b = getFieldAttribute("b");
        EsRelation relation = relation().withAttributes(List.of(a, b));

        for (PushDownLimitTestCase<? extends UnaryPlan> pushableLimitTestCase : PUSHABLE_LIMIT_TEST_CASES) {
            int precedingLimitValue = randomIntBetween(1, 10_000);
            Limit precedingLimit = new Limit(EMPTY, new Literal(EMPTY, precedingLimitValue, INTEGER), relation);

            LogicalPlan pushableLimitTestPlan = pushableLimitTestCase.buildPlan(precedingLimit, a);

            int pushableLimitValue = randomIntBetween(1, 10_000);
            Limit pushableLimit = new Limit(EMPTY, new Literal(EMPTY, pushableLimitValue, INTEGER), pushableLimitTestPlan);

            LogicalPlan optimizedPlan = optimizePlan(pushableLimit);

            pushableLimitTestCase.checkOptimizedPlan(pushableLimitTestPlan, optimizedPlan);

            assertEquals(
                as(optimizedPlan, UnaryPlan.class).child(),
                new Limit(EMPTY, new Literal(EMPTY, Math.min(pushableLimitValue, precedingLimitValue), INTEGER), relation)
            );
        }
    }

    public void testNonPushableLimit() {
        FieldAttribute a = getFieldAttribute("a");
        FieldAttribute b = getFieldAttribute("b");
        EsRelation relation = relation().withAttributes(List.of(a, b));

        for (PushDownLimitTestCase<? extends UnaryPlan> nonPushableLimitTestCase : NON_PUSHABLE_LIMIT_TEST_CASES) {
            int precedingLimitValue = randomIntBetween(1, 10_000);
            Limit precedingLimit = new Limit(EMPTY, new Literal(EMPTY, precedingLimitValue, INTEGER), relation);
            UnaryPlan nonPushableLimitTestPlan = nonPushableLimitTestCase.buildPlan(precedingLimit, a);
            int nonPushableLimitValue = randomIntBetween(1, 10_000);
            Limit nonPushableLimit = new Limit(EMPTY, new Literal(EMPTY, nonPushableLimitValue, INTEGER), nonPushableLimitTestPlan);
            Limit optimizedPlan = as(optimizePlan(nonPushableLimit), Limit.class);
            nonPushableLimitTestCase.checkOptimizedPlan(nonPushableLimitTestPlan, optimizedPlan.child());
            assertEquals(
                optimizedPlan,
                new Limit(
                    EMPTY,
                    new Literal(EMPTY, Math.min(nonPushableLimitValue, precedingLimitValue), INTEGER),
                    nonPushableLimitTestPlan
                )
            );
            assertEquals(as(optimizedPlan.child(), UnaryPlan.class).child(), nonPushableLimitTestPlan.child());
        }
    }

    private static final List<PushDownLimitTestCase<? extends LogicalPlan>> DUPLICATING_TEST_CASES = List.of(
        new PushDownLimitTestCase<>(
            Enrich.class,
            (plan, attr) -> new Enrich(EMPTY, plan, Enrich.Mode.REMOTE, randomLiteral(KEYWORD), attr, null, Map.of(), List.of()),
            (basePlan, optimizedPlan) -> {
                assertEquals(basePlan.source(), optimizedPlan.source());
                assertEquals(basePlan.mode(), optimizedPlan.mode());
                assertEquals(basePlan.policyName(), optimizedPlan.policyName());
                assertEquals(basePlan.matchField(), optimizedPlan.matchField());
                var limit = as(optimizedPlan.child(), Limit.class);
                assertTrue(limit.local());
                assertFalse(limit.duplicated());
            }
        ),
        new PushDownLimitTestCase<>(MvExpand.class, (plan, attr) -> new MvExpand(EMPTY, plan, attr, attr), (basePlan, optimizedPlan) -> {
            assertEquals(basePlan.source(), optimizedPlan.source());
            assertEquals(basePlan.expanded(), optimizedPlan.expanded());
            var limit = as(optimizedPlan.child(), Limit.class);
            assertFalse(limit.local());
            assertFalse(limit.duplicated());
        }),
        new PushDownLimitTestCase<>(
            Join.class,
            (plan, attr) -> new Join(EMPTY, plan, plan, new JoinConfig(JoinTypes.LEFT, List.of(), List.of(), attr)),
            (basePlan, optimizedPlan) -> {
                assertEquals(basePlan.source(), optimizedPlan.source());
                var limit = as(optimizedPlan.left(), Limit.class);
                assertFalse(limit.local());
                assertFalse(limit.duplicated());
            }
        )

    );

    public void testPushableLimitDuplicate() {
        FieldAttribute a = getFieldAttribute("a");
        FieldAttribute b = getFieldAttribute("b");
        EsRelation relation = relation().withAttributes(List.of(a, b));

        for (PushDownLimitTestCase<? extends LogicalPlan> duplicatingTestCase : DUPLICATING_TEST_CASES) {
            int precedingLimitValue = randomIntBetween(1, 10_000);
            Limit precedingLimit = new Limit(EMPTY, new Literal(EMPTY, precedingLimitValue, INTEGER), relation);
            LogicalPlan duplicatingLimitTestPlan = duplicatingTestCase.buildPlan(precedingLimit, a);
            // Explicitly raise the probability of equal limits, to test for https://github.com/elastic/elasticsearch/issues/139250
            int upperLimitValue = randomBoolean() ? precedingLimitValue : randomIntBetween(1, precedingLimitValue);
            Limit upperLimit = new Limit(EMPTY, new Literal(EMPTY, upperLimitValue, INTEGER), duplicatingLimitTestPlan);
            Limit optimizedPlan = as(optimizePlan(upperLimit), Limit.class);
            duplicatingTestCase.checkOptimizedPlan(duplicatingLimitTestPlan, optimizedPlan.child());
            assertTrue(optimizedPlan.duplicated());
            assertFalse(optimizedPlan.local());
        }
    }

    private LogicalPlan optimizePlan(LogicalPlan plan) {
        return new PushDownAndCombineLimits().apply(plan, unboundLogicalOptimizerContext());
    }


    /**
     * <pre>{@code
     * Limit[10[INTEGER],false,false]
     * \_Fork[[_meta_field{r}#28, emp_no{r}#29, first_name{r}#30, gender{r}#31, hire_date{r}#32, job{r}#33, job.raw{r}#34, l
     * anguages{r}#35, last_name{r}#36, long_noidx{r}#37, salary{r}#38, _fork{r}#39]]
     *   |_Project[[_meta_field{f}#12, emp_no{f}#6, first_name{f}#7, gender{f}#8, hire_date{f}#13, job{f}#14, job.raw{f}#15, lang
     * uages{f}#9, last_name{f}#10, long_noidx{f}#16, salary{f}#11, _fork{r}#4]]
     *   | \_Eval[[fork1[KEYWORD] AS _fork#4]]
     *   |   \_Limit[10[INTEGER],false,false]
     *   |     \_Filter[emp_no{f}#6 > 100[INTEGER]]
     *   |       \_EsRelation[employees][_meta_field{f}#12, emp_no{f}#6, first_name{f}#7, ge..]
     *   \_Project[[_meta_field{f}#23, emp_no{f}#17, first_name{f}#18, gender{f}#19, hire_date{f}#24, job{f}#25, job.raw{f}#26, l
     * anguages{f}#20, last_name{f}#21, long_noidx{f}#27, salary{f}#22, _fork{r}#4]]
     *     \_Eval[[fork2[KEYWORD] AS _fork#4]]
     *       \_Limit[10[INTEGER],false,false]
     *         \_Filter[emp_no{f}#17 < 10[INTEGER]]
     *           \_EsRelation[employees][_meta_field{f}#23, emp_no{f}#17, first_name{f}#18, ..]
     * }</pre>
     */
    public void testPushDownLimitInFork() {
        var query = """
            from employees
             | fork (where emp_no > 100)
                    (where emp_no < 10)
             | limit 10
            """;
        var plan = randomBoolean() ? optimizedPlan(query) : planWithoutForkImplicitLimit(query);
        var limit = as(plan, Limit.class);
        assertThat(((Literal) limit.limit()).value(), equalTo(10));
        var fork = as(limit.child(), Fork.class);

        assertThat(fork.children(), hasSize(2));

        for (LogicalPlan branch : fork.children()) {
            var branchProject = as(branch, Project.class);
            var branchEval = as(branchProject.child(), Eval.class);
            var branchLimit = as(branchEval.child(), Limit.class);

            assertThat(((Literal) branchLimit.limit()).value(), equalTo(10));

            var branchFilter = as(branchLimit.child(), Filter.class);
            assertThat(branchFilter.child(), instanceOf(EsRelation.class));
        }
    }

    /**
     * <pre>{@code
     * Limit[10[INTEGER],false,false]
     * \_Fork[[_meta_field{r}#28, emp_no{r}#29, first_name{r}#30, gender{r}#31, hire_date{r}#32, job{r}#33, job.raw{r}#34, l
     * anguages{r}#35, last_name{r}#36, long_noidx{r}#37, salary{r}#38, _fork{r}#39]]
     *   |_Project[[_meta_field{f}#12, emp_no{f}#6, first_name{f}#7, gender{f}#8, hire_date{f}#13, job{f}#14, job.raw{f}#15, lang
     * uages{f}#9, last_name{f}#10, long_noidx{f}#16, salary{f}#11, _fork{r}#4]]
     *   | \_Eval[[fork1[KEYWORD] AS _fork#4]]
     *   |   \_Limit[5[INTEGER],false,false]
     *   |     \_Filter[emp_no{f}#6 > 100[INTEGER]]
     *   |       \_EsRelation[employees][_meta_field{f}#12, emp_no{f}#6, first_name{f}#7, ge..]
     *   \_Project[[_meta_field{f}#23, emp_no{f}#17, first_name{f}#18, gender{f}#19, hire_date{f}#24, job{f}#25, job.raw{f}#26, l
     * anguages{f}#20, last_name{f}#21, long_noidx{f}#27, salary{f}#22, _fork{r}#4]]
     *     \_Eval[[fork2[KEYWORD] AS _fork#4]]
     *       \_Limit[10[INTEGER],false,false]
     *         \_Filter[emp_no{f}#17 < 10[INTEGER]]
     *           \_EsRelation[employees][_meta_field{f}#23, emp_no{f}#17, first_name{f}#18, ..]
     * }</pre>
     */
    public void testPushDownLimitOnlyInOneForkBranch() {
        var query = """
            from employees
             | fork (where emp_no > 100 | limit 5)
                    (where emp_no < 10 | limit 100)
             | limit 10
            """;
        var plan = randomBoolean() ? optimizedPlan(query) : planWithoutForkImplicitLimit(query);
        var limit = as(plan, Limit.class);
        assertThat(((Literal) limit.limit()).value(), equalTo(10));
        var fork = as(limit.child(), Fork.class);

        assertThat(fork.children(), hasSize(2));

        // first branch
        var firstBranchProject = as(fork.children().getFirst(), Project.class);
        var firstBranchEval = as(firstBranchProject.child(), Eval.class);
        var firstBranchLimit = as(firstBranchEval.child(), Limit.class);
        // Limit stays the same
        assertThat(((Literal) firstBranchLimit.limit()).value(), equalTo(5));

        var firstBranchFilter = as(firstBranchLimit.child(), Filter.class);
        assertThat(firstBranchFilter.child(), instanceOf(EsRelation.class));

        // second branch
        var secondBranchProject = as(fork.children().get(1), Project.class);
        var secondBranchEval = as(secondBranchProject.child(), Eval.class);
        var secondBranchLimit = as(secondBranchEval.child(), Limit.class);
        // Limit outside of fork was pushed down
        assertThat(((Literal) secondBranchLimit.limit()).value(), equalTo(10));

        var secondBranchFilter = as(secondBranchLimit.child(), Filter.class);
        assertThat(secondBranchFilter.child(), instanceOf(EsRelation.class));
    }

    /**
     * <pre>{@code
     * Limit[10[INTEGER],true,false]
     * \_MvExpand[emp_no{r}#33,emp_no{r}#44]
     *   \_Eval[[1[INTEGER] AS x#7]]
     *     \_Limit[10[INTEGER],false,false]
     *       \_Fork[[_meta_field{r}#32, emp_no{r}#33, first_name{r}#34, gender{r}#35, hire_date{r}#36, job{r}#37, job.raw{r}#38, l
     * anguages{r}#39, last_name{r}#40, long_noidx{r}#41, salary{r}#42, _fork{r}#43]]
     *         |_Project[[_meta_field{f}#16, emp_no{f}#10, first_name{f}#11, gender{f}#12, hire_date{f}#17, job{f}#18, job.raw{f}#19, l
     * anguages{f}#13, last_name{f}#14, long_noidx{f}#20, salary{f}#15, _fork{r}#4]]
     *         | \_Eval[[fork1[KEYWORD] AS _fork#4]]
     *         |   \_Limit[10[INTEGER],false,false]
     *         |     \_Filter[emp_no{f}#10 > 100[INTEGER]]
     *         |       \_EsRelation[employees][_meta_field{f}#16, emp_no{f}#10, first_name{f}#11, ..]
     *         \_Project[[_meta_field{f}#27, emp_no{f}#21, first_name{f}#22, gender{f}#23, hire_date{f}#28, job{f}#29, job.raw{f}#30, l
     * anguages{f}#24, last_name{f}#25, long_noidx{f}#31, salary{f}#26, _fork{r}#4]]
     *           \_Eval[[fork2[KEYWORD] AS _fork#4]]
     *             \_Limit[10[INTEGER],false,false]
     *               \_Filter[emp_no{f}#21 < 10[INTEGER]]
     *                 \_EsRelation[employees][_meta_field{f}#27, emp_no{f}#21, first_name{f}#22, ..]
     * }</pre>
     */
    public void testPushDownLimitInForkPastEvalAndMvExpand() {
        var query = """
            from employees
             | fork (where emp_no > 100 | LIMIT 500)
                    (where emp_no < 10 | LIMIT 500)
             | eval x = 1
             | mv_expand emp_no
             | limit 10
            """;
        var plan = randomBoolean() ? optimizedPlan(query) : planWithoutForkImplicitLimit(query);
        var limit = as(plan, Limit.class);
        assertThat(((Literal) limit.limit()).value(), equalTo(10));

        var mvExpand = as(limit.child(), MvExpand.class);
        var eval = as(mvExpand.child(), Eval.class);

        var mvExpandLimit = as(eval.child(), Limit.class);
        assertThat(((Literal) mvExpandLimit.limit()).value(), equalTo(10));

        var fork = as(mvExpandLimit.child(), Fork.class);
        assertThat(fork.children(), hasSize(2));

        for (LogicalPlan branch : fork.children()) {
            var branchProject = as(branch, Project.class);
            var branchEval = as(branchProject.child(), Eval.class);
            var branchLimit = as(branchEval.child(), Limit.class);

            assertThat(((Literal) branchLimit.limit()).value(), equalTo(10));

            var branchFilter = as(branchLimit.child(), Filter.class);
            assertThat(branchFilter.child(), instanceOf(EsRelation.class));
        }
    }
}

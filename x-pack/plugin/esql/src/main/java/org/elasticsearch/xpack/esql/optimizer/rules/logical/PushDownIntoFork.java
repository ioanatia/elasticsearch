/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.optimizer.rules.logical;

import org.elasticsearch.xpack.esql.core.expression.Attribute;
import org.elasticsearch.xpack.esql.core.expression.Expression;
import org.elasticsearch.xpack.esql.core.expression.NamedExpression;
import org.elasticsearch.xpack.esql.core.expression.ReferenceAttribute;
import org.elasticsearch.xpack.esql.optimizer.LogicalOptimizerContext;
import org.elasticsearch.xpack.esql.plan.logical.Filter;
import org.elasticsearch.xpack.esql.plan.logical.Fork;
import org.elasticsearch.xpack.esql.plan.logical.LogicalPlan;
import org.elasticsearch.xpack.esql.plan.logical.MvExpand;
import org.elasticsearch.xpack.esql.plan.logical.UnaryPlan;
import org.elasticsearch.xpack.esql.plan.logical.UnionAll;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PushDownIntoFork extends OptimizerRules.ParameterizedOptimizerRule<LogicalPlan, LogicalOptimizerContext> {
    public PushDownIntoFork() {
        super(OptimizerRules.TransformDirection.DOWN);
    }

    @Override
    protected LogicalPlan rule(LogicalPlan plan, LogicalOptimizerContext context) {
        if (context.configuration().pragmas().forkImplicitLimit()) {
            return plan;
        }
        if (false == (plan instanceof UnaryPlan unaryPlan
            && unaryPlan.child() instanceof Fork
            && unaryPlan.child() instanceof UnionAll == false)) {
            return plan;
        }

        if (plan instanceof Filter filter) {
            return pushDownFilterIntoFork(filter);
        } else if (plan instanceof MvExpand mvExpand) {
            return pushDownMvExpandIntoFork(mvExpand);
        } else {
            return plan;
        }
    }

    private static LogicalPlan pushDownMvExpandIntoFork(MvExpand mvExpand) {
        Fork fork = (Fork) mvExpand.child();
        List<LogicalPlan> newForkChildren = new ArrayList<>();
        for (LogicalPlan forkChild : fork.children()) {
            LogicalPlan newForkChild = pushDownMvExpandIntoForkBranch(fork, forkChild, mvExpand);
            newForkChildren.add(newForkChild);
        }

        List<Attribute> newOutput = new ArrayList<>(fork.output());
        newOutput.removeIf(o -> o.name().equals(mvExpand.expanded().name()));
        newOutput.add(mvExpand.expanded());
        return new Fork(fork.source(), newForkChildren, newOutput);
    }

    private static LogicalPlan pushDownFilterIntoFork(Filter filter) {
        Fork fork = (Fork) filter.child();
        List<LogicalPlan> newForkChildren = new ArrayList<>();
        for (LogicalPlan forkChild : fork.children()) {
            LogicalPlan newForkChild = pushDownFilterIntoForkBranch(fork, forkChild, filter);
            newForkChildren.add(newForkChild);
        }

        return fork.replaceChildren(newForkChildren);
    }

    private static LogicalPlan pushDownFilterIntoForkBranch(Fork fork, LogicalPlan forkChild, Filter filter) {
        Map<Expression, Expression> outputMap = PushDownUtils.outputMap(fork, forkChild);

        Expression newFilterExpression = filter.condition().transformDown(exp -> {
            if (outputMap.containsKey(exp)) {
                return outputMap.get(exp);
            }
            return exp;
        });

        return new Filter(filter.source(), forkChild, newFilterExpression);
    }

    private static LogicalPlan pushDownMvExpandIntoForkBranch(Fork fork, LogicalPlan forkChild, MvExpand mvExpand) {
        Map<Expression, Expression> outputMap = PushDownUtils.outputMap(fork, forkChild);

        NamedExpression newTarget = (NamedExpression) mvExpand.target().transformDown(exp -> {
            if (outputMap.containsKey(exp)) {
                return outputMap.get(exp);
            }
            return exp;
        });

        Attribute oldExpanded = mvExpand.expanded();
        Attribute newExpanded = new ReferenceAttribute(
            oldExpanded.source(),
            oldExpanded.qualifier(),
            oldExpanded.name(),
            oldExpanded.dataType(),
            oldExpanded.nullable(),
            null,
            false
        );

        return new MvExpand(mvExpand.source(), forkChild, newTarget, newExpanded);
    }
}

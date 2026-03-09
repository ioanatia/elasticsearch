/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.optimizer.rules.logical;

import org.elasticsearch.test.junit.annotations.TestLogging;
import org.elasticsearch.xpack.esql.optimizer.AbstractLogicalPlanOptimizerTests;

@TestLogging(value = "org.elasticsearch.xpack.esql:TRACE", reason = "debug")
public class PushDownIntoForkTests extends AbstractLogicalPlanOptimizerTests {

    public void testSingleFilterPushDown() {
        var query = """
            from employees
             | fork (where emp_no > 100)
                    (where emp_no < 10)
             | where salary > 10
             | sort emp_no
             | limit 10
            """;
        var plan = planWithoutForkImplicitLimit(query);
    }

    public void testMvExpandPushDown() {
        var query = """
            from employees
             | fork (where emp_no > 100)
                    (where emp_no < 10)
             | mv_expand languages
             | sort emp_no
             | limit 10
            """;
        var plan = planWithoutForkImplicitLimit(query);
    }

    public void testMultiplePushDown() {
        var query = """
            from employees
             | fork (where emp_no > 100)
                    (where emp_no < 10)
             | where _fork == "fork1"
             | mv_expand languages
             | sort emp_no
             | limit 10
            """;
        var plan = planWithoutForkImplicitLimit(query);
    }
}

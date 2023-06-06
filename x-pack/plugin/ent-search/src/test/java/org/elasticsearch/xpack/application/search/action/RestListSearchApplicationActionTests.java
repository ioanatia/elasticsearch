/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.application.search.action;

import org.elasticsearch.license.XPackLicenseState;
import org.elasticsearch.test.rest.FakeRestRequest;

public class RestListSearchApplicationActionTests extends AbstractRestSearchApplicationActionTests {
    public void testWithNonCompliantLicense() throws Exception {
        checkLicenseForRequest(new FakeRestRequest());
    }

    @Override
    protected SearchApplicationRestHandler getRestAction(XPackLicenseState licenseState) {
        return new RestListSearchApplicationAction(licenseState);
    }
}

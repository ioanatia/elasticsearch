/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.application.roleprovider;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.cluster.metadata.IndexAbstraction;
import org.elasticsearch.xpack.core.security.action.user.GetUserPrivilegesResponse;
import org.elasticsearch.xpack.core.security.authc.Authentication;
import org.elasticsearch.xpack.core.security.authc.Subject;
import org.elasticsearch.xpack.core.security.authz.AuthorizationEngine;
import org.elasticsearch.xpack.core.security.authz.ResolvedIndices;
import org.elasticsearch.xpack.core.security.authz.RoleDescriptor;
import org.elasticsearch.xpack.core.security.authz.accesscontrol.IndicesAccessControl;
import org.elasticsearch.xpack.core.security.authz.permission.ResourcePrivileges;
import org.elasticsearch.xpack.core.security.authz.privilege.ApplicationPrivilegeDescriptor;
import org.elasticsearch.xpack.core.security.authz.privilege.ConfigurableClusterPrivilege;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ApplicationAuthorizationEngine implements AuthorizationEngine {
    @Override
    public void resolveAuthorizationInfo(RequestInfo requestInfo, ActionListener<AuthorizationInfo> listener) {
        final Authentication authentication = requestInfo.getAuthentication();
        listener.onResponse(new ApplicationAuthorizationInfo(authentication.getEffectiveSubject().getUser().roles()));
    }

    @Override
    public void resolveAuthorizationInfo(Subject subject, ActionListener<AuthorizationInfo> listener) {
        listener.onResponse(new ApplicationAuthorizationInfo(subject.getUser().roles()));
    }

    @Override
    public void authorizeRunAs(RequestInfo requestInfo, AuthorizationInfo authorizationInfo, ActionListener<AuthorizationResult> listener) {
        listener.onResponse(AuthorizationResult.deny());
    }

    @Override
    public void authorizeClusterAction(
        RequestInfo requestInfo,
        AuthorizationInfo authorizationInfo,
        ActionListener<AuthorizationResult> listener
    ) {
        listener.onResponse(AuthorizationResult.deny());
    }

    @Override
    public void authorizeIndexAction(
        RequestInfo requestInfo,
        AuthorizationInfo authorizationInfo,
        AsyncSupplier<ResolvedIndices> indicesAsyncSupplier,
        Map<String, IndexAbstraction> aliasOrIndexLookup,
        ActionListener<IndexAuthorizationResult> listener
    ) {
        listener.onResponse(new IndexAuthorizationResult(IndicesAccessControl.DENIED));
    }

    @Override
    public void loadAuthorizedIndices(
        RequestInfo requestInfo,
        AuthorizationInfo authorizationInfo,
        Map<String, IndexAbstraction> indicesLookup,
        ActionListener<AuthorizedIndices> listener
    ) {
        listener.onResponse(new AuthorizedIndices() {
            public Supplier<Set<String>> all() {
                return () -> Set.of();
            }

            public boolean check(String name) {
                return false;
            }
        });
    }

    @Override
    public void validateIndexPermissionsAreSubset(
        RequestInfo requestInfo,
        AuthorizationInfo authorizationInfo,
        Map<String, List<String>> indexNameToNewNames,
        ActionListener<AuthorizationResult> listener
    ) {
        listener.onResponse(AuthorizationResult.deny());
    }

    @Override
    public void checkPrivileges(
        AuthorizationInfo authorizationInfo,
        PrivilegesToCheck privilegesToCheck,
        Collection<ApplicationPrivilegeDescriptor> applicationPrivilegeDescriptors,
        ActionListener<PrivilegesCheckResult> listener
    ) {
        listener.onResponse(getPrivilegesCheckResult(privilegesToCheck, false));
    }

    @Override
    public void getUserPrivileges(AuthorizationInfo authorizationInfo, ActionListener<GetUserPrivilegesResponse> listener) {
        listener.onResponse(getUserPrivilegesResponse());
    }

    private GetUserPrivilegesResponse getUserPrivilegesResponse() {
        final Set<String> cluster = Collections.emptySet();
        final Set<ConfigurableClusterPrivilege> conditionalCluster = Collections.emptySet();
        // TODO: change indices
        final Set<GetUserPrivilegesResponse.Indices> indices = Collections.emptySet();
        final Set<RoleDescriptor.ApplicationResourcePrivileges> application = Collections.emptySet();
        final Set<String> runAs = Collections.emptySet();

        return new GetUserPrivilegesResponse(cluster, conditionalCluster, indices, application, runAs, Set.of());
    }

    private PrivilegesCheckResult getPrivilegesCheckResult(PrivilegesToCheck privilegesToCheck, boolean authorized) {
        Map<String, Boolean> clusterPrivMap = new HashMap<>();
        for (String clusterPriv : privilegesToCheck.cluster()) {
            clusterPrivMap.put(clusterPriv, authorized);
        }
        final Map<String, ResourcePrivileges> indices = new LinkedHashMap<>();
        for (RoleDescriptor.IndicesPrivileges check : privilegesToCheck.index()) {
            for (String index : check.getIndices()) {
                final Map<String, Boolean> privileges = new HashMap<>();
                final ResourcePrivileges existing = indices.get(index);
                if (existing != null) {
                    privileges.putAll(existing.getPrivileges());
                }
                for (String privilege : check.getPrivileges()) {
                    privileges.put(privilege, authorized);
                }
                indices.put(index, ResourcePrivileges.builder(index).addPrivileges(privileges).build());
            }
        }
        final Map<String, Collection<ResourcePrivileges>> privilegesByApplication = new HashMap<>();
        Set<String> applicationNames = Arrays.stream(privilegesToCheck.application())
            .map(RoleDescriptor.ApplicationResourcePrivileges::getApplication)
            .collect(Collectors.toSet());
        for (String applicationName : applicationNames) {
            final Map<String, ResourcePrivileges> appPrivilegesByResource = new LinkedHashMap<>();
            for (RoleDescriptor.ApplicationResourcePrivileges p : privilegesToCheck.application()) {
                if (applicationName.equals(p.getApplication())) {
                    for (String resource : p.getResources()) {
                        final Map<String, Boolean> privileges = new HashMap<>();
                        final ResourcePrivileges existing = appPrivilegesByResource.get(resource);
                        if (existing != null) {
                            privileges.putAll(existing.getPrivileges());
                        }
                        for (String privilege : p.getPrivileges()) {
                            privileges.put(privilege, authorized);
                        }
                        appPrivilegesByResource.put(resource, ResourcePrivileges.builder(resource).addPrivileges(privileges).build());
                    }
                }
            }
            privilegesByApplication.put(applicationName, appPrivilegesByResource.values());
        }
        return new PrivilegesCheckResult(authorized, new PrivilegesCheckResult.Details(clusterPrivMap, indices, privilegesByApplication));
    }

    public class ApplicationAuthorizationInfo implements AuthorizationInfo {
        private final String[] roles;

        @Override
        public Map<String, Object> asMap() {
            return Collections.singletonMap("roles", roles);
        }

        ApplicationAuthorizationInfo(String[] roles) {
            this.roles = roles;
        }
    }

}

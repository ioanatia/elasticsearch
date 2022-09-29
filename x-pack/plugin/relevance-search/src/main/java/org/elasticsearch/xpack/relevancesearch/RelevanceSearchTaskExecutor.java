/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.relevancesearch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.ResourceAlreadyExistsException;
import org.elasticsearch.Version;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.support.ActiveShardCount;
import org.elasticsearch.client.internal.Client;
import org.elasticsearch.cluster.ClusterChangedEvent;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.cluster.ClusterStateListener;
import org.elasticsearch.cluster.metadata.MetadataCreateIndexService;
import org.elasticsearch.gateway.GatewayService;
import org.elasticsearch.indices.SystemIndexDescriptor;
import org.elasticsearch.persistent.AllocatedPersistentTask;
import org.elasticsearch.persistent.PersistentTaskState;
import org.elasticsearch.persistent.PersistentTasksCustomMetadata;
import org.elasticsearch.persistent.PersistentTasksExecutor;
import org.elasticsearch.persistent.PersistentTasksService;
import org.elasticsearch.tasks.TaskId;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.client.internal.Client;
import org.elasticsearch.transport.RemoteTransportException;

import java.util.Map;

import static org.elasticsearch.xpack.core.ClientHelper.executeAsyncWithOrigin;


public class RelevanceSearchTaskExecutor extends PersistentTasksExecutor<RelevanceSearchTaskParams> implements ClusterStateListener {
    private static final Logger logger = LogManager.getLogger(RelevanceSearchTaskExecutor.class);
    private final ClusterService clusterService;
    private final PersistentTasksService persistentTasksService;

    private final Client client;


    protected RelevanceSearchTaskExecutor(Client client, ClusterService clusterService, ThreadPool threadPool) {
        super( "relevance-search", ThreadPool.Names.GENERIC);
        clusterService.addListener(this);
        this.clusterService = clusterService;
        this.client = client;
        this.persistentTasksService = new PersistentTasksService(clusterService, threadPool, client);
    }

    @Override
    protected RelevanceSearchTask createTask(
        long id,
        String type,
        String action,
        TaskId parentTaskId,
        PersistentTasksCustomMetadata.PersistentTask<RelevanceSearchTaskParams> taskInProgress,
        Map<String, String> headers
    ) {
        return new RelevanceSearchTask(
            id, type, action, "description", parentTaskId, headers
        );
    }

    @Override
    public void clusterChanged(ClusterChangedEvent event) {
        if (event.state().blocks().hasGlobalBlock(GatewayService.STATE_NOT_RECOVERED_BLOCK)) {
            // wait for state recovered
            return;
        }

        DiscoveryNode masterNode = event.state().nodes().getMasterNode();
        if (masterNode == null || masterNode.getVersion().before(Version.V_7_14_0)) {
            // wait for master to be upgraded so it understands geoip task
            return;
        }

        clusterService.removeListener(this);

        persistentTasksService.sendStartRequest(
            "relevance-search",
            "relevance-search",
            new RelevanceSearchTaskParams(),
            ActionListener.wrap(r -> logger.debug("Started relevance search task"), e -> {
                Throwable t = e instanceof RemoteTransportException ? e.getCause() : e;
                if (t instanceof ResourceAlreadyExistsException == false) {
                    logger.error("failed relevance search task", e);
                }
            })
        );
    }

    @Override
    protected void nodeOperation(AllocatedPersistentTask task, RelevanceSearchTaskParams params, PersistentTaskState state) {
        logger.info("Creating relevance settings system index");
        createSystemIndex();
    }

    private void createSystemIndex() {
        SystemIndexDescriptor descriptor = RelevanceSettingsIndexManager.getSystemIndexDescriptor();

        CreateIndexRequest request = new CreateIndexRequest(descriptor.getPrimaryIndex())
            .origin(descriptor.getOrigin())
            .mapping(descriptor.getMappings())
            .settings(descriptor.getSettings())
            .waitForActiveShards(ActiveShardCount.ALL);
        executeAsyncWithOrigin(
            client.threadPool().getThreadContext(),
            descriptor.getOrigin(),
            request,
            new ActionListener<CreateIndexResponse>() {
                @Override
                public void onResponse(CreateIndexResponse createIndexResponse) {
                }

                @Override
                public void onFailure(Exception e) {
                    logger.info("Failed to create system index " + e.toString());
                }
            },
            client.admin().indices()::create
        );
    }
}

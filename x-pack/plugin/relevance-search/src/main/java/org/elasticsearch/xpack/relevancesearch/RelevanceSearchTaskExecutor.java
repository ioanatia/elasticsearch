/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.relevancesearch;
import org.elasticsearch.cluster.ClusterChangedEvent;
import org.elasticsearch.cluster.ClusterStateListener;
import org.elasticsearch.persistent.AllocatedPersistentTask;
import org.elasticsearch.persistent.PersistentTaskState;
import org.elasticsearch.persistent.PersistentTasksExecutor;
import org.elasticsearch.threadpool.ThreadPool;


public class RelevanceSearchTaskExecutor extends PersistentTasksExecutor<RelevanceSearchTaskParams> implements ClusterStateListener {

    protected RelevanceSearchTaskExecutor() {
        super( "RELEVANCE_SEARCH", ThreadPool.Names.GENERIC);
    }

    @Override
    public void clusterChanged(ClusterChangedEvent event) {

    }

    @Override
    protected void nodeOperation(AllocatedPersistentTask task, RelevanceSearchTaskParams params, PersistentTaskState state) {

    }
}

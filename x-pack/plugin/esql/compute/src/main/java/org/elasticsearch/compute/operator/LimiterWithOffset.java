/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.compute.operator;

import java.util.concurrent.atomic.AtomicInteger;

public class LimiterWithOffset {
    private final int limit;
    private final int offset;
    private final AtomicInteger collected = new AtomicInteger();

    public LimiterWithOffset(int limit, int offset) {
        this.limit = limit + offset;
        this.offset = offset;
    }

    /**
     * Returns the remaining number of hits that can be collected.
     */
    public int remaining() {
        final int remaining = limit - collected.get();
        assert remaining >= 0 : remaining;
        return remaining;
    }

    /**
     * Returns the limit of this limiter.
     */
    public int limit() {
        return limit;
    }

    /**
     * Tries to accumulate hits and returns the number of hits that has been accepted.
     *
     * @param numHits the number of hits to try to accumulate
     * @return the accepted number of hits. If the returned number is less than the numHits,
     * it means the limit has been reached and the difference can be discarded.
     */
    public LimiterStatus tryAccumulateHits(int numHits) {
        int current = collected.get();
        int skipped = Math.max(0, Math.min(offset - current, numHits));
        int available = Math.min(numHits - skipped, remaining());
        int rejected = numHits - skipped - available;
        collected.addAndGet(skipped + available);
        return new LimiterStatus(available, skipped, rejected);
    }

    /**
     * Result returned by @tryAccumulateHits.
     * @param accepted how many rows were accepted.
     * @param offset how many rows from the beginning of the input were skipped.
     * @param rejected how many rows from the end of the input were rejected.
     */
    public record LimiterStatus(int accepted, int offset, int rejected) {}
}

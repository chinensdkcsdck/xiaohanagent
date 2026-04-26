package com.atguigu.java.ai.langchain4j.lock;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

public class MetricsDistributedLockClient implements DistributedLockClient {

    private final String lockType;
    private final DistributedLockClient delegate;
    private final MeterRegistry meterRegistry;

    public MetricsDistributedLockClient(String lockType, DistributedLockClient delegate, MeterRegistry meterRegistry) {
        this.lockType = lockType;
        this.delegate = delegate;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public boolean tryLock(String lockKey, long waitMs, long leaseMs) {
        boolean ok = false;
        try {
            ok = delegate.tryLock(lockKey, waitMs, leaseMs);
            return ok;
        } finally {
            count("app.lock.acquire", ok ? "success" : "fail");
        }
    }

    @Override
    public void unlock(String lockKey) {
        try {
            delegate.unlock(lockKey);
            count("app.lock.release", "success");
        } catch (RuntimeException ex) {
            count("app.lock.release", "fail");
        }
    }

    private void count(String metric, String status) {
        Counter.builder(metric)
                .tag("type", lockType)
                .tag("status", status)
                .register(meterRegistry)
                .increment();
    }
}

package com.atguigu.java.ai.langchain4j.lock;

public interface DistributedLockClient {

    boolean tryLock(String lockKey, long waitMs, long leaseMs);

    void unlock(String lockKey);
}

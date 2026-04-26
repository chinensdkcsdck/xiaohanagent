package com.atguigu.java.ai.langchain4j.lock;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

public class RedisDistributedLockClient implements DistributedLockClient {

    private final RedissonClient redissonClient;

    public RedisDistributedLockClient(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public boolean tryLock(String lockKey, long waitMs, long leaseMs) {
        try {
            RLock lock = redissonClient.getLock(lockKey);
            return lock.tryLock(waitMs, leaseMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void unlock(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}

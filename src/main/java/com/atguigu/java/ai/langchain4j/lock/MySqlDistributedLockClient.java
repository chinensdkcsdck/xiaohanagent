package com.atguigu.java.ai.langchain4j.lock;

import com.atguigu.java.ai.langchain4j.mapper.DbLockMapper;

public class MySqlDistributedLockClient implements DistributedLockClient {

    private final DbLockMapper dbLockMapper;

    public MySqlDistributedLockClient(DbLockMapper dbLockMapper) {
        this.dbLockMapper = dbLockMapper;
    }

    @Override
    public boolean tryLock(String lockKey, long waitMs, long leaseMs) {
        int timeoutSeconds = (int) Math.max(1, waitMs / 1000);
        Integer locked = dbLockMapper.acquireLock(lockKey, timeoutSeconds);
        return locked != null && locked == 1;
    }

    @Override
    public void unlock(String lockKey) {
        dbLockMapper.releaseLock(lockKey);
    }
}

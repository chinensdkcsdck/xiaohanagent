package com.atguigu.java.ai.langchain4j.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.lock")
public class LockProperties {

    private String type = "mysql";
    private long waitMs = 200;
    private long leaseMs = 5000;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public long getWaitMs() {
        return waitMs;
    }

    public void setWaitMs(long waitMs) {
        this.waitMs = waitMs;
    }

    public long getLeaseMs() {
        return leaseMs;
    }

    public void setLeaseMs(long leaseMs) {
        this.leaseMs = leaseMs;
    }
}

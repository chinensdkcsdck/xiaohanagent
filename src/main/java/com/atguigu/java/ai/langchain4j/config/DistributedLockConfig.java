package com.atguigu.java.ai.langchain4j.config;

import com.atguigu.java.ai.langchain4j.lock.DistributedLockClient;
import com.atguigu.java.ai.langchain4j.lock.MetricsDistributedLockClient;
import com.atguigu.java.ai.langchain4j.lock.MySqlDistributedLockClient;
import com.atguigu.java.ai.langchain4j.lock.RedisDistributedLockClient;
import com.atguigu.java.ai.langchain4j.mapper.DbLockMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DistributedLockConfig {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "app.lock", name = "type", havingValue = "redis")
    public RedissonClient redissonClient(RedisProperties redisProperties) {
        Config config = new Config();
        String host = redisProperties.getHost() == null ? "localhost" : redisProperties.getHost();
        int port = redisProperties.getPort() > 0 ? redisProperties.getPort() : 6379;
        String address = "redis://" + host + ":" + port;

        SingleServerConfig single = config.useSingleServer().setAddress(address);
        if (redisProperties.getPassword() != null && !redisProperties.getPassword().isEmpty()) {
            single.setPassword(redisProperties.getPassword());
        }
        single.setDatabase(redisProperties.getDatabase());
        return Redisson.create(config);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.lock", name = "type", havingValue = "redis")
    public DistributedLockClient redisDistributedLockClient(RedissonClient redissonClient,
                                                            MeterRegistry meterRegistry) {
        return new MetricsDistributedLockClient(
                "redis",
                new RedisDistributedLockClient(redissonClient),
                meterRegistry
        );
    }

    @Bean
    @ConditionalOnMissingBean(DistributedLockClient.class)
    public DistributedLockClient mySqlDistributedLockClient(DbLockMapper dbLockMapper,
                                                            MeterRegistry meterRegistry) {
        return new MetricsDistributedLockClient(
                "mysql",
                new MySqlDistributedLockClient(dbLockMapper),
                meterRegistry
        );
    }
}

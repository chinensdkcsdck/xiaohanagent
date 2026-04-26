package com.atguigu.java.ai.langchain4j.mq;

import com.atguigu.java.ai.langchain4j.config.MqProperties;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true")
public class RabbitListenerConfig {

    @Bean(name = "bookingListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory bookingListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MqProperties mqProperties) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxAttempts(Math.max(1, mqProperties.getListenerMaxAttempts()))
                .backOffOptions(
                        Math.max(100, mqProperties.getListenerInitialIntervalMs()),
                        Math.max(1.0d, mqProperties.getListenerMultiplier()),
                        Math.max(1000, mqProperties.getListenerMaxIntervalMs())
                )
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        return factory;
    }
}

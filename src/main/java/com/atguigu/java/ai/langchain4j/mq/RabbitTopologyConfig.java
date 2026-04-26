package com.atguigu.java.ai.langchain4j.mq;

import com.atguigu.java.ai.langchain4j.config.MqProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true")
public class RabbitTopologyConfig {

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public DirectExchange bookingExchange(MqProperties mqProperties) {
        return new DirectExchange(mqProperties.getBookingExchange(), true, false);
    }

    @Bean
    public Queue bookingQueue(MqProperties mqProperties) {
        return QueueBuilder.durable(mqProperties.getBookingQueue())
                .withArgument("x-dead-letter-exchange", mqProperties.getBookingDlxExchange())
                .withArgument("x-dead-letter-routing-key", mqProperties.getBookingDlxRoutingKey())
                .build();
    }

    @Bean
    public Binding bookingBinding(Queue bookingQueue, DirectExchange bookingExchange, MqProperties mqProperties) {
        return BindingBuilder.bind(bookingQueue).to(bookingExchange).with(mqProperties.getBookingRoutingKey());
    }

    @Bean
    public DirectExchange bookingDlxExchange(MqProperties mqProperties) {
        return new DirectExchange(mqProperties.getBookingDlxExchange(), true, false);
    }

    @Bean
    public Queue bookingDlxQueue(MqProperties mqProperties) {
        return QueueBuilder.durable(mqProperties.getBookingDlxQueue()).build();
    }

    @Bean
    public Binding bookingDlxBinding(Queue bookingDlxQueue,
                                     DirectExchange bookingDlxExchange,
                                     MqProperties mqProperties) {
        return BindingBuilder.bind(bookingDlxQueue).to(bookingDlxExchange).with(mqProperties.getBookingDlxRoutingKey());
    }
}

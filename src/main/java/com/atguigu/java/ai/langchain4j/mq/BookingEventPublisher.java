package com.atguigu.java.ai.langchain4j.mq;

import com.atguigu.java.ai.langchain4j.config.MqProperties;
import com.atguigu.java.ai.langchain4j.mq.event.BookingCreatedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true")
public class BookingEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final MqProperties mqProperties;

    public BookingEventPublisher(RabbitTemplate rabbitTemplate, MqProperties mqProperties) {
        this.rabbitTemplate = rabbitTemplate;
        this.mqProperties = mqProperties;
    }

    public void publishAfterCommit(BookingCreatedEvent event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publishNow(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishNow(event);
            }
        });
    }

    private void publishNow(BookingCreatedEvent event) {
        rabbitTemplate.convertAndSend(
                mqProperties.getBookingExchange(),
                mqProperties.getBookingRoutingKey(),
                event
        );
    }
}

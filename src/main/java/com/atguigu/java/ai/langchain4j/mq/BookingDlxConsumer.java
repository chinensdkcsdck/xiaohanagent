package com.atguigu.java.ai.langchain4j.mq;

import com.atguigu.java.ai.langchain4j.entity.MqDeadLetterEvent;
import com.atguigu.java.ai.langchain4j.mapper.MqDeadLetterEventMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true")
public class BookingDlxConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookingDlxConsumer.class);

    private final MqDeadLetterEventMapper mqDeadLetterEventMapper;
    private final MeterRegistry meterRegistry;

    public BookingDlxConsumer(MqDeadLetterEventMapper mqDeadLetterEventMapper, MeterRegistry meterRegistry) {
        this.mqDeadLetterEventMapper = mqDeadLetterEventMapper;
        this.meterRegistry = meterRegistry;
    }

    @RabbitListener(queues = "${app.mq.booking-dlx-queue}")
    public void onDeadLetter(Message message) {
        if (message == null) {
            return;
        }

        MessageProperties properties = message.getMessageProperties();
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        String eventId = properties == null ? null : properties.getMessageId();
        String queue = properties == null ? "unknown" : properties.getConsumerQueue();

        MqDeadLetterEvent dead = new MqDeadLetterEvent();
        dead.setEventId(eventId);
        dead.setBizType("APPOINTMENT");
        dead.setAction("DLQ");
        dead.setQueueName(queue == null ? "unknown" : queue);
        dead.setPayload(payload);
        dead.setErrorMessage("message routed to DLQ after retries");
        dead.setCreatedAt(LocalDateTime.now());
        mqDeadLetterEventMapper.insert(dead);

        Counter.builder("app.mq.dlq.consumed")
                .tag("queue", dead.getQueueName())
                .register(meterRegistry)
                .increment();
        Counter.builder("app.mq.dlq.alert")
                .tag("queue", dead.getQueueName())
                .register(meterRegistry)
                .increment();

        log.error("ALERT: booking message moved to DLQ queue={} eventId={} payload={}",
                dead.getQueueName(), dead.getEventId(), payload);
    }
}

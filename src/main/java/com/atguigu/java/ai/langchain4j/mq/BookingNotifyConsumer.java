package com.atguigu.java.ai.langchain4j.mq;

import com.atguigu.java.ai.langchain4j.entity.MessageConsumeLog;
import com.atguigu.java.ai.langchain4j.mapper.MessageConsumeLogMapper;
import com.atguigu.java.ai.langchain4j.mq.event.BookingCreatedEvent;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true")
public class BookingNotifyConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookingNotifyConsumer.class);

    private final MessageConsumeLogMapper messageConsumeLogMapper;

    public BookingNotifyConsumer(MessageConsumeLogMapper messageConsumeLogMapper) {
        this.messageConsumeLogMapper = messageConsumeLogMapper;
    }

    @RabbitListener(queues = "${app.mq.booking.queue}", containerFactory = "bookingListenerContainerFactory")
    public void onBookingCreated(BookingCreatedEvent event) {
        if (event == null || !StringUtils.hasText(event.getEventId())) {
            return;
        }

        boolean consumed = alreadyConsumed(event.getEventId());
        if (consumed) {
            return;
        }

        MessageConsumeLog consumeLog = new MessageConsumeLog();
        consumeLog.setMessageId(event.getEventId());
        consumeLog.setEventType(event.getAction());
        consumeLog.setStatus("SUCCESS");
        consumeLog.setConsumedAt(LocalDateTime.now());
        try {
            int inserted = messageConsumeLogMapper.insert(consumeLog);
            if (inserted <= 0) {
                throw new IllegalStateException("consume log insert failed");
            }
        } catch (DuplicateKeyException ex) {
            return;
        }

        log.info("booking-event-consumed eventId={} bizType={} action={} username={}",
                event.getEventId(), event.getBizType(), event.getAction(), event.getUsername());
    }

    private boolean alreadyConsumed(String messageId) {
        Long count = messageConsumeLogMapper.selectCount(
                new LambdaQueryWrapper<MessageConsumeLog>()
                        .eq(MessageConsumeLog::getMessageId, messageId)
                        .last("limit 1")
        );
        return count != null && count > 0;
    }
}

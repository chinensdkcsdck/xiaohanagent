package com.atguigu.java.ai.langchain4j.controller;

import com.atguigu.java.ai.langchain4j.entity.MqDeadLetterEvent;
import com.atguigu.java.ai.langchain4j.service.DeadLetterEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "运营排障接口")
@RestController
@RequestMapping("/api/operations")
public class OperationsApiController {

    private final DeadLetterEventService deadLetterEventService;

    public OperationsApiController(DeadLetterEventService deadLetterEventService) {
        this.deadLetterEventService = deadLetterEventService;
    }

    @Operation(summary = "查询MQ死信事件")
    @GetMapping("/dlq-events")
    public List<DeadLetterEventView> recentDlqEvents(@RequestParam(defaultValue = "20") Integer limit) {
        return deadLetterEventService.recent(limit == null ? 20 : limit)
                .stream()
                .map(this::toView)
                .toList();
    }

    private DeadLetterEventView toView(MqDeadLetterEvent event) {
        return new DeadLetterEventView(
                event.getId(),
                event.getEventId(),
                event.getBizType(),
                event.getAction(),
                event.getQueueName(),
                event.getErrorMessage(),
                event.getCreatedAt() == null ? null : event.getCreatedAt().toString(),
                event.getPayload()
        );
    }

    public record DeadLetterEventView(
            Long id,
            String eventId,
            String bizType,
            String action,
            String queueName,
            String errorMessage,
            String createdAt,
            String payload
    ) {
    }
}

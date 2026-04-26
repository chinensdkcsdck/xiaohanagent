package com.atguigu.java.ai.langchain4j.controller;

import com.atguigu.java.ai.langchain4j.auth.AuthConstants;
import com.atguigu.java.ai.langchain4j.auth.LoginUser;
import com.atguigu.java.ai.langchain4j.auth.UserContextHolder;
import com.atguigu.java.ai.langchain4j.bean.ChatForm;
import com.atguigu.java.ai.langchain4j.config.AiMetricsState;
import com.atguigu.java.ai.langchain4j.config.ToolInvocationTracker;
import com.atguigu.java.ai.langchain4j.exception.BusinessException;
import com.atguigu.java.ai.langchain4j.exception.ErrorCode;
import com.atguigu.java.ai.langchain4j.service.SkillOrchestratorService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Tag(name = "小涵对话")
@RestController
@RequestMapping("xiaohan")
public class XiaohanController {

    @Autowired
    private SkillOrchestratorService skillOrchestratorService;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private AiMetricsState aiMetricsState;

    @Operation(summary = "对话")
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@Valid @RequestBody ChatForm chatForm, HttpSession session) {
        LoginUser user = (LoginUser) session.getAttribute(AuthConstants.SESSION_USER_KEY);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }

        boolean success = false;
        Timer.Sample sample = Timer.start(meterRegistry);
        ToolInvocationTracker.reset();
        UserContextHolder.set(user);
        try {
            String answer = skillOrchestratorService.chat(user.userId(), chatForm.getMessage());
            success = true;
            return Flux.just(answer == null ? "" : answer);
        } finally {
            int toolCalls = ToolInvocationTracker.currentCalls();
            boolean toolHit = toolCalls > 0;

            aiMetricsState.recordChatRequest(toolHit);

            Counter.builder("app.chat.requests")
                    .tag("status", success ? "success" : "fail")
                    .register(meterRegistry)
                    .increment();

            Counter.builder("app.chat.tool.hit")
                    .tag("hit", String.valueOf(toolHit))
                    .register(meterRegistry)
                    .increment();

            DistributionSummary.builder("app.chat.tool.calls_per_request")
                    .register(meterRegistry)
                    .record(toolCalls);

            sample.stop(Timer.builder("app.chat.latency")
                    .register(meterRegistry));

            ToolInvocationTracker.clear();
            UserContextHolder.clear();
        }
    }
}

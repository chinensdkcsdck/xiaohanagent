package com.atguigu.java.ai.langchain4j.config;

import dev.langchain4j.agent.tool.Tool;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
public class ToolMetricsAspect {

    @Autowired
    private MeterRegistry meterRegistry;

    @Around("@annotation(dev.langchain4j.agent.tool.Tool)")
    public Object aroundTool(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Tool tool = method.getAnnotation(Tool.class);
        String toolName = (tool != null && !tool.name().isBlank()) ? tool.name() : method.getName();

        ToolInvocationTracker.markInvoked(toolName);
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            Object result = joinPoint.proceed();
            Counter.builder("app.tool.calls")
                    .tag("tool", toolName)
                    .tag("status", "success")
                    .register(meterRegistry)
                    .increment();
            return result;
        } catch (Throwable ex) {
            Counter.builder("app.tool.calls")
                    .tag("tool", toolName)
                    .tag("status", "fail")
                    .register(meterRegistry)
                    .increment();
            throw ex;
        } finally {
            sample.stop(Timer.builder("app.tool.latency")
                    .tag("tool", toolName)
                    .register(meterRegistry));
        }
    }
}

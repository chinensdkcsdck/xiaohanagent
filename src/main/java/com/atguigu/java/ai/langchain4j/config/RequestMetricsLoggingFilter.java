package com.atguigu.java.ai.langchain4j.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
public class RequestMetricsLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestMetricsLoggingFilter.class);

    private final MeterRegistry meterRegistry;

    public RequestMetricsLoggingFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator")
                || uri.startsWith("/swagger-ui")
                || uri.startsWith("/v3/api-docs")
                || uri.startsWith("/webjars")
                || "/".equals(uri)
                || "/index.html".equals(uri)
                || "/app.js".equals(uri)
                || "/styles.css".equals(uri)
                || "/favicon.ico".equals(uri);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startNanos = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

            double chatToolHitRate = gaugeValue("app.chat.tool.hit.rate");
            double ragRecallRate = gaugeValue("app.rag.recall.rate");
            double cpuUsage = gaugeValue("system.cpu.usage");
            double heapUsedMb = gaugeValue("jvm.memory.used", "area", "heap") / 1024 / 1024;

            double chatReqSuccess = counterCount("app.chat.requests", "status", "success");
            double chatReqFail = counterCount("app.chat.requests", "status", "fail");
            double ragReqSuccess = counterCount("app.rag.requests", "status", "success");
            double ragReqFail = counterCount("app.rag.requests", "status", "fail");
            double toolCallSuccess = counterCount("app.tool.calls", "status", "success");
            double toolCallFail = counterCount("app.tool.calls", "status", "fail");

            log.info("关键指标 请求方法={} 请求路径={} 状态码={} 耗时毫秒={} 聊天请求(成功/失败)={}/{} 工具调用(成功/失败)={}/{} RAG请求(成功/失败)={}/{} 工具命中率={} RAG召回率={} CPU使用率={} 堆内存MB={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    durationMs,
                    asLong(chatReqSuccess),
                    asLong(chatReqFail),
                    asLong(toolCallSuccess),
                    asLong(toolCallFail),
                    asLong(ragReqSuccess),
                    asLong(ragReqFail),
                    formatDecimal(chatToolHitRate),
                    formatDecimal(ragRecallRate),
                    formatDecimal(cpuUsage),
                    formatDecimal(heapUsedMb));
        }
    }

    private double gaugeValue(String name) {
        Gauge gauge = meterRegistry.find(name).gauge();
        if (gauge == null) {
            return 0.0d;
        }
        double value = gauge.value();
        return Double.isFinite(value) ? value : 0.0d;
    }

    private double gaugeValue(String name, String tagKey, String tagValue) {
        Gauge gauge = meterRegistry.find(name).tag(tagKey, tagValue).gauge();
        if (gauge == null) {
            return 0.0d;
        }
        double value = gauge.value();
        return Double.isFinite(value) ? value : 0.0d;
    }

    private double counterCount(String name, String tagKey, String tagValue) {
        return meterRegistry.find(name)
                .tag(tagKey, tagValue)
                .counters()
                .stream()
                .mapToDouble(Counter::count)
                .sum();
    }

    private String formatDecimal(double value) {
        return String.format("%.4f", value);
    }

    private long asLong(double value) {
        return Math.round(value);
    }
}

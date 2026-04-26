package com.atguigu.java.ai.langchain4j.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class AiMetricsState {

    private final AtomicLong chatRequests = new AtomicLong(0);
    private final AtomicLong chatToolHitRequests = new AtomicLong(0);
    private final AtomicLong ragSuccessRequests = new AtomicLong(0);
    private final AtomicLong ragHitRequests = new AtomicLong(0);

    public AiMetricsState(MeterRegistry meterRegistry) {
        Gauge.builder("app.chat.tool.hit.rate", this, AiMetricsState::chatToolHitRate)
                .description("Tool hit ratio per chat request")
                .register(meterRegistry);

        Gauge.builder("app.rag.recall.rate", this, AiMetricsState::ragRecallRate)
                .description("RAG recall ratio (requests with retrieved content / total retrieval requests)")
                .register(meterRegistry);
    }

    public void recordChatRequest(boolean toolHit) {
        chatRequests.incrementAndGet();
        if (toolHit) {
            chatToolHitRequests.incrementAndGet();
        }
    }

    public void recordRagRequest(boolean success, boolean hit) {
        if (!success) {
            return;
        }
        ragSuccessRequests.incrementAndGet();
        if (hit) {
            ragHitRequests.incrementAndGet();
        }
    }

    public double chatToolHitRate() {
        long total = chatRequests.get();
        if (total == 0) {
            return 0.0d;
        }
        return (double) chatToolHitRequests.get() / total;
    }

    public double ragRecallRate() {
        long total = ragSuccessRequests.get();
        if (total == 0) {
            return 0.0d;
        }
        return (double) ragHitRequests.get() / total;
    }
}

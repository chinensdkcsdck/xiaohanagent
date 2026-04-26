package com.atguigu.java.ai.langchain4j.config;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.List;
import java.util.Map;

public class ObservedContentRetriever implements ContentRetriever {

    private final ContentRetriever delegate;
    private final AiMetricsState aiMetricsState;
    private final Counter ragRequestsSuccess;
    private final Counter ragRequestsFail;
    private final Counter ragHitTrue;
    private final Counter ragHitFalse;
    private final DistributionSummary ragRetrievedCount;
    private final DistributionSummary ragScoreAvg;
    private final Timer ragLatency;

    public ObservedContentRetriever(ContentRetriever delegate, MeterRegistry meterRegistry, AiMetricsState aiMetricsState) {
        this.delegate = delegate;
        this.aiMetricsState = aiMetricsState;
        this.ragRequestsSuccess = Counter.builder("app.rag.requests")
                .tag("status", "success")
                .register(meterRegistry);
        this.ragRequestsFail = Counter.builder("app.rag.requests")
                .tag("status", "fail")
                .register(meterRegistry);
        this.ragHitTrue = Counter.builder("app.rag.hit")
                .tag("hit", "true")
                .register(meterRegistry);
        this.ragHitFalse = Counter.builder("app.rag.hit")
                .tag("hit", "false")
                .register(meterRegistry);
        this.ragRetrievedCount = DistributionSummary.builder("app.rag.retrieved.count")
                .register(meterRegistry);
        this.ragScoreAvg = DistributionSummary.builder("app.rag.score.avg")
                .register(meterRegistry);
        this.ragLatency = Timer.builder("app.rag.latency")
                .register(meterRegistry);
    }

    @Override
    public List<Content> retrieve(Query query) {
        Timer.Sample sample = Timer.start();
        try {
            List<Content> contents = delegate.retrieve(query);
            int hitCount = contents == null ? 0 : contents.size();
            boolean hit = hitCount > 0;

            aiMetricsState.recordRagRequest(true, hit);

            ragRequestsSuccess.increment();

            if (hit) {
                ragHitTrue.increment();
            } else {
                ragHitFalse.increment();
            }

            ragRetrievedCount.record(hitCount);

            recordScore(contents);
            return contents;
        } catch (Throwable ex) {
            aiMetricsState.recordRagRequest(false, false);
            ragRequestsFail.increment();
            throw ex;
        } finally {
            sample.stop(ragLatency);
        }
    }

    private void recordScore(List<Content> contents) {
        if (contents == null || contents.isEmpty()) {
            return;
        }
        double total = 0.0d;
        int count = 0;
        for (Content content : contents) {
            if (content == null) {
                continue;
            }
            Map<ContentMetadata, Object> metadata = content.metadata();
            if (metadata == null) {
                continue;
            }
            Object score = metadata.get(ContentMetadata.SCORE);
            if (!(score instanceof Number number)) {
                continue;
            }
            total += number.doubleValue();
            count++;
        }
        if (count > 0) {
            ragScoreAvg.record(total / count);
        }
    }
}

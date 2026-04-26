package com.atguigu.java.ai.langchain4j.config;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

class ObservedContentRetrieverABBenchmarkTest {

    @Test
    void abBenchmark_observed_vs_plain_delegate() throws Exception {
        int threads = 100;
        int totalRequests = 100_000;
        int warmupRequests = 10_000;

        BenchmarkResult plainNoDelay = runScenario("B-plain-no-delay", threads, totalRequests, warmupRequests, 0, false);
        BenchmarkResult observedNoDelay = runScenario("A-observed-no-delay", threads, totalRequests, warmupRequests, 0, true);

        BenchmarkResult plain2ms = runScenario("B-plain-2ms", threads, totalRequests, warmupRequests, 2_000_000L, false);
        BenchmarkResult observed2ms = runScenario("A-observed-2ms", threads, totalRequests, warmupRequests, 2_000_000L, true);

        BenchmarkResult plain8ms = runScenario("B-plain-8ms", threads, totalRequests, warmupRequests, 8_000_000L, false);
        BenchmarkResult observed8ms = runScenario("A-observed-8ms", threads, totalRequests, warmupRequests, 8_000_000L, true);

        printComparison(plainNoDelay, observedNoDelay);
        printComparison(plain2ms, observed2ms);
        printComparison(plain8ms, observed8ms);
    }

    private BenchmarkResult runScenario(String name,
                                        int threads,
                                        int totalRequests,
                                        int warmupRequests,
                                        long simulatedDelayNanos,
                                        boolean withObservedWrapper) throws Exception {
        ContentRetriever baseRetriever = query -> {
            if (simulatedDelayNanos > 0) {
                LockSupport.parkNanos(simulatedDelayNanos);
            }
            return Collections.emptyList();
        };

        ContentRetriever retriever;
        if (withObservedWrapper) {
            SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
            AiMetricsState state = new AiMetricsState(meterRegistry);
            retriever = new ObservedContentRetriever(baseRetriever, meterRegistry, state);
        } else {
            retriever = baseRetriever;
        }

        runOnce(retriever, threads, warmupRequests);
        return runOnce(retriever, threads, totalRequests).withName(name);
    }

    private BenchmarkResult runOnce(ContentRetriever retriever, int threads, int totalRequests) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threads);
        int requestsPerThread = totalRequests / threads;
        int remainder = totalRequests % threads;

        List<List<Long>> allSamples = new ArrayList<>(threads);
        for (int i = 0; i < threads; i++) {
            int thisThreadRequests = requestsPerThread + (i < remainder ? 1 : 0);
            List<Long> samples = new ArrayList<>(thisThreadRequests);
            allSamples.add(samples);
            pool.submit(() -> {
                try {
                    startGate.await();
                    for (int j = 0; j < thisThreadRequests; j++) {
                        long begin = System.nanoTime();
                        List<Content> ignored = retriever.retrieve((Query) null);
                        if (ignored == null) {
                            throw new IllegalStateException("retrieve returned null");
                        }
                        samples.add(System.nanoTime() - begin);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        long start = System.nanoTime();
        startGate.countDown();
        boolean finished = doneGate.await(5, TimeUnit.MINUTES);
        long elapsed = System.nanoTime() - start;
        pool.shutdownNow();

        if (!finished) {
            throw new IllegalStateException("Benchmark timed out");
        }

        List<Long> merged = new ArrayList<>(totalRequests);
        for (List<Long> s : allSamples) {
            merged.addAll(s);
        }
        merged.sort(Comparator.naturalOrder());

        long p50 = percentile(merged, 0.50);
        long p95 = percentile(merged, 0.95);
        long p99 = percentile(merged, 0.99);
        double avgNs = merged.stream().mapToLong(Long::longValue).average().orElse(0.0d);
        double qps = totalRequests / (elapsed / 1_000_000_000.0);

        return new BenchmarkResult("", totalRequests, elapsed, avgNs, p50, p95, p99, qps);
    }

    private long percentile(List<Long> sortedNs, double p) {
        if (sortedNs.isEmpty()) {
            return 0;
        }
        int idx = (int) Math.min(sortedNs.size() - 1, Math.floor(p * sortedNs.size()));
        return sortedNs.get(idx);
    }

    private void printComparison(BenchmarkResult plain, BenchmarkResult observed) {
        double qpsDropPct = (plain.qps - observed.qps) / plain.qps * 100.0;
        double avgIncreasePct = (observed.avgNs - plain.avgNs) / plain.avgNs * 100.0;

        System.out.println("======================================");
        System.out.println("A/B Benchmark Comparison");
        System.out.println("B (plain)    : " + plain);
        System.out.println("A (observed) : " + observed);
        System.out.printf("QPS drop: %.2f%%%n", qpsDropPct);
        System.out.printf("Avg latency increase: %.2f%%%n", avgIncreasePct);
        System.out.println("======================================");
    }

    private record BenchmarkResult(
            String name,
            int totalRequests,
            long elapsedNs,
            double avgNs,
            long p50Ns,
            long p95Ns,
            long p99Ns,
            double qps
    ) {
        BenchmarkResult withName(String newName) {
            return new BenchmarkResult(newName, totalRequests, elapsedNs, avgNs, p50Ns, p95Ns, p99Ns, qps);
        }

        @Override
        public String toString() {
            return String.format(
                    "%s | requests=%d, elapsed=%s, qps=%.2f, avg=%.2f us, p50=%.2f us, p95=%.2f us, p99=%.2f us",
                    name,
                    totalRequests,
                    Duration.ofNanos(elapsedNs),
                    qps,
                    avgNs / 1_000.0,
                    p50Ns / 1_000.0,
                    p95Ns / 1_000.0,
                    p99Ns / 1_000.0
            );
        }
    }
}

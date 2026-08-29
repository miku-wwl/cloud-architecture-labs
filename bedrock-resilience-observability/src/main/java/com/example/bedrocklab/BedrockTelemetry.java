package com.example.bedrocklab;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import software.amazon.awssdk.core.metrics.CoreMetric;
import software.amazon.awssdk.http.HttpMetric;
import software.amazon.awssdk.metrics.MetricCollection;
import software.amazon.awssdk.metrics.MetricPublisher;
import software.amazon.awssdk.metrics.SdkMetric;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BedrockTelemetry implements MetricPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(BedrockTelemetry.class);
    private static final int MAX_RECENT_STATS = 100;

    private final MeterRegistry registry;
    private final ThreadLocal<InvocationState> current = new ThreadLocal<>();
    private final ConcurrentLinkedDeque<RetryStats> recent = new ConcurrentLinkedDeque<>();

    public BedrockTelemetry(MeterRegistry registry) {
        this.registry = registry;
    }

    Invocation open(String modelId) {
        InvocationState state = new InvocationState(modelId);
        current.set(state);
        return new Invocation(state);
    }

    @Override
    public void publish(MetricCollection collection) {
        if (collection == null) {
            throw new IllegalArgumentException("metricCollection must not be null");
        }
        try {
            Optional.ofNullable(current.get()).ifPresent(state -> publish(collection, state));
        } catch (RuntimeException exception) {
            LOGGER.warn("AWS SDK 遥测发布失败，但不影响 Bedrock 调用", exception);
        }
    }

    private void publish(MetricCollection root, InvocationState state) {
        List<MetricCollection> all = flatten(root);
        RetryStats stats = new RetryStats(
                state.modelId,
                first(all, CoreMetric.RETRY_COUNT, 0),
                values(all, CoreMetric.BACKOFF_DELAY_DURATION).stream().reduce(Duration.ZERO, Duration::plus),
                values(all, CoreMetric.ERROR_TYPE),
                values(all, HttpMetric.HTTP_STATUS_CODE),
                first(all, CoreMetric.API_CALL_DURATION, Duration.ZERO));
        state.stats.set(stats);
        recent.addFirst(stats);
        while (recent.size() > MAX_RECENT_STATS) {
            recent.pollLast();
        }
        recordSdkMetrics(stats);
        LOGGER.info("AWS SDK Bedrock metrics modelId={} retryCount={} backoffMs={} throttled={}",
                stats.modelId(), stats.retryCount(), stats.totalBackoffDelay().toMillis(), stats.throttled());
    }

    void recordSuccess(String modelId, long latencyMs, int inputTokens, int outputTokens) {
        Tags tags = Tags.of("model_id", modelId);
        registry.counter("genai_bedrock_requests", tags.and("outcome", "success")).increment();
        Timer.builder("genai_bedrock_model_latency").tags(tags).publishPercentileHistogram()
                .register(registry).record(Duration.ofMillis(latencyMs));
        registry.counter("genai_bedrock_input_tokens", tags).increment(inputTokens);
        registry.counter("genai_bedrock_output_tokens", tags).increment(outputTokens);
    }

    void recordFailure(String modelId, String errorType) {
        Tags tags = Tags.of("model_id", modelId);
        registry.counter("genai_bedrock_requests", tags.and("outcome", "failure")).increment();
        registry.counter("genai_bedrock_failures", tags.and("error_type", errorType)).increment();
    }

    private void recordSdkMetrics(RetryStats stats) {
        Tags tags = Tags.of("model_id", stats.modelId(), "operation", "Converse");
        Counter.builder("genai_bedrock_retries").tags(tags).register(registry).increment(stats.retryCount());
        Counter.builder("genai_bedrock_throttled_attempts").tags(tags).register(registry)
                .increment(stats.httpStatusCodes().stream().filter(status -> status == 429).count());
        Timer.builder("genai_bedrock_sdk_api_call_duration").tags(tags).publishPercentileHistogram()
                .register(registry).record(stats.apiCallDuration());
        registry.summary("genai_bedrock_backoff_delay_ms", tags)
                .record(stats.totalBackoffDelay().toMillis());
    }

    List<RetryStats> recentStats() {
        return List.copyOf(recent);
    }

    void clearRecentStats() {
        recent.clear();
    }

    @Override
    public void close() {
        recent.clear();
        current.remove();
    }

    private static List<MetricCollection> flatten(MetricCollection root) {
        List<MetricCollection> result = new ArrayList<>();
        result.add(root);
        root.children().forEach(child -> result.addAll(flatten(child)));
        return result;
    }

    private static <T> List<T> values(List<MetricCollection> collections, SdkMetric<T> metric) {
        return collections.stream().flatMap(item -> item.metricValues(metric).stream()).toList();
    }

    private static <T> T first(List<MetricCollection> collections, SdkMetric<T> metric, T fallback) {
        return values(collections, metric).stream().findFirst().orElse(fallback);
    }

    final class Invocation implements AutoCloseable {
        private final InvocationState state;

        private Invocation(InvocationState state) {
            this.state = state;
        }

        RetryStats stats() {
            return state.stats.get();
        }

        @Override
        public void close() {
            if (current.get() == state) {
                current.remove();
            }
        }
    }

    private static final class InvocationState {
        private final String modelId;
        private final AtomicReference<RetryStats> stats;

        private InvocationState(String modelId) {
            this.modelId = modelId;
            this.stats = new AtomicReference<>(RetryStats.empty(modelId));
        }
    }

    public record RetryStats(
            String modelId,
            int retryCount,
            Duration totalBackoffDelay,
            List<String> errorTypes,
            List<Integer> httpStatusCodes,
            Duration apiCallDuration) {

        static RetryStats empty(String modelId) {
            return new RetryStats(modelId, 0, Duration.ZERO, List.of(), List.of(), Duration.ZERO);
        }

        public boolean throttled() {
            return httpStatusCodes.contains(429)
                    || errorTypes.stream().anyMatch(value -> value != null
                    && value.toLowerCase().contains("throttl"));
        }
    }
}

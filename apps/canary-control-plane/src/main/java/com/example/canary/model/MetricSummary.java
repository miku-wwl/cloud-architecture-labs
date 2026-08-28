package com.example.canary.model;

public record MetricSummary(long requestCount, long errorCount, double averageLatencyMs) {
    public double errorRate() {
        return requestCount == 0 ? 0.0 : (double) errorCount / requestCount;
    }
}

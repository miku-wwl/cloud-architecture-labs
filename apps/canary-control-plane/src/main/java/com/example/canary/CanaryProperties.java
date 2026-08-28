package com.example.canary;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CanaryProperties {
    public final String serviceName;
    public final String stableVersion;
    public final String candidateVersion;
    public final String routingTable;
    public final String releaseTable;
    public final String metricsTable;
    public final String eventBusName;
    public final String cloudWatchNamespace;
    public final int evaluationWindowSeconds;
    public final int minimumRequestCount;
    public final double maxErrorRate;
    public final double maxLatencyMs;
    public final URI stableUrl;
    public final URI candidateUrl;

    public CanaryProperties(
            @Value("${app.service-name:payment-api}") String serviceName,
            @Value("${app.stable-version:stable-v1}") String stableVersion,
            @Value("${app.candidate-version:candidate-v2}") String candidateVersion,
            @Value("${app.routing-table:canary-routing-state}") String routingTable,
            @Value("${app.release-table:canary-releases}") String releaseTable,
            @Value("${app.metrics-table:canary-metrics-window}") String metricsTable,
            @Value("${app.event-bus-name:canary-release-bus}") String eventBusName,
            @Value("${app.cloudwatch-namespace:CanaryDemo/PaymentAPI}") String cloudWatchNamespace,
            @Value("${app.evaluation-window-seconds:10}") int evaluationWindowSeconds,
            @Value("${app.minimum-request-count:10}") int minimumRequestCount,
            @Value("${app.max-error-rate:0.05}") double maxErrorRate,
            @Value("${app.max-latency-ms:300}") double maxLatencyMs,
            @Value("${app.stable-url:http://localhost:8081}") URI stableUrl,
            @Value("${app.candidate-url:http://localhost:8082}") URI candidateUrl) {
        this.serviceName = serviceName;
        this.stableVersion = stableVersion;
        this.candidateVersion = candidateVersion;
        this.routingTable = routingTable;
        this.releaseTable = releaseTable;
        this.metricsTable = metricsTable;
        this.eventBusName = eventBusName;
        this.cloudWatchNamespace = cloudWatchNamespace;
        this.evaluationWindowSeconds = evaluationWindowSeconds;
        this.minimumRequestCount = minimumRequestCount;
        this.maxErrorRate = maxErrorRate;
        this.maxLatencyMs = maxLatencyMs;
        this.stableUrl = stableUrl;
        this.candidateUrl = candidateUrl;
    }
}

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
    public final String eventBusName;
    public final String cloudWatchNamespace;
    public final int evaluationWindowSeconds;
    public final URI stableUrl;
    public final URI candidateUrl;

    public CanaryProperties(
            @Value("${app.service-name:library-api}") String serviceName,
            @Value("${app.stable-version:stable-v1}") String stableVersion,
            @Value("${app.candidate-version:candidate-v2}") String candidateVersion,
            @Value("${app.routing-table:canary-routing-state}") String routingTable,
            @Value("${app.release-table:canary-releases}") String releaseTable,
            @Value("${app.event-bus-name:canary-release-bus}") String eventBusName,
            @Value("${app.cloudwatch-namespace:CanaryDemo/LibraryAPI}") String cloudWatchNamespace,
            @Value("${app.evaluation-window-seconds:10}") int evaluationWindowSeconds,
            @Value("${app.stable-url:http://localhost:8081}") URI stableUrl,
            @Value("${app.candidate-url:http://localhost:8082}") URI candidateUrl) {
        this.serviceName = serviceName;
        this.stableVersion = stableVersion;
        this.candidateVersion = candidateVersion;
        this.routingTable = routingTable;
        this.releaseTable = releaseTable;
        this.eventBusName = eventBusName;
        this.cloudWatchNamespace = cloudWatchNamespace;
        this.evaluationWindowSeconds = evaluationWindowSeconds;
        this.stableUrl = stableUrl;
        this.candidateUrl = candidateUrl;
    }
}

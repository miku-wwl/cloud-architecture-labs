package com.example.canary.dashboard;

import com.example.canary.CanaryProperties;
import com.example.canary.model.MetricSummary;
import com.example.canary.model.ReleaseRecord;
import com.example.canary.model.RoutingState;
import com.example.canary.store.DynamoStore;
import com.example.canary.routing.TrafficService;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
    private final DynamoStore store;
    private final CanaryProperties properties;
    private final TrafficService traffic;

    public DashboardService(DynamoStore store, CanaryProperties properties, TrafficService traffic) {
        this.store = store;
        this.properties = properties;
        this.traffic = traffic;
    }

    public Map<String, Object> snapshot() {
        RoutingState routing = store.getRoutingState();
        Instant since = Instant.now().minus(Duration.ofMinutes(10));
        MetricSummary stable = store.summarizeMetrics(properties.stableVersion, since);
        MetricSummary candidate = store.summarizeMetrics(properties.candidateVersion, since);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("routing", routing);
        result.put("stableMetrics", metrics(stable));
        result.put("candidateMetrics", metrics(candidate));
        result.put("recentRelease", store.mostRecentRelease().orElse(null));
        result.put("traffic", traffic.status());
        return result;
    }

    private static Map<String, Object> metrics(MetricSummary summary) {
        return Map.of("requests", summary.requestCount(), "errors", summary.errorCount(),
                "errorRate", summary.errorRate(), "avgLatencyMs", summary.averageLatencyMs());
    }
}

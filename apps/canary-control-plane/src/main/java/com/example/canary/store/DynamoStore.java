package com.example.canary.store;

import com.example.canary.CanaryProperties;
import com.example.canary.model.MetricSummary;
import com.example.canary.model.ReleaseRecord;
import com.example.canary.model.RoutingState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

@Repository
public class DynamoStore {
    private static final Logger log = LoggerFactory.getLogger(DynamoStore.class);
    private final DynamoDbClient dynamo;
    private final CanaryProperties properties;

    public DynamoStore(DynamoDbClient dynamo, CanaryProperties properties) {
        this.dynamo = dynamo;
        this.properties = properties;
    }

    public void ensureInitialRoutingState() {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("serviceName", s(properties.serviceName));
        item.put("stableVersion", s(properties.stableVersion));
        item.put("candidateVersion", s(properties.candidateVersion));
        item.put("candidatePercentage", n(0));
        item.put("releaseId", s("none"));
        item.put("updatedAt", s(Instant.now().toString()));
        item.put("activeReleaseId", s("none"));
        try {
            dynamo.putItem(PutItemRequest.builder().tableName(properties.routingTable).item(item)
                    .conditionExpression("attribute_not_exists(serviceName)").build());
        } catch (ConditionalCheckFailedException ignored) {
            // The initial item already exists.
        } catch (SdkServiceException ex) {
            log.warn("routing_state_init_failed table={} error={}", properties.routingTable, ex.getMessage());
        }
    }

    public RoutingState getRoutingState() {
        try {
            Map<String, AttributeValue> item = dynamo.getItem(GetItemRequest.builder()
                    .tableName(properties.routingTable).key(Map.of("serviceName", s(properties.serviceName))).build()).item();
            if (item == null || item.isEmpty()) {
                return RoutingState.initial(properties.serviceName, properties.stableVersion, properties.candidateVersion);
            }
            return new RoutingState(properties.serviceName, text(item, "stableVersion", properties.stableVersion),
                    text(item, "candidateVersion", properties.candidateVersion), number(item, "candidatePercentage", 0),
                    text(item, "releaseId", "none"), text(item, "updatedAt", ""));
        } catch (SdkServiceException ex) {
            log.warn("routing_state_read_failed error={}", ex.getMessage());
            return RoutingState.initial(properties.serviceName, properties.stableVersion, properties.candidateVersion);
        }
    }

    public boolean tryAcquireRelease(String releaseId) {
        try {
            dynamo.updateItem(UpdateItemRequest.builder().tableName(properties.routingTable)
                    .key(Map.of("serviceName", s(properties.serviceName)))
                    .updateExpression("SET activeReleaseId = :releaseId, updatedAt = :updatedAt")
                    .conditionExpression("attribute_not_exists(activeReleaseId) OR activeReleaseId = :none")
                    .expressionAttributeValues(Map.of(":releaseId", s(releaseId), ":none", s("none"),
                            ":updatedAt", s(Instant.now().toString()))).build());
            return true;
        } catch (ConditionalCheckFailedException ex) {
            return false;
        }
    }

    public void saveCreatedRelease(ReleaseRecord release) {
        dynamo.putItem(PutItemRequest.builder().tableName(properties.releaseTable)
                .item(releaseItem(release)).conditionExpression("attribute_not_exists(releaseId)").build());
    }

    public void markReleaseFailed(String releaseId, String reason) {
        updateRelease(releaseId, Map.of("status", s("FAILED"), "failureReason", s(reason),
                "updatedAt", s(Instant.now().toString())));
        clearActiveRelease(releaseId);
    }

    public Optional<ReleaseRecord> getRelease(String releaseId) {
        Map<String, AttributeValue> item = dynamo.getItem(GetItemRequest.builder().tableName(properties.releaseTable)
                .key(Map.of("releaseId", s(releaseId))).build()).item();
        return item == null || item.isEmpty() ? Optional.empty() : Optional.of(toRelease(item));
    }

    public Optional<ReleaseRecord> mostRecentRelease() {
        try {
            List<ReleaseRecord> releases = dynamo.scan(ScanRequest.builder().tableName(properties.releaseTable).build())
                    .items().stream().map(this::toRelease).toList();
            return releases.stream().max(Comparator.comparing(ReleaseRecord::updatedAt));
        } catch (SdkServiceException ex) {
            log.warn("release_scan_failed error={}", ex.getMessage());
            return Optional.empty();
        }
    }

    public void updateRelease(String releaseId, Map<String, AttributeValue> fields) {
        if (fields.isEmpty()) return;
        StringBuilder expression = new StringBuilder("SET ");
        Map<String, AttributeValue> values = new HashMap<>();
        int i = 0;
        for (Map.Entry<String, AttributeValue> entry : fields.entrySet()) {
            if (i++ > 0) expression.append(", ");
            String token = ":v" + i;
            expression.append(entry.getKey()).append(" = ").append(token);
            values.put(token, entry.getValue());
        }
        dynamo.updateItem(UpdateItemRequest.builder().tableName(properties.releaseTable)
                .key(Map.of("releaseId", s(releaseId))).updateExpression(expression.toString())
                .expressionAttributeValues(values).build());
    }

    public void clearActiveRelease(String releaseId) {
        try {
            dynamo.updateItem(UpdateItemRequest.builder().tableName(properties.routingTable)
                    .key(Map.of("serviceName", s(properties.serviceName)))
                    .updateExpression("REMOVE activeReleaseId")
                    .conditionExpression("activeReleaseId = :releaseId")
                    .expressionAttributeValues(Map.of(":releaseId", s(releaseId))).build());
        } catch (ConditionalCheckFailedException ignored) {
            // A newer release owns the lock or it was already cleared.
        }
    }

    public void resetRoutingState() {
        dynamo.updateItem(UpdateItemRequest.builder().tableName(properties.routingTable)
                .key(Map.of("serviceName", s(properties.serviceName)))
                .updateExpression("SET stableVersion = :stable, candidateVersion = :candidate, candidatePercentage = :zero, releaseId = :none, updatedAt = :updatedAt REMOVE activeReleaseId")
                .expressionAttributeValues(Map.of(":stable", s(properties.stableVersion), ":candidate", s(properties.candidateVersion),
                        ":zero", n(0), ":none", s("none"), ":updatedAt", s(Instant.now().toString()))).build());
    }

    public MetricSummary summarizeMetrics(String version, Instant since) {
        String prefix = String.format("%013d", since.toEpochMilli());
        try {
            List<Map<String, AttributeValue>> items = dynamo.query(QueryRequest.builder().tableName(properties.metricsTable)
                    .keyConditionExpression("metricKey = :key AND observedAt >= :since")
                    .expressionAttributeValues(Map.of(":key", s(properties.serviceName + "#" + version), ":since", s(prefix)))
                    .build()).items();
            long errors = items.stream().mapToLong(item -> number(item, "errorCount", 0)).sum();
            double latencyTotal = items.stream().mapToDouble(item -> number(item, "latencyMs", 0)).sum();
            return new MetricSummary(items.size(), errors, items.isEmpty() ? 0 : latencyTotal / items.size());
        } catch (SdkServiceException ex) {
            log.warn("metric_summary_failed version={} error={}", version, ex.getMessage());
            return new MetricSummary(0, 0, 0);
        }
    }

    private Map<String, AttributeValue> releaseItem(ReleaseRecord release) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("releaseId", s(release.releaseId()));
        item.put("serviceName", s(release.serviceName()));
        item.put("stableVersion", s(release.stableVersion()));
        item.put("candidateVersion", s(release.candidateVersion()));
        item.put("status", s(release.status()));
        item.put("currentStage", s(release.currentStage()));
        item.put("candidatePercentage", n(release.candidatePercentage()));
        item.put("startedAt", s(release.startedAt()));
        item.put("updatedAt", s(release.updatedAt()));
        item.put("failureReason", s(release.failureReason()));
        item.put("finalDecision", s(release.finalDecision()));
        return item;
    }

    private ReleaseRecord toRelease(Map<String, AttributeValue> item) {
        return new ReleaseRecord(text(item, "releaseId", ""), text(item, "serviceName", ""),
                text(item, "stableVersion", ""), text(item, "candidateVersion", ""), text(item, "status", ""),
                text(item, "currentStage", ""), number(item, "candidatePercentage", 0), text(item, "startedAt", ""),
                text(item, "updatedAt", ""), text(item, "failureReason", ""), text(item, "finalDecision", ""));
    }

    private static AttributeValue s(String value) { return AttributeValue.builder().s(value == null ? "" : value).build(); }
    private static AttributeValue n(long value) { return AttributeValue.builder().n(Long.toString(value)).build(); }
    private static String text(Map<String, AttributeValue> item, String key, String fallback) {
        return item.containsKey(key) && item.get(key).s() != null ? item.get(key).s() : fallback;
    }
    private static int number(Map<String, AttributeValue> item, String key, int fallback) {
        try { return item.containsKey(key) ? Integer.parseInt(item.get(key).n()) : fallback; }
        catch (RuntimeException ex) { return fallback; }
    }
}

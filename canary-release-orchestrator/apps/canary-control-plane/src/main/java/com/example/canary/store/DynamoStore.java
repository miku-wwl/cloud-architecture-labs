package com.example.canary.store;

import com.example.canary.CanaryProperties;
import com.example.canary.model.ReleaseRecord;
import com.example.canary.model.RoutingState;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
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
        try {
            dynamo.putItem(PutItemRequest.builder().tableName(properties.routingTable).item(item)
                    .conditionExpression("attribute_not_exists(serviceName)").build());
        } catch (ConditionalCheckFailedException ignored) {
            // The initial routing item already exists.
        } catch (SdkServiceException ex) {
            log.warn("routing_state_init_failed table={} error={}", properties.routingTable, ex.getMessage());
        }
    }

    public RoutingState getRoutingState() {
        try {
            Map<String, AttributeValue> item = dynamo.getItem(GetItemRequest.builder()
                    .tableName(properties.routingTable)
                    .key(Map.of("serviceName", s(properties.serviceName))).build()).item();
            if (item == null || item.isEmpty()) {
                return RoutingState.initial(properties.serviceName, properties.stableVersion, properties.candidateVersion);
            }
            return new RoutingState(properties.serviceName,
                    text(item, "stableVersion", properties.stableVersion),
                    text(item, "candidateVersion", properties.candidateVersion),
                    number(item, "candidatePercentage", 0),
                    text(item, "releaseId", "none"),
                    text(item, "updatedAt", ""));
        } catch (SdkServiceException ex) {
            log.warn("routing_state_read_failed error={}", ex.getMessage());
            return RoutingState.initial(properties.serviceName, properties.stableVersion, properties.candidateVersion);
        }
    }

    public void saveCreatedRelease(ReleaseRecord release) {
        dynamo.putItem(PutItemRequest.builder().tableName(properties.releaseTable)
                .item(releaseItem(release))
                .conditionExpression("attribute_not_exists(releaseId)").build());
    }

    public Optional<ReleaseRecord> getRelease(String releaseId) {
        Map<String, AttributeValue> item = dynamo.getItem(GetItemRequest.builder()
                .tableName(properties.releaseTable)
                .key(Map.of("releaseId", s(releaseId))).build()).item();
        return item == null || item.isEmpty() ? Optional.empty() : Optional.of(toRelease(item));
    }

    public void markReleaseFailed(String releaseId, String reason) {
        dynamo.updateItem(UpdateItemRequest.builder().tableName(properties.releaseTable)
                .key(Map.of("releaseId", s(releaseId)))
                .updateExpression("SET #status = :status, failureReason = :reason, updatedAt = :updatedAt")
                .expressionAttributeNames(Map.of("#status", "status"))
                .expressionAttributeValues(Map.of(
                        ":status", s("FAILED"),
                        ":reason", s(reason),
                        ":updatedAt", s(Instant.now().toString())))
                .build());
    }

    public void resetRoutingState() {
        dynamo.updateItem(UpdateItemRequest.builder().tableName(properties.routingTable)
                .key(Map.of("serviceName", s(properties.serviceName)))
                .updateExpression("SET stableVersion = :stable, candidateVersion = :candidate, "
                        + "candidatePercentage = :zero, releaseId = :none, updatedAt = :updatedAt "
                        + "REMOVE activeReleaseId")
                .expressionAttributeValues(Map.of(
                        ":stable", s(properties.stableVersion),
                        ":candidate", s(properties.candidateVersion),
                        ":zero", n(0),
                        ":none", s("none"),
                        ":updatedAt", s(Instant.now().toString())))
                .build());
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
        return new ReleaseRecord(
                text(item, "releaseId", ""),
                text(item, "serviceName", ""),
                text(item, "stableVersion", ""),
                text(item, "candidateVersion", ""),
                text(item, "status", ""),
                text(item, "currentStage", ""),
                number(item, "candidatePercentage", 0),
                text(item, "startedAt", ""),
                text(item, "updatedAt", ""),
                text(item, "failureReason", ""),
                text(item, "finalDecision", ""));
    }

    private static AttributeValue s(String value) {
        return AttributeValue.builder().s(value == null ? "" : value).build();
    }

    private static AttributeValue n(long value) {
        return AttributeValue.builder().n(Long.toString(value)).build();
    }

    private static String text(Map<String, AttributeValue> item, String key, String fallback) {
        return item.containsKey(key) && item.get(key).s() != null ? item.get(key).s() : fallback;
    }

    private static int number(Map<String, AttributeValue> item, String key, int fallback) {
        try {
            return item.containsKey(key) ? Integer.parseInt(item.get(key).n()) : fallback;
        } catch (RuntimeException ex) {
            return fallback;
        }
    }
}

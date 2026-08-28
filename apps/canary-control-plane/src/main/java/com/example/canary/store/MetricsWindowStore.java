package com.example.canary.store;

import com.example.canary.CanaryProperties;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

@Repository
public class MetricsWindowStore {
    private static final Logger log = LoggerFactory.getLogger(MetricsWindowStore.class);
    private final DynamoDbClient dynamo;
    private final CanaryProperties properties;

    public MetricsWindowStore(DynamoDbClient dynamo, CanaryProperties properties) {
        this.dynamo = dynamo;
        this.properties = properties;
    }

    public void record(String version, int httpStatus, long latencyMs, Instant timestamp) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("metricKey", AttributeValue.builder().s(properties.serviceName + "#" + version).build());
        item.put("observedAt", AttributeValue.builder()
                .s(String.format("%013d#%s", timestamp.toEpochMilli(), UUID.randomUUID())).build());
        item.put("errorCount", AttributeValue.builder().n(httpStatus >= 500 ? "1" : "0").build());
        item.put("latencyMs", AttributeValue.builder().n(Long.toString(latencyMs)).build());
        try {
            dynamo.putItem(PutItemRequest.builder().tableName(properties.metricsTable).item(item).build());
        } catch (SdkServiceException ex) {
            log.warn("metric_window_write_failed version={} error={}", version, ex.getMessage());
        }
    }
}

package com.example.securemcp;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

@Repository
public class DynamoUserDataRepository implements UserDataRepository {

    private final DynamoDbClient client;
    private final McpProperties properties;

    public DynamoUserDataRepository(DynamoDbClient client, McpProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public Optional<Map<String, Object>> findByPrincipalId(String principalId) {
        var response = client.getItem(GetItemRequest.builder()
                .tableName(properties.getUserDataTable())
                .consistentRead(true)
                .key(Map.of("principalId", AttributeValue.builder().s(principalId).build()))
                .build());
        if (!response.hasItem() || response.item().isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        response.item().forEach((key, value) -> result.put(key, fromAttributeValue(value)));
        return Optional.of(result);
    }

    private static Object fromAttributeValue(AttributeValue value) {
        if (value.s() != null) {
            return value.s();
        }
        if (value.n() != null) {
            return new BigDecimal(value.n());
        }
        if (value.bool() != null) {
            return value.bool();
        }
        if (value.hasM()) {
            Map<String, Object> map = new LinkedHashMap<>();
            value.m().forEach((key, nested) -> map.put(key, fromAttributeValue(nested)));
            return map;
        }
        if (value.hasL()) {
            List<Object> list = new ArrayList<>();
            value.l().forEach(nested -> list.add(fromAttributeValue(nested)));
            return list;
        }
        if (value.hasSs()) {
            return value.ss();
        }
        if (value.hasBs()) {
            return value.bs().stream().map(Object::toString).toList();
        }
        return null;
    }
}

package com.example.securemcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class McpToolsTest {

    @Test
    void bindsEveryToolToTheResolvedPrincipal() {
        UserDataRepository repository = id -> Optional.of(Map.of(
                "principalId", id,
                "orders", List.of(Map.of("orderId", "order-1")),
                "preferences", Map.of("language", "zh")));
        PrincipalResolver resolver = new PrincipalResolver();
        McpProperties properties = new McpProperties();
        properties.setAllowDebugPrincipal(true);
        McpTools tools = new McpTools(repository, resolver, new ObjectMapper());

        PrincipalContextHolder.set(new Principal("alice-principal", Principal.Type.SERVICE));
        try {
            var result = tools.listMyOrders(null, Map.of("limit", 1));
            assertEquals(false, result.isError());
            assertEquals(1, result.content().size());
            org.junit.jupiter.api.Assertions.assertTrue(result.content().getFirst().toString().contains("alice-principal"));
        } finally {
            PrincipalContextHolder.clear();
        }
    }

    @Test
    void rejectsOutOfRangeLimitBeforeRepositoryUse() {
        UserDataRepository repository = id -> { throw new AssertionError("不应访问数据库"); };
        McpTools tools = new McpTools(repository, new PrincipalResolver(), new ObjectMapper());
        PrincipalContextHolder.set(new Principal("alice-principal", Principal.Type.SERVICE));
        try {
            assertThrows(IllegalArgumentException.class, () -> tools.listMyOrders(null, Map.of("limit", 1000)));
        } finally {
            PrincipalContextHolder.clear();
        }
    }
}

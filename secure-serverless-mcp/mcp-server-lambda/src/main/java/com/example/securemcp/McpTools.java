package com.example.securemcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import org.springframework.stereotype.Component;

@Component
public class McpTools {

    private final UserDataRepository repository;
    private final PrincipalResolver principalResolver;
    private final ObjectMapper objectMapper;

    public McpTools(UserDataRepository repository, PrincipalResolver principalResolver, ObjectMapper objectMapper) {
        this.repository = repository;
        this.principalResolver = principalResolver;
        this.objectMapper = objectMapper;
    }

    public McpSchema.CallToolResult getMyProfile(McpSyncServerExchange exchange, Map<String, Object> ignored) {
        Principal principal = principalResolver.requirePrincipal(exchange);
        return success(principal, "profile", dataFor(principal));
    }

    public McpSchema.CallToolResult listMyOrders(McpSyncServerExchange exchange, Map<String, Object> arguments) {
        Principal principal = principalResolver.requirePrincipal(exchange);
        int limit = readLimit(arguments);
        Map<String, Object> userData = dataFor(principal);
        Object rawOrders = userData.get("orders");
        List<?> orders = rawOrders instanceof List<?> list ? list : List.of();
        List<?> limited = new ArrayList<>(orders.subList(0, Math.min(limit, orders.size())));
        return success(principal, "orders", limited);
    }

    public McpSchema.CallToolResult getMyPreferences(McpSyncServerExchange exchange, Map<String, Object> ignored) {
        Principal principal = principalResolver.requirePrincipal(exchange);
        Map<String, Object> userData = dataFor(principal);
        Object preferences = userData.getOrDefault("preferences", Map.of());
        return success(principal, "preferences", preferences);
    }

    private Map<String, Object> dataFor(Principal principal) {
        return repository.findByPrincipalId(principal.id())
                .orElseThrow(() -> new IllegalArgumentException("没有找到当前 principal 的本地演示数据"));
    }

    private static int readLimit(Map<String, Object> arguments) {
        Object raw = arguments == null ? null : arguments.get("limit");
        int limit = raw == null ? 5 : (raw instanceof Number number ? number.intValue() : -1);
        if (limit < 1 || limit > 20) {
            throw new IllegalArgumentException("limit 必须在 1 到 20 之间");
        }
        return limit;
    }

    private McpSchema.CallToolResult success(Principal principal, String kind, Object value) {
        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("principalType", principal.type().name());
        structured.put("principalId", principal.id());
        structured.put(kind, value);
        try {
            return McpSchema.CallToolResult.builder()
                    .addTextContent(objectMapper.writeValueAsString(structured))
                    .structuredContent(structured)
                    .build();
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("无法序列化 MCP 工具结果", ex);
        }
    }
}

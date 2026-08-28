package com.example.securemcp;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PrincipalResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(PrincipalResolver.class);

    public McpTransportContext extract(HttpServletRequest request, McpProperties properties) {
        Principal principal = principalFromTrustedHeaders(request, properties);
        LOGGER.info("MCP principal context source={} principalPresent={}",
                request.getHeader("X-Mcp-Principal-Source"), principal != null);
        if (principal == null) {
            return McpTransportContext.EMPTY;
        }
        return McpTransportContext.create(Map.of(
                "principalId", principal.id(),
                "principalType", principal.type().name()));
    }

    public Principal requirePrincipal(McpSyncServerExchange exchange) {
        if (exchange != null) {
            Object id = exchange.transportContext().get("principalId");
            Object type = exchange.transportContext().get("principalType");
            if (id instanceof String principalId && type instanceof String typeName) {
                try {
                    return new Principal(principalId, Principal.Type.valueOf(typeName));
                } catch (IllegalArgumentException ignored) {
                    // Fall through to the request-local value for unit tests.
                }
            }
        }
        Principal principal = PrincipalContextHolder.get();
        if (principal == null) {
            Object contextId = exchange == null ? null : exchange.transportContext().get("principalId");
            Object contextType = exchange == null ? null : exchange.transportContext().get("principalType");
            throw new IllegalStateException("MCP 工具调用缺少 API Gateway 验证后的 principal (contextId="
                    + contextId + ", contextType=" + contextType + ")");
        }
        return principal;
    }

    private static Principal principalFromTrustedHeaders(HttpServletRequest request, McpProperties properties) {
        String source = request.getHeader("X-Mcp-Principal-Source");
        boolean trustedLambdaHeader = "validated-jwt".equalsIgnoreCase(source);
        boolean localDebugHeader = properties.isAllowDebugPrincipal() && "debug-local".equalsIgnoreCase(source);
        if (!trustedLambdaHeader && !localDebugHeader) {
            return null;
        }
        String id = request.getHeader("X-Mcp-Principal-Id");
        String typeValue = request.getHeader("X-Mcp-Principal-Type");
        if (id == null || id.isBlank() || typeValue == null || typeValue.isBlank()) {
            return null;
        }
        try {
            return new Principal(id, Principal.Type.valueOf(typeValue.toUpperCase()));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}

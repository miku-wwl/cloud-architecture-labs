package com.example.securemcp;

import java.util.List;
import java.util.Map;

import jakarta.servlet.Servlet;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpServerConfiguration {

    @Bean
    McpJsonMapper mcpJsonMapper() {
        return McpJsonDefaults.getMapper();
    }

    @Bean
    HttpServletStreamableServerTransportProvider mcpTransport(McpJsonMapper jsonMapper,
                                                              PrincipalResolver principalResolver,
                                                              McpProperties properties) {
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(jsonMapper)
                .mcpEndpoint("/mcp")
                .contextExtractor(request -> principalResolver.extract(request, properties))
                .disallowDelete(true)
                .build();
    }

    @Bean
    ServletRegistrationBean<Servlet> mcpServlet(HttpServletStreamableServerTransportProvider transport) {
        ServletRegistrationBean<Servlet> registration = new ServletRegistrationBean<>(transport, "/mcp");
        registration.setLoadOnStartup(1);
        return registration;
    }

    @Bean(destroyMethod = "close")
    McpSyncServer mcpServer(HttpServletStreamableServerTransportProvider transport, McpJsonMapper jsonMapper,
                            McpTools tools) {
        List<McpServerFeatures.SyncToolSpecification> specifications = List.of(
                tool("get_my_profile", "读取当前 JWT principal 的资料，不接受用户标识参数。",
                        emptySchema(), (exchange, request) -> tools.getMyProfile(exchange, request.arguments())),
                tool("list_my_orders", "读取当前 JWT principal 的订单，limit 只能是 1 到 20。",
                        limitSchema(), (exchange, request) -> tools.listMyOrders(exchange, request.arguments())),
                tool("get_my_preferences", "读取当前 JWT principal 的偏好，不接受用户标识参数。",
                        emptySchema(), (exchange, request) -> tools.getMyPreferences(exchange, request.arguments())));
        return McpServer.sync(transport)
                .jsonMapper(jsonMapper)
                .serverInfo("secure-serverless-mcp", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .tools(specifications)
                .build();
    }

    private static McpServerFeatures.SyncToolSpecification tool(
            String name, String description, Map<String, Object> schema,
            java.util.function.BiFunction<io.modelcontextprotocol.server.McpSyncServerExchange,
                    McpSchema.CallToolRequest, McpSchema.CallToolResult> handler) {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder(name, schema).description(description).build())
                .callHandler((exchange, request) -> handler.apply(exchange, request))
                .build();
    }

    private static Map<String, Object> emptySchema() {
        return Map.of("type", "object", "properties", Map.of(), "additionalProperties", false);
    }

    private static Map<String, Object> limitSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of("limit", Map.of(
                        "type", "integer",
                        "minimum", 1,
                        "maximum", 20,
                        "default", 5)),
                "additionalProperties", false);
    }
}

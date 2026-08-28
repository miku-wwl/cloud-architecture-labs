package com.example.securemcp.client;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema;

public final class McpClientCli {

    private McpClientCli() {
    }

    public static void main(String[] args) throws Exception {
        String command = args.length == 0 ? "full-demo" : args[0];
        McpClientCli cli = new McpClientCli();
        switch (command) {
            case "token" -> cli.printToken();
            case "initialize" -> cli.withClient(client -> print(client.initialize()));
            case "list-tools" -> cli.withClient(client -> print(client.listTools()));
            case "call-profile" -> cli.withClient(client -> print(client.callTool(
                    McpSchema.CallToolRequest.builder("get_my_profile").arguments(Map.of()).build())));
            case "call-orders" -> cli.withClient(client -> print(client.callTool(
                    McpSchema.CallToolRequest.builder("list_my_orders").arguments(Map.of("limit", 5)).build())));
            case "call-preferences" -> cli.withClient(client -> print(client.callTool(
                    McpSchema.CallToolRequest.builder("get_my_preferences").arguments(Map.of()).build())));
            case "full-demo" -> cli.fullDemo();
            default -> throw new IllegalArgumentException("未知命令: " + command
                    + "; 可用命令: token, initialize, list-tools, call-profile, call-orders, call-preferences, full-demo");
        }
    }

    private void printToken() throws IOException, InterruptedException {
        TokenClient.TokenResponse token = acquireToken();
        System.out.printf("OAuth PASS: expiresIn=%d scope=%s fingerprint=%s%n",
                token.expiresIn(), token.scope(), token.fingerprint());
    }

    private void fullDemo() throws Exception {
        String token;
        if (System.getenv("MCP_ACCESS_TOKEN") != null && !System.getenv("MCP_ACCESS_TOKEN").isBlank()) {
            token = System.getenv("MCP_ACCESS_TOKEN");
            System.out.println("Local client mode: using caller-supplied access token placeholder; OAuth is tested by token command.");
        } else {
            TokenClient.TokenResponse oauthToken = acquireToken();
            token = oauthToken.accessToken();
            System.out.printf("OAuth PASS: expiresIn=%d scope=%s fingerprint=%s%n",
                    oauthToken.expiresIn(), oauthToken.scope(), oauthToken.fingerprint());
        }
        withClient(token, client -> {
            print("initialize", client.initialize());
            print("tools/list", client.listTools());
            print("tools/call get_my_profile", client.callTool(
                    McpSchema.CallToolRequest.builder("get_my_profile").arguments(Map.of()).build()));
            print("tools/call list_my_orders", client.callTool(
                    McpSchema.CallToolRequest.builder("list_my_orders").arguments(Map.of("limit", 5)).build()));
            print("tools/call get_my_preferences", client.callTool(
                    McpSchema.CallToolRequest.builder("get_my_preferences").arguments(Map.of()).build()));
        });
    }

    private TokenClient.TokenResponse acquireToken() throws IOException, InterruptedException {
        String endpoint = required("MCP_TOKEN_ENDPOINT");
        String clientId = required("MCP_CLIENT_ID");
        String clientSecret = required("MCP_CLIENT_SECRET");
        String scope = System.getenv().getOrDefault("MCP_SCOPE", "mcp-api/read");
        return new TokenClient(HttpClient.newHttpClient(), new ObjectMapper())
                .clientCredentials(endpoint, clientId, clientSecret, scope);
    }

    private void withClient(ClientAction action) throws Exception {
        withClient(accessToken(), action);
    }

    private void withClient(String token, ClientAction action) throws Exception {
        String endpoint = System.getenv().getOrDefault("MCP_ENDPOINT", "http://localhost:8090/mcp");
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(endpoint)
                .openConnectionOnStartup(false)
                .connectTimeout(Duration.ofSeconds(10))
                .httpRequestCustomizer((request, method, uri, body, context) -> {
                    request.header("Authorization", "Bearer " + token);
                    String debugPrincipal = System.getenv("MCP_DEBUG_PRINCIPAL_ID");
                    if (debugPrincipal != null && !debugPrincipal.isBlank()) {
                        request.header("X-Mcp-Principal-Id", debugPrincipal);
                        request.header("X-Mcp-Principal-Type",
                                System.getenv().getOrDefault("MCP_DEBUG_PRINCIPAL_TYPE", "SERVICE"));
                        request.header("X-Mcp-Principal-Source", "debug-local");
                    }
                })
                .build();
        long requestTimeoutSeconds = Long.parseLong(
                System.getenv().getOrDefault("MCP_REQUEST_TIMEOUT_SECONDS", "60"));
        try (McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(requestTimeoutSeconds))
                .initializationTimeout(Duration.ofSeconds(requestTimeoutSeconds))
                .clientInfo(new McpSchema.Implementation("secure-mcp-cli", "1.0.0"))
                .build()) {
            action.run(client);
        }
    }

    private static void print(Object value) {
        try {
            System.out.println(McpJsonDefaults.getMapper().writeValueAsString(value));
        } catch (IOException ex) {
            System.out.println(value);
        }
    }

    private static void print(String label, Object value) {
        System.out.print(label + ": ");
        print(value);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少环境变量 " + name);
        }
        return value;
    }

    private String accessToken() throws IOException, InterruptedException {
        String configured = System.getenv("MCP_ACCESS_TOKEN");
        return configured == null || configured.isBlank() ? acquireToken().accessToken() : configured;
    }

    @FunctionalInterface
    private interface ClientAction {
        void run(McpSyncClient client) throws Exception;
    }
}

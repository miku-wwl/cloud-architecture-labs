package com.example.securemcp;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Map;

import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.proxy.model.HttpApiV2ProxyRequest;
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler;
import com.amazonaws.serverless.exceptions.ContainerInitializationException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class StreamLambdaHandler implements RequestStreamHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static volatile SpringBootLambdaContainerHandler<HttpApiV2ProxyRequest, AwsProxyResponse> handler;

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
        JsonNode event = MAPPER.readTree(input);
        normalizeMcpAcceptHeader(event);
        injectValidatedPrincipalHeaders(event);
        getHandler().proxyStream(new ByteArrayInputStream(MAPPER.writeValueAsBytes(event)), output, context);
    }

    private static void normalizeMcpAcceptHeader(JsonNode event) {
        if (!(event instanceof ObjectNode root)) {
            return;
        }
        ObjectNode headers = root.with("headers");
        removeHeader(headers, "accept");
        headers.put("Accept", "application/json; text/event-stream");
    }

    private static SpringBootLambdaContainerHandler<HttpApiV2ProxyRequest, AwsProxyResponse> getHandler() {
        SpringBootLambdaContainerHandler<HttpApiV2ProxyRequest, AwsProxyResponse> current = handler;
        if (current != null) {
            return current;
        }
        synchronized (StreamLambdaHandler.class) {
            current = handler;
            if (current == null) {
                try {
                    current = SpringBootLambdaContainerHandler.getHttpApiV2ProxyHandler(SecureMcpApplication.class);
                    handler = current;
                } catch (ContainerInitializationException ex) {
                    throw new IllegalStateException("Spring Boot Lambda 容器初始化失败", ex);
                }
            }
            return current;
        }
    }

    private static void injectValidatedPrincipalHeaders(JsonNode event) {
        if (!(event instanceof ObjectNode root)) {
            return;
        }
        ObjectNode headers = root.with("headers");
        removeHeader(headers, "x-mcp-principal-id");
        removeHeader(headers, "x-mcp-principal-type");
        removeHeader(headers, "x-mcp-principal-source");

        JsonNode claims = root.path("requestContext").path("authorizer").path("jwt").path("claims");
        String subject = textClaim(claims, "sub");
        String clientId = textClaim(claims, "client_id");
        String id = subject != null ? subject : clientId;
        if (id == null) {
            return;
        }
        headers.put("x-mcp-principal-id", id);
        headers.put("x-mcp-principal-type", subject != null ? Principal.Type.USER.name() : Principal.Type.SERVICE.name());
        headers.put("x-mcp-principal-source", "validated-jwt");
    }

    private static String textClaim(JsonNode claims, String name) {
        JsonNode value = claims.get(name);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private static void removeHeader(ObjectNode headers, String name) {
        Iterator<Map.Entry<String, JsonNode>> fields = headers.fields();
        while (fields.hasNext()) {
            if (fields.next().getKey().equalsIgnoreCase(name)) {
                fields.remove();
            }
        }
    }
}

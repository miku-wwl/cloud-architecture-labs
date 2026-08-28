package com.example.securemcp.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TokenClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TokenClient(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public TokenResponse clientCredentials(String tokenEndpoint, String clientId, String clientSecret, String scope)
            throws IOException, InterruptedException {
        String credentials = Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        String form = "grant_type=client_credentials&scope=" + encode(scope);
        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenEndpoint))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Cognito token endpoint 返回 HTTP " + response.statusCode() + ": " + response.body());
        }
        JsonNode body = objectMapper.readTree(response.body());
        String accessToken = body.path("access_token").asText(null);
        if (accessToken == null || accessToken.isBlank()) {
            throw new IOException("Cognito token 响应没有 access_token");
        }
        return new TokenResponse(accessToken, body.path("expires_in").asInt(0), body.path("scope").asText(scope));
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    public record TokenResponse(String accessToken, int expiresIn, String scope) {
        public String fingerprint() {
            return Integer.toHexString(accessToken.hashCode());
        }
    }
}

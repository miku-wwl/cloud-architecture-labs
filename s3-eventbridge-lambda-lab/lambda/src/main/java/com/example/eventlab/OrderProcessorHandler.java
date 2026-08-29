package com.example.eventlab;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public class OrderProcessorHandler implements RequestHandler<S3EventBridgeEvent, ProcessingResult> {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    private final S3Client s3;
    private final String expectedBucket;

    public OrderProcessorHandler() {
        String region = envOrDefault("AWS_REGION", "us-east-1");
        String endpoint = System.getenv("AWS_ENDPOINT_URL");
        var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .forcePathStyle(Boolean.parseBoolean(envOrDefault("S3_FORCE_PATH_STYLE", "false")));
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        this.s3 = builder.build();
        this.expectedBucket = System.getenv("EXPECTED_BUCKET_NAME");
    }

    @Override
    public ProcessingResult handleRequest(S3EventBridgeEvent event, Context context) {
        String bucket = requireBucket(event);
        String sourceKey = requireInputKey(event);
        if (expectedBucket != null && !expectedBucket.isBlank() && !expectedBucket.equals(bucket)) {
            throw new IllegalArgumentException("event bucket does not match EXPECTED_BUCKET_NAME");
        }

        String payload = s3.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(bucket)
                .key(sourceKey)
                .build()).asUtf8String();

        ProcessingResult result;
        try {
            Order order = MAPPER.readValue(payload, Order.class);
            String validationError = order.validationError();
            result = validationError == null
                    ? ProcessingResult.processed(order, sourceKey, Instant.now().toString())
                    : ProcessingResult.invalid(sourceKey, validationError, Instant.now().toString());
        } catch (JsonProcessingException exception) {
            result = ProcessingResult.invalid(sourceKey, "invalid JSON", Instant.now().toString());
        }

        String resultKey = result.status().equals("PROCESSED")
                ? resultKey(sourceKey)
                : errorKey(sourceKey);
        putResult(bucket, resultKey, result);
        log(context, "processed sourceKey=" + sourceKey + " resultKey=" + resultKey
                + " status=" + result.status());
        return result;
    }

    static String resultKey(String sourceKey) {
        return "processed/" + baseName(sourceKey) + ".result.json";
    }

    static String errorKey(String sourceKey) {
        return "processed/" + baseName(sourceKey) + ".error.json";
    }

    static ProcessingResult process(Order order, String sourceKey, String processedAt) {
        String validationError = order.validationError();
        return validationError == null
                ? ProcessingResult.processed(order, sourceKey, processedAt)
                : ProcessingResult.invalid(sourceKey, validationError, processedAt);
    }

    private void putResult(String bucket, String key, ProcessingResult result) {
        try {
            byte[] body = MAPPER.writeValueAsBytes(result);
            s3.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType("application/json")
                    .build(), RequestBody.fromBytes(body));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize processing result", exception);
        }
    }

    private static String requireBucket(S3EventBridgeEvent event) {
        if (event == null || event.detail() == null || event.detail().bucket() == null
                || event.detail().bucket().name() == null || event.detail().bucket().name().isBlank()) {
            throw new IllegalArgumentException("event detail.bucket.name is required");
        }
        return event.detail().bucket().name();
    }

    private static String requireInputKey(S3EventBridgeEvent event) {
        if (event.detail().object() == null || event.detail().object().key() == null
                || !event.detail().object().key().startsWith("input/")) {
            throw new IllegalArgumentException("event detail.object.key must start with input/");
        }
        return event.detail().object().key();
    }

    private static String baseName(String sourceKey) {
        String fileName = sourceKey.substring(sourceKey.lastIndexOf('/') + 1);
        return fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - 5) : fileName;
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void log(Context context, String message) {
        if (context != null && context.getLogger() != null) {
            context.getLogger().log(message + System.lineSeparator());
        }
    }
}

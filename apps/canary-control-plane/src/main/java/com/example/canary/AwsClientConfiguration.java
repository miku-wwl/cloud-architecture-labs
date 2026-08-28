package com.example.canary;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;

@Configuration
public class AwsClientConfiguration {
    private final URI endpoint;
    private final Region region;
    private final StaticCredentialsProvider credentials;

    public AwsClientConfiguration(
            @Value("${aws.endpoint:http://localhost:4566}") URI endpoint,
            @Value("${aws.region:us-east-1}") String region,
            @Value("${aws.access-key-id:test}") String accessKey,
            @Value("${aws.secret-access-key:test}") String secretKey) {
        this.endpoint = endpoint;
        this.region = Region.of(region);
        this.credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }

    @Bean
    DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder().endpointOverride(endpoint).region(region)
                .credentialsProvider(credentials).build();
    }

    @Bean
    CloudWatchClient cloudWatchClient() {
        return CloudWatchClient.builder().endpointOverride(endpoint).region(region)
                .credentialsProvider(credentials).build();
    }

    @Bean
    EventBridgeClient eventBridgeClient() {
        return EventBridgeClient.builder().endpointOverride(endpoint).region(region)
                .credentialsProvider(credentials).build();
    }
}

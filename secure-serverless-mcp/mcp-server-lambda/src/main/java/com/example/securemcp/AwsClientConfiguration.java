package com.example.securemcp;

import java.net.URI;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
public class AwsClientConfiguration {

    @Bean(destroyMethod = "close")
    DynamoDbClient dynamoDbClient(McpProperties properties) {
        LocalOnlyEndpointGuard.assertAllowed(properties.getAwsEndpointUrl(), properties.isLocalOnly());
        return DynamoDbClient.builder()
                .endpointOverride(URI.create(properties.getAwsEndpointUrl()))
                .region(Region.of(properties.getAwsRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.getAwsAccessKeyId(), properties.getAwsSecretAccessKey())))
                .build();
    }
}

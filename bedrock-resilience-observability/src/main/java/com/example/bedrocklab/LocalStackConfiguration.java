package com.example.bedrocklab;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Pattern;

import io.micrometer.cloudwatch2.CloudWatchConfig;
import io.micrometer.cloudwatch2.CloudWatchMeterRegistry;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.config.MeterFilter;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("local")
@ConditionalOnProperty(name = "lab.localstack.cloudwatch-enabled", havingValue = "true")
public class LocalStackConfiguration {
    private static final Pattern INTEGRAL_LATENCY = Pattern.compile(
            "(\\\"latencyMs\\\"\\s*:\\s*)(-?\\d+)\\.0(?=\\s*[,}])");

    @Bean(destroyMethod = "close")
    CloudWatchAsyncClient localStackCloudWatchClient(BedrockConfiguration.Properties properties,
                                                      AwsCredentialsProvider credentialsProvider) {
        return CloudWatchAsyncClient.builder()
                .endpointOverride(properties.getEndpointOverride())
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean(destroyMethod = "close")
    CloudWatchMeterRegistry localStackCloudWatchRegistry(CloudWatchAsyncClient client) {
        CloudWatchConfig config = new CloudWatchConfig() {
            public String get(String key) { return null; }
            public String namespace() { return "GenAI/BedrockLab"; }
            public Duration step() { return Duration.ofSeconds(5); }
            public int batchSize() { return 20; }
        };
        CloudWatchMeterRegistry registry = new CloudWatchMeterRegistry(config, Clock.SYSTEM, client);
        registry.config().meterFilter(MeterFilter.denyUnless(id -> id.getName().startsWith("genai_bedrock")));
        return registry;
    }

    static byte[] normalizeLatency(byte[] responseBody) {
        String json = new String(responseBody, StandardCharsets.UTF_8);
        return INTEGRAL_LATENCY.matcher(json).replaceAll("$1$2").getBytes(StandardCharsets.UTF_8);
    }

    static final class ConverseResponseInterceptor implements ExecutionInterceptor {
        private static final Logger LOGGER = LoggerFactory.getLogger(ConverseResponseInterceptor.class);

        @Override
        public Optional<InputStream> modifyHttpResponseContent(Context.ModifyHttpResponse context,
                                                               ExecutionAttributes attributes) {
            if (context.httpResponse().statusCode() != 200
                    || !context.httpRequest().encodedPath().endsWith("/converse")
                    || context.responseBody().isEmpty()) {
                return context.responseBody();
            }
            try {
                byte[] original = context.responseBody().orElseThrow().readAllBytes();
                return Optional.of(new ByteArrayInputStream(normalizeLatency(original)));
            } catch (IOException exception) {
                LOGGER.warn("无法转换 LocalStack Converse 响应，保留 AWS SDK 原始失败语义", exception);
                return context.responseBody();
            }
        }
    }
}

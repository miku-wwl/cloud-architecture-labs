package com.example.bedrocklab;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.ssm.SsmClient;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BedrockConfiguration.Properties.class)
public class BedrockConfiguration {
    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger(BedrockConfiguration.class);

    @Bean
    AwsCredentialsProvider awsCredentialsProvider(Properties properties) {
        return properties.isDummyCredentials()
                ? StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"))
                : DefaultCredentialsProvider.create();
    }

    @Bean
    BedrockModelSettings bedrockModelSettings(Properties properties,
                                               AwsCredentialsProvider credentialsProvider) {
        String parameterName = properties.getModelParameterName();
        if (parameterName == null || parameterName.isBlank()) {
            return new BedrockModelSettings(properties.getModelId(), properties.getAllowedModelIds());
        }

        var builder = SsmClient.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(credentialsProvider);
        if (properties.getEndpointOverride() != null) {
            builder.endpointOverride(properties.getEndpointOverride());
        }

        String modelId;
        try (SsmClient client = builder.build()) {
            modelId = client.getParameter(request -> request.name(parameterName)).parameter().value();
        }
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalStateException("SSM 模型参数不能为空: " + parameterName);
        }

        Set<String> allowedModelIds = new LinkedHashSet<>(properties.getAllowedModelIds());
        allowedModelIds.add(modelId);
        LOGGER.info("SSM_MODEL_CONFIG LOADED parameter={} modelId={}", parameterName, modelId);
        return new BedrockModelSettings(modelId, allowedModelIds);
    }

    @Bean
    StandardRetryStrategy bedrockRetryStrategy(Properties properties) {
        return StandardRetryStrategy.builder()
                .maxAttempts(properties.getMaxAttempts())
                .circuitBreakerEnabled(true)
                .build();
    }

    @Bean(destroyMethod = "close")
    BedrockRuntimeClient bedrockClient(Properties properties,
                                       StandardRetryStrategy retryStrategy,
                                       BedrockTelemetry telemetry,
                                       AwsCredentialsProvider credentialsProvider) {
        ClientOverrideConfiguration.Builder overrides = ClientOverrideConfiguration.builder()
                .retryStrategy(retryStrategy)
                .apiCallTimeout(properties.getApiCallTimeout())
                .apiCallAttemptTimeout(properties.getApiCallAttemptTimeout())
                .addMetricPublisher(telemetry);

        if (properties.getEndpointOverride() != null) {
            overrides.addExecutionInterceptor(new LocalStackConfiguration.ConverseResponseInterceptor());
        }

        var builder = BedrockRuntimeClient.builder()
                .region(Region.of(properties.getRegion()))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .overrideConfiguration(overrides.build());

        if (properties.getEndpointOverride() != null) {
            builder.endpointOverride(properties.getEndpointOverride());
        }
        builder.credentialsProvider(credentialsProvider);
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean(OpenTelemetry.class)
    OpenTelemetry openTelemetry() {
        return GlobalOpenTelemetry.get();
    }

    @Bean
    Tracer bedrockTracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer("bedrock-resilience-observability", "1.0.0");
    }

    record BedrockModelSettings(String defaultModelId, Set<String> allowedModelIds) {
        BedrockModelSettings {
            allowedModelIds = Set.copyOf(allowedModelIds);
        }

        boolean allows(String modelId) {
            return allowedModelIds.contains(modelId);
        }
    }

    @ConfigurationProperties(prefix = "lab.bedrock")
    public static class Properties {
        private String region = "us-east-1";
        private String modelId = "amazon.nova-lite-v1:0";
        private String modelParameterName;
        private Set<String> allowedModelIds = new LinkedHashSet<>(Set.of("amazon.nova-lite-v1:0"));
        private URI endpointOverride;
        private boolean dummyCredentials;
        private int maxAttempts = 3;
        private Duration apiCallTimeout = Duration.ofSeconds(30);
        private Duration apiCallAttemptTimeout = Duration.ofSeconds(10);

        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        public String getModelId() { return modelId; }
        public void setModelId(String modelId) { this.modelId = modelId; }
        public String getModelParameterName() { return modelParameterName; }
        public void setModelParameterName(String modelParameterName) { this.modelParameterName = modelParameterName; }
        public Set<String> getAllowedModelIds() { return allowedModelIds; }
        public void setAllowedModelIds(Set<String> allowedModelIds) { this.allowedModelIds = allowedModelIds; }
        public URI getEndpointOverride() { return endpointOverride; }
        public void setEndpointOverride(URI endpointOverride) { this.endpointOverride = endpointOverride; }
        public boolean isDummyCredentials() { return dummyCredentials; }
        public void setDummyCredentials(boolean dummyCredentials) { this.dummyCredentials = dummyCredentials; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public Duration getApiCallTimeout() { return apiCallTimeout; }
        public void setApiCallTimeout(Duration apiCallTimeout) { this.apiCallTimeout = apiCallTimeout; }
        public Duration getApiCallAttemptTimeout() { return apiCallAttemptTimeout; }
        public void setApiCallAttemptTimeout(Duration apiCallAttemptTimeout) {
            this.apiCallAttemptTimeout = apiCallAttemptTimeout;
        }
    }
}

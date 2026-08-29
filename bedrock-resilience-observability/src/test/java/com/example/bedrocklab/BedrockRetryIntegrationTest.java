package com.example.bedrocklab;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.example.bedrocklab.BedrockTelemetry.RetryStats;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(BedrockRetryIntegrationTest.TestTelemetryConfiguration.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BedrockRetryIntegrationTest {
    private static final WireMockServer WIREMOCK = new WireMockServer(options().dynamicPort());
    private static final String PROMPT = "Explain exponential backoff in one sentence.";

    static {
        WIREMOCK.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("lab.bedrock.endpoint-override", () -> WIREMOCK.baseUrl());
        registry.add("lab.bedrock.dummy-credentials", () -> "true");
        registry.add("lab.bedrock.allowed-model-ids",
                () -> "lab.success-model,lab.transient-model,lab.throttle-model,lab.persistent-throttle,lab.fast-model,lab.slow-model");
        registry.add("lab.bedrock.api-call-timeout", () -> "20s");
        registry.add("lab.bedrock.api-call-attempt-timeout", () -> "5s");
        registry.add("lab.localstack.cloudwatch-enabled", () -> "false");
    }

    @Autowired MockMvc mockMvc;
    @Autowired BedrockTelemetry telemetry;
    @Autowired MeterRegistry meterRegistry;
    @Autowired InMemorySpanExporter spanExporter;

    @BeforeEach
    void reset() {
        WIREMOCK.resetAll();
        telemetry.clearRecentStats();
        meterRegistry.clear();
        spanExporter.reset();
    }

    @AfterAll
    static void stopWireMock() {
        WIREMOCK.stop();
    }

    @Test
    @Order(1)
    void successUsesOneAttemptAndRecordsUsage() throws Exception {
        stubSuccess("lab.success-model", "success", 10, 20, 30, 123, 0);

        invoke("lab.success-model")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("success"))
                .andExpect(jsonPath("$.usage.totalTokens").value(30))
                .andExpect(jsonPath("$.retry.sdkRetryCount").value(0));

        WIREMOCK.verify(1, postRequestedFor(path("lab.success-model")));
        RetryStats stats = stats("lab.success-model");
        assertThat(stats.retryCount()).isZero();
        printEvidence("SUCCESS", 1, stats, "HTTP 200");
    }

    @Test
    @Order(2)
    void transient500500200RecoversThroughSdkStandardRetry() throws Exception {
        stubTwoFailuresThenSuccess("transient", "lab.transient-model", 500, "InternalServerException", 120);

        invoke("lab.transient-model")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retry.sdkRetryCount").value(2));

        WIREMOCK.verify(3, postRequestedFor(path("lab.transient-model")));
        RetryStats stats = stats("lab.transient-model");
        assertThat(stats.retryCount()).isEqualTo(2);
        assertThat(stats.totalBackoffDelay()).isPositive();
        printEvidence("TRANSIENT_THEN_SUCCESS", 3, stats, "HTTP 200");
    }

    @Test
    @Order(3)
    void throttle429429200RecoversAndPublishesBackoff() throws Exception {
        stubTwoFailuresThenSuccess("throttle", "lab.throttle-model", 429, "ThrottlingException", 140);

        invoke("lab.throttle-model")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retry.sdkRetryCount").value(2))
                .andExpect(jsonPath("$.retry.sdkBackoffMs").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.retry.throttled").value(true));

        WIREMOCK.verify(3, postRequestedFor(path("lab.throttle-model")));
        RetryStats stats = stats("lab.throttle-model");
        assertThat(stats.retryCount()).isEqualTo(2);
        assertThat(stats.totalBackoffDelay()).isPositive();
        assertThat(stats.errorTypes()).anyMatch(value -> value.contains("Throttling"));
        assertThat(stats.httpStatusCodes()).contains(429);
        printEvidence("THROTTLE_THEN_SUCCESS", 3, stats, "HTTP 200");
    }

    @Test
    @Order(4)
    void persistentThrottleFailsAfterBoundedAttemptsAndMapsTo429() throws Exception {
        WIREMOCK.stubFor(post(path("lab.persistent-throttle"))
                .willReturn(errorResponse(429, "ThrottlingException")));

        invoke("lab.persistent-throttle")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("BEDROCK_THROTTLED"));

        WIREMOCK.verify(3, postRequestedFor(path("lab.persistent-throttle")));
        RetryStats stats = stats("lab.persistent-throttle");
        assertThat(stats.retryCount()).isEqualTo(2);
        assertThat(stats.totalBackoffDelay()).isPositive();
        assertThat(stats.httpStatusCodes()).containsOnly(429);
        printEvidence("PERSISTENT_THROTTLE", 3, stats, "HTTP 429 controlled error");
    }

    @Test
    @Order(5)
    void modelLatencyAndTelemetryAreCorrelatedWithControlledModelId() throws Exception {
        stubSuccess("lab.fast-model", "fast", 3, 5, 8, 25, 0);
        stubSuccess("lab.slow-model", "slow", 4, 9, 13, 900, 60);

        invoke("lab.fast-model")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelLatencyMs").value(25));
        invoke("lab.slow-model")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelLatencyMs").value(900));

        assertThat(spanExporter.getFinishedSpanItems())
                .filteredOn(span -> "bedrock.converse".equals(span.getName()))
                .extracting(span -> span.getAttributes().get(AttributeKey.stringKey("gen_ai.request.model")))
                .contains("lab.fast-model", "lab.slow-model");
        assertThat(meterRegistry.find("genai_bedrock_model_latency").tag("model_id", "lab.slow-model").timer())
                .isNotNull();
        System.out.println("EVIDENCE scenario=MODEL_LATENCY_COMPARISON fastModelLatencyMs=25 slowModelLatencyMs=900 result=PASS");
    }

    @Test
    @Order(6)
    void retryEvidenceComesFromSdkMetricPublisherAndPromptIsNotAMetricLabel() throws Exception {
        stubTwoFailuresThenSuccess("metric-source", "lab.transient-model", 500, "InternalServerException", 100);

        invoke("lab.transient-model")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retry.sdkRetryCount").value(2));

        assertThat(telemetry.recentStats()).isNotEmpty();
        assertThat(stats("lab.transient-model").retryCount()).isEqualTo(2);
        assertThat(meterRegistry.getMeters())
                .flatExtracting(meter -> meter.getId().getTags())
                .noneMatch(tag -> tag.getValue().contains(PROMPT));
    }

    @Test
    @Order(7)
    void unknownModelIsRejectedBeforeCreatingHighCardinalityMetrics() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"safe\",\"modelId\":\"user-controlled-random-model\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MODEL_NOT_ALLOWED"));
        WIREMOCK.verify(0, postRequestedFor(urlPathEqualTo("/model/user-controlled-random-model/converse")));
    }

    private org.springframework.test.web.servlet.ResultActions invoke(String modelId) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"" + PROMPT + "\",\"modelId\":\"" + modelId + "\"}"));
    }

    private static com.github.tomakehurst.wiremock.matching.UrlPathPattern path(String modelId) {
        return urlPathEqualTo("/model/" + modelId + "/converse");
    }

    private static void stubSuccess(String modelId, String answer, int input, int output, int total,
                                    long latencyMs, int fixedDelayMs) {
        WIREMOCK.stubFor(post(path(modelId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withFixedDelay(fixedDelayMs)
                        .withBody(successBody(answer, input, output, total, latencyMs))));
    }

    private static void stubTwoFailuresThenSuccess(String scenario, String modelId, int statusCode,
                                                   String errorType, long latencyMs) {
        WIREMOCK.stubFor(post(path(modelId))
                .inScenario(scenario)
                .whenScenarioStateIs(Scenario.STARTED)
                .willSetStateTo("SECOND")
                .willReturn(errorResponse(statusCode, errorType)));
        WIREMOCK.stubFor(post(path(modelId))
                .inScenario(scenario)
                .whenScenarioStateIs("SECOND")
                .willSetStateTo("THIRD")
                .willReturn(errorResponse(statusCode, errorType)));
        WIREMOCK.stubFor(post(path(modelId))
                .inScenario(scenario)
                .whenScenarioStateIs("THIRD")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(successBody("recovered", 7, 11, 18, latencyMs))));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder errorResponse(
            int statusCode, String errorType) {
        return aResponse()
                .withStatus(statusCode)
                .withHeader("Content-Type", "application/json")
                .withHeader("x-amzn-errortype", errorType)
                .withBody("{\"message\":\"injected failure\",\"__type\":\"" + errorType + "\"}");
    }

    private static String successBody(String answer, int input, int output, int total, long latencyMs) {
        return """
                {
                  "output":{"message":{"role":"assistant","content":[{"text":"%s"}]}},
                  "stopReason":"end_turn",
                  "usage":{"inputTokens":%d,"outputTokens":%d,"totalTokens":%d},
                  "metrics":{"latencyMs":%d}
                }
                """.formatted(answer, input, output, total, latencyMs);
    }

    private RetryStats stats(String modelId) {
        return telemetry.recentStats().stream()
                .filter(item -> modelId.equals(item.modelId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No SDK retry stats for " + modelId));
    }

    private static void printEvidence(String scenario, int attempts, RetryStats stats, String result) {
        System.out.printf(
                "EVIDENCE scenario=%s attempts=%d sdkRetryCount=%d backoffMs=%d throttled=%s result=%s%n",
                scenario,
                attempts,
                stats.retryCount(),
                stats.totalBackoffDelay().toMillis(),
                stats.throttled(),
                result);
    }

    @TestConfiguration
    static class TestTelemetryConfiguration {
        @Bean
        InMemorySpanExporter spanExporter() {
            return InMemorySpanExporter.create();
        }

        @Bean
        @Primary
        OpenTelemetry testOpenTelemetry(InMemorySpanExporter exporter) {
            SdkTracerProvider provider = SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                    .build();
            return OpenTelemetrySdk.builder().setTracerProvider(provider).build();
        }
    }
}

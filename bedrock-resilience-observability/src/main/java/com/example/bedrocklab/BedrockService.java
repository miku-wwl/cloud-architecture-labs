package com.example.bedrocklab;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.UUID;

import com.example.bedrocklab.BedrockTelemetry.RetryStats;
import com.example.bedrocklab.BedrockConfiguration.BedrockModelSettings;
import com.example.bedrocklab.ChatController.ChatRequest;
import com.example.bedrocklab.ChatController.ChatResponse;
import com.example.bedrocklab.ChatController.RetryEvidence;
import com.example.bedrocklab.ChatController.TokenUsage;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.BedrockRuntimeException;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class BedrockService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BedrockService.class);
    private static final AttributeKey<List<String>> XRAY_ANNOTATIONS =
            AttributeKey.stringArrayKey("aws.xray.annotations");

    private final BedrockRuntimeClient client;
    private final BedrockModelSettings modelSettings;
    private final BedrockTelemetry telemetry;
    private final Tracer tracer;

    public BedrockService(BedrockRuntimeClient client,
                          BedrockModelSettings modelSettings,
                          BedrockTelemetry telemetry,
                          Tracer tracer) {
        this.client = client;
        this.modelSettings = modelSettings;
        this.telemetry = telemetry;
        this.tracer = tracer;
    }

    ChatResponse chat(ChatRequest request) {
        String requestId = UUID.randomUUID().toString();
        String modelId = request.modelId() == null || request.modelId().isBlank()
                ? modelSettings.defaultModelId() : request.modelId();
        if (!modelSettings.allows(modelId)) {
            throw failure(requestId, modelId, HttpStatus.BAD_REQUEST,
                    "MODEL_NOT_ALLOWED", "modelId 不在本实验允许列表中", null, RetryStats.empty(modelId));
        }

        Span span = tracer.spanBuilder("bedrock.converse")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("gen_ai.system", "aws.bedrock")
                .setAttribute("gen_ai.request.model", modelId)
                .setAttribute("aws.bedrock.operation", "Converse")
                .setAttribute("model_id", modelId)
                .setAttribute("operation", "Converse")
                .setAttribute("throttled", false)
                .setAttribute(XRAY_ANNOTATIONS, List.of("model_id", "operation", "error_type", "throttled"))
                .startSpan();

        try (Scope ignored = span.makeCurrent(); BedrockTelemetry.Invocation invocation = telemetry.open(modelId)) {
            try {
                ConverseResponse response = client.converse(converseRequest(modelId, request.message()));
                RetryStats retry = invocation.stats();
                long latencyMs = response.metrics().latencyMs();
                int inputTokens = response.usage().inputTokens();
                int outputTokens = response.usage().outputTokens();
                applyRetryAttributes(span, retry);
                span.setAttribute("gen_ai.usage.input_tokens", inputTokens);
                span.setAttribute("gen_ai.usage.output_tokens", outputTokens);
                span.setAttribute("gen_ai.usage.total_tokens", response.usage().totalTokens());
                span.setAttribute("bedrock.model_latency_ms", latencyMs);
                span.setAttribute("error_type", "none");
                telemetry.recordSuccess(modelId, latencyMs, inputTokens, outputTokens);
                LOGGER.info("Bedrock Converse succeeded requestId={} modelId={} traceId={} retryCount={} modelLatencyMs={}",
                        requestId, modelId, span.getSpanContext().getTraceId(), retry.retryCount(), latencyMs);
                return new ChatResponse(
                        requestId,
                        modelId,
                        answer(response),
                        new TokenUsage(inputTokens, outputTokens, response.usage().totalTokens()),
                        latencyMs,
                        new RetryEvidence(retry.retryCount(), retry.totalBackoffDelay().toMillis(), retry.throttled()));
            } catch (BedrockRuntimeException exception) {
                throw classify(requestId, modelId, exception, invocation.stats());
            } catch (SdkClientException exception) {
                RetryStats retry = invocation.stats();
                if (hasCause(exception, SocketTimeoutException.class)) {
                    throw failure(requestId, modelId, HttpStatus.GATEWAY_TIMEOUT,
                            "BEDROCK_TIMEOUT", "模型调用超时。", exception, retry);
                }
                throw failure(requestId, modelId, HttpStatus.BAD_GATEWAY,
                        "BEDROCK_FAILURE", "模型调用失败。", exception, retry);
            }
        } catch (BedrockCallException exception) {
            RetryStats retry = exception.retryStats();
            applyRetryAttributes(span, retry);
            span.setAttribute("error.type", exception.code());
            span.setAttribute("error_type", exception.code());
            span.setAttribute("throttled", retry.throttled());
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR, exception.code());
            telemetry.recordFailure(modelId, exception.code());
            Throwable cause = exception.getCause();
            LOGGER.warn("Bedrock Converse failed requestId={} modelId={} errorType={} retryCount={} backoffMs={} causeType={}",
                    requestId, modelId, exception.code(), retry.retryCount(), retry.totalBackoffDelay().toMillis(),
                    cause == null ? "none" : cause.getClass().getSimpleName());
            throw exception;
        } finally {
            span.end();
        }
    }

    private static ConverseRequest converseRequest(String modelId, String text) {
        return ConverseRequest.builder()
                .modelId(modelId)
                .messages(Message.builder().role(ConversationRole.USER)
                        .content(ContentBlock.fromText(text)).build())
                .inferenceConfig(InferenceConfiguration.builder().maxTokens(256).temperature(0.2f).build())
                .build();
    }

    private static String answer(ConverseResponse response) {
        return response.output().message().content().stream()
                .map(ContentBlock::text)
                .filter(text -> text != null && !text.isBlank())
                .findFirst()
                .orElse("");
    }

    private static BedrockCallException classify(String requestId, String modelId,
                                                  BedrockRuntimeException exception, RetryStats retry) {
        String errorCode = exception.awsErrorDetails() == null ? "" : exception.awsErrorDetails().errorCode();
        if (exception.statusCode() == 429 || retry.throttled() || errorCode.contains("Throttl")) {
            return failure(requestId, modelId, HttpStatus.TOO_MANY_REQUESTS,
                    "BEDROCK_THROTTLED", "模型暂时受到限流，请稍后重试。", exception, retry);
        }
        if (exception.statusCode() == 408 || exception.statusCode() == 504 || errorCode.contains("Timeout")) {
            return failure(requestId, modelId, HttpStatus.GATEWAY_TIMEOUT,
                    "BEDROCK_TIMEOUT", "模型调用超时。", exception, retry);
        }
        if (exception.statusCode() >= 500
                || List.of("ModelNotReadyException", "ServiceUnavailableException").contains(errorCode)) {
            return failure(requestId, modelId, HttpStatus.SERVICE_UNAVAILABLE,
                    "BEDROCK_TRANSIENT_FAILURE", "模型服务暂时不可用。", exception, retry);
        }
        return failure(requestId, modelId, HttpStatus.BAD_GATEWAY,
                "BEDROCK_FAILURE", "模型调用失败。", exception, retry);
    }

    private static BedrockCallException failure(String requestId, String modelId, HttpStatus status,
                                                String code, String message, Throwable cause, RetryStats retry) {
        return new BedrockCallException(requestId, modelId, status, code, message, cause, retry);
    }

    private static void applyRetryAttributes(Span span, RetryStats retry) {
        span.setAttribute("aws.sdk.retry_count", retry.retryCount());
        span.setAttribute("aws.sdk.backoff_delay_ms", retry.totalBackoffDelay().toMillis());
        span.setAttribute("throttled", retry.throttled());
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    public static final class BedrockCallException extends RuntimeException {
        private final String requestId;
        private final String modelId;
        private final HttpStatus status;
        private final String code;
        private final RetryStats retryStats;

        private BedrockCallException(String requestId, String modelId, HttpStatus status, String code,
                                     String message, Throwable cause, RetryStats retryStats) {
            super(message, cause);
            this.requestId = requestId;
            this.modelId = modelId;
            this.status = status;
            this.code = code;
            this.retryStats = retryStats;
        }

        String requestId() { return requestId; }
        String modelId() { return modelId; }
        HttpStatus status() { return status; }
        String code() { return code; }
        RetryStats retryStats() { return retryStats; }
    }
}

package com.example.bedrocklab;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final BedrockService service;

    public ChatController(BedrockService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok(service.chat(request));
    }

    public record ChatRequest(@NotBlank @Size(max = 4000) String message, String modelId) {
    }

    public record ChatResponse(
            String requestId,
            String modelId,
            String answer,
            TokenUsage usage,
            long modelLatencyMs,
            RetryEvidence retry) {
    }

    public record TokenUsage(int inputTokens, int outputTokens, int totalTokens) {
    }

    public record RetryEvidence(int sdkRetryCount, long sdkBackoffMs, boolean throttled) {
    }
}

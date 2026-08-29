package com.example.bedrocklab;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(BedrockService.BedrockCallException.class)
    ResponseEntity<ApiError> bedrockFailure(BedrockService.BedrockCallException exception) {
        return ResponseEntity.status(exception.status()).body(new ApiError(
                exception.requestId(), exception.code(), exception.getMessage(), exception.modelId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidRequest() {
        return ResponseEntity.badRequest().body(new ApiError(
                UUID.randomUUID().toString(), "INVALID_REQUEST", "请求参数无效。", null));
    }

    record ApiError(String requestId, String code, String message, String modelId) {
    }
}

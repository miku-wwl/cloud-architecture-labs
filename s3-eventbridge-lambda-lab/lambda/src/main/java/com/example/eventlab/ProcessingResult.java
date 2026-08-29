package com.example.eventlab;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProcessingResult(
        String orderId,
        String sourceKey,
        String status,
        String processedAt,
        BigDecimal amount,
        String currency,
        String reason) {

    static ProcessingResult processed(Order order, String sourceKey, String processedAt) {
        return new ProcessingResult(order.orderId(), sourceKey, "PROCESSED", processedAt,
                order.amount(), order.currency(), null);
    }

    static ProcessingResult invalid(String sourceKey, String reason, String processedAt) {
        return new ProcessingResult(null, sourceKey, "INVALID", processedAt,
                null, null, reason);
    }
}

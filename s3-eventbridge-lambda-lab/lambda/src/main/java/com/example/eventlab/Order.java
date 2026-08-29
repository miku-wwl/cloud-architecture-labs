package com.example.eventlab;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Order(String orderId, String customerId, BigDecimal amount, String currency) {
    String validationError() {
        if (isBlank(orderId)) {
            return "missing orderId";
        }
        if (isBlank(customerId)) {
            return "missing customerId";
        }
        if (amount == null) {
            return "missing amount";
        }
        if (amount.signum() < 0) {
            return "amount must not be negative";
        }
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            return "currency must be a three-letter uppercase code";
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

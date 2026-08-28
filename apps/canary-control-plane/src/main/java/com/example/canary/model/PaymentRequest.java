package com.example.canary.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

public record PaymentRequest(@NotBlank String orderId, @DecimalMin("0.01") double amount) {
}

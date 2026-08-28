package com.example.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/authorize")
    public Map<String, Object> authorize(@Valid @RequestBody PaymentRequest request) {
        return paymentService.authorize(request.orderId(), request.amount());
    }

    @ExceptionHandler(PaymentService.PaymentFailureException.class)
    public ResponseEntity<Map<String, Object>> paymentFailure(PaymentService.PaymentFailureException ex) {
        return ResponseEntity.internalServerError().body(Map.of(
                "orderId", ex.orderId(),
                "status", "DECLINED",
                "reason", ex.reason(),
                "servedBy", paymentService.version()));
    }

    public record PaymentRequest(@NotBlank String orderId, @DecimalMin("0.01") double amount) {
    }
}

package com.example.payment;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class FaultModeController {
    private final PaymentService paymentService;

    public FaultModeController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PutMapping("/fault-mode/{mode}")
    public Map<String, String> setFaultMode(@PathVariable String mode) {
        FaultMode selected = paymentService.setFaultMode(FaultMode.parse(mode));
        return Map.of("version", paymentService.version(), "faultMode", selected.name());
    }

    @GetMapping("/fault-mode")
    public Map<String, String> getFaultMode() {
        return Map.of("version", paymentService.version(), "faultMode", paymentService.faultMode().name());
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "version", paymentService.version()));
    }
}

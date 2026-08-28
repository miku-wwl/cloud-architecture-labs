package com.example.canary.release;

import com.example.canary.CanaryProperties;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PaymentBackendClient {
    private final RestClient candidate;

    public PaymentBackendClient(CanaryProperties properties, RestClient.Builder builder) {
        this.candidate = builder.baseUrl(properties.candidateUrl.toString()).build();
    }

    public void setCandidateFaultMode(String mode) {
        candidate.put().uri("/internal/fault-mode/{mode}", mode).retrieve().toBodilessEntity();
    }

    public Map<?, ?> candidateHealth() {
        return candidate.get().uri("/internal/health").retrieve().body(Map.class);
    }
}

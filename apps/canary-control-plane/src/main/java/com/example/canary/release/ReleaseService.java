package com.example.canary.release;

import com.example.canary.CanaryProperties;
import com.example.canary.model.ReleaseRecord;
import com.example.canary.model.ReleaseRequest;
import com.example.canary.model.RoutingState;
import com.example.canary.store.DynamoStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

@Service
public class ReleaseService {
    private static final Map<String, String> SCENARIOS = Map.of(
            "HEALTHY", "HEALTHY", "ERROR", "ERROR", "SLOW", "SLOW");
    private final DynamoStore store;
    private final PaymentBackendClient backend;
    private final EventBridgeClient events;
    private final CanaryProperties properties;
    private final ObjectMapper objectMapper;

    public ReleaseService(DynamoStore store, PaymentBackendClient backend, EventBridgeClient events,
                          CanaryProperties properties, ObjectMapper objectMapper) {
        this.store = store;
        this.backend = backend;
        this.events = events;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeRoutingState() {
        store.ensureInitialRoutingState();
    }

    public ReleaseRecord start(ReleaseRequest request) {
        String scenario = request.scenario().trim().toUpperCase();
        if (!SCENARIOS.containsKey(scenario)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scenario must be HEALTHY, ERROR, or SLOW");
        }
        if (!properties.candidateVersion.equals(request.candidateVersion())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "candidateVersion must be " + properties.candidateVersion);
        }

        String releaseId = "rel-" + UUID.randomUUID();
        if (!store.tryAcquireRelease(releaseId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "an active release already exists for payment-api");
        }
        Instant now = Instant.now();
        ReleaseRecord created = new ReleaseRecord(releaseId, properties.serviceName, properties.stableVersion,
                properties.candidateVersion, "CREATED", "CREATED", 0, now.toString(), now.toString(), "", "");
        try {
            backend.setCandidateFaultMode(SCENARIOS.get(scenario));
            store.saveCreatedRelease(created);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("releaseId", releaseId);
            detail.put("serviceName", properties.serviceName);
            detail.put("stableVersion", properties.stableVersion);
            detail.put("candidateVersion", properties.candidateVersion);
            detail.put("evaluationWindowSeconds", properties.evaluationWindowSeconds);
            detail.put("scenario", scenario);
            PutEventsRequestEntry entry = PutEventsRequestEntry.builder()
                    .eventBusName(properties.eventBusName)
                    .source("demo.canary")
                    .detailType("CanaryReleaseRequested")
                    .detail(json(detail)).build();
            var response = events.putEvents(PutEventsRequest.builder().entries(entry).build());
            if (response.failedEntryCount() != null && response.failedEntryCount() > 0) {
                throw new IllegalStateException("EventBridge rejected the release event: " + response.entries());
            }
            return created;
        } catch (RuntimeException ex) {
            store.markReleaseFailed(releaseId, "RELEASE_START_FAILED: " + ex.getMessage());
            throw ex;
        }
    }

    public ReleaseRecord get(String releaseId) {
        return store.getRelease(releaseId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "release not found"));
    }

    public RoutingState routing() {
        return store.getRoutingState();
    }

    public void reset() {
        backend.setCandidateFaultMode("HEALTHY");
        store.resetRoutingState();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("cannot serialize release event", ex);
        }
    }
}

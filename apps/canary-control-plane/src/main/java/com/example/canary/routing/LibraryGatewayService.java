package com.example.canary.routing;

import com.example.canary.CanaryProperties;
import com.example.canary.model.BookBorrowRequest;
import com.example.canary.model.RoutingState;
import com.example.canary.observability.MetricsPublisher;
import com.example.canary.store.DynamoStore;
import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class LibraryGatewayService {
    private static final Logger log = LoggerFactory.getLogger(LibraryGatewayService.class);
    private final DynamoStore store;
    private final DeterministicRouter router;
    private final MetricsPublisher metrics;
    private final CanaryProperties properties;
    private final RestClient stableClient;
    private final RestClient candidateClient;

    public LibraryGatewayService(DynamoStore store, DeterministicRouter router, MetricsPublisher metrics,
                                 CanaryProperties properties, RestClient.Builder restClientBuilder) {
        this.store = store;
        this.router = router;
        this.metrics = metrics;
        this.properties = properties;
        this.stableClient = restClientBuilder.baseUrl(properties.stableUrl.toString()).build();
        this.candidateClient = restClientBuilder.baseUrl(properties.candidateUrl.toString()).build();
    }

    public ResponseEntity<byte[]> borrow(BookBorrowRequest request, String requestedSessionId) {
        String sessionId = requestedSessionId == null || requestedSessionId.isBlank()
                ? "anonymous-" + UUID.randomUUID() : requestedSessionId;
        RoutingState state = store.getRoutingState();
        String servedBy = router.choose(sessionId, state.stableVersion(), state.candidateVersion(), state.candidatePercentage());
        RestClient client = servedBy.equals(state.candidateVersion()) ? candidateClient : stableClient;
        long started = System.nanoTime();
        int status = 502;
        try {
            ResponseEntity<byte[]> response = client.post().uri("/api/books/borrow")
                    .contentType(MediaType.APPLICATION_JSON).header("X-Session-Id", sessionId).body(request)
                    .exchange((outboundRequest, responseMessage) -> {
                        byte[] body;
                        try (var input = responseMessage.getBody()) {
                            body = input == null ? new byte[0] : input.readAllBytes();
                        } catch (IOException ex) {
                            throw new RestClientException("cannot read library backend response", ex);
                        }
                        HttpHeaders headers = new HttpHeaders();
                        headers.putAll(responseMessage.getHeaders());
                        return new ResponseEntity<>(body, headers, responseMessage.getStatusCode());
                    });
            status = response.getStatusCode().value();
            return response;
        } catch (RestClientException ex) {
            log.warn("upstream_call_failed serviceName={} servedBy={} sessionId={} error={}",
                    properties.serviceName, servedBy, sessionId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(("{\"error\":\"library backend unavailable\",\"servedBy\":\"" + servedBy + "\"}")
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } finally {
            long latencyMs = Math.max(0, (System.nanoTime() - started) / 1_000_000);
            metrics.publish(servedBy, status, latencyMs);
            log.info("gateway_request serviceName={} sessionId={} servedBy={} candidatePercentage={} latencyMs={} httpStatus={}",
                    properties.serviceName, sessionId, servedBy, state.candidatePercentage(), latencyMs, status);
        }
    }
}

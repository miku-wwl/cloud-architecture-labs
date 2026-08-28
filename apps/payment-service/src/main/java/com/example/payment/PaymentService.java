package com.example.payment;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private final String appVersion;
    private final AtomicInteger requestCounter = new AtomicInteger();
    private volatile FaultMode faultMode;

    public PaymentService(@Value("${app.version:payment-local}") String appVersion,
                          @Value("${app.fault-mode:HEALTHY}") String initialFaultMode) {
        this.appVersion = appVersion;
        this.faultMode = isStable() ? FaultMode.HEALTHY : FaultMode.parse(initialFaultMode);
    }

    public Map<String, Object> authorize(String orderId, double amount) {
        FaultMode mode = faultMode;
        if (mode == FaultMode.SLOW) {
            sleep(Duration.ofMillis(700));
        }
        int requestNumber = requestCounter.incrementAndGet();
        if (mode == FaultMode.ERROR && requestNumber % 3 == 0) {
            log.info("payment_result version={} faultMode={} requestNumber={} status=500",
                    appVersion, mode, requestNumber);
            throw new PaymentFailureException(orderId, "DETERMINISTIC_CANDIDATE_ERROR");
        }
        log.info("payment_result version={} faultMode={} requestNumber={} status=200",
                appVersion, mode, requestNumber);
        return Map.of("orderId", orderId, "status", "APPROVED", "servedBy", appVersion);
    }

    public FaultMode setFaultMode(FaultMode requestedMode) {
        if (isStable() && requestedMode != FaultMode.HEALTHY) {
            throw new IllegalArgumentException("stable-v1 is permanently HEALTHY");
        }
        faultMode = requestedMode;
        requestCounter.set(0);
        return faultMode;
    }

    public String version() {
        return appVersion;
    }

    public FaultMode faultMode() {
        return faultMode;
    }

    private boolean isStable() {
        return "stable-v1".equals(appVersion);
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("payment request interrupted");
        }
    }

    public static class PaymentFailureException extends RuntimeException {
        private final String orderId;
        private final String reason;

        public PaymentFailureException(String orderId, String reason) {
            super(reason);
            this.orderId = orderId;
            this.reason = reason;
        }

        public String orderId() {
            return orderId;
        }

        public String reason() {
            return reason;
        }
    }
}

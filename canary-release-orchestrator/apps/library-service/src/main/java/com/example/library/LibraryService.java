package com.example.library;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LibraryService {
    private static final Logger log = LoggerFactory.getLogger(LibraryService.class);
    private final String appVersion;
    private final AtomicInteger requestCounter = new AtomicInteger();
    private volatile FaultMode faultMode;

    public LibraryService(@Value("${app.version:library-local}") String appVersion,
                          @Value("${app.fault-mode:HEALTHY}") String initialFaultMode) {
        this.appVersion = appVersion;
        this.faultMode = isStable() ? FaultMode.HEALTHY : FaultMode.parse(initialFaultMode);
    }

    public Map<String, Object> borrow(String bookId, String memberId) {
        FaultMode mode = faultMode;
        if (mode == FaultMode.SLOW) {
            sleep(Duration.ofMillis(700));
        }
        int requestNumber = requestCounter.incrementAndGet();
        if (mode == FaultMode.ERROR && requestNumber % 3 == 0) {
            log.info("library_result version={} faultMode={} requestNumber={} status=500",
                    appVersion, mode, requestNumber);
            throw new LibraryFailureException(bookId, memberId, "DETERMINISTIC_CANDIDATE_ERROR");
        }
        log.info("library_result version={} faultMode={} requestNumber={} status=200",
                appVersion, mode, requestNumber);
        return Map.of("bookId", bookId, "memberId", memberId, "status", "BORROWED", "servedBy", appVersion);
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
            throw new IllegalStateException("library request interrupted");
        }
    }

    public static class LibraryFailureException extends RuntimeException {
        private final String bookId;
        private final String memberId;
        private final String reason;

        public LibraryFailureException(String bookId, String memberId, String reason) {
            super(reason);
            this.bookId = bookId;
            this.memberId = memberId;
            this.reason = reason;
        }

        public String bookId() {
            return bookId;
        }

        public String memberId() {
            return memberId;
        }

        public String reason() {
            return reason;
        }
    }
}

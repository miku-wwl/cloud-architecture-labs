package com.example.canary.routing;

import com.example.canary.model.PaymentRequest;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TrafficService {
    private static final Logger log = LoggerFactory.getLogger(TrafficService.class);
    private final PaymentGatewayService gateway;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private volatile int rps;
    private volatile ScheduledExecutorService executor;
    private volatile ExecutorService workers;

    public TrafficService(PaymentGatewayService gateway) {
        this.gateway = gateway;
    }

    public synchronized Map<String, Object> start(int requestedRps) {
        stopInternal();
        rps = requestedRps;
        running.set(true);
        executor = Executors.newSingleThreadScheduledExecutor(threadFactory());
        workers = Executors.newFixedThreadPool(Math.min(8, Math.max(2, requestedRps / 5)), threadFactory());
        long periodMs = Math.max(10, 1_000L / requestedRps);
        executor.scheduleAtFixedRate(() -> workers.submit(this::sendOne), 0, periodMs, TimeUnit.MILLISECONDS);
        return status();
    }

    public synchronized Map<String, Object> stop() {
        stopInternal();
        return status();
    }

    public Map<String, Object> status() {
        return Map.of("running", running.get(), "rps", rps, "sent", sent.get(), "gatewayErrors", failures.get());
    }

    private void sendOne() {
        long sequence = sent.incrementAndGet();
        String session = "demo-session-" + (sequence % 200);
        try {
            gateway.authorize(new PaymentRequest("demo-order-" + sequence, 1.00), session);
        } catch (RuntimeException ex) {
            failures.incrementAndGet();
            log.warn("demo_traffic_request_failed sequence={} error={}", sequence, ex.getMessage());
        }
    }

    private synchronized void stopInternal() {
        running.set(false);
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        if (workers != null) {
            workers.shutdownNow();
            workers = null;
        }
    }

    private static java.util.concurrent.ThreadFactory threadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "demo-traffic-generator");
            thread.setDaemon(true);
            return thread;
        };
    }
}

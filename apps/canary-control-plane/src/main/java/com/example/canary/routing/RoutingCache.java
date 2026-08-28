package com.example.canary.routing;

import com.example.canary.model.RoutingState;
import com.example.canary.store.DynamoStore;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class RoutingCache {
    private final DynamoStore store;
    private volatile RoutingState cached;
    private volatile long expiresAt;

    public RoutingCache(DynamoStore store) {
        this.store = store;
    }

    public RoutingState get() {
        long now = System.currentTimeMillis();
        if (cached == null || now >= expiresAt) {
            synchronized (this) {
                if (cached == null || now >= expiresAt) {
                    cached = store.getRoutingState();
                    expiresAt = now + 1_000;
                }
            }
        }
        return cached;
    }

    public void invalidate() {
        expiresAt = 0;
    }
}

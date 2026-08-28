package com.example.securemcp;

import java.net.URI;
import java.util.Locale;

public final class LocalOnlyEndpointGuard {

    private LocalOnlyEndpointGuard() {
    }

    public static void assertAllowed(String endpoint, boolean localOnly) {
        if (!localOnly) {
            return;
        }
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("LOCAL_ONLY=true 时必须配置 AWS_ENDPOINT_URL");
        }
        final URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("AWS_ENDPOINT_URL 不是合法 URI: " + endpoint, ex);
        }
        if (!isAllowedHost(uri.getHost())) {
            throw new IllegalStateException("LOCAL_ONLY=true 拒绝非本地 AWS endpoint: " + endpoint);
        }
    }

    static boolean isAllowedHost(String host) {
        if (host == null) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals("localhost")
                || normalized.equals("127.0.0.1")
                || normalized.equals("localstack")
                || normalized.equals("host.docker.internal")
                || normalized.endsWith(".localstack.cloud");
    }
}

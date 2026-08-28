package com.example.securemcp;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LocalOnlyEndpointGuardTest {

    @Test
    void allowsConfiguredLocalStackHosts() {
        assertDoesNotThrow(() -> LocalOnlyEndpointGuard.assertAllowed("http://localhost:4566", true));
        assertDoesNotThrow(() -> LocalOnlyEndpointGuard.assertAllowed("http://localstack:4566", true));
        assertDoesNotThrow(() -> LocalOnlyEndpointGuard.assertAllowed("http://host.docker.internal:4566", true));
        assertDoesNotThrow(() -> LocalOnlyEndpointGuard.assertAllowed("https://api.localstack.cloud", true));
    }

    @Test
    void rejectsRealAwsWhenLocalOnly() {
        assertThrows(IllegalStateException.class,
                () -> LocalOnlyEndpointGuard.assertAllowed("https://dynamodb.us-east-1.amazonaws.com", true));
    }

    @Test
    void canDisableGuardExplicitlyForNonLocalDeployment() {
        assertDoesNotThrow(() -> LocalOnlyEndpointGuard.assertAllowed(
                "https://dynamodb.us-east-1.amazonaws.com", false));
    }
}

package com.example.securemcp.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TokenClientTest {

    @Test
    void fingerprintDoesNotExposeTheToken() {
        TokenClient.TokenResponse response = new TokenClient.TokenResponse("local-token-value", 60, "mcp-api/read");
        assertTrue(!response.fingerprint().contains(response.accessToken()));
    }
}

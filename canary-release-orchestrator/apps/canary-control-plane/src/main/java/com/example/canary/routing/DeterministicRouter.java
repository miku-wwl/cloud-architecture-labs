package com.example.canary.routing;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.springframework.stereotype.Component;

@Component
public class DeterministicRouter {
    public String choose(String sessionId, String stableVersion, String candidateVersion, int candidatePercentage) {
        if (candidatePercentage <= 0) return stableVersion;
        if (candidatePercentage >= 100) return candidateVersion;
        return bucket(sessionId) < candidatePercentage ? candidateVersion : stableVersion;
    }

    public int bucket(String sessionId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(sessionId.getBytes(StandardCharsets.UTF_8));
            return Math.floorMod(ByteBuffer.wrap(digest).getInt(), 100);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by the JDK", ex);
        }
    }
}

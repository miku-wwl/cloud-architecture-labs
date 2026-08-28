package com.example.canary.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DeterministicRouterTest {
    private final DeterministicRouter router = new DeterministicRouter();

    @Test
    void sameSessionAlwaysChoosesTheSameBackend() {
        String first = router.choose("session-42", "stable-v1", "candidate-v2", 25);
        for (int i = 0; i < 20; i++) {
            assertEquals(first, router.choose("session-42", "stable-v1", "candidate-v2", 25));
        }
    }

    @Test
    void boundariesAreExplicit() {
        assertEquals("stable-v1", router.choose("any", "stable-v1", "candidate-v2", 0));
        assertEquals("candidate-v2", router.choose("any", "stable-v1", "candidate-v2", 100));
    }

    @Test
    void manySessionsApproximateFivePercent() {
        long candidate = 0;
        for (int i = 0; i < 10_000; i++) {
            if (router.choose("session-" + i, "stable-v1", "candidate-v2", 5).equals("candidate-v2")) candidate++;
        }
        assertTrue(candidate > 350 && candidate < 650, "candidate count was " + candidate);
    }
}

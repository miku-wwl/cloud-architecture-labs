package com.example.bedrocklab;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class LocalStackConfigurationTest {
    @Test
    void normalizesOnlyIntegralLatencyFloatRequiredByLocalStackCompatibility() {
        String response = """
                {"metrics":{"latencyMs":76266.0},"other":0.2,"text":"76266.0"}
                """;

        String normalized = new String(
                LocalStackConfiguration.normalizeLatency(response.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8);

        assertThat(normalized)
                .contains("\"latencyMs\":76266")
                .contains("\"other\":0.2")
                .contains("\"text\":\"76266.0\"");
    }
}

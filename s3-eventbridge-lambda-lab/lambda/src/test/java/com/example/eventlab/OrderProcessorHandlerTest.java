package com.example.eventlab;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class OrderProcessorHandlerTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesRealisticS3EventBridgeEnvelope() throws Exception {
        String json = """
                {
                  "version":"0",
                  "id":"event-001",
                  "detail-type":"Object Created",
                  "source":"aws.s3",
                  "account":"000000000000",
                  "region":"us-east-1",
                  "detail":{
                    "bucket":{"name":"event-driven-orders"},
                    "object":{"key":"input/order-001.json","size":123,"etag":"abc"},
                    "reason":"PutObject"
                  }
                }
                """;

        S3EventBridgeEvent event = mapper.readValue(json, S3EventBridgeEvent.class);

        assertThat(event.detailType()).isEqualTo("Object Created");
        assertThat(event.source()).isEqualTo("aws.s3");
        assertThat(event.detail().bucket().name()).isEqualTo("event-driven-orders");
        assertThat(event.detail().object().key()).isEqualTo("input/order-001.json");
    }

    @Test
    void transformsInputKeyWithoutCreatingAnInputRecursion() {
        assertThat(OrderProcessorHandler.resultKey("input/order-001.json"))
                .isEqualTo("processed/order-001.result.json");
        assertThat(OrderProcessorHandler.errorKey("input/malformed-order.json"))
                .isEqualTo("processed/malformed-order.error.json");
    }

    @Test
    void generatesProcessedResultAndDeterministicInvalidResult() {
        Order valid = new Order("ORD-001", "CUS-001", new BigDecimal("125.50"), "NZD");
        Order malformed = new Order(null, "CUS-BROKEN", new BigDecimal("12.00"), "NZD");

        assertThat(OrderProcessorHandler.process(valid, "input/order-001.json", "2026-08-30T00:00:00Z"))
                .isEqualTo(new ProcessingResult("ORD-001", "input/order-001.json", "PROCESSED",
                        "2026-08-30T00:00:00Z", new BigDecimal("125.50"), "NZD", null));
        assertThat(OrderProcessorHandler.process(malformed, "input/malformed-order.json", "2026-08-30T00:00:00Z"))
                .isEqualTo(new ProcessingResult(null, "input/malformed-order.json", "INVALID",
                        "2026-08-30T00:00:00Z", null, null, "missing orderId"));
    }
}

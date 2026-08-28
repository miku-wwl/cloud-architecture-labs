package com.example.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PaymentServiceTest {
    @Test
    void errorModeFailsEveryThirdRequestDeterministically() {
        PaymentService service = new PaymentService("candidate-v2", "ERROR");
        assertEquals("APPROVED", service.authorize("o-1", 10).get("status"));
        assertEquals("APPROVED", service.authorize("o-2", 10).get("status"));
        PaymentService.PaymentFailureException failure = assertThrows(
                PaymentService.PaymentFailureException.class,
                () -> service.authorize("o-3", 10));
        assertEquals("DETERMINISTIC_CANDIDATE_ERROR", failure.reason());
    }

    @Test
    void stableCannotBeMadeUnhealthy() {
        PaymentService service = new PaymentService("stable-v1", "ERROR");
        assertEquals(FaultMode.HEALTHY, service.faultMode());
        assertThrows(IllegalArgumentException.class, () -> service.setFaultMode(FaultMode.SLOW));
    }
}

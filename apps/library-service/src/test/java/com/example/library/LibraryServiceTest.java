package com.example.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LibraryServiceTest {
    @Test
    void errorModeFailsEveryThirdRequestDeterministically() {
        LibraryService service = new LibraryService("candidate-v2", "ERROR");
        assertEquals("BORROWED", service.borrow("book-1", "member-1").get("status"));
        assertEquals("BORROWED", service.borrow("book-2", "member-1").get("status"));
        LibraryService.LibraryFailureException failure = assertThrows(
                LibraryService.LibraryFailureException.class,
                () -> service.borrow("book-3", "member-1"));
        assertEquals("DETERMINISTIC_CANDIDATE_ERROR", failure.reason());
    }

    @Test
    void stableCannotBeMadeUnhealthy() {
        LibraryService service = new LibraryService("stable-v1", "ERROR");
        assertEquals(FaultMode.HEALTHY, service.faultMode());
        assertThrows(IllegalArgumentException.class, () -> service.setFaultMode(FaultMode.SLOW));
    }
}

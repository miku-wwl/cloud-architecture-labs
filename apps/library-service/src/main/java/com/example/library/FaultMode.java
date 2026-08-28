package com.example.library;

public enum FaultMode {
    HEALTHY,
    SLOW,
    ERROR;

    public static FaultMode parse(String value) {
        try {
            return value == null ? HEALTHY : valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("mode must be HEALTHY, SLOW, or ERROR");
        }
    }
}

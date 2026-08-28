package com.example.securemcp;

public record Principal(String id, Type type) {

    public enum Type {
        USER,
        SERVICE
    }
}

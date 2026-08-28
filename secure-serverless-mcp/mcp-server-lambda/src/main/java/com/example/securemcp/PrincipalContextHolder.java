package com.example.securemcp;

final class PrincipalContextHolder {

    private static final ThreadLocal<Principal> CURRENT = new ThreadLocal<>();

    private PrincipalContextHolder() {
    }

    static void set(Principal principal) {
        CURRENT.set(principal);
    }

    static Principal get() {
        return CURRENT.get();
    }

    static void clear() {
        CURRENT.remove();
    }
}

package com.example.canary.model;

public record RoutingState(String serviceName, String stableVersion, String candidateVersion,
                           int candidatePercentage, String releaseId, String updatedAt) {
    public static RoutingState initial(String serviceName, String stableVersion, String candidateVersion) {
        return new RoutingState(serviceName, stableVersion, candidateVersion, 0, "none", "");
    }
}

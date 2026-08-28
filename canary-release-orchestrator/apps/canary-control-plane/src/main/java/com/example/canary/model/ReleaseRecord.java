package com.example.canary.model;

public record ReleaseRecord(String releaseId, String serviceName, String stableVersion,
                            String candidateVersion, String status, String currentStage,
                            int candidatePercentage, String startedAt, String updatedAt,
                            String failureReason, String finalDecision) {
}

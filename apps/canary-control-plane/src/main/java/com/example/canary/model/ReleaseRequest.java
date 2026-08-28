package com.example.canary.model;

import jakarta.validation.constraints.NotBlank;

public record ReleaseRequest(@NotBlank String candidateVersion) {
}

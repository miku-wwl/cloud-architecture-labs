package com.example.canary.model;

import jakarta.validation.constraints.NotBlank;

public record BookBorrowRequest(@NotBlank String bookId, @NotBlank String memberId) {
}

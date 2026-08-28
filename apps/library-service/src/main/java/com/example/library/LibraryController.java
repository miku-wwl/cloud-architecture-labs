package com.example.library;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
public class LibraryController {
    private final LibraryService libraryService;

    public LibraryController(LibraryService libraryService) {
        this.libraryService = libraryService;
    }

    @PostMapping("/borrow")
    public Map<String, Object> borrow(@Valid @RequestBody BorrowRequest request) {
        return libraryService.borrow(request.bookId(), request.memberId());
    }

    @ExceptionHandler(LibraryService.LibraryFailureException.class)
    public ResponseEntity<Map<String, Object>> libraryFailure(LibraryService.LibraryFailureException ex) {
        return ResponseEntity.internalServerError().body(Map.of(
                "bookId", ex.bookId(),
                "memberId", ex.memberId(),
                "status", "BORROW_FAILED",
                "reason", ex.reason(),
                "servedBy", libraryService.version()));
    }

    public record BorrowRequest(@NotBlank String bookId, @NotBlank String memberId) {
    }
}

package com.example.library;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class FaultModeController {
    private final LibraryService libraryService;

    public FaultModeController(LibraryService libraryService) {
        this.libraryService = libraryService;
    }

    @PutMapping("/fault-mode/{mode}")
    public Map<String, String> setFaultMode(@PathVariable String mode) {
        FaultMode selected = libraryService.setFaultMode(FaultMode.parse(mode));
        return Map.of("version", libraryService.version(), "faultMode", selected.name());
    }

    @GetMapping("/fault-mode")
    public Map<String, String> getFaultMode() {
        return Map.of("version", libraryService.version(), "faultMode", libraryService.faultMode().name());
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "version", libraryService.version()));
    }
}

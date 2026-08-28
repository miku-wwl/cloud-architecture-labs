package com.example.canary.release;

import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo")
public class DemoController {
    private final ReleaseService releases;

    public DemoController(ReleaseService releases) {
        this.releases = releases;
    }

    @PostMapping("/reset")
    public Map<String, String> reset() {
        releases.reset();
        return Map.of("status", "RESET", "candidatePercentage", "0");
    }
}

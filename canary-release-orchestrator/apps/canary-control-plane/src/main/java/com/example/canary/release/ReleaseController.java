package com.example.canary.release;

import com.example.canary.model.ReleaseRecord;
import com.example.canary.model.ReleaseRequest;
import com.example.canary.model.RoutingState;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/releases")
public class ReleaseController {
    private final ReleaseService releases;

    public ReleaseController(ReleaseService releases) {
        this.releases = releases;
    }

    @PostMapping
    public ResponseEntity<ReleaseRecord> start(@Valid @RequestBody ReleaseRequest request) {
        return ResponseEntity.accepted().body(releases.start(request));
    }

    @GetMapping("/{releaseId}")
    public ReleaseRecord get(@PathVariable String releaseId) {
        return releases.get(releaseId);
    }

    @GetMapping("/routing")
    public RoutingState routing() {
        return releases.routing();
    }
}

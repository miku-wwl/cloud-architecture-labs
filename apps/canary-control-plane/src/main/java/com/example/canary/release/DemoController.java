package com.example.canary.release;

import com.example.canary.routing.TrafficService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo")
public class DemoController {
    private final TrafficService traffic;
    private final ReleaseService releases;

    public DemoController(TrafficService traffic, ReleaseService releases) {
        this.traffic = traffic;
        this.releases = releases;
    }

    @PostMapping("/traffic/start")
    public ResponseEntity<Map<String, Object>> startTraffic(@RequestParam(defaultValue = "10") int rps) {
        if (rps < 1 || rps > 100) return ResponseEntity.badRequest().body(Map.of("error", "rps must be 1..100"));
        return ResponseEntity.ok(traffic.start(rps));
    }

    @PostMapping("/traffic/stop")
    public Map<String, Object> stopTraffic() {
        return traffic.stop();
    }

    @GetMapping("/traffic")
    public Map<String, Object> trafficStatus() {
        return traffic.status();
    }

    @PostMapping("/reset")
    public Map<String, String> reset() {
        releases.reset();
        return Map.of("status", "RESET", "candidatePercentage", "0");
    }
}

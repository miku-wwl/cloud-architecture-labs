package com.example.canary.dashboard;

import com.example.canary.release.ReleaseService;
import com.example.canary.routing.TrafficService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class DashboardController {
    private final ReleaseService releases;
    private final TrafficService traffic;
    private final DashboardService dashboard;

    public DashboardController(ReleaseService releases, TrafficService traffic, DashboardService dashboard) {
        this.releases = releases;
        this.traffic = traffic;
        this.dashboard = dashboard;
    }

    @GetMapping("/")
    public String page(Model model) {
        model.addAttribute("serviceName", "payment-api");
        return "dashboard";
    }

    @GetMapping("/api/dashboard")
    @ResponseBody
    public Map<String, Object> data() {
        return dashboard.snapshot();
    }
}

package com.example.canary.routing;

import com.example.canary.model.PaymentRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentGatewayController {
    private final PaymentGatewayService gateway;

    public PaymentGatewayController(PaymentGatewayService gateway) {
        this.gateway = gateway;
    }

    @PostMapping("/authorize")
    public ResponseEntity<byte[]> authorize(@Valid @RequestBody PaymentRequest request,
                                            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        return gateway.authorize(request, sessionId);
    }
}

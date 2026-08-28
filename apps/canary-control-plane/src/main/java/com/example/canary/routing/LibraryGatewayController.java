package com.example.canary.routing;

import com.example.canary.model.BookBorrowRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
public class LibraryGatewayController {
    private final LibraryGatewayService gateway;

    public LibraryGatewayController(LibraryGatewayService gateway) {
        this.gateway = gateway;
    }

    @PostMapping("/borrow")
    public ResponseEntity<byte[]> borrow(@Valid @RequestBody BookBorrowRequest request,
                                            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        return gateway.borrow(request, sessionId);
    }
}

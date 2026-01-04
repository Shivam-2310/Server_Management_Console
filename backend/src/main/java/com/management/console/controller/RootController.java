package com.management.console.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Root controller to handle requests to the base path.
 * Returns a simple API information response.
 */
@RestController
public class RootController {

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> root() {
        return ResponseEntity.ok(Map.of(
                "name", "Server Management Console API",
                "version", "1.0.0",
                "status", "running",
                "endpoints", Map.of(
                        "health", "/actuator/health",
                        "api", "/api",
                        "docs", "See API documentation"
                )
        ));
    }
}


package com.example.diagnosticservice.controller;

import com.example.diagnosticservice.service.DatabaseLoggingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/database")
@RequiredArgsConstructor
@Slf4j
public class DatabaseHealthController {
    
    private final DatabaseLoggingService databaseLoggingService;
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getDatabaseHealth() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Test database connection by logging a test message
            databaseLoggingService.logMessageReceived(
                "test-message-" + System.currentTimeMillis(),
                "test-topic", 0, 0L, "test-key",
                "Test message for database health check",
                "No error"
            );
            
            response.put("status", "UP");
            response.put("message", "Database connection successful");
            response.put("timestamp", Instant.now().toString());
            
            log.info("Database health check successful");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Database health check failed", e);
            response.put("status", "DOWN");
            response.put("message", "Database connection failed: " + e.getMessage());
            response.put("timestamp", Instant.now().toString());
            response.put("error", e.getClass().getSimpleName());
            
            return ResponseEntity.status(503).body(response);
        }
    }
    
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> testDatabase() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Test various database operations
            databaseLoggingService.logCircuitBreakerEvent(
                "test-circuit-breaker", "TEST_EVENT",
                "CLOSED", "OPEN", 10.5, 5.0,
                100L, 10L, 5L, "Test event for database testing"
            );
            
            response.put("status", "SUCCESS");
            response.put("message", "Database operations test successful");
            response.put("timestamp", Instant.now().toString());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Database test failed", e);
            response.put("status", "FAILED");
            response.put("message", "Database test failed: " + e.getMessage());
            response.put("timestamp", Instant.now().toString());
            response.put("error", e.getClass().getSimpleName());
            
            return ResponseEntity.status(500).body(response);
        }
    }
}

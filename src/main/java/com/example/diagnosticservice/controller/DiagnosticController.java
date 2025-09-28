package com.example.diagnosticservice.controller;

import com.example.diagnosticservice.service.DiagnosticService;
import com.example.diagnosticservice.service.MessageAttemptTracker;
import com.example.diagnosticservice.service.RetryService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/diagnostic")
@Slf4j
public class DiagnosticController {

    private final DiagnosticService diagnosticService;
    private final MessageAttemptTracker attemptTracker;
    private final RetryService retryService;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public DiagnosticController(DiagnosticService diagnosticService,
                              MessageAttemptTracker attemptTracker,
                              RetryService retryService,
                              CircuitBreakerRegistry circuitBreakerRegistry) {
        this.diagnosticService = diagnosticService;
        this.attemptTracker = attemptTracker;
        this.retryService = retryService;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        log.debug("Health check requested");
        
        Map<String, Object> health = new HashMap<>();
        DiagnosticService.DiagnosticServiceHealth serviceHealth = diagnosticService.getHealthStatus();
        
        // Add service health
        health.put("service", serviceHealth);
        
        // Add additional health indicators
        health.put("timestamp", java.time.Instant.now());
        health.put("status", "UP");
        
        // Add database connectivity check
        try {
            // You could add a database health check here
            health.put("database", "UP");
        } catch (Exception e) {
            health.put("database", "DOWN");
            health.put("databaseError", e.getMessage());
        }
        
        // Add Kafka connectivity check
        try {
            // You could add a Kafka health check here
            health.put("kafka", "UP");
        } catch (Exception e) {
            health.put("kafka", "DOWN");
            health.put("kafkaError", e.getMessage());
        }
        
        return ResponseEntity.ok(health);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        log.debug("Stats requested");
        
        try {
            Map<String, Object> stats = new HashMap<>();
            
            // Diagnostic service health
            stats.put("health", diagnosticService.getHealthStatus());
            
            // Attempt tracker stats
            stats.put("attemptTracker", attemptTracker.getStats());
            
            // Retry service stats
            stats.put("retryService", retryService.getRetryStats());
            
            // Circuit breaker stats
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("diagnosticService");
            Map<String, Object> circuitBreakerStats = new HashMap<>();
            circuitBreakerStats.put("state", circuitBreaker.getState().name());
            circuitBreakerStats.put("failureRate", circuitBreaker.getMetrics().getFailureRate());
            circuitBreakerStats.put("numberOfBufferedCalls", circuitBreaker.getMetrics().getNumberOfBufferedCalls());
            circuitBreakerStats.put("numberOfFailedCalls", circuitBreaker.getMetrics().getNumberOfFailedCalls());
            circuitBreakerStats.put("numberOfSuccessfulCalls", circuitBreaker.getMetrics().getNumberOfSuccessfulCalls());
            circuitBreakerStats.put("numberOfNotPermittedCalls", circuitBreaker.getMetrics().getNumberOfNotPermittedCalls());
            stats.put("circuitBreaker", circuitBreakerStats);
            
            // Add timestamp
            stats.put("timestamp", java.time.Instant.now());
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("Error retrieving stats", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to retrieve statistics: " + e.getMessage());
            errorResponse.put("timestamp", java.time.Instant.now());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/circuit-breaker/state")
    public ResponseEntity<Map<String, String>> getCircuitBreakerState() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("diagnosticService");
        
        Map<String, String> state = new HashMap<>();
        state.put("state", circuitBreaker.getState().name());
        state.put("name", circuitBreaker.getName());
        
        return ResponseEntity.ok(state);
    }

    @GetMapping("/circuit-breaker/metrics")
    public ResponseEntity<Map<String, Object>> getCircuitBreakerMetrics() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("diagnosticService");
        
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("failureRate", circuitBreaker.getMetrics().getFailureRate());
        metrics.put("numberOfBufferedCalls", circuitBreaker.getMetrics().getNumberOfBufferedCalls());
        metrics.put("numberOfFailedCalls", circuitBreaker.getMetrics().getNumberOfFailedCalls());
        metrics.put("numberOfSuccessfulCalls", circuitBreaker.getMetrics().getNumberOfSuccessfulCalls());
        metrics.put("numberOfNotPermittedCalls", circuitBreaker.getMetrics().getNumberOfNotPermittedCalls());
        // Note: getAverageResponseTime() is not available in CircuitBreaker.Metrics
        // metrics.put("averageResponseTime", circuitBreaker.getMetrics().getAverageResponseTime());
        
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/database/stats")
    public ResponseEntity<Map<String, Object>> getDatabaseStats() {
        log.debug("Database stats requested");
        
        Map<String, Object> dbStats = new HashMap<>();
        
        try {
            // This would require injecting a database service
            // For now, return a placeholder structure
            dbStats.put("messageLogs", Map.of(
                "total", "N/A - requires database service",
                "success", "N/A - requires database service",
                "failed", "N/A - requires database service"
            ));
            dbStats.put("retryAttempts", "N/A - requires database service");
            dbStats.put("deadLetterMessages", "N/A - requires database service");
            dbStats.put("circuitBreakerEvents", "N/A - requires database service");
            dbStats.put("timestamp", java.time.Instant.now());
            
        } catch (Exception e) {
            log.error("Error retrieving database stats", e);
            dbStats.put("error", "Failed to retrieve database statistics: " + e.getMessage());
        }
        
        return ResponseEntity.ok(dbStats);
    }

    @GetMapping("/kafka/stats")
    public ResponseEntity<Map<String, Object>> getKafkaStats() {
        log.debug("Kafka stats requested");
        
        Map<String, Object> kafkaStats = new HashMap<>();
        
        try {
            // This would require injecting a Kafka admin client
            // For now, return a placeholder structure
            kafkaStats.put("topics", Map.of(
                "projection-processing-queue", "N/A - requires Kafka admin client",
                "failed-projection-messages", "N/A - requires Kafka admin client",
                "dead-letter-queue", "N/A - requires Kafka admin client"
            ));
            kafkaStats.put("consumerGroups", "N/A - requires Kafka admin client");
            kafkaStats.put("timestamp", java.time.Instant.now());
            
        } catch (Exception e) {
            log.error("Error retrieving Kafka stats", e);
            kafkaStats.put("error", "Failed to retrieve Kafka statistics: " + e.getMessage());
        }
        
        return ResponseEntity.ok(kafkaStats);
    }
}

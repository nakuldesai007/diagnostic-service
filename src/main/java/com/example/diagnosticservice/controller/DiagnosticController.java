package com.example.diagnosticservice.controller;

import com.example.diagnosticservice.service.DiagnosticService;
import com.example.diagnosticservice.service.MessageAttemptTracker;
import com.example.diagnosticservice.service.RetryService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
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
    public ResponseEntity<DiagnosticService.DiagnosticServiceHealth> getHealth() {
        log.debug("Health check requested");
        return ResponseEntity.ok(diagnosticService.getHealthStatus());
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        log.debug("Stats requested");
        
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
        
        return ResponseEntity.ok(stats);
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
}

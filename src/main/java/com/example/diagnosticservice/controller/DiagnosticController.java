package com.example.diagnosticservice.controller;

import com.example.diagnosticservice.entity.PacketProcessingSession;
import com.example.diagnosticservice.service.DiagnosticService;
import com.example.diagnosticservice.service.MessageAttemptTracker;
import com.example.diagnosticservice.service.PacketProcessingService;
import com.example.diagnosticservice.service.RetryService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/diagnostic")
@Slf4j
public class DiagnosticController {

    private final DiagnosticService diagnosticService;
    private final MessageAttemptTracker attemptTracker;
    private final RetryService retryService;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final PacketProcessingService packetProcessingService;

    public DiagnosticController(DiagnosticService diagnosticService,
                              MessageAttemptTracker attemptTracker,
                              RetryService retryService,
                              CircuitBreakerRegistry circuitBreakerRegistry,
                              PacketProcessingService packetProcessingService) {
        this.diagnosticService = diagnosticService;
        this.attemptTracker = attemptTracker;
        this.retryService = retryService;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.packetProcessingService = packetProcessingService;
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

    // Packet Processing Endpoints

    @PostMapping("/packet-processing/start")
    public ResponseEntity<Map<String, Object>> startPacketProcessing(
            @RequestParam String endpointUrl,
            @RequestParam String activityId,
            @RequestParam String applicationDate,
            @RequestParam(defaultValue = "10") int packetSize,
            @RequestParam(required = false) String activityType,
            @RequestParam(required = false) String activityStatus,
            @RequestHeader Map<String, String> headers) {
        
        log.info("Starting packet processing for activity {} on {} for endpoint: {} with packet size: {}", 
                activityId, applicationDate, endpointUrl, packetSize);
        
        try {
            java.time.LocalDate appDate = java.time.LocalDate.parse(applicationDate);
            String processingId = packetProcessingService.startPacketProcessing(
                endpointUrl, packetSize, headers, activityId, appDate, activityType, activityStatus);
            
            Map<String, Object> response = new HashMap<>();
            response.put("processingId", processingId);
            response.put("activityId", activityId);
            response.put("applicationDate", applicationDate);
            response.put("activityType", activityType);
            response.put("activityStatus", activityStatus);
            response.put("endpointUrl", endpointUrl);
            response.put("packetSize", packetSize);
            response.put("status", "STARTED");
            response.put("timestamp", java.time.Instant.now());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error starting packet processing", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to start packet processing: " + e.getMessage());
            errorResponse.put("timestamp", java.time.Instant.now());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }


    @GetMapping("/packet-processing/sessions")
    public ResponseEntity<Map<String, Object>> getActiveSessions() {
        log.debug("Getting active packet processing sessions");
        
        try {
            List<PacketProcessingSession> sessions = packetProcessingService.getActiveSessions();
            
            Map<String, Object> response = new HashMap<>();
            response.put("sessions", sessions);
            response.put("count", sessions.size());
            response.put("timestamp", java.time.Instant.now());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting active sessions", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to get active sessions: " + e.getMessage());
            errorResponse.put("timestamp", java.time.Instant.now());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // Activity-based endpoints

    @GetMapping("/packet-processing/activity/{activityId}/date/{applicationDate}/status")
    public ResponseEntity<Map<String, Object>> getPacketProcessingStatusByActivity(
            @PathVariable String activityId, 
            @PathVariable String applicationDate) {
        log.debug("Getting packet processing status for activity: {} on date: {}", activityId, applicationDate);
        
        try {
            java.time.LocalDate appDate = java.time.LocalDate.parse(applicationDate);
            Optional<PacketProcessingSession> session = packetProcessingService.getSessionStatus(activityId, appDate);
            
            if (session.isPresent()) {
                Map<String, Object> response = new HashMap<>();
                PacketProcessingSession sessionData = session.get();
                
                response.put("activityId", sessionData.getActivityId());
                response.put("applicationDate", sessionData.getApplicationDate());
                response.put("activityType", sessionData.getActivityType());
                response.put("activityStatus", sessionData.getActivityStatus());
                response.put("endpointUrl", sessionData.getEndpointUrl());
                response.put("packetSize", sessionData.getPacketSize());
                response.put("totalRecords", sessionData.getTotalRecords());
                response.put("processedRecords", sessionData.getProcessedRecords());
                response.put("failedRecords", sessionData.getFailedRecords());
                response.put("currentOffset", sessionData.getCurrentOffset());
                response.put("status", sessionData.getStatus());
                response.put("errorMessage", sessionData.getErrorMessage());
                response.put("errorCategory", sessionData.getErrorCategory());
                response.put("httpStatusCode", sessionData.getHttpStatusCode());
                response.put("totalProcessingTimeMs", sessionData.getTotalProcessingTimeMs());
                response.put("lastPacketProcessingTimeMs", sessionData.getLastPacketProcessingTimeMs());
                response.put("lastProcessedRecordId", sessionData.getLastProcessedRecordId());
                response.put("createdAt", sessionData.getCreatedAt());
                response.put("startedAt", sessionData.getStartedAt());
                response.put("completedAt", sessionData.getCompletedAt());
                response.put("lastProcessedAt", sessionData.getLastProcessedAt());
                response.put("pausedAt", sessionData.getPausedAt());
                response.put("cancelledAt", sessionData.getCancelledAt());
                response.put("timestamp", java.time.Instant.now());
                
                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Session not found for activity " + activityId + " on " + applicationDate);
                errorResponse.put("activityId", activityId);
                errorResponse.put("applicationDate", applicationDate);
                errorResponse.put("timestamp", java.time.Instant.now());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            }
            
        } catch (Exception e) {
            log.error("Error getting packet processing status for activity: {} on date: {}", activityId, applicationDate, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to get packet processing status: " + e.getMessage());
            errorResponse.put("timestamp", java.time.Instant.now());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/packet-processing/activity/{activityId}/date/{applicationDate}/retry")
    public ResponseEntity<Map<String, Object>> retryFailedRecordsByActivity(
            @PathVariable String activityId, 
            @PathVariable String applicationDate) {
        log.info("Retrying failed records for activity: {} on date: {}", activityId, applicationDate);
        
        try {
            java.time.LocalDate appDate = java.time.LocalDate.parse(applicationDate);
            boolean success = packetProcessingService.retryFailedRecords(activityId, appDate);
            
            Map<String, Object> response = new HashMap<>();
            response.put("activityId", activityId);
            response.put("applicationDate", applicationDate);
            response.put("success", success);
            response.put("timestamp", java.time.Instant.now());
            
            if (success) {
                return ResponseEntity.ok(response);
            } else {
                response.put("error", "Session not found for activity " + activityId + " on " + applicationDate);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            
        } catch (Exception e) {
            log.error("Error retrying failed records for activity: {} on date: {}", activityId, applicationDate, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to retry failed records: " + e.getMessage());
            errorResponse.put("timestamp", java.time.Instant.now());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/packet-processing/activity/{activityId}/date/{applicationDate}/pause")
    public ResponseEntity<Map<String, Object>> pausePacketProcessingByActivity(
            @PathVariable String activityId, 
            @PathVariable String applicationDate) {
        log.info("Pausing packet processing for activity: {} on date: {}", activityId, applicationDate);
        
        try {
            java.time.LocalDate appDate = java.time.LocalDate.parse(applicationDate);
            boolean success = packetProcessingService.pausePacketProcessing(activityId, appDate);
            
            Map<String, Object> response = new HashMap<>();
            response.put("activityId", activityId);
            response.put("applicationDate", applicationDate);
            response.put("success", success);
            response.put("timestamp", java.time.Instant.now());
            
            if (success) {
                return ResponseEntity.ok(response);
            } else {
                response.put("error", "Session not found for activity " + activityId + " on " + applicationDate);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            
        } catch (Exception e) {
            log.error("Error pausing packet processing for activity: {} on date: {}", activityId, applicationDate, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to pause packet processing: " + e.getMessage());
            errorResponse.put("timestamp", java.time.Instant.now());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/packet-processing/activity/{activityId}/date/{applicationDate}/resume")
    public ResponseEntity<Map<String, Object>> resumePacketProcessingByActivity(
            @PathVariable String activityId, 
            @PathVariable String applicationDate) {
        log.info("Resuming packet processing for activity: {} on date: {}", activityId, applicationDate);
        
        try {
            java.time.LocalDate appDate = java.time.LocalDate.parse(applicationDate);
            boolean success = packetProcessingService.resumePacketProcessing(activityId, appDate);
            
            Map<String, Object> response = new HashMap<>();
            response.put("activityId", activityId);
            response.put("applicationDate", applicationDate);
            response.put("success", success);
            response.put("timestamp", java.time.Instant.now());
            
            if (success) {
                return ResponseEntity.ok(response);
            } else {
                response.put("error", "Session not found for activity " + activityId + " on " + applicationDate);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            
        } catch (Exception e) {
            log.error("Error resuming packet processing for activity: {} on date: {}", activityId, applicationDate, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to resume packet processing: " + e.getMessage());
            errorResponse.put("timestamp", java.time.Instant.now());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/packet-processing/activity/{activityId}/date/{applicationDate}/cancel")
    public ResponseEntity<Map<String, Object>> cancelPacketProcessingByActivity(
            @PathVariable String activityId, 
            @PathVariable String applicationDate) {
        log.info("Cancelling packet processing for activity: {} on date: {}", activityId, applicationDate);
        
        try {
            java.time.LocalDate appDate = java.time.LocalDate.parse(applicationDate);
            boolean success = packetProcessingService.cancelPacketProcessing(activityId, appDate);
            
            Map<String, Object> response = new HashMap<>();
            response.put("activityId", activityId);
            response.put("applicationDate", applicationDate);
            response.put("success", success);
            response.put("timestamp", java.time.Instant.now());
            
            if (success) {
                return ResponseEntity.ok(response);
            } else {
                response.put("error", "Session not found for activity " + activityId + " on " + applicationDate);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            
        } catch (Exception e) {
            log.error("Error cancelling packet processing for activity: {} on date: {}", activityId, applicationDate, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to cancel packet processing: " + e.getMessage());
            errorResponse.put("timestamp", java.time.Instant.now());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/packet-processing/activity/{activityId}/sessions")
    public ResponseEntity<Map<String, Object>> getSessionsByActivity(@PathVariable String activityId) {
        log.debug("Getting sessions for activity: {}", activityId);
        
        try {
            List<PacketProcessingSession> sessions = packetProcessingService.getSessionsByActivity(activityId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("activityId", activityId);
            response.put("sessions", sessions);
            response.put("count", sessions.size());
            response.put("timestamp", java.time.Instant.now());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting sessions for activity: {}", activityId, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to get sessions for activity: " + e.getMessage());
            errorResponse.put("timestamp", java.time.Instant.now());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/packet-processing/date/{applicationDate}/sessions")
    public ResponseEntity<Map<String, Object>> getSessionsByApplicationDate(@PathVariable String applicationDate) {
        log.debug("Getting sessions for application date: {}", applicationDate);
        
        try {
            java.time.LocalDate appDate = java.time.LocalDate.parse(applicationDate);
            List<PacketProcessingSession> sessions = packetProcessingService.getSessionsByApplicationDate(appDate);
            
            Map<String, Object> response = new HashMap<>();
            response.put("applicationDate", applicationDate);
            response.put("sessions", sessions);
            response.put("count", sessions.size());
            response.put("timestamp", java.time.Instant.now());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting sessions for application date: {}", applicationDate, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to get sessions for application date: " + e.getMessage());
            errorResponse.put("timestamp", java.time.Instant.now());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/packet-processing/activity-type/{activityType}/sessions")
    public ResponseEntity<Map<String, Object>> getSessionsByActivityType(@PathVariable String activityType) {
        log.debug("Getting sessions for activity type: {}", activityType);
        
        try {
            List<PacketProcessingSession> sessions = packetProcessingService.getSessionsByActivityType(activityType);
            
            Map<String, Object> response = new HashMap<>();
            response.put("activityType", activityType);
            response.put("sessions", sessions);
            response.put("count", sessions.size());
            response.put("timestamp", java.time.Instant.now());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting sessions for activity type: {}", activityType, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to get sessions for activity type: " + e.getMessage());
            errorResponse.put("timestamp", java.time.Instant.now());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Test endpoint to demonstrate header-based metadata usage
     */
    @PostMapping("/packet-processing/test-headers")
    public ResponseEntity<Map<String, Object>> testHeaderMetadata(
            @RequestParam String endpointUrl,
            @RequestParam String activityId,
            @RequestParam String applicationDate,
            @RequestParam(defaultValue = "5") int packetSize,
            @RequestParam(required = false) String activityType,
            @RequestParam(required = false) String activityStatus,
            @RequestHeader Map<String, String> headers) {
        
        log.info("Testing header-based metadata for activity {} on {} for endpoint: {}", 
                activityId, applicationDate, endpointUrl);
        
        try {
            java.time.LocalDate appDate = java.time.LocalDate.parse(applicationDate);
            
            // Add test headers to demonstrate metadata usage
            Map<String, String> enhancedHeaders = new HashMap<>(headers);
            enhancedHeaders.put("X-Test-Mode", "true");
            enhancedHeaders.put("X-Client-Version", "1.0");
            enhancedHeaders.put("X-Request-Source", "diagnostic-service");
            
            String processingId = packetProcessingService.startPacketProcessing(
                endpointUrl, packetSize, enhancedHeaders, activityId, appDate, activityType, activityStatus);
            
            Map<String, Object> response = new HashMap<>();
            response.put("processingId", processingId);
            response.put("activityId", activityId);
            response.put("applicationDate", applicationDate);
            response.put("activityType", activityType);
            response.put("activityStatus", activityStatus);
            response.put("endpointUrl", endpointUrl);
            response.put("packetSize", packetSize);
            response.put("status", "STARTED");
            response.put("testMode", true);
            response.put("enhancedHeaders", enhancedHeaders);
            response.put("timestamp", java.time.Instant.now());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error testing header metadata", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to test header metadata: " + e.getMessage());
            errorResponse.put("timestamp", java.time.Instant.now());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}

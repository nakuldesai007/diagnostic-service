package com.example.diagnosticservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Entity for logging circuit breaker events and state changes
 */
@Entity
@Table(name = "circuit_breaker_events", indexes = {
    @Index(name = "idx_circuit_breaker_name", columnList = "circuitBreakerName"),
    @Index(name = "idx_event_type", columnList = "eventType"),
    @Index(name = "idx_created_at", columnList = "createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CircuitBreakerEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 100)
    private String circuitBreakerName;
    
    @Column(nullable = false, length = 50)
    private String eventType; // STATE_TRANSITION, FAILURE_RATE_EXCEEDED, SLOW_CALL_RATE_EXCEEDED, CALL_NOT_PERMITTED
    
    @Column(length = 50)
    private String fromState;
    
    @Column(length = 50)
    private String toState;
    
    @Column
    private Double failureRate;
    
    @Column
    private Double slowCallRate;
    
    @Column
    private Long callCount;
    
    @Column
    private Long failureCount;
    
    @Column
    private Long slowCallCount;
    
    @Column(columnDefinition = "TEXT")
    private String details;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}

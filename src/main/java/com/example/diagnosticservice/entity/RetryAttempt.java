package com.example.diagnosticservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Entity for logging retry attempts and their outcomes
 */
@Entity
@Table(name = "retry_attempts", indexes = {
    @Index(name = "idx_message_id", columnList = "messageId"),
    @Index(name = "idx_attempt_number", columnList = "attemptNumber"),
    @Index(name = "idx_created_at", columnList = "createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryAttempt {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 255)
    private String messageId;
    
    @Column(nullable = false)
    private Integer attemptNumber;
    
    @Column(length = 50)
    private String status; // SCHEDULED, IN_PROGRESS, SUCCESS, FAILED, CANCELLED
    
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(length = 50)
    private String errorCategory;
    
    @Column
    private Long delayMs;
    
    @Column
    private Long processingTimeMs;
    
    @Column(columnDefinition = "TEXT")
    private String originalMessage;
    
    @Column(columnDefinition = "TEXT")
    private String retryMessage;
    
    @Column(length = 255)
    private String topic;
    
    @Column
    private Integer partition;
    
    @Column(name = "\"offset\"")
    private Long offset;
    
    @Column(columnDefinition = "TEXT")
    private String failureReason;
    
    @Column(columnDefinition = "TEXT")
    private String stackTrace;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column
    private Instant scheduledAt;
    
    @Column
    private Instant startedAt;
    
    @Column
    private Instant completedAt;
}

package com.example.diagnosticservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Entity for logging all messages consumed by the diagnostic service
 */
@Entity
@Table(name = "message_logs", indexes = {
    @Index(name = "idx_message_id", columnList = "messageId"),
    @Index(name = "idx_topic_partition", columnList = "topic, partition"),
    @Index(name = "idx_created_at", columnList = "createdAt"),
    @Index(name = "idx_processing_status", columnList = "processingStatus")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 255)
    private String messageId;
    
    @Column(nullable = false, length = 255)
    private String topic;
    
    @Column(nullable = false)
    private Integer partition;
    
    @Column(nullable = false)
    private Long offset;
    
    @Column(length = 255)
    private String messageKey;
    
    @Column(columnDefinition = "TEXT")
    private String originalMessage;
    
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(length = 50)
    private String errorCategory;
    
    @Column(length = 50)
    private String processingStatus; // RECEIVED, PROCESSING, RETRY, DLQ, SUCCESS, FAILED
    
    @Column
    private Integer attemptCount;
    
    @Column
    private Integer maxRetries;
    
    @Column(length = 50)
    private String circuitBreakerState;
    
    @Column(columnDefinition = "TEXT")
    private String failureReason;
    
    @Column
    private Long processingTimeMs;
    
    @Column(length = 255)
    private String sourceService;
    
    @Column(columnDefinition = "TEXT")
    private String stackTrace;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    
    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
    
    @Column
    private Instant processedAt;
    
    @Column
    private Instant retryScheduledAt;
    
    @Column
    private Instant dlqSentAt;
}

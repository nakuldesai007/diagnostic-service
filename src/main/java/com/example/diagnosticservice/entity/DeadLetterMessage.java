package com.example.diagnosticservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Entity for logging messages sent to Dead Letter Queue
 */
@Entity
@Table(name = "dead_letter_messages", indexes = {
    @Index(name = "idx_message_id", columnList = "messageId"),
    @Index(name = "idx_error_category", columnList = "errorCategory"),
    @Index(name = "idx_created_at", columnList = "createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeadLetterMessage {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 255)
    private String messageId;
    
    @Column(columnDefinition = "TEXT")
    private String originalMessage;
    
    @Column(columnDefinition = "TEXT")
    private String failureReason;
    
    @Column(nullable = false)
    private Integer attemptCount;
    
    @Column(length = 50)
    private String errorCategory;
    
    @Column(length = 255)
    private String sourceTopic;
    
    @Column
    private Integer partition;
    
    @Column
    private Long offset;
    
    @Column(length = 255)
    private String sourceService;
    
    @Column(columnDefinition = "TEXT")
    private String stackTrace;
    
    @Column(columnDefinition = "TEXT")
    private String dlqMessage;
    
    @Column(length = 50)
    private String processingStatus; // SENT, FAILED, PENDING
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column
    private Instant sentAt;
    
    @Column
    private Instant failedAt;
}

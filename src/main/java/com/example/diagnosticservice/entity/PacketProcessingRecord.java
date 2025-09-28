package com.example.diagnosticservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Entity for tracking individual records within packet processing sessions
 */
@Entity
@Table(name = "packet_processing_records", indexes = {
    @Index(name = "idx_packet_activity_id", columnList = "activityId"),
    @Index(name = "idx_packet_application_date", columnList = "applicationDate"),
    @Index(name = "idx_packet_record_id", columnList = "recordId"),
    @Index(name = "idx_packet_status", columnList = "status"),
    @Index(name = "idx_packet_created_at", columnList = "createdAt"),
    @Index(name = "idx_packet_number", columnList = "packetNumber")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PacketProcessingRecord {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 255)
    private String activityId;
    
    @Column(nullable = false)
    private java.time.LocalDate applicationDate;
    
    @Column(nullable = false, length = 255)
    private String recordId;
    
    @Column(nullable = false)
    private Integer packetNumber;
    
    @Column(nullable = false)
    private Integer recordIndex;
    
    @Column(length = 50)
    private String status; // PENDING, PROCESSING, SUCCESS, FAILED, SKIPPED
    
    @Column(columnDefinition = "TEXT")
    private String recordData;
    
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(length = 50)
    private String errorCategory;
    
    @Column(columnDefinition = "TEXT")
    private String failureReason;
    
    @Column
    private Long processingTimeMs;
    
    @Column
    private Integer retryCount;
    
    @Column
    private Integer maxRetries;
    
    @Column(columnDefinition = "TEXT")
    private String stackTrace;
    
    @Column(columnDefinition = "TEXT")
    private String requestData;
    
    @Column(columnDefinition = "TEXT")
    private String responseData;
    
    @Column
    private Integer httpStatusCode;
    
    @Column(columnDefinition = "TEXT")
    private String requestHeaders;
    
    @Column(columnDefinition = "TEXT")
    private String responseHeaders;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column
    private Instant processedAt;
    
    @Column
    private Instant failedAt;
    
    @Column
    private Instant retryScheduledAt;
}

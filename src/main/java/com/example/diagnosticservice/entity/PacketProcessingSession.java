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
 * Entity for tracking packet processing sessions
 */
@Entity
@Table(name = "packet_processing_sessions", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"activityId", "applicationDate"}),
       indexes = {
    @Index(name = "idx_activity_id", columnList = "activityId"),
    @Index(name = "idx_application_date", columnList = "applicationDate"),
    @Index(name = "idx_activity_type", columnList = "activityType"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "createdAt"),
    @Index(name = "idx_endpoint_url", columnList = "endpointUrl")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PacketProcessingSession {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 255)
    private String activityId;
    
    @Column(nullable = false)
    private java.time.LocalDate applicationDate;
    
    @Column(length = 255)
    private String activityType;
    
    @Column(length = 255)
    private String activityStatus;
    
    @Column(nullable = false, length = 500)
    private String endpointUrl;
    
    @Column(nullable = false)
    private Integer packetSize;
    
    @Column(nullable = false)
    private Integer totalRecords;
    
    @Column(nullable = false)
    private Integer processedRecords;
    
    @Column(nullable = false)
    private Integer failedRecords;
    
    @Column(nullable = false)
    private Integer currentOffset;
    
    @Column(length = 50)
    private String status; // ACTIVE, COMPLETED, FAILED, PAUSED, CANCELLED
    
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(length = 50)
    private String errorCategory;
    
    @Column(columnDefinition = "TEXT")
    private String failureReason;
    
    @Column
    private Long totalProcessingTimeMs;
    
    @Column
    private Long lastPacketProcessingTimeMs;
    
    @Column(length = 255)
    private String lastProcessedRecordId;
    
    @Column(columnDefinition = "TEXT")
    private String lastProcessedRecordData;
    
    @Column(columnDefinition = "TEXT")
    private String requestHeaders;
    
    @Column(columnDefinition = "TEXT")
    private String responseHeaders;
    
    @Column
    private Integer httpStatusCode;
    
    @Column(columnDefinition = "TEXT")
    private String stackTrace;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    
    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
    
    @Column
    private Instant startedAt;
    
    @Column
    private Instant completedAt;
    
    @Column
    private Instant lastProcessedAt;
    
    @Column
    private Instant pausedAt;
    
    @Column
    private Instant cancelledAt;
}

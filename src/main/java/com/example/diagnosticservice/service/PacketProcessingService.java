package com.example.diagnosticservice.service;

import com.example.diagnosticservice.entity.PacketProcessingRecord;
import com.example.diagnosticservice.entity.PacketProcessingSession;
import com.example.diagnosticservice.repository.PacketProcessingRecordRepository;
import com.example.diagnosticservice.repository.PacketProcessingSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class PacketProcessingService {

    private final RestClientService restClientService;
    private final PacketProcessingSessionRepository sessionRepository;
    private final PacketProcessingRecordRepository recordRepository;
    private final ErrorClassificationService errorClassificationService;
    private final ObjectMapper objectMapper;

    @Value("${packet.processing.default-packet-size:10}")
    private int defaultPacketSize;

    @Value("${packet.processing.max-retries:3}")
    private int maxRetries;

    @Value("${packet.processing.retry-delay-ms:5000}")
    private long retryDelayMs;

    public PacketProcessingService(RestClientService restClientService,
                                 PacketProcessingSessionRepository sessionRepository,
                                 PacketProcessingRecordRepository recordRepository,
                                 ErrorClassificationService errorClassificationService,
                                 ObjectMapper objectMapper) {
        this.restClientService = restClientService;
        this.sessionRepository = sessionRepository;
        this.recordRepository = recordRepository;
        this.errorClassificationService = errorClassificationService;
        this.objectMapper = objectMapper;
    }

    /**
     * Starts a new packet processing session
     */
    @Transactional
    public String startPacketProcessing(String endpointUrl, int packetSize, Map<String, String> headers, 
                                      String activityId, java.time.LocalDate applicationDate, 
                                      String activityType, String activityStatus) {
        
        log.info("Starting packet processing for activity {} on {} for endpoint: {}", 
                activityId, applicationDate, endpointUrl);
        
        // Check if session already exists for this activity and date
        Optional<PacketProcessingSession> existingSession = sessionRepository
            .findByActivityIdAndApplicationDate(activityId, applicationDate);
        
        if (existingSession.isPresent()) {
            PacketProcessingSession existing = existingSession.get();
            if ("ACTIVE".equals(existing.getStatus()) || "PAUSED".equals(existing.getStatus())) {
                log.warn("Session already exists for activity {} on {}", 
                        activityId, applicationDate);
                return activityId + "-" + applicationDate;
            }
        }
        
        PacketProcessingSession session = PacketProcessingSession.builder()
            .activityId(activityId)
            .applicationDate(applicationDate)
            .activityType(activityType)
            .activityStatus(activityStatus)
            .endpointUrl(endpointUrl)
            .packetSize(packetSize > 0 ? packetSize : defaultPacketSize)
            .totalRecords(0)
            .processedRecords(0)
            .failedRecords(0)
            .currentOffset(0)
            .status("ACTIVE")
            .requestHeaders(convertHeadersToString(headers))
            .createdAt(Instant.now())
            .startedAt(Instant.now())
            .build();
        
        sessionRepository.save(session);
        
        // Start processing asynchronously
        processPacketsAsync(activityId, applicationDate);
        
        return activityId + "-" + applicationDate;
    }

    /**
     * Resumes a paused or failed packet processing session
     */
    @Transactional
    public boolean resumePacketProcessing(String activityId, java.time.LocalDate applicationDate) {
        Optional<PacketProcessingSession> sessionOpt = sessionRepository
            .findByActivityIdAndApplicationDate(activityId, applicationDate);
        
        if (sessionOpt.isEmpty()) {
            log.error("Session not found for activity {} on {}", activityId, applicationDate);
            return false;
        }
        
        PacketProcessingSession session = sessionOpt.get();
        
        if (!"PAUSED".equals(session.getStatus()) && !"FAILED".equals(session.getStatus())) {
            log.warn("Session for activity {} on {} is not in a resumable state: {}", 
                    activityId, applicationDate, session.getStatus());
            return false;
        }
        
        log.info("Resuming packet processing for activity {} on {}", activityId, applicationDate);
        
        session.setStatus("ACTIVE");
        session.setUpdatedAt(Instant.now());
        sessionRepository.save(session);
        
        // Resume processing asynchronously
        processPacketsAsync(activityId, applicationDate);
        
        return true;
    }

    /**
     * Pauses a packet processing session
     */
    @Transactional
    public boolean pausePacketProcessing(String activityId, java.time.LocalDate applicationDate) {
        Optional<PacketProcessingSession> sessionOpt = sessionRepository
            .findByActivityIdAndApplicationDate(activityId, applicationDate);
        
        if (sessionOpt.isEmpty()) {
            log.error("Session not found for activity {} on {}", activityId, applicationDate);
            return false;
        }
        
        PacketProcessingSession session = sessionOpt.get();
        
        if (!"ACTIVE".equals(session.getStatus())) {
            log.warn("Session for activity {} on {} is not active, cannot pause: {}", 
                    activityId, applicationDate, session.getStatus());
            return false;
        }
        
        log.info("Pausing packet processing for activity {} on {}", activityId, applicationDate);
        
        session.setStatus("PAUSED");
        session.setPausedAt(Instant.now());
        session.setUpdatedAt(Instant.now());
        sessionRepository.save(session);
        
        return true;
    }

    /**
     * Cancels a packet processing session
     */
    @Transactional
    public boolean cancelPacketProcessing(String activityId, java.time.LocalDate applicationDate) {
        Optional<PacketProcessingSession> sessionOpt = sessionRepository
            .findByActivityIdAndApplicationDate(activityId, applicationDate);
        
        if (sessionOpt.isEmpty()) {
            log.error("Session not found for activity {} on {}", activityId, applicationDate);
            return false;
        }
        
        PacketProcessingSession session = sessionOpt.get();
        
        if ("COMPLETED".equals(session.getStatus()) || "CANCELLED".equals(session.getStatus())) {
            log.warn("Session for activity {} on {} is already completed or cancelled: {}", 
                    activityId, applicationDate, session.getStatus());
            return false;
        }
        
        log.info("Cancelling packet processing for activity {} on {}", activityId, applicationDate);
        
        session.setStatus("CANCELLED");
        session.setCancelledAt(Instant.now());
        session.setUpdatedAt(Instant.now());
        sessionRepository.save(session);
        
        return true;
    }

    /**
     * Gets the status of a packet processing session by activity attributes
     */
    public Optional<PacketProcessingSession> getSessionStatus(String activityId, java.time.LocalDate applicationDate) {
        return sessionRepository.findByActivityIdAndApplicationDate(activityId, applicationDate);
    }

    /**
     * Gets all active or paused sessions
     */
    public List<PacketProcessingSession> getActiveSessions() {
        return sessionRepository.findActiveOrPausedSessions();
    }

    /**
     * Retries failed records by activity attributes
     */
    @Transactional
    public boolean retryFailedRecords(String activityId, java.time.LocalDate applicationDate) {
        Optional<PacketProcessingSession> sessionOpt = sessionRepository.findByActivityIdAndApplicationDate(activityId, applicationDate);
        
        if (sessionOpt.isEmpty()) {
            log.error("Session not found for activity {} on {}", activityId, applicationDate);
            return false;
        }
        
        PacketProcessingSession session = sessionOpt.get();
        List<PacketProcessingRecord> failedRecords = recordRepository.findRetryableFailedRecordsByActivity(activityId, applicationDate);
        
        if (failedRecords.isEmpty()) {
            log.info("No retryable failed records found for activity {} on {}", activityId, applicationDate);
            return true;
        }
        
        log.info("Retrying {} failed records for activity {} on {}", failedRecords.size(), activityId, applicationDate);
        
        for (PacketProcessingRecord record : failedRecords) {
            record.setStatus("PENDING");
            record.setRetryCount(record.getRetryCount() + 1);
            record.setRetryScheduledAt(Instant.now());
            recordRepository.save(record);
        }
        
        // Resume processing if session is paused
        if ("PAUSED".equals(session.getStatus())) {
            resumePacketProcessing(activityId, applicationDate);
        }
        
        return true;
    }

    /**
     * Gets sessions by activity ID
     */
    public List<PacketProcessingSession> getSessionsByActivity(String activityId) {
        return sessionRepository.findByActivityId(activityId);
    }

    /**
     * Gets sessions by application date
     */
    public List<PacketProcessingSession> getSessionsByApplicationDate(java.time.LocalDate applicationDate) {
        return sessionRepository.findByApplicationDate(applicationDate);
    }

    /**
     * Gets sessions by activity type
     */
    public List<PacketProcessingSession> getSessionsByActivityType(String activityType) {
        return sessionRepository.findByActivityType(activityType);
    }

    /**
     * Asynchronously processes packets for a session
     */
    @Async
    public CompletableFuture<Void> processPacketsAsync(String activityId, java.time.LocalDate applicationDate) {
        try {
            processPackets(activityId, applicationDate);
        } catch (Exception e) {
            log.error("Error processing packets for activity {} on {}", activityId, applicationDate, e);
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Main packet processing logic
     */
    @Transactional
    public void processPackets(String activityId, java.time.LocalDate applicationDate) {
        Optional<PacketProcessingSession> sessionOpt = sessionRepository
            .findByActivityIdAndApplicationDate(activityId, applicationDate);
        
        if (sessionOpt.isEmpty()) {
            log.error("Session not found for activity {} on {}", activityId, applicationDate);
            return;
        }
        
        PacketProcessingSession session = sessionOpt.get();
        
        if (!"ACTIVE".equals(session.getStatus())) {
            log.debug("Session for activity {} on {} is not active, skipping processing: {}", 
                    activityId, applicationDate, session.getStatus());
            return;
        }
        
        log.info("Processing packets for activity {} on {}", activityId, applicationDate);
        
        int packetNumber = 0;
        int totalProcessed = 0;
        int totalFailed = 0;
        long totalProcessingTime = 0;
        
        try {
            while (true) {
                // Check if session is still active
                session = sessionRepository.findByActivityIdAndApplicationDate(activityId, applicationDate).orElse(null);
                if (session == null || !"ACTIVE".equals(session.getStatus())) {
                    log.debug("Session for activity {} on {} is no longer active, stopping processing", activityId, applicationDate);
                    break;
                }
                
                packetNumber++;
                long packetStartTime = System.currentTimeMillis();
                
                // Fetch records for this packet using aggressive retry for packet processing
                RestClientService.RestClientResponse response = restClientService.fetchRecordsForPacketProcessing(
                    session.getEndpointUrl(),
                    session.getCurrentOffset(),
                    session.getPacketSize(),
                    parseHeadersFromString(session.getRequestHeaders())
                );
                
                if (!response.isSuccess()) {
                    log.error("Failed to fetch packet {} for activity {} on {}: {}", 
                            packetNumber, activityId, applicationDate, response.getErrorMessage());
                    
                    // Update session with error
                    session.setStatus("FAILED");
                    session.setErrorMessage(response.getErrorMessage());
                    session.setErrorCategory(response.getErrorCategory());
                    session.setHttpStatusCode(response.getHttpStatusCode());
                    session.setUpdatedAt(Instant.now());
                    sessionRepository.save(session);
                    break;
                }
                
                List<Map<String, Object>> records = response.getRecords();
                if (records == null || records.isEmpty()) {
                    log.info("No more records to process for activity {} on {}", activityId, applicationDate);
                    break;
                }

                // Log packet metadata from headers if available
                if (response.getPacketMetadata() != null) {
                    RestClientService.PacketMetadata metadata = response.getPacketMetadata();
                    log.debug("Packet {} metadata - Total: {}, HasMore: {}, NextOffset: {}, ServerTime: {}ms", 
                            packetNumber, metadata.getTotalRecords(), metadata.isHasMoreRecords(), 
                            metadata.getNextOffset(), metadata.getServerProcessingTime());
                }
                
                // Process each record in the packet
                int packetProcessed = 0;
                int packetFailed = 0;
                
                for (int i = 0; i < records.size(); i++) {
                    Map<String, Object> record = records.get(i);
                    String recordId = extractRecordId(record, activityId, applicationDate, packetNumber, i);
                    
                    PacketProcessingRecord processingRecord = PacketProcessingRecord.builder()
                        .activityId(activityId)
                        .applicationDate(applicationDate)
                        .recordId(recordId)
                        .packetNumber(packetNumber)
                        .recordIndex(i)
                        .status("PENDING")
                        .recordData(convertRecordToString(record))
                        .maxRetries(maxRetries)
                        .retryCount(0)
                        .createdAt(Instant.now())
                        .build();
                    
                    recordRepository.save(processingRecord);
                    
                    // Process the record
                    boolean success = processRecord(processingRecord, record);
                    
                    if (success) {
                        packetProcessed++;
                        totalProcessed++;
                    } else {
                        packetFailed++;
                        totalFailed++;
                    }
                }
                
                long packetProcessingTime = System.currentTimeMillis() - packetStartTime;
                totalProcessingTime += packetProcessingTime;
                
                // Update session progress
                session.setCurrentOffset(session.getCurrentOffset() + records.size());
                session.setProcessedRecords(totalProcessed);
                session.setFailedRecords(totalFailed);
                session.setLastProcessedRecordId(extractRecordId(records.get(records.size() - 1), activityId, applicationDate, packetNumber, records.size() - 1));
                session.setLastProcessedRecordData(convertRecordToString(records.get(records.size() - 1)));
                session.setLastPacketProcessingTimeMs(packetProcessingTime);
                session.setTotalProcessingTimeMs(totalProcessingTime);
                session.setLastProcessedAt(Instant.now());
                session.setUpdatedAt(Instant.now());
                sessionRepository.save(session);
                
                log.info("Processed packet {} for activity {} on {}: {} records, {} successful, {} failed, {}ms", 
                    packetNumber, activityId, applicationDate, records.size(), packetProcessed, packetFailed, packetProcessingTime);
                
                // Check if we've reached the end based on header metadata or record count
                boolean hasMoreRecords = true;
                if (response.getPacketMetadata() != null) {
                    hasMoreRecords = response.getPacketMetadata().isHasMoreRecords();
                    log.debug("Using header metadata - hasMoreRecords: {}", hasMoreRecords);
                } else {
                    // Fallback to record count logic
                    hasMoreRecords = records.size() >= session.getPacketSize();
                    log.debug("Using record count logic - hasMoreRecords: {} (records: {}, packetSize: {})", 
                            hasMoreRecords, records.size(), session.getPacketSize());
                }
                
                if (!hasMoreRecords) {
                    log.info("Reached end of data for activity {} on {} (header metadata or record count)", activityId, applicationDate);
                    break;
                }
            }
            
            // Mark session as completed
            session = sessionRepository.findByActivityIdAndApplicationDate(activityId, applicationDate).orElse(null);
            if (session != null && "ACTIVE".equals(session.getStatus())) {
                session.setStatus("COMPLETED");
                session.setCompletedAt(Instant.now());
                session.setUpdatedAt(Instant.now());
                sessionRepository.save(session);
                
                log.info("Completed packet processing for activity {} on {}: {} total processed, {} total failed", 
                    activityId, applicationDate, totalProcessed, totalFailed);
            }
            
        } catch (Exception e) {
            log.error("Error processing packets for activity {} on {}", activityId, applicationDate, e);
            
            // Mark session as failed
            session = sessionRepository.findByActivityIdAndApplicationDate(activityId, applicationDate).orElse(null);
            if (session != null) {
                session.setStatus("FAILED");
                session.setErrorMessage("Processing error: " + e.getMessage());
                session.setErrorCategory("PROCESSING_ERROR");
                session.setStackTrace(getStackTrace(e));
                session.setUpdatedAt(Instant.now());
                sessionRepository.save(session);
            }
        }
    }

    /**
     * Processes a single record
     */
    private boolean processRecord(PacketProcessingRecord record, Map<String, Object> recordData) {
        long startTime = System.currentTimeMillis();
        
        try {
            record.setStatus("PROCESSING");
            record.setProcessedAt(Instant.now());
            recordRepository.save(record);
            
            // Simulate record processing - in real implementation, this would do actual processing
            // For now, we'll just simulate some processing time and potential failures
            Thread.sleep(100 + (long)(Math.random() * 200)); // 100-300ms processing time
            
            // Simulate occasional failures for testing
            if (Math.random() < 0.1) { // 10% failure rate
                throw new RuntimeException("Simulated processing failure");
            }
            
            record.setStatus("SUCCESS");
            record.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            record.setProcessedAt(Instant.now());
            recordRepository.save(record);
            
            return true;
            
        } catch (Exception e) {
            log.error("Error processing record {} for activity {} on {}: {}", record.getRecordId(), record.getActivityId(), record.getApplicationDate(), e.getMessage());
            
            record.setStatus("FAILED");
            record.setErrorMessage(e.getMessage());
            record.setErrorCategory(errorClassificationService.classifyError(e.getMessage()).getCategory());
            record.setFailureReason("Processing error: " + e.getMessage());
            record.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            record.setFailedAt(Instant.now());
            record.setStackTrace(getStackTrace(e));
            recordRepository.save(record);
            
            return false;
        }
    }

    private String extractRecordId(Map<String, Object> record, String activityId, java.time.LocalDate applicationDate, int packetNumber, int recordIndex) {
        // Try to extract ID from common fields
        if (record.containsKey("id")) {
            return String.valueOf(record.get("id"));
        } else if (record.containsKey("recordId")) {
            return String.valueOf(record.get("recordId"));
        } else if (record.containsKey("key")) {
            return String.valueOf(record.get("key"));
        } else {
            return activityId + "-" + applicationDate + "-" + packetNumber + "-" + recordIndex;
        }
    }

    private String convertRecordToString(Map<String, Object> record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (Exception e) {
            log.warn("Failed to convert record to string: {}", e.getMessage());
            return record.toString();
        }
    }

    private String convertHeadersToString(Map<String, String> headers) {
        try {
            return objectMapper.writeValueAsString(headers);
        } catch (Exception e) {
            log.warn("Failed to convert headers to string: {}", e.getMessage());
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseHeadersFromString(String headersJson) {
        try {
            return objectMapper.readValue(headersJson, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse headers from string: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private String getStackTrace(Exception e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
}

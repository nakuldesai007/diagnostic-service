package com.example.diagnosticservice.service;

import com.example.diagnosticservice.entity.*;
import com.example.diagnosticservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DatabaseLoggingService {
    
    private final MessageLogRepository messageLogRepository;
    private final CircuitBreakerEventRepository circuitBreakerEventRepository;
    private final RetryAttemptRepository retryAttemptRepository;
    private final DeadLetterMessageRepository deadLetterMessageRepository;
    
    @Transactional
    public void logMessageReceived(String messageId, String topic, Integer partition, Long offset, 
                                 String messageKey, String originalMessage, String errorMessage) {
        try {
            MessageLog messageLog = MessageLog.builder()
                    .messageId(messageId)
                    .topic(topic)
                    .partition(partition)
                    .offset(offset)
                    .messageKey(messageKey)
                    .originalMessage(originalMessage)
                    .errorMessage(errorMessage)
                    .processingStatus("RECEIVED")
                    .attemptCount(0)
                    .createdAt(Instant.now())
                    .build();
            
            messageLogRepository.save(messageLog);
            log.debug("Logged message received: {}", messageId);
        } catch (Exception e) {
            log.error("Failed to log message received: {}", messageId, e);
        }
    }
    
    @Transactional
    public void logMessageProcessing(String messageId, String processingStatus, String errorCategory, 
                                   String circuitBreakerState, Long processingTimeMs, String failureReason) {
        try {
            Optional<MessageLog> existingLog = messageLogRepository.findByMessageId(messageId);
            if (existingLog.isPresent()) {
                MessageLog messageLog = existingLog.get();
                messageLog.setProcessingStatus(processingStatus);
                messageLog.setErrorCategory(errorCategory);
                messageLog.setCircuitBreakerState(circuitBreakerState);
                messageLog.setProcessingTimeMs(processingTimeMs);
                messageLog.setFailureReason(failureReason);
                messageLog.setProcessedAt(Instant.now());
                messageLog.setUpdatedAt(Instant.now());
                
                messageLogRepository.save(messageLog);
                log.debug("Updated message processing status: {} -> {}", messageId, processingStatus);
            }
        } catch (Exception e) {
            log.error("Failed to log message processing: {}", messageId, e);
        }
    }
    
    @Transactional
    public void logRetryAttempt(String messageId, Integer attemptNumber, String status, String errorMessage, 
                               String errorCategory, Long delayMs, String originalMessage, String retryMessage,
                               String topic, Integer partition, Long offset) {
        try {
            RetryAttempt retryAttempt = RetryAttempt.builder()
                    .messageId(messageId)
                    .attemptNumber(attemptNumber)
                    .status(status)
                    .errorMessage(errorMessage)
                    .errorCategory(errorCategory)
                    .delayMs(delayMs)
                    .originalMessage(originalMessage)
                    .retryMessage(retryMessage)
                    .topic(topic)
                    .partition(partition)
                    .offset(offset)
                    .createdAt(Instant.now())
                    .scheduledAt(Instant.now())
                    .build();
            
            retryAttemptRepository.save(retryAttempt);
            log.debug("Logged retry attempt: {} (attempt {})", messageId, attemptNumber);
        } catch (Exception e) {
            log.error("Failed to log retry attempt: {}", messageId, e);
        }
    }
    
    @Transactional
    public void logCircuitBreakerEvent(String circuitBreakerName, String eventType, String fromState, 
                                     String toState, Double failureRate, Double slowCallRate, 
                                     Long callCount, Long failureCount, Long slowCallCount, String details) {
        try {
            CircuitBreakerEvent event = CircuitBreakerEvent.builder()
                    .circuitBreakerName(circuitBreakerName)
                    .eventType(eventType)
                    .fromState(fromState)
                    .toState(toState)
                    .failureRate(failureRate)
                    .slowCallRate(slowCallRate)
                    .callCount(callCount)
                    .failureCount(failureCount)
                    .slowCallCount(slowCallCount)
                    .details(details)
                    .createdAt(Instant.now())
                    .build();
            
            circuitBreakerEventRepository.save(event);
            log.debug("Logged circuit breaker event: {} - {}", circuitBreakerName, eventType);
        } catch (Exception e) {
            log.error("Failed to log circuit breaker event: {}", circuitBreakerName, e);
        }
    }
    
    @Transactional
    public void logDeadLetterMessage(String messageId, String originalMessage, String failureReason, 
                                   Integer attemptCount, String errorCategory, String sourceTopic,
                                   Integer partition, Long offset, String sourceService, String stackTrace) {
        try {
            DeadLetterMessage dlqMessage = DeadLetterMessage.builder()
                    .messageId(messageId)
                    .originalMessage(originalMessage)
                    .failureReason(failureReason)
                    .attemptCount(attemptCount)
                    .errorCategory(errorCategory)
                    .sourceTopic(sourceTopic)
                    .partition(partition)
                    .offset(offset)
                    .sourceService(sourceService)
                    .stackTrace(stackTrace)
                    .processingStatus("SENT")
                    .createdAt(Instant.now())
                    .sentAt(Instant.now())
                    .build();
            
            deadLetterMessageRepository.save(dlqMessage);
            log.debug("Logged dead letter message: {}", messageId);
        } catch (Exception e) {
            log.error("Failed to log dead letter message: {}", messageId, e);
        }
    }
    
    @Transactional
    public void updateRetryAttemptStatus(Long retryAttemptId, String status, Long processingTimeMs, 
                                       String failureReason, String stackTrace) {
        try {
            Optional<RetryAttempt> retryAttempt = retryAttemptRepository.findById(retryAttemptId);
            if (retryAttempt.isPresent()) {
                RetryAttempt attempt = retryAttempt.get();
                attempt.setStatus(status);
                attempt.setProcessingTimeMs(processingTimeMs);
                attempt.setFailureReason(failureReason);
                attempt.setStackTrace(stackTrace);
                attempt.setCompletedAt(Instant.now());
                
                retryAttemptRepository.save(attempt);
                log.debug("Updated retry attempt status: {} -> {}", retryAttemptId, status);
            }
        } catch (Exception e) {
            log.error("Failed to update retry attempt status: {}", retryAttemptId, e);
        }
    }
    
    // Query methods for monitoring and analytics
    public List<MessageLog> getMessageHistory(String messageId) {
        return messageLogRepository.findMessageHistory(messageId);
    }
    
    public List<Object[]> getErrorCategoryStats(Instant since) {
        return messageLogRepository.getErrorCategoryStats(since);
    }
    
    public List<Object[]> getProcessingStatusStats(Instant since) {
        return messageLogRepository.getProcessingStatusStats(since);
    }
    
    public Long getMessageCountByStatus(String status, Instant since) {
        return messageLogRepository.countByProcessingStatusSince(status, since);
    }
}

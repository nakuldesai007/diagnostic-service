package com.example.diagnosticservice.service;

import com.example.diagnosticservice.model.DeadLetterMessage;
import com.example.diagnosticservice.model.FailedProjectionMessage;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class DiagnosticService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final CircuitBreaker circuitBreaker;
    private final ErrorClassificationService errorClassificationService;
    private final RetryService retryService;
    private final MessageAttemptTracker attemptTracker;
    private final DatabaseLoggingService databaseLoggingService;

    @Value("${kafka.topics.dead-letter-queue:dead-letter-queue}")
    private String deadLetterQueueTopic;

    @Value("${diagnostic.service.max-retry-attempts:3}")
    private int maxRetryAttempts;

    public DiagnosticService(KafkaTemplate<String, Object> kafkaTemplate,
                           CircuitBreaker circuitBreaker,
                           ErrorClassificationService errorClassificationService,
                           RetryService retryService,
                           MessageAttemptTracker attemptTracker,
                           DatabaseLoggingService databaseLoggingService) {
        this.kafkaTemplate = kafkaTemplate;
        this.circuitBreaker = circuitBreaker;
        this.errorClassificationService = errorClassificationService;
        this.retryService = retryService;
        this.attemptTracker = attemptTracker;
        this.databaseLoggingService = databaseLoggingService;
    }

    @KafkaListener(topics = "${kafka.topics.failed-projection-messages:failed-projection-messages}")
    public void handleFailedProjectionMessage(
            @Payload FailedProjectionMessage failedMessage,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String messageId = failedMessage.getMessageId() != null ? failedMessage.getMessageId() : generateMessageId(topic, partition, offset);
        
        log.info("Received failed projection message: {} from topic: {}, partition: {}, offset: {}", 
                messageId, topic, partition, offset);

        // Log message received to database
        databaseLoggingService.logMessageReceived(
            messageId, topic, partition, offset, 
            "test-key", // You can extract this from headers if needed
            failedMessage.getOriginalMessage(), 
            failedMessage.getErrorMessage()
        );

        long startTime = System.currentTimeMillis();
        try {
            // Execute within circuit breaker
            circuitBreaker.executeSupplier(() -> {
                processFailedMessage(messageId, failedMessage, topic, partition, offset);
                return null;
            });
            
            // Log successful processing
            long processingTime = System.currentTimeMillis() - startTime;
            databaseLoggingService.logMessageProcessing(
                messageId, "SUCCESS", null, 
                circuitBreaker.getState().name(), processingTime, null
            );
            
            // Acknowledge the message only after successful processing
            acknowledgment.acknowledge();
            log.debug("Successfully processed and acknowledged message: {}", messageId);
            
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker is OPEN, sending message {} to DLQ without processing", messageId);
            long processingTime = System.currentTimeMillis() - startTime;
            databaseLoggingService.logMessageProcessing(
                messageId, "CIRCUIT_BREAKER_OPEN", null, 
                circuitBreaker.getState().name(), processingTime, "Circuit breaker open - service unavailable"
            );
            sendToDeadLetterQueue(messageId, failedMessage.getOriginalMessage(), "Circuit breaker open - service unavailable", 0);
            acknowledgment.acknowledge(); // Still acknowledge to prevent reprocessing
            
        } catch (Exception e) {
            log.error("Error processing failed projection message: {}", messageId, e);
            long processingTime = System.currentTimeMillis() - startTime;
            databaseLoggingService.logMessageProcessing(
                messageId, "FAILED", null, 
                circuitBreaker.getState().name(), processingTime, "Processing error: " + e.getMessage()
            );
            sendToDeadLetterQueue(messageId, failedMessage.getOriginalMessage(), "Processing error: " + e.getMessage(), 0);
            acknowledgment.acknowledge(); // Acknowledge to prevent infinite reprocessing
        }
    }

    private void processFailedMessage(String messageId, FailedProjectionMessage failedMessage, String topic, int partition, long offset) {
        log.debug("Processing failed message: {}", messageId);
        
        // Update the failed message with additional metadata if not already set
        if (failedMessage.getSourceTopic() == null) {
            failedMessage = FailedProjectionMessage.builder()
                    .messageId(failedMessage.getMessageId())
                    .originalMessage(failedMessage.getOriginalMessage())
                    .errorMessage(failedMessage.getErrorMessage())
                    .stackTrace(failedMessage.getStackTrace())
                    .failureTimestamp(failedMessage.getFailureTimestamp())
                    .sourceTopic(topic)
                    .partition(partition)
                    .offset(offset)
                    .build();
        }

        String errorMessage = failedMessage.getErrorMessage();
        ErrorClassificationService.ErrorBucket errorBucket = errorClassificationService.classifyError(errorMessage);
        
        log.info("Message {} classified as: {} (retryable: {})", 
                messageId, errorBucket.getCategory(), errorBucket.isRetryable());

        if (errorBucket.isRetryable()) {
            handleRetryableError(messageId, failedMessage.getOriginalMessage(), errorMessage);
        } else {
            log.info("Non-retryable error for message {}, sending to DLQ: {}", messageId, errorBucket.getCategory());
            sendToDeadLetterQueue(messageId, failedMessage.getOriginalMessage(), errorMessage, attemptTracker.getAttemptCount(messageId));
        }
    }

    private void handleRetryableError(String messageId, String message, String errorMessage) {
        int currentAttempts = attemptTracker.getAttemptCount(messageId);
        
        if (currentAttempts >= maxRetryAttempts) {
            log.warn("Message {} has exceeded max retry attempts ({}), sending to DLQ", 
                    messageId, maxRetryAttempts);
            sendToDeadLetterQueue(messageId, message, 
                    "Max retry attempts exceeded: " + errorMessage, currentAttempts);
            return;
        }

        // Check if we can retry this specific error
        if (!retryService.canRetry(messageId, errorMessage)) {
            log.info("Message {} cannot be retried, sending to DLQ", messageId);
            sendToDeadLetterQueue(messageId, message, errorMessage, currentAttempts);
            return;
        }

        // Schedule retry
        retryService.retryMessage(messageId, message, errorMessage);
        log.info("Scheduled retry for message {} (attempt {}/{})", 
                messageId, currentAttempts + 1, maxRetryAttempts);
    }

    private void sendToDeadLetterQueue(String messageId, String originalMessage, String failureReason, int attemptCount) {
        try {
            ErrorClassificationService.ErrorBucket errorBucket = errorClassificationService.classifyError(failureReason);
            
            DeadLetterMessage dlqMessage = DeadLetterMessage.builder()
                    .originalMessage(originalMessage)
                    .failureReason(failureReason)
                    .timestamp(Instant.now())
                    .attemptCount(attemptCount)
                    .errorCategory(errorBucket.getCategory())
                    .build();

            kafkaTemplate.send(deadLetterQueueTopic, messageId, dlqMessage)
                    .whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            log.error("Failed to send message {} to DLQ", messageId, throwable);
                            // Log failed DLQ attempt to database
                            databaseLoggingService.logDeadLetterMessage(
                                messageId, originalMessage, failureReason, attemptCount,
                                errorBucket.getCategory(), "unknown", 0, 0L, "diagnostic-service", 
                                throwable.getMessage()
                            );
                        } else {
                            log.info("Successfully sent message {} to DLQ after {} attempts", 
                                    messageId, attemptCount);
                            // Log successful DLQ send to database
                            databaseLoggingService.logDeadLetterMessage(
                                messageId, originalMessage, failureReason, attemptCount,
                                errorBucket.getCategory(), "unknown", 0, 0L, "diagnostic-service", null
                            );
                        }
                    });

        } catch (Exception e) {
            log.error("Error creating DLQ message for messageId: {}", messageId, e);
            // Log error to database
            databaseLoggingService.logDeadLetterMessage(
                messageId, originalMessage, failureReason, attemptCount,
                "unknown", "unknown", 0, 0L, "diagnostic-service", e.getMessage()
            );
        }
    }

    private String extractErrorMessage(String message) {
        // Simple error message extraction - in real implementation, this would be more sophisticated
        if (message == null || message.trim().isEmpty()) {
            return "Empty or null message";
        }
        
        // Try to extract error information from the message
        // This is a simplified implementation - in practice, you'd parse the actual message structure
        if (message.contains("Exception") || message.contains("Error")) {
            return message;
        }
        
        return "Unknown error in message processing";
    }

    private String generateMessageId(String topic, int partition, long offset) {
        return String.format("%s-%d-%d-%s", topic, partition, offset, UUID.randomUUID().toString().substring(0, 8));
    }

    /**
     * Health check method for monitoring
     */
    public DiagnosticServiceHealth getHealthStatus() {
        var circuitBreakerState = circuitBreaker.getState();
        var attemptStats = attemptTracker.getStats();
        var retryStats = retryService.getRetryStats();
        
        return new DiagnosticServiceHealth(
                circuitBreakerState.name(),
                attemptStats.getTotalMessages(),
                attemptStats.getTotalAttempts(),
                retryStats.getAverageAttemptsPerMessage()
        );
    }

    /**
     * Health status DTO
     */
    public static class DiagnosticServiceHealth {
        private final String circuitBreakerState;
        private final int totalMessages;
        private final long totalAttempts;
        private final double averageAttemptsPerMessage;

        public DiagnosticServiceHealth(String circuitBreakerState, int totalMessages, 
                                     long totalAttempts, double averageAttemptsPerMessage) {
            this.circuitBreakerState = circuitBreakerState;
            this.totalMessages = totalMessages;
            this.totalAttempts = totalAttempts;
            this.averageAttemptsPerMessage = averageAttemptsPerMessage;
        }

        public String getCircuitBreakerState() {
            return circuitBreakerState;
        }

        public int getTotalMessages() {
            return totalMessages;
        }

        public long getTotalAttempts() {
            return totalAttempts;
        }

        public double getAverageAttemptsPerMessage() {
            return averageAttemptsPerMessage;
        }
    }
}

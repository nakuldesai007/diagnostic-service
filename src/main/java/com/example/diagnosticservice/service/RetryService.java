package com.example.diagnosticservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;

@Service
@Slf4j
public class RetryService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MessageAttemptTracker attemptTracker;
    private final ErrorClassificationService errorClassificationService;

    @Value("${diagnostic.service.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${diagnostic.service.retry.initial-delay-ms:1000}")
    private long initialDelayMs;

    @Value("${diagnostic.service.retry.backoff-multiplier:2.0}")
    private double backoffMultiplier;

    @Value("${diagnostic.service.retry.max-delay-ms:30000}")
    private long maxDelayMs;

    @Value("${kafka.topics.projection-processing-queue:projection-processing-queue}")
    private String projectionProcessingTopic;

    public RetryService(KafkaTemplate<String, Object> kafkaTemplate,
                       MessageAttemptTracker attemptTracker,
                       ErrorClassificationService errorClassificationService) {
        this.kafkaTemplate = kafkaTemplate;
        this.attemptTracker = attemptTracker;
        this.errorClassificationService = errorClassificationService;
    }

    /**
     * Retries a message with exponential backoff
     *
     * @param messageId The message identifier
     * @param originalMessage The original message content
     * @param errorMessage The error message that caused the failure
     */
    public void retryMessage(String messageId, String originalMessage, String errorMessage) {
        int currentAttempts = attemptTracker.getAttemptCount(messageId);
        
        if (currentAttempts >= maxRetryAttempts) {
            log.warn("Message {} has exceeded max retry attempts ({}), will be sent to DLQ", 
                    messageId, maxRetryAttempts);
            return;
        }

        // Check if error is retryable
        if (!errorClassificationService.shouldRetry(errorMessage)) {
            log.info("Error for message {} is not retryable: {}", messageId, errorMessage);
            return;
        }

        // Calculate delay with exponential backoff
        long delay = calculateBackoffDelay(currentAttempts);
        
        log.info("Scheduling retry for message {} (attempt {}/{}) with delay {}ms", 
                messageId, currentAttempts + 1, maxRetryAttempts, delay);

        // Schedule retry with delay using Spring's async
        scheduleRetryWithDelay(messageId, originalMessage, delay);
    }

    /**
     * Schedules a retry with delay using Spring's async mechanism
     */
    @Async
    public void scheduleRetryWithDelay(String messageId, String originalMessage, long delayMs) {
        try {
            // Wait for the specified delay
            Thread.sleep(delayMs);
            
            // Increment attempt count before sending
            attemptTracker.incrementAttemptCount(messageId);
            
            // Send message back to processing queue
            kafkaTemplate.send(projectionProcessingTopic, originalMessage)
                    .whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            log.error("Failed to send retry message {} to processing queue", 
                                    messageId, throwable);
                        } else {
                            log.info("Successfully sent retry message {} to processing queue", messageId);
                        }
                    });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Retry scheduling interrupted for message {}", messageId, e);
        } catch (Exception e) {
            log.error("Error during retry execution for message {}", messageId, e);
        }
    }

    /**
     * Calculates the backoff delay for a given attempt number
     *
     * @param attemptNumber The current attempt number (0-based)
     * @return The delay in milliseconds
     */
    private long calculateBackoffDelay(int attemptNumber) {
        if (attemptNumber <= 0) {
            return initialDelayMs;
        }

        // Calculate exponential backoff with jitter
        long baseDelay = (long) (initialDelayMs * Math.pow(backoffMultiplier, attemptNumber));
        
        // Apply jitter (±25% random variation)
        double jitter = 0.5 + (Math.random() * 0.5); // 0.5 to 1.0
        long jitteredDelay = (long) (baseDelay * jitter);
        
        // Cap at maximum delay
        long finalDelay = Math.min(jitteredDelay, maxDelayMs);
        
        log.debug("Calculated backoff delay for attempt {}: {}ms (base: {}ms, jitter: {})", 
                attemptNumber, finalDelay, baseDelay, jitter);
        
        return finalDelay;
    }

    /**
     * Gets the next retry delay for a message without actually scheduling the retry
     *
     * @param messageId The message identifier
     * @return The next retry delay in milliseconds, or -1 if max attempts reached
     */
    public long getNextRetryDelay(String messageId) {
        int currentAttempts = attemptTracker.getAttemptCount(messageId);
        
        if (currentAttempts >= maxRetryAttempts) {
            return -1;
        }
        
        return calculateBackoffDelay(currentAttempts);
    }

    /**
     * Checks if a message can be retried
     *
     * @param messageId The message identifier
     * @param errorMessage The error message
     * @return true if the message can be retried
     */
    public boolean canRetry(String messageId, String errorMessage) {
        if (attemptTracker.hasExceededMaxAttempts(messageId, maxRetryAttempts)) {
            return false;
        }
        
        return errorClassificationService.shouldRetry(errorMessage);
    }

    /**
     * Gets retry statistics for monitoring
     *
     * @return RetryStats containing current retry statistics
     */
    public RetryStats getRetryStats() {
        var attemptStats = attemptTracker.getStats();
        return new RetryStats(
                attemptStats.getTotalMessages(),
                attemptStats.getTotalAttempts(),
                maxRetryAttempts,
                initialDelayMs,
                backoffMultiplier
        );
    }

    /**
     * Statistics class for retry service
     */
    public static class RetryStats {
        private final int totalMessages;
        private final long totalAttempts;
        private final int maxAttempts;
        private final long initialDelayMs;
        private final double backoffMultiplier;

        public RetryStats(int totalMessages, long totalAttempts, int maxAttempts, 
                         long initialDelayMs, double backoffMultiplier) {
            this.totalMessages = totalMessages;
            this.totalAttempts = totalAttempts;
            this.maxAttempts = maxAttempts;
            this.initialDelayMs = initialDelayMs;
            this.backoffMultiplier = backoffMultiplier;
        }

        public int getTotalMessages() {
            return totalMessages;
        }

        public long getTotalAttempts() {
            return totalAttempts;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public long getInitialDelayMs() {
            return initialDelayMs;
        }

        public double getBackoffMultiplier() {
            return backoffMultiplier;
        }

        public double getAverageAttemptsPerMessage() {
            return totalMessages > 0 ? (double) totalAttempts / totalMessages : 0.0;
        }
    }
}

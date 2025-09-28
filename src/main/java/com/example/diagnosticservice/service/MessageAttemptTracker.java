package com.example.diagnosticservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;

@Component
@Slf4j
public class MessageAttemptTracker {

    private final ConcurrentHashMap<String, AttemptInfo> attemptCounts = new ConcurrentHashMap<>();
    
    @Value("${diagnostic.service.attempt-tracker.ttl-hours:1}")
    private int ttlHours;

    /**
     * Gets the current attempt count for a message
     *
     * @param messageId The message identifier
     * @return The number of attempts made for this message
     */
    public int getAttemptCount(String messageId) {
        AttemptInfo info = attemptCounts.get(messageId);
        return info != null ? info.getAttemptCount() : 0;
    }

    /**
     * Increments the attempt count for a message
     *
     * @param messageId The message identifier
     * @return The new attempt count
     */
    public int incrementAttemptCount(String messageId) {
        AttemptInfo info = attemptCounts.compute(messageId, (key, existing) -> {
            if (existing == null) {
                return new AttemptInfo(1, Instant.now());
            } else {
                return new AttemptInfo(existing.getAttemptCount() + 1, existing.getFirstAttemptTime());
            }
        });
        
        log.debug("Incremented attempt count for message {} to {}", messageId, info.getAttemptCount());
        return info.getAttemptCount();
    }

    /**
     * Resets the attempt count for a message (useful for successful processing)
     *
     * @param messageId The message identifier
     */
    public void resetAttemptCount(String messageId) {
        attemptCounts.remove(messageId);
        log.debug("Reset attempt count for message {}", messageId);
    }

    /**
     * Checks if a message has exceeded the maximum retry attempts
     *
     * @param messageId The message identifier
     * @param maxAttempts The maximum number of attempts allowed
     * @return true if the message has exceeded max attempts
     */
    public boolean hasExceededMaxAttempts(String messageId, int maxAttempts) {
        int currentAttempts = getAttemptCount(messageId);
        boolean exceeded = currentAttempts >= maxAttempts;
        
        if (exceeded) {
            log.warn("Message {} has exceeded max attempts: {}/{}", messageId, currentAttempts, maxAttempts);
        }
        
        return exceeded;
    }

    /**
     * Gets the time since the first attempt for a message
     *
     * @param messageId The message identifier
     * @return Duration since first attempt, or null if message not found
     */
    public Duration getTimeSinceFirstAttempt(String messageId) {
        AttemptInfo info = attemptCounts.get(messageId);
        if (info == null) {
            return null;
        }
        return Duration.between(info.getFirstAttemptTime(), Instant.now());
    }

    /**
     * Gets statistics about tracked messages
     *
     * @return AttemptTrackerStats containing current statistics
     */
    public AttemptTrackerStats getStats() {
        int totalMessages = attemptCounts.size();
        long totalAttempts = attemptCounts.values().stream()
            .mapToLong(AttemptInfo::getAttemptCount)
            .sum();
        
        return new AttemptTrackerStats(totalMessages, totalAttempts);
    }

    /**
     * Cleans up expired entries based on TTL
     * Runs every hour using Spring's scheduling
     */
    @Scheduled(fixedRate = 3600000) // 1 hour in milliseconds
    public void cleanupExpiredEntries() {
        Instant cutoffTime = Instant.now().minus(Duration.ofHours(ttlHours));
        
        int removedCount = 0;
        var iterator = attemptCounts.entrySet().iterator();
        
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().getFirstAttemptTime().isBefore(cutoffTime)) {
                iterator.remove();
                removedCount++;
            }
        }
        
        if (removedCount > 0) {
            log.info("Cleaned up {} expired attempt tracking entries", removedCount);
        }
    }

    /**
     * Shuts down the cleanup executor
     * Note: No longer needed since we're using @Scheduled instead of manual executor
     */
    public void shutdown() {
        // No-op: Spring manages the scheduled task lifecycle
        log.info("MessageAttemptTracker shutdown called - using Spring @Scheduled");
    }

    /**
     * Internal class to track attempt information
     */
    private static class AttemptInfo {
        private final int attemptCount;
        private final Instant firstAttemptTime;

        public AttemptInfo(int attemptCount, Instant firstAttemptTime) {
            this.attemptCount = attemptCount;
            this.firstAttemptTime = firstAttemptTime;
        }

        public int getAttemptCount() {
            return attemptCount;
        }

        public Instant getFirstAttemptTime() {
            return firstAttemptTime;
        }
    }

    /**
     * Statistics class for attempt tracker
     */
    public static class AttemptTrackerStats {
        private final int totalMessages;
        private final long totalAttempts;

        public AttemptTrackerStats(int totalMessages, long totalAttempts) {
            this.totalMessages = totalMessages;
            this.totalAttempts = totalAttempts;
        }

        public int getTotalMessages() {
            return totalMessages;
        }

        public long getTotalAttempts() {
            return totalAttempts;
        }

        public double getAverageAttemptsPerMessage() {
            return totalMessages > 0 ? (double) totalAttempts / totalMessages : 0.0;
        }
    }
}

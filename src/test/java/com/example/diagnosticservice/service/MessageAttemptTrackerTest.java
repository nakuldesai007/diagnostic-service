package com.example.diagnosticservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class MessageAttemptTrackerTest {

    private MessageAttemptTracker messageAttemptTracker;

    @BeforeEach
    void setUp() {
        messageAttemptTracker = new MessageAttemptTracker();
    }

    @Test
    void testGetAttemptCountForNewMessage() {
        int count = messageAttemptTracker.getAttemptCount("new-message-id");
        assertEquals(0, count);
    }

    @Test
    void testIncrementAttemptCount() {
        String messageId = "test-message-1";
        
        // Initially should be 0
        assertEquals(0, messageAttemptTracker.getAttemptCount(messageId));
        
        // Increment and check
        int newCount = messageAttemptTracker.incrementAttemptCount(messageId);
        assertEquals(1, newCount);
        assertEquals(1, messageAttemptTracker.getAttemptCount(messageId));
        
        // Increment again
        newCount = messageAttemptTracker.incrementAttemptCount(messageId);
        assertEquals(2, newCount);
        assertEquals(2, messageAttemptTracker.getAttemptCount(messageId));
    }

    @Test
    void testResetAttemptCount() {
        String messageId = "test-message-2";
        
        // Increment a few times
        messageAttemptTracker.incrementAttemptCount(messageId);
        messageAttemptTracker.incrementAttemptCount(messageId);
        assertEquals(2, messageAttemptTracker.getAttemptCount(messageId));
        
        // Reset and verify
        messageAttemptTracker.resetAttemptCount(messageId);
        assertEquals(0, messageAttemptTracker.getAttemptCount(messageId));
    }

    @Test
    void testHasExceededMaxAttempts() {
        String messageId = "test-message-3";
        int maxAttempts = 3;
        
        // Should not exceed initially
        assertFalse(messageAttemptTracker.hasExceededMaxAttempts(messageId, maxAttempts));
        
        // Increment to max
        messageAttemptTracker.incrementAttemptCount(messageId);
        messageAttemptTracker.incrementAttemptCount(messageId);
        messageAttemptTracker.incrementAttemptCount(messageId);
        
        // Should exceed now
        assertTrue(messageAttemptTracker.hasExceededMaxAttempts(messageId, maxAttempts));
    }

    @Test
    void testGetTimeSinceFirstAttempt() {
        String messageId = "test-message-4";
        
        // Should be null for new message
        assertNull(messageAttemptTracker.getTimeSinceFirstAttempt(messageId));
        
        // Increment and check time
        messageAttemptTracker.incrementAttemptCount(messageId);
        Duration timeSinceFirst = messageAttemptTracker.getTimeSinceFirstAttempt(messageId);
        
        assertNotNull(timeSinceFirst);
        assertTrue(timeSinceFirst.toMillis() >= 0);
        assertTrue(timeSinceFirst.toMillis() < 1000); // Should be very recent
    }

    @Test
    void testGetStats() {
        String messageId1 = "test-message-5";
        String messageId2 = "test-message-6";
        
        // Initially should have 0 messages and 0 attempts
        MessageAttemptTracker.AttemptTrackerStats stats = messageAttemptTracker.getStats();
        assertEquals(0, stats.getTotalMessages());
        assertEquals(0, stats.getTotalAttempts());
        assertEquals(0.0, stats.getAverageAttemptsPerMessage());
        
        // Add some attempts
        messageAttemptTracker.incrementAttemptCount(messageId1);
        messageAttemptTracker.incrementAttemptCount(messageId1);
        messageAttemptTracker.incrementAttemptCount(messageId2);
        
        // Check updated stats
        stats = messageAttemptTracker.getStats();
        assertEquals(2, stats.getTotalMessages());
        assertEquals(3, stats.getTotalAttempts());
        assertEquals(1.5, stats.getAverageAttemptsPerMessage());
    }

    @Test
    void testMultipleMessages() {
        String messageId1 = "message-1";
        String messageId2 = "message-2";
        String messageId3 = "message-3";
        
        // Increment different messages
        messageAttemptTracker.incrementAttemptCount(messageId1);
        messageAttemptTracker.incrementAttemptCount(messageId1);
        messageAttemptTracker.incrementAttemptCount(messageId2);
        messageAttemptTracker.incrementAttemptCount(messageId3);
        messageAttemptTracker.incrementAttemptCount(messageId3);
        messageAttemptTracker.incrementAttemptCount(messageId3);
        
        // Verify individual counts
        assertEquals(2, messageAttemptTracker.getAttemptCount(messageId1));
        assertEquals(1, messageAttemptTracker.getAttemptCount(messageId2));
        assertEquals(3, messageAttemptTracker.getAttemptCount(messageId3));
        
        // Verify total stats
        MessageAttemptTracker.AttemptTrackerStats stats = messageAttemptTracker.getStats();
        assertEquals(3, stats.getTotalMessages());
        assertEquals(6, stats.getTotalAttempts());
        assertEquals(2.0, stats.getAverageAttemptsPerMessage());
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        String messageId = "concurrent-test";
        int numberOfThreads = 10;
        int incrementsPerThread = 100;
        
        Thread[] threads = new Thread[numberOfThreads];
        
        // Create threads that increment the same message
        for (int i = 0; i < numberOfThreads; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    messageAttemptTracker.incrementAttemptCount(messageId);
                }
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Verify final count
        int expectedCount = numberOfThreads * incrementsPerThread;
        assertEquals(expectedCount, messageAttemptTracker.getAttemptCount(messageId));
        
        // Verify stats
        MessageAttemptTracker.AttemptTrackerStats stats = messageAttemptTracker.getStats();
        assertEquals(1, stats.getTotalMessages());
        assertEquals(expectedCount, stats.getTotalAttempts());
    }
}

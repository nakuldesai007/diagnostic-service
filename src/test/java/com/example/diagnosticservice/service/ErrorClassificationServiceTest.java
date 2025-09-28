package com.example.diagnosticservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ErrorClassificationServiceTest {

    private ErrorClassificationService errorClassificationService;

    @BeforeEach
    void setUp() {
        errorClassificationService = new ErrorClassificationService();
    }

    @Test
    void testClassifyTransientError() {
        // Test timeout errors
        ErrorClassificationService.ErrorBucket result = errorClassificationService.classifyError("Connection timeout occurred");
        assertEquals(ErrorClassificationService.ErrorBucket.TRANSIENT_ERROR, result);
        assertTrue(result.isRetryable());

        // Test connection refused
        result = errorClassificationService.classifyError("Connection refused to database");
        assertEquals(ErrorClassificationService.ErrorBucket.TRANSIENT_ERROR, result);
        assertTrue(result.isRetryable());

        // Test service unavailable
        result = errorClassificationService.classifyError("Service temporarily unavailable");
        assertEquals(ErrorClassificationService.ErrorBucket.TRANSIENT_ERROR, result);
        assertTrue(result.isRetryable());
    }

    @Test
    void testClassifyValidationError() {
        // Test validation errors
        ErrorClassificationService.ErrorBucket result = errorClassificationService.classifyError("Validation failed for field 'email'");
        assertEquals(ErrorClassificationService.ErrorBucket.VALIDATION_ERROR, result);
        assertFalse(result.isRetryable());

        // Test invalid format
        result = errorClassificationService.classifyError("Invalid format for date field");
        assertEquals(ErrorClassificationService.ErrorBucket.VALIDATION_ERROR, result);
        assertFalse(result.isRetryable());

        // Test business rule violation
        result = errorClassificationService.classifyError("Business rule violation: amount exceeds limit");
        assertEquals(ErrorClassificationService.ErrorBucket.VALIDATION_ERROR, result);
        assertFalse(result.isRetryable());
    }

    @Test
    void testClassifySystemError() {
        // Test database errors
        ErrorClassificationService.ErrorBucket result = errorClassificationService.classifyError("Database connection pool exhausted");
        assertEquals(ErrorClassificationService.ErrorBucket.SYSTEM_ERROR, result);
        assertTrue(result.isRetryable());

        // Test memory issues
        result = errorClassificationService.classifyError("Out of memory error occurred");
        assertEquals(ErrorClassificationService.ErrorBucket.SYSTEM_ERROR, result);
        assertTrue(result.isRetryable());

        // Test system overload
        result = errorClassificationService.classifyError("System overload detected");
        assertEquals(ErrorClassificationService.ErrorBucket.SYSTEM_ERROR, result);
        assertTrue(result.isRetryable());
    }

    @Test
    void testClassifyPermanentError() {
        // Test not found errors
        ErrorClassificationService.ErrorBucket result = errorClassificationService.classifyError("User not found with ID 123");
        assertEquals(ErrorClassificationService.ErrorBucket.PERMANENT_ERROR, result);
        assertFalse(result.isRetryable());

        // Test unauthorized
        result = errorClassificationService.classifyError("Unauthorized access attempt");
        assertEquals(ErrorClassificationService.ErrorBucket.PERMANENT_ERROR, result);
        assertFalse(result.isRetryable());

        // Test unsupported operation
        result = errorClassificationService.classifyError("Unsupported operation for this entity type");
        assertEquals(ErrorClassificationService.ErrorBucket.PERMANENT_ERROR, result);
        assertFalse(result.isRetryable());
    }

    @Test
    void testClassifyNullOrEmptyError() {
        // Test null error message
        ErrorClassificationService.ErrorBucket result = errorClassificationService.classifyError(null);
        assertEquals(ErrorClassificationService.ErrorBucket.PERMANENT_ERROR, result);
        assertFalse(result.isRetryable());

        // Test empty error message
        result = errorClassificationService.classifyError("");
        assertEquals(ErrorClassificationService.ErrorBucket.PERMANENT_ERROR, result);
        assertFalse(result.isRetryable());

        // Test whitespace only
        result = errorClassificationService.classifyError("   ");
        assertEquals(ErrorClassificationService.ErrorBucket.PERMANENT_ERROR, result);
        assertFalse(result.isRetryable());
    }

    @Test
    void testShouldRetry() {
        // Test retryable errors
        assertTrue(errorClassificationService.shouldRetry("Connection timeout"));
        assertTrue(errorClassificationService.shouldRetry("Database connection failed"));
        assertTrue(errorClassificationService.shouldRetry("System overload"));

        // Test non-retryable errors
        assertFalse(errorClassificationService.shouldRetry("Validation failed"));
        assertFalse(errorClassificationService.shouldRetry("User not found"));
        assertFalse(errorClassificationService.shouldRetry("Unauthorized access"));
    }

    @Test
    void testGetRetryDelayMultiplier() {
        assertEquals(1.0, errorClassificationService.getRetryDelayMultiplier(ErrorClassificationService.ErrorBucket.TRANSIENT_ERROR));
        assertEquals(1.5, errorClassificationService.getRetryDelayMultiplier(ErrorClassificationService.ErrorBucket.SYSTEM_ERROR));
        assertEquals(0.0, errorClassificationService.getRetryDelayMultiplier(ErrorClassificationService.ErrorBucket.VALIDATION_ERROR));
        assertEquals(0.0, errorClassificationService.getRetryDelayMultiplier(ErrorClassificationService.ErrorBucket.PERMANENT_ERROR));
    }

    @Test
    void testCaseInsensitiveClassification() {
        // Test uppercase
        ErrorClassificationService.ErrorBucket result = errorClassificationService.classifyError("CONNECTION TIMEOUT");
        assertEquals(ErrorClassificationService.ErrorBucket.TRANSIENT_ERROR, result);

        // Test mixed case
        result = errorClassificationService.classifyError("Validation Failed");
        assertEquals(ErrorClassificationService.ErrorBucket.VALIDATION_ERROR, result);

        // Test lowercase
        result = errorClassificationService.classifyError("database error");
        assertEquals(ErrorClassificationService.ErrorBucket.SYSTEM_ERROR, result);
    }
}

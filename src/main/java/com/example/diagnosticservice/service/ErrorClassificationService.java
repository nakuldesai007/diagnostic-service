package com.example.diagnosticservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
@Slf4j
public class ErrorClassificationService {

    public enum ErrorBucket {
        TRANSIENT_ERROR("transient", true, "Network, timeout, temporary service unavailability"),
        VALIDATION_ERROR("validation", false, "Data format, business rule violations"),
        SYSTEM_ERROR("system", true, "Database connection, external service failures"),
        PERMANENT_ERROR("permanent", false, "Invalid data, unsupported operations");

        private final String category;
        private final boolean retryable;
        private final String description;

        ErrorBucket(String category, boolean retryable, String description) {
            this.category = category;
            this.retryable = retryable;
            this.description = description;
        }

        public boolean isRetryable() {
            return retryable;
        }

        public String getCategory() {
            return category;
        }

        public String getDescription() {
            return description;
        }
    }

    // Static patterns for error classification
    private static final Pattern TRANSIENT_PATTERNS = Pattern.compile(
        "(?i).*(timeout|connection.*refused|service.*unavailable|network.*error|temporary.*failure|retry.*later).*"
    );

    private static final Pattern VALIDATION_PATTERNS = Pattern.compile(
        "(?i).*(validation|invalid.*format|missing.*field|constraint.*violation|business.*rule).*"
    );

    private static final Pattern SYSTEM_PATTERNS = Pattern.compile(
        "(?i).*(database.*error|connection.*pool|out.*of.*memory|disk.*space|system.*overload).*"
    );

    private static final Pattern PERMANENT_PATTERNS = Pattern.compile(
        "(?i).*(not.*found|unauthorized|forbidden|unsupported.*operation|malformed.*data).*"
    );

    /**
     * Classifies an error message into one of the predefined buckets
     *
     * @param errorMessage The error message to classify
     * @return The appropriate ErrorBucket
     */
    public ErrorBucket classifyError(String errorMessage) {
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            log.warn("Empty or null error message provided, defaulting to PERMANENT_ERROR");
            return ErrorBucket.PERMANENT_ERROR;
        }

        String normalizedMessage = errorMessage.toLowerCase().trim();

        // Check patterns in order of priority
        if (TRANSIENT_PATTERNS.matcher(normalizedMessage).matches()) {
            log.debug("Classified as TRANSIENT_ERROR: {}", errorMessage);
            return ErrorBucket.TRANSIENT_ERROR;
        }

        if (VALIDATION_PATTERNS.matcher(normalizedMessage).matches()) {
            log.debug("Classified as VALIDATION_ERROR: {}", errorMessage);
            return ErrorBucket.VALIDATION_ERROR;
        }

        if (SYSTEM_PATTERNS.matcher(normalizedMessage).matches()) {
            log.debug("Classified as SYSTEM_ERROR: {}", errorMessage);
            return ErrorBucket.SYSTEM_ERROR;
        }

        if (PERMANENT_PATTERNS.matcher(normalizedMessage).matches()) {
            log.debug("Classified as PERMANENT_ERROR: {}", errorMessage);
            return ErrorBucket.PERMANENT_ERROR;
        }

        // Default classification based on common patterns
        if (normalizedMessage.contains("exception") || normalizedMessage.contains("error")) {
            log.debug("Default classification as SYSTEM_ERROR for generic error: {}", errorMessage);
            return ErrorBucket.SYSTEM_ERROR;
        }

        log.debug("Default classification as PERMANENT_ERROR: {}", errorMessage);
        return ErrorBucket.PERMANENT_ERROR;
    }

    /**
     * Determines if an error should be retried based on its classification
     *
     * @param errorMessage The error message
     * @return true if the error should be retried, false otherwise
     */
    public boolean shouldRetry(String errorMessage) {
        ErrorBucket bucket = classifyError(errorMessage);
        boolean retryable = bucket.isRetryable();
        log.debug("Error '{}' classified as '{}', retryable: {}", errorMessage, bucket.getCategory(), retryable);
        return retryable;
    }

    /**
     * Gets the retry delay multiplier based on error type
     *
     * @param errorBucket The error bucket
     * @return The delay multiplier
     */
    public double getRetryDelayMultiplier(ErrorBucket errorBucket) {
        return switch (errorBucket) {
            case TRANSIENT_ERROR -> 1.0; // Standard backoff
            case SYSTEM_ERROR -> 1.5;    // Longer delays for system errors
            case VALIDATION_ERROR, PERMANENT_ERROR -> 0.0; // No retry
        };
    }
}

package com.example.diagnosticservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Configuration properties for the diagnostic service.
 * Maps to the 'diagnostic' section in application.yml
 */
@Component
@ConfigurationProperties(prefix = "diagnostic")
public class DiagnosticProperties {

    private Service service = new Service();

    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
    }

    public static class Service {
        private int maxRetryAttempts = 3;
        private Retry retry = new Retry();
        private CircuitBreaker circuitBreaker = new CircuitBreaker();
        private AttemptTracker attemptTracker = new AttemptTracker();

        public int getMaxRetryAttempts() {
            return maxRetryAttempts;
        }

        public void setMaxRetryAttempts(int maxRetryAttempts) {
            this.maxRetryAttempts = maxRetryAttempts;
        }

        public Retry getRetry() {
            return retry;
        }

        public void setRetry(Retry retry) {
            this.retry = retry;
        }

        public CircuitBreaker getCircuitBreaker() {
            return circuitBreaker;
        }

        public void setCircuitBreaker(CircuitBreaker circuitBreaker) {
            this.circuitBreaker = circuitBreaker;
        }

        public AttemptTracker getAttemptTracker() {
            return attemptTracker;
        }

        public void setAttemptTracker(AttemptTracker attemptTracker) {
            this.attemptTracker = attemptTracker;
        }

        public static class Retry {
            private long initialDelayMs = 1000;
            private double backoffMultiplier = 2.0;
            private long maxDelayMs = 30000;

            public long getInitialDelayMs() {
                return initialDelayMs;
            }

            public void setInitialDelayMs(long initialDelayMs) {
                this.initialDelayMs = initialDelayMs;
            }

            public double getBackoffMultiplier() {
                return backoffMultiplier;
            }

            public void setBackoffMultiplier(double backoffMultiplier) {
                this.backoffMultiplier = backoffMultiplier;
            }

            public long getMaxDelayMs() {
                return maxDelayMs;
            }

            public void setMaxDelayMs(long maxDelayMs) {
                this.maxDelayMs = maxDelayMs;
            }
        }

        public static class CircuitBreaker {
            private int failureRateThreshold = 50;
            private Duration waitDurationOpenState = Duration.ofSeconds(30);
            private int slidingWindowSize = 10;
            private int minimumNumberOfCalls = 5;
            private int permittedNumberOfCallsInHalfOpenState = 3;
            private int slowCallRateThreshold = 50;
            private Duration slowCallDurationThreshold = Duration.ofSeconds(2);

            public int getFailureRateThreshold() {
                return failureRateThreshold;
            }

            public void setFailureRateThreshold(int failureRateThreshold) {
                this.failureRateThreshold = failureRateThreshold;
            }

            public Duration getWaitDurationOpenState() {
                return waitDurationOpenState;
            }

            public void setWaitDurationOpenState(Duration waitDurationOpenState) {
                this.waitDurationOpenState = waitDurationOpenState;
            }

            public int getSlidingWindowSize() {
                return slidingWindowSize;
            }

            public void setSlidingWindowSize(int slidingWindowSize) {
                this.slidingWindowSize = slidingWindowSize;
            }

            public int getMinimumNumberOfCalls() {
                return minimumNumberOfCalls;
            }

            public void setMinimumNumberOfCalls(int minimumNumberOfCalls) {
                this.minimumNumberOfCalls = minimumNumberOfCalls;
            }

            public int getPermittedNumberOfCallsInHalfOpenState() {
                return permittedNumberOfCallsInHalfOpenState;
            }

            public void setPermittedNumberOfCallsInHalfOpenState(int permittedNumberOfCallsInHalfOpenState) {
                this.permittedNumberOfCallsInHalfOpenState = permittedNumberOfCallsInHalfOpenState;
            }

            public int getSlowCallRateThreshold() {
                return slowCallRateThreshold;
            }

            public void setSlowCallRateThreshold(int slowCallRateThreshold) {
                this.slowCallRateThreshold = slowCallRateThreshold;
            }

            public Duration getSlowCallDurationThreshold() {
                return slowCallDurationThreshold;
            }

            public void setSlowCallDurationThreshold(Duration slowCallDurationThreshold) {
                this.slowCallDurationThreshold = slowCallDurationThreshold;
            }
        }

        public static class AttemptTracker {
            private int ttlHours = 1;

            public int getTtlHours() {
                return ttlHours;
            }

            public void setTtlHours(int ttlHours) {
                this.ttlHours = ttlHours;
            }
        }
    }
}

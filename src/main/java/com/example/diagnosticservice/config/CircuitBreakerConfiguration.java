package com.example.diagnosticservice.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@Slf4j
public class CircuitBreakerConfiguration {

    @Value("${diagnostic.service.circuit-breaker.failure-rate-threshold:50}")
    private float failureRateThreshold;

    @Value("${diagnostic.service.circuit-breaker.wait-duration-open-state:30s}")
    private Duration waitDurationInOpenState;

    @Value("${diagnostic.service.circuit-breaker.sliding-window-size:10}")
    private int slidingWindowSize;

    @Value("${diagnostic.service.circuit-breaker.minimum-number-of-calls:5}")
    private int minimumNumberOfCalls;

    @Value("${diagnostic.service.circuit-breaker.permitted-number-of-calls-in-half-open-state:3}")
    private int permittedNumberOfCallsInHalfOpenState;

    @Value("${diagnostic.service.circuit-breaker.slow-call-rate-threshold:50}")
    private float slowCallRateThreshold;

    @Value("${diagnostic.service.circuit-breaker.slow-call-duration-threshold:2s}")
    private Duration slowCallDurationThreshold;

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRateThreshold)
                .waitDurationInOpenState(waitDurationInOpenState)
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(minimumNumberOfCalls)
                .permittedNumberOfCallsInHalfOpenState(permittedNumberOfCallsInHalfOpenState)
                .slowCallRateThreshold(slowCallRateThreshold)
                .slowCallDurationThreshold(slowCallDurationThreshold)
                .recordExceptions(Exception.class)
                .ignoreExceptions(IllegalArgumentException.class) // Don't count validation errors as failures
                .build();

        log.info("Circuit Breaker configured with: failureRateThreshold={}%, waitDurationInOpenState={}, " +
                "slidingWindowSize={}, minimumNumberOfCalls={}, permittedNumberOfCallsInHalfOpenState={}, " +
                "slowCallRateThreshold={}%, slowCallDurationThreshold={}",
                failureRateThreshold, waitDurationInOpenState, slidingWindowSize, minimumNumberOfCalls,
                permittedNumberOfCallsInHalfOpenState, slowCallRateThreshold, slowCallDurationThreshold);

        return CircuitBreakerRegistry.of(config);
    }

    @Bean
    public CircuitBreaker diagnosticServiceCircuitBreaker(CircuitBreakerRegistry circuitBreakerRegistry) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("diagnosticService");
        
        // Add event listeners for monitoring
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> {
                    log.info("Circuit Breaker state transition: {} -> {}", 
                            event.getStateTransition().getFromState(), 
                            event.getStateTransition().getToState());
                })
                .onFailureRateExceeded(event -> {
                    log.warn("Circuit Breaker failure rate exceeded: {}%", event.getFailureRate());
                })
                .onSlowCallRateExceeded(event -> {
                    log.warn("Circuit Breaker slow call rate exceeded: {}%", event.getSlowCallRate());
                })
                .onCallNotPermitted(event -> {
                    log.warn("Circuit Breaker call not permitted - circuit is OPEN");
                });

        return circuitBreaker;
    }
}

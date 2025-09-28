package com.example.diagnosticservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.HashMap;
import java.util.Map;

@Configuration
@Slf4j
public class RetryConfiguration {

    @Bean
    public RetryTemplate restClientRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        
        // Configure retry policy
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(HttpServerErrorException.class, true);
        retryableExceptions.put(ResourceAccessException.class, true);
        retryableExceptions.put(Exception.class, true);
        
        // Don't retry client errors (4xx)
        retryableExceptions.put(HttpClientErrorException.class, false);
        
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3, retryableExceptions);
        retryTemplate.setRetryPolicy(retryPolicy);
        
        // Configure backoff policy
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000); // 1 second
        backOffPolicy.setMultiplier(2.0); // Double the interval each time
        backOffPolicy.setMaxInterval(10000); // Max 10 seconds
        retryTemplate.setBackOffPolicy(backOffPolicy);
        
        return retryTemplate;
    }

    @Bean
    public RetryTemplate packetProcessingRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        
        // More aggressive retry for packet processing
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(HttpServerErrorException.class, true);
        retryableExceptions.put(ResourceAccessException.class, true);
        retryableExceptions.put(Exception.class, true);
        retryableExceptions.put(HttpClientErrorException.class, false);
        
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(5, retryableExceptions);
        retryTemplate.setRetryPolicy(retryPolicy);
        
        // Faster backoff for packet processing
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(500); // 0.5 seconds
        backOffPolicy.setMultiplier(1.5); // 1.5x multiplier
        backOffPolicy.setMaxInterval(5000); // Max 5 seconds
        retryTemplate.setBackOffPolicy(backOffPolicy);
        
        return retryTemplate;
    }
}

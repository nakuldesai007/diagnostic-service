package com.example.diagnosticservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Kafka topics.
 * Maps to the 'kafka.topics' section in application.yml
 */
@Component
@ConfigurationProperties(prefix = "kafka.topics")
public class KafkaTopicsProperties {

    private String failedProjectionMessages = "failed-projection-messages";
    private String projectionProcessingQueue = "projection-processing-queue";
    private String deadLetterQueue = "dead-letter-queue";

    public String getFailedProjectionMessages() {
        return failedProjectionMessages;
    }

    public void setFailedProjectionMessages(String failedProjectionMessages) {
        this.failedProjectionMessages = failedProjectionMessages;
    }

    public String getProjectionProcessingQueue() {
        return projectionProcessingQueue;
    }

    public void setProjectionProcessingQueue(String projectionProcessingQueue) {
        this.projectionProcessingQueue = projectionProcessingQueue;
    }

    public String getDeadLetterQueue() {
        return deadLetterQueue;
    }

    public void setDeadLetterQueue(String deadLetterQueue) {
        this.deadLetterQueue = deadLetterQueue;
    }
}

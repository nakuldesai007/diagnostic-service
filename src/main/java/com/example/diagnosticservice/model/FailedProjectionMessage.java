package com.example.diagnosticservice.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailedProjectionMessage {
    private String messageId;
    private String originalMessage;
    private String errorMessage;
    private String stackTrace;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant failureTimestamp;
    private String sourceTopic;
    private int partition;
    private long offset;
}

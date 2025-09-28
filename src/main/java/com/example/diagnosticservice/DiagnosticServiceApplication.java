package com.example.diagnosticservice;

import com.example.diagnosticservice.config.DiagnosticProperties;
import com.example.diagnosticservice.config.KafkaTopicsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({DiagnosticProperties.class, KafkaTopicsProperties.class})
@EnableRetry
@EnableAsync
@EnableScheduling
public class DiagnosticServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiagnosticServiceApplication.class, args);
    }

}

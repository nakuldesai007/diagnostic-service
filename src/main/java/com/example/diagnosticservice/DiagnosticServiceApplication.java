package com.example.diagnosticservice;

import com.example.diagnosticservice.config.DiagnosticProperties;
import com.example.diagnosticservice.config.KafkaTopicsProperties;
import com.example.diagnosticservice.config.TimezoneConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;

@SpringBootApplication
@EnableConfigurationProperties({DiagnosticProperties.class, KafkaTopicsProperties.class})
@EnableRetry
@EnableAsync
@EnableScheduling
@Slf4j
public class DiagnosticServiceApplication {

    public static void main(String[] args) {
        // Set system timezone to America/New_York before Spring Boot starts
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
        log.info("System timezone set to: {}", TimeZone.getDefault().getID());
        
        SpringApplication.run(DiagnosticServiceApplication.class, args);
    }

    @PostConstruct
    public void init() {
        log.info("Application started with timezone: {}", TimeZone.getDefault().getID());
        log.info("Default timezone configured as: {}", TimezoneConfig.getDefaultTimezone());
    }

}

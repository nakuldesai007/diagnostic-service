package com.example.diagnosticservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;

@Configuration
@EnableJpaRepositories(basePackages = "com.example.diagnosticservice.repository")
@EntityScan(basePackages = "com.example.diagnosticservice.entity")
@EnableTransactionManagement
@Slf4j
public class DatabaseConfig {
    
    @PostConstruct
    public void configureTimezone() {
        // Ensure database operations use America/New_York timezone
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
        log.info("Database configuration initialized with timezone: {}", TimeZone.getDefault().getID());
    }
}

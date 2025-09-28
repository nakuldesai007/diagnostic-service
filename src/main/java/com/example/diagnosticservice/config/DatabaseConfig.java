package com.example.diagnosticservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableJpaRepositories(basePackages = "com.example.diagnosticservice.repository")
@EntityScan(basePackages = "com.example.diagnosticservice.entity")
@EnableTransactionManagement
@Slf4j
public class DatabaseConfig {
    
    // Database configuration is handled by Spring Boot auto-configuration
    // based on application.yml properties
}

package com.example.diagnosticservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Configuration class for timezone handling across the application.
 * Ensures all date/time operations use America/New_York timezone.
 */
@Configuration
@Slf4j
public class TimezoneConfig {

    public static final ZoneId DEFAULT_TIMEZONE = ZoneId.of("America/New_York");
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        
        // Configure serializers with America/New_York timezone
        javaTimeModule.addSerializer(LocalDate.class, new LocalDateSerializer(DATE_FORMATTER));
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DATETIME_FORMATTER));
        
        mapper.registerModule(javaTimeModule);
        mapper.setTimeZone(java.util.TimeZone.getTimeZone(DEFAULT_TIMEZONE));
        
        log.info("Configured ObjectMapper with timezone: {}", DEFAULT_TIMEZONE);
        return mapper;
    }

    /**
     * Get the default timezone for the application
     */
    public static ZoneId getDefaultTimezone() {
        return DEFAULT_TIMEZONE;
    }
    
    /**
     * Convert an Instant to America/New_York timezone
     */
    public static java.time.ZonedDateTime toNewYorkTime(Instant instant) {
        return instant.atZone(DEFAULT_TIMEZONE);
    }
    
    /**
     * Get current time in America/New_York timezone
     */
    public static java.time.ZonedDateTime nowInNewYork() {
        return java.time.ZonedDateTime.now(DEFAULT_TIMEZONE);
    }
}

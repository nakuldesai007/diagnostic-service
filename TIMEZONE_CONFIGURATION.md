# Timezone Configuration - America/New_York

This document outlines the comprehensive timezone configuration implemented across the diagnostic service to ensure all operations use America/New_York timezone.

## Configuration Changes

### 1. Application Configuration Files

#### Main Application Configuration (`application.yml`)
- Added Jackson timezone configuration:
  ```yaml
  spring:
    jackson:
      time-zone: America/New_York
      date-format: yyyy-MM-dd HH:mm:ss
  ```
- Updated logging patterns to include timezone information:
  ```yaml
  logging:
    pattern:
      console: "%d{yyyy-MM-dd HH:mm:ss} [America/New_York] - %msg%n"
      file: "%d{yyyy-MM-dd HH:mm:ss} [America/New_York] [%thread] %-5level %logger{36} - %msg%n"
  ```

#### Environment-Specific Configurations
- **Development** (`application-dev.yml`): Added timezone-aware logging patterns
- **Production** (`application-prod.yml`): Added timezone-aware logging patterns  
- **Test** (`application-test.yml`): Added timezone-aware logging patterns

### 2. Java Configuration Classes

#### New TimezoneConfig Class
Created `src/main/java/com/example/diagnosticservice/config/TimezoneConfig.java`:
- Configures ObjectMapper with America/New_York timezone
- Provides utility methods for timezone conversions
- Sets default timezone for Jackson serialization/deserialization

#### Updated DiagnosticServiceApplication
- Sets system timezone to America/New_York at application startup
- Logs timezone configuration on startup
- Uses `@PostConstruct` to verify timezone setup

#### Updated DatabaseConfig
- Ensures database operations use America/New_York timezone
- Logs timezone configuration for database layer

### 3. Docker Configuration

#### Dockerfile
- Added timezone environment variable: `ENV TZ=America/New_York`
- Installed timezone data package: `RUN apk add --no-cache tzdata`

#### Docker Compose
- Added timezone environment variable to diagnostic-service container:
  ```yaml
  environment:
    TZ: America/New_York
  ```

## Technical Implementation Details

### Date/Time Handling Strategy
The application uses a hybrid approach for timezone handling:

1. **Database Storage**: All timestamps are stored as UTC `Instant` objects (timezone-agnostic)
2. **Application Logic**: All operations use America/New_York timezone
3. **JSON Serialization**: Jackson converts to America/New_York timezone for API responses
4. **Logging**: All log timestamps display in America/New_York timezone

### Entity Classes
All entity classes already use `Instant` for timestamp fields, which is the recommended approach:
- `MessageLog`: createdAt, updatedAt, processedAt, retryScheduledAt, dlqSentAt
- `RetryAttempt`: createdAt, scheduledAt, startedAt, completedAt
- `CircuitBreakerEvent`: createdAt
- `PacketProcessingRecord`: createdAt, processedAt, failedAt, retryScheduledAt
- `PacketProcessingSession`: createdAt, updatedAt, startedAt, completedAt, lastProcessedAt, pausedAt, cancelledAt
- `DeadLetterMessage`: createdAt, sentAt, failedAt

### Benefits of This Configuration

1. **Consistency**: All parts of the application use the same timezone
2. **Clarity**: Logs and API responses clearly indicate the timezone being used
3. **Maintainability**: Centralized timezone configuration makes future changes easier
4. **Compliance**: Meets requirements for America/New_York timezone usage
5. **Database Integrity**: UTC storage ensures data consistency regardless of timezone changes

## Verification

To verify the timezone configuration is working correctly:

1. **Check Application Logs**: Look for timezone initialization messages:
   ```
   System timezone set to: America/New_York
   Application started with timezone: America/New_York
   Default timezone configured as: America/New_York
   ```

2. **Check Log Format**: Log entries should show `[America/New_York]` in timestamps

3. **Check API Responses**: Date/time fields in JSON responses should be in America/New_York timezone

4. **Check Database**: Timestamps should be stored as UTC but displayed in America/New_York timezone

## Environment Variables

The following environment variables ensure timezone consistency:

- `TZ=America/New_York` (Docker containers)
- `spring.jackson.time-zone=America/New_York` (Spring Boot configuration)

## Notes

- The application maintains backward compatibility with existing data
- All existing `Instant` fields continue to work correctly
- The configuration is environment-agnostic and works across development, test, and production
- Database migrations are not required as the underlying data structure remains unchanged

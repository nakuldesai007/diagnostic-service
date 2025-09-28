# Diagnostic Service

A Spring Boot application that implements a diagnostic service design pattern for handling failed projection messages from Kafka queues. The service includes error classification, retry mechanisms with exponential backoff, circuit breaker patterns, and dead letter queue handling.

## Features

- **Kafka Consumer**: Consumes failed projection messages from Kafka topics
- **Error Classification**: Static buckets for categorizing error messages (Transient, Validation, System, Permanent)
- **Retry Mechanism**: Exponential backoff with configurable max attempts (default: 3)
- **Circuit Breaker**: Resilience4j circuit breaker for system protection
- **Dead Letter Queue**: Handles permanently failed messages
- **Monitoring**: Health checks, metrics, and statistics endpoints
- **Configurable**: Environment-specific configurations for dev/prod

## Architecture

The service implements the following design patterns:

1. **Event-Driven Architecture**: Kafka consumer for failed messages
2. **Strategy Pattern**: Error classification into static buckets
3. **Retry Pattern**: Exponential backoff with max attempts
4. **Circuit Breaker Pattern**: Failure protection and recovery
5. **Dead Letter Queue Pattern**: Handling permanently failed messages

## Error Classification

The service classifies errors into four static buckets:

- **TRANSIENT_ERROR**: Network, timeout, temporary service unavailability (retryable)
- **VALIDATION_ERROR**: Data format, business rule violations (not retryable)
- **SYSTEM_ERROR**: Database connection, external service failures (retryable)
- **PERMANENT_ERROR**: Invalid data, unsupported operations (not retryable)

## Prerequisites

- Java 21+
- Maven 3.6+
- Apache Kafka 2.8+
- Spring Boot 3.5.0

## Quick Start

### 1. Clone and Build

```bash
cd projects/diagnostic-service
mvn clean install
```

### 2. Start Kafka

```bash
# Start Zookeeper
bin/zookeeper-server-start.sh config/zookeeper.properties

# Start Kafka
bin/kafka-server-start.sh config/server.properties

# Create required topics
bin/kafka-topics.sh --create --topic failed-projection-messages --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
bin/kafka-topics.sh --create --topic projection-processing-queue --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
bin/kafka-topics.sh --create --topic dead-letter-queue --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
```

### 3. Run the Application

```bash
# Development mode
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Or with JAR
java -jar target/diagnostic-service-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

### 4. Test the Service

Send a test message to the failed projection messages topic:

```bash
bin/kafka-console-producer.sh --topic failed-projection-messages --bootstrap-server localhost:9092
```

Enter a test message:
```
{"messageId":"test-1","originalMessage":"test data","errorMessage":"Connection timeout","stackTrace":"java.net.ConnectException"}
```

## Configuration

### Application Properties

Key configuration properties in `application.yml`:

```yaml
diagnostic:
  service:
    max-retry-attempts: 3
    retry:
      initial-delay-ms: 1000
      backoff-multiplier: 2.0
      max-delay-ms: 30000
    circuit-breaker:
      failure-rate-threshold: 50
      wait-duration-open-state: 30s
      sliding-window-size: 10
      minimum-number-of-calls: 5
      permitted-number-of-calls-in-half-open-state: 3
```

### Environment Profiles

- **dev**: Lower thresholds for faster testing
- **prod**: Higher thresholds and better monitoring
- **default**: Balanced configuration

## API Endpoints

### Health Check
```bash
GET /api/diagnostic/health
```

### Statistics
```bash
GET /api/diagnostic/stats
```

### Circuit Breaker State
```bash
GET /api/diagnostic/circuit-breaker/state
```

### Circuit Breaker Metrics
```bash
GET /api/diagnostic/circuit-breaker/metrics
```

### Actuator Endpoints
```bash
GET /actuator/health
GET /actuator/metrics
GET /actuator/circuitbreakers
```

## Monitoring

The service provides comprehensive monitoring through:

1. **Health Checks**: Service health and circuit breaker status
2. **Metrics**: Attempt counts, retry statistics, failure rates
3. **Logging**: Structured logging with different levels per environment
4. **Circuit Breaker Events**: State transitions and failure notifications

## Error Handling Flow

1. **Message Reception**: Kafka consumer receives failed projection message
2. **Error Classification**: Message is classified into appropriate error bucket
3. **Retry Decision**: Based on classification and attempt count
4. **Circuit Breaker Check**: Service availability is checked
5. **Retry or DLQ**: Message is either retried or sent to dead letter queue

## Retry Logic

- **Max Attempts**: Configurable (default: 3)
- **Backoff Strategy**: Exponential backoff with jitter
- **Delay Calculation**: `initialDelay * (backoffMultiplier ^ attemptNumber) * jitter`
- **Max Delay**: Configurable cap to prevent excessive delays

## Circuit Breaker Configuration

- **Failure Rate Threshold**: Percentage of failed calls to open circuit
- **Wait Duration**: Time to wait before attempting half-open state
- **Sliding Window**: Number of calls to consider for failure rate
- **Minimum Calls**: Minimum calls before calculating failure rate

## Development

### Running Tests

```bash
mvn test
```

### Code Style

The project uses Lombok for reducing boilerplate code and follows Spring Boot conventions.

### Adding New Error Patterns

To add new error classification patterns, modify the `ErrorClassificationService`:

```java
private static final Pattern NEW_ERROR_PATTERN = Pattern.compile(
    "(?i).*(your.*pattern.*here).*"
);
```

## Deployment

### Docker

```dockerfile
FROM openjdk:21-jre-slim
COPY target/diagnostic-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### Environment Variables

For production deployment, set these environment variables:

```bash
KAFKA_BOOTSTRAP_SERVERS=your-kafka-servers
KAFKA_CONSUMER_GROUP_ID=your-group-id
SPRING_PROFILES_ACTIVE=prod
```

## Troubleshooting

### Common Issues

1. **Kafka Connection**: Ensure Kafka is running and accessible
2. **Circuit Breaker Open**: Check failure rates and system health
3. **Message Processing**: Review logs for error patterns
4. **Memory Issues**: Monitor attempt tracker TTL and cleanup

### Logs

Check application logs for:
- Circuit breaker state transitions
- Retry attempts and failures
- Error classifications
- DLQ message processing

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## License

This project is licensed under the MIT License.

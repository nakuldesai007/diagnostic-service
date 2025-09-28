# Diagnostic Service Architecture Diagram

## System Overview

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           DIAGNOSTIC SERVICE FRAMEWORK                          │
└─────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Test Scripts  │    │   pgAdmin UI    │    │  Monitoring     │
│                 │    │   (Port 8082)   │    │  Scripts        │
│ • test-e2e.sh   │    │                 │    │                 │
│ • test-perf.sh  │    │                 │    │                 │
│ • test-simple.sh│    │                 │    │                 │
└─────────┬───────┘    └─────────┬───────┘    └─────────┬───────┘
          │                      │                      │
          │                      │                      │
          ▼                      ▼                      ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              DOCKER ENVIRONMENT                                │
│                                                                                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐                │
│  │   Kafka         │  │   PostgreSQL    │  │   pgAdmin       │                │
│  │   (Port 9092)   │  │   (Port 5432)   │  │   (Port 8082)   │                │
│  │                 │  │                 │  │                 │                │
│  │ Topics:         │  │ Tables:         │  │ Web UI for      │                │
│  │ • projection-   │  │ • message_logs  │  │ Database        │                │
│  │   processing-   │  │ • retry_        │  │ Management      │                │
│  │   queue         │  │   attempts      │  │                 │                │
│  │ • failed-       │  │ • circuit_      │  │                 │                │
│  │   projection-   │  │   breaker_      │  │                 │                │
│  │   messages      │  │   events        │  │                 │                │
│  │ • dead-letter-  │  │ • dead_letter_  │  │                 │                │
│  │   queue         │  │   messages      │  │                 │                │
│  └─────────┬───────┘  └─────────┬───────┘  └─────────────────┘                │
│            │                     │                                            │
│            │                     │                                            │
│            ▼                     ▼                                            │
│  ┌─────────────────────────────────────────────────────────────────────────┐  │
│  │                    DIAGNOSTIC SERVICE APPLICATION                       │  │
│  │                           (Port 8080)                                  │  │
│  │                                                                         │  │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐        │  │
│  │  │   Controllers   │  │    Services     │  │   Repositories  │        │  │
│  │  │                 │  │                 │  │                 │        │  │
│  │  │ • Diagnostic    │  │ • Diagnostic    │  │ • MessageLog    │        │  │
│  │  │   Controller    │  │   Service       │  │   Repository    │        │  │
│  │  │   (REST API)    │  │                 │  │ • RetryAttempt  │        │  │
│  │  │                 │  │ • RetryService  │  │   Repository    │        │  │
│  │  │                 │  │                 │  │ • CircuitBreaker│        │  │
│  │  │                 │  │ • MessageAttempt│  │   Event Repo    │        │  │
│  │  │                 │  │   Tracker       │  │ • DeadLetter    │        │  │
│  │  │                 │  │                 │  │   Message Repo  │        │  │
│  │  │                 │  │ • Database      │  │                 │        │  │
│  │  │                 │  │   Logging       │  │                 │        │  │
│  │  │                 │  │   Service       │  │                 │        │  │
│  │  └─────────┬───────┘  └─────────┬───────┘  └─────────┬───────┘        │  │
│  │            │                     │                     │                │  │
│  │            │                     │                     │                │  │
│  │            ▼                     ▼                     ▼                │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐  │  │
│  │  │                    KAFKA CONSUMERS                             │  │  │
│  │  │                                                                 │  │  │
│  │  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  │  │  │
│  │  │  │   Main Consumer │  │  Fallback       │  │  Failed Message │  │  │  │
│  │  │  │                 │  │  Consumer       │  │  Consumer       │  │  │  │
│  │  │  │ • Handles       │  │                 │  │                 │  │  │  │
│  │  │  │   Projection    │  │ • Handles       │  │ • Handles       │  │  │  │
│  │  │  │   Messages      │  │   Raw String    │  │   Failed        │  │  │  │
│  │  │  │                 │  │   Messages      │  │   Messages      │  │  │  │
│  │  │  │ • JSON          │  │                 │  │                 │  │  │  │
│  │  │  │   Deserialization│  │ • Deserialization│  │ • Retry Logic  │  │  │  │
│  │  │  │                 │  │   Fallback      │  │                 │  │  │  │
│  │  │  └─────────┬───────┘  └─────────┬───────┘  └─────────┬───────┘  │  │  │
│  │  └────────────┼─────────────────────┼─────────────────────┼─────────┘  │  │
│  │               │                     │                     │            │  │
│  │               ▼                     ▼                     ▼            │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐  │  │
│  │  │                    RESILIENCE PATTERNS                         │  │  │
│  │  │                                                                 │  │  │
│  │  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  │  │  │
│  │  │  │ Circuit Breaker │  │ Retry Service   │  │ Dead Letter     │  │  │  │
│  │  │  │                 │  │                 │  │ Queue           │  │  │  │
│  │  │  │ • Prevents      │  │ • Exponential   │  │                 │  │  │  │
│  │  │  │   Cascading     │  │   Backoff       │  │ • Handles       │  │  │  │
│  │  │  │   Failures      │  │                 │  │   Final         │  │  │  │
│  │  │  │                 │  │ • Configurable  │  │   Failures      │  │  │  │
│  │  │  │ • State:        │  │   Max Attempts  │  │                 │  │  │  │
│  │  │  │   CLOSED/OPEN   │  │                 │  │ • Prevents      │  │  │  │
│  │  │  │                 │  │ • Jitter        │  │   Message Loss  │  │  │  │
│  │  │  └─────────────────┘  └─────────────────┘  └─────────────────┘  │  │  │
│  │  └─────────────────────────────────────────────────────────────────┘  │  │
│  └─────────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────┘

## Message Flow

```
Test Script → Kafka Producer → Kafka Topic → Kafka Consumer → Processing → Database
     │              │              │              │              │           │
     │              │              │              │              │           │
     ▼              ▼              ▼              ▼              ▼           ▼
┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐
│  Send   │  │  JSON   │  │  Queue  │  │ Deserialize│ │ Process │  │  Log    │
│ Message │  │ Message │  │ Message │  │ & Route  │  │ Message │  │ Results │
│         │  │         │  │         │  │          │  │         │  │         │
└─────────┘  └─────────┘  └─────────┘  └─────────┘  └─────────┘  └─────────┘
```

## Error Handling Flow

```
Message Processing Error
         │
         ▼
┌─────────────────┐
│  Circuit Breaker│
│  Check          │
└─────────┬───────┘
          │
          ▼
┌─────────────────┐
│  Retry Service  │
│  (Max 3 attempts)│
└─────────┬───────┘
          │
          ▼
┌─────────────────┐
│  Dead Letter    │
│  Queue          │
└─────────────────┘
```

## API Endpoints

```
GET  /api/diagnostic/health              - Service health status
GET  /api/diagnostic/stats               - Comprehensive statistics
GET  /api/diagnostic/circuit-breaker/state - Circuit breaker state
GET  /api/diagnostic/circuit-breaker/metrics - Circuit breaker metrics
GET  /api/diagnostic/database/stats      - Database statistics
GET  /api/diagnostic/kafka/stats         - Kafka statistics
```

## Technology Stack

- **Framework**: Spring Boot 3.x
- **Database**: PostgreSQL 15
- **Message Queue**: Apache Kafka
- **Containerization**: Docker & Docker Compose
- **Database UI**: pgAdmin 4
- **Resilience**: Resilience4j (Circuit Breaker, Retry)
- **Build Tool**: Maven
- **Database Migration**: Flyway

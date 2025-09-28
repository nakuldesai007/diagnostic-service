# Diagnostic Service Component Diagram

## Detailed Component Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           DIAGNOSTIC SERVICE COMPONENTS                        │
└─────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────┐
│                              PRESENTATION LAYER                                │
└─────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│  Test Scripts   │  │   pgAdmin UI    │  │  Monitoring     │  │  REST API       │
│                 │  │                 │  │  Scripts        │  │  Endpoints      │
│ • E2E Tests     │  │ • Database      │  │                 │  │                 │
│ • Performance   │  │   Management    │  │ • check-        │  │ • /health       │
│   Tests         │  │ • Query Builder │  │   services.sh   │  │ • /stats        │
│ • Simple Tests  │  │ • Data Viewer   │  │ • monitor-db-   │  │ • /circuit-     │
│                 │  │                 │  │   performance.sh│  │   breaker/*     │
│                 │  │                 │  │ • view-kafka-   │  │ • /database/*   │
│                 │  │                 │  │   messages.sh   │  │ • /kafka/*      │
└─────────┬───────┘  └─────────┬───────┘  └─────────┬───────┘  └─────────┬───────┘
          │                      │                      │                      │
          │                      │                      │                      │
          ▼                      ▼                      ▼                      ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              APPLICATION LAYER                                 │
└─────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────┐
│                              CONTROLLER LAYER                                  │
└─────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────┐
│                           DiagnosticController                                  │
│                                                                                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐                │
│  │   Health        │  │   Statistics    │  │   Circuit       │                │
│  │   Endpoints     │  │   Endpoints     │  │   Breaker       │                │
│  │                 │  │                 │  │   Endpoints     │                │
│  │ • getHealth()   │  │ • getStats()    │  │ • getState()    │                │
│  │ • Database      │  │ • Comprehensive │  │ • getMetrics()  │                │
│  │   Status        │  │   Metrics       │  │                 │                │
│  │ • Kafka Status  │  │ • Error         │  │                 │                │
│  │                 │  │   Handling      │  │                 │                │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘                │
│                                                                                 │
│  ┌─────────────────┐  ┌─────────────────┐                                      │
│  │   Database      │  │   Kafka         │                                      │
│  │   Endpoints     │  │   Endpoints     │                                      │
│  │                 │  │                 │                                      │
│  │ • getDatabase   │  │ • getKafka      │                                      │
│  │   Stats()       │  │   Stats()       │                                      │
│  │                 │  │                 │                                      │
│  └─────────────────┘  └─────────────────┘                                      │
└─────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────┐
│                              SERVICE LAYER                                     │
└─────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────┐
│                           DiagnosticService                                     │
│                                                                                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐                │
│  │   Kafka         │  │   Message       │  │   Error         │                │
│  │   Consumers     │  │   Processing    │  │   Handling      │                │
│  │                 │  │                 │  │                 │                │
│  │ • Main Consumer │  │ • Projection    │  │ • Circuit       │                │
│  │   (JSON)        │  │   Processing    │  │   Breaker       │                │
│  │ • Fallback      │  │ • Retry Logic   │  │   Integration   │                │
│  │   Consumer      │  │ • Dead Letter   │  │ • Exception     │                │
│  │   (String)      │  │   Handling      │  │   Management    │                │
│  │ • Failed        │  │                 │  │                 │                │
│  │   Message       │  │                 │  │                 │                │
│  │   Consumer      │  │                 │  │                 │                │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘                │
└─────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────┐
│                           RetryService                                          │
│                                                                                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐                │
│  │   Retry         │  │   Backoff       │  │   Statistics    │                │
│  │   Logic         │  │   Strategy      │  │   Tracking      │                │
│  │                 │  │                 │  │                 │                │
│  │ • Max Attempts  │  │ • Exponential   │  │ • Attempt       │                │
│  │   (3)           │  │   Backoff       │  │   Counting      │                │
│  │ • Delay         │  │ • Jitter        │  │ • Success/Fail  │                │
│  │   Calculation   │  │   Application   │  │   Rates         │                │
│  │                 │  │                 │  │                 │                │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘                │
└─────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────┐
│                        MessageAttemptTracker                                   │
│                                                                                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐                │
│  │   Message       │  │   Attempt       │  │   Statistics    │                │
│  │   Tracking      │  │   Counting      │  │   Calculation   │                │
│  │                 │  │                 │  │                 │                │
│  │ • Message ID    │  │ • Per Message   │  │ • Average       │                │
│  │   Tracking      │  │   Attempts      │  │   Attempts      │                │
│  │ • Status        │  │ • Total         │  │ • Success       │                │
│  │   Monitoring    │  │   Attempts      │  │   Rates         │                │
│  │                 │  │                 │  │                 │                │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘                │
└─────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────┐
│                        DatabaseLoggingService                                  │
│                                                                                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐                │
│  │   Message       │  │   Processing    │  │   Error         │                │
│  │   Logging       │  │   Logging       │  │   Logging       │                │
│  │                 │  │                 │  │                 │                │
│  │ • Received      │  │ • Success       │  │ • Failed        │                │
│  │   Messages      │  │   Processing    │  │   Processing    │                │
│  │ • Metadata      │  │ • Timing        │  │ • Error         │                │
│  │   Storage       │  │   Information   │  │   Details       │                │
│  │                 │  │                 │  │                 │                │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘                │
└─────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────┐
│                              DATA LAYER                                        │
└─────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────┐
│                              REPOSITORY LAYER                                  │
└─────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│ MessageLog      │  │ RetryAttempt    │  │ CircuitBreaker  │  │ DeadLetter      │
│ Repository      │  │ Repository      │  │ Event Repository│  │ Message         │
│                 │  │                 │  │                 │  │ Repository      │
│ • CRUD          │  │ • CRUD          │  │ • CRUD          │  │ • CRUD          │
│   Operations    │  │   Operations    │  │   Operations    │  │   Operations    │
│ • Query         │  │ • Query         │  │ • Query         │  │ • Query         │
│   Methods       │  │   Methods       │  │   Methods       │  │   Methods       │
│ • Statistics    │  │ • Statistics    │  │ • Statistics    │  │ • Statistics    │
│   Queries       │  │   Queries       │  │   Queries       │  │   Queries       │
└─────────┬───────┘  └─────────┬───────┘  └─────────┬───────┘  └─────────┬───────┘
          │                     │                     │                     │
          │                     │                     │                     │
          ▼                     ▼                     ▼                     ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              DATABASE LAYER                                    │
└─────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────┐
│                              PostgreSQL                                        │
│                                                                                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐                │
│  │ message_logs    │  │ retry_attempts  │  │ circuit_breaker │                │
│  │                 │  │                 │  │ _events         │                │
│  │ • message_id    │  │ • attempt_id    │  │ • event_id      │                │
│  │ • topic         │  │ • message_id    │  │ • circuit_      │                │
│  │ • partition     │  │ • attempt_num   │  │   breaker_name  │                │
│  │ • offset        │  │ • status        │  │ • event_type    │                │
│  │ • processing_   │  │ • delay_ms      │  │ • from_state    │                │
│  │   status        │  │ • created_at    │  │ • to_state      │                │
│  │ • created_at    │  │                 │  │ • created_at    │                │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘                │
│                                                                                 │
│  ┌─────────────────┐                                                           │
│  │ dead_letter_    │                                                           │
│  │ messages        │                                                           │
│  │                 │                                                           │
│  │ • message_id    │                                                           │
│  │ • original_     │                                                           │
│  │   topic         │                                                           │
│  │ • message_      │                                                           │
│  │   content       │                                                           │
│  │ • error_        │                                                           │
│  │   message       │                                                           │
│  │ • created_at    │                                                           │
│  └─────────────────┘                                                           │
└─────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────┐
│                              MESSAGE LAYER                                     │
└─────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────┐
│                              Apache Kafka                                      │
│                                                                                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐                │
│  │ projection-     │  │ failed-         │  │ dead-letter-    │                │
│  │ processing-     │  │ projection-     │  │ queue           │                │
│  │ queue           │  │ messages        │  │                 │                │
│  │                 │  │                 │  │                 │                │
│  │ • Main input    │  │ • Retry         │  │ • Final         │                │
│  │   topic         │  │   messages      │  │   failures      │                │
│  │ • 3 partitions  │  │ • 3 partitions  │  │ • 3 partitions  │                │
│  │ • JSON          │  │ • Failed        │  │ • Dead letter   │                │
│  │   messages      │  │   messages      │  │   messages      │                │
│  │                 │  │                 │  │                 │                │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘                │
└─────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────┐
│                              INFRASTRUCTURE LAYER                              │
└─────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│ Docker          │  │ Docker Compose  │  │ pgAdmin         │  │ Monitoring      │
│                 │  │                 │  │                 │  │ Scripts         │
│ • Container     │  │ • Service       │  │ • Database      │  │                 │
│   Management    │  │   Orchestration │  │   Management    │  │ • check-        │
│ • Image         │  │ • Network       │  │ • Query Builder │  │   services.sh   │
│   Building      │  │   Configuration │  │ • Data Viewer   │  │ • monitor-db-   │
│ • Port          │  │ • Volume        │  │ • User          │  │   performance.sh│
│   Mapping       │  │   Management    │  │   Management    │  │ • view-kafka-   │
│                 │  │                 │  │                 │  │   messages.sh   │
└─────────────────┘  └─────────────────┘  └─────────────────┘  └─────────────────┘
```

## Configuration Layer

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              CONFIGURATION LAYER                               │
└─────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│ KafkaConfig     │  │ DatabaseConfig  │  │ CircuitBreaker  │  │ Diagnostic      │
│                 │  │                 │  │ Configuration   │  │ Properties      │
│ • Producer      │  │ • DataSource    │  │                 │  │                 │
│   Factory       │  │   Configuration │  │ • Circuit       │  │ • Service       │
│ • Consumer      │  │ • JPA           │  │   Breaker       │  │   Configuration │
│   Factory       │  │   Configuration │  │   Settings      │  │ • Retry         │
│ • Topic         │  │ • Flyway        │  │ • Retry         │  │   Configuration │
│   Configuration │  │   Configuration │  │   Settings      │  │ • Circuit       │
│ • Serialization │  │ • Connection    │  │ • Timeout       │  │   Breaker       │
│   Settings      │  │   Pool          │  │   Settings      │  │   Settings      │
└─────────────────┘  └─────────────────┘  └─────────────────┘  └─────────────────┘
```

## Data Flow Summary

1. **Input**: Test scripts send messages to Kafka
2. **Processing**: Diagnostic service consumes and processes messages
3. **Resilience**: Circuit breaker and retry logic handle failures
4. **Storage**: All events and results are logged to PostgreSQL
5. **Monitoring**: REST API provides real-time statistics and health checks
6. **Management**: pgAdmin provides database management interface

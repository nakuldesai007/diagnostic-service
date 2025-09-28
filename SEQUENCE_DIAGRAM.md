# Diagnostic Service Sequence Diagram

## Message Processing Flow

```
┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│ Test Script │  │   Kafka     │  │ Diagnostic  │  │ PostgreSQL  │  │ pgAdmin     │
│             │  │             │  │ Service     │  │             │  │             │
└─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘
       │                │                │                │                │
       │                │                │                │                │
       │ 1. Send Message│                │                │                │
       ├───────────────►│                │                │                │
       │                │                │                │                │
       │                │ 2. Store in    │                │                │
       │                │    Topic       │                │                │
       │                │                │                │                │
       │                │ 3. Consume     │                │                │
       │                ├───────────────►│                │                │
       │                │                │                │                │
       │                │ 4. Deserialize │                │                │
       │                │    Message     │                │                │
       │                │                │                │                │
       │                │ 5. Log Message │                │                │
       │                │    Received    │                │                │
       │                ├───────────────►│                │                │
       │                │                │                │                │
       │                │ 6. Process     │                │                │
       │                │    Message     │                │                │
       │                │                │                │                │
       │                │ 7. Check       │                │                │
       │                │    Circuit     │                │                │
       │                │    Breaker     │                │                │
       │                │                │                │                │
       │                │ 8. Process     │                │                │
       │                │    Successfully│                │                │
       │                │                │                │                │
       │                │ 9. Log Success │                │                │
       │                ├───────────────►│                │                │
       │                │                │                │                │
       │                │ 10. Acknowledge│                │                │
       │                │     Message    │                │                │
       │                │◄───────────────┤                │                │
       │                │                │                │                │
       │                │                │ 11. Query Data │                │
       │                │                │◄───────────────┤                │
       │                │                │                │                │
       │                │                │ 12. Display    │                │
       │                │                │    Results     │                │
       │                │                │                │                │
```

## Error Handling Flow

```
┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│ Test Script │  │   Kafka     │  │ Diagnostic  │  │ PostgreSQL  │  │ Dead Letter │
│             │  │             │  │ Service     │  │             │  │ Queue       │
└─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘
       │                │                │                │                │
       │                │                │                │                │
       │ 1. Send Message│                │                │                │
       ├───────────────►│                │                │                │
       │                │                │                │                │
       │                │ 2. Store in    │                │                │
       │                │    Topic       │                │                │
       │                │                │                │                │
       │                │ 3. Consume     │                │                │
       │                ├───────────────►│                │                │
       │                │                │                │                │
       │                │ 4. Deserialize │                │                │
       │                │    Error       │                │                │
       │                │                │                │                │
       │                │ 5. Fallback    │                │                │
       │                │    Consumer    │                │                │
       │                │                │                │                │
       │                │ 6. Parse as    │                │                │
       │                │    String      │                │                │
       │                │                │                │                │
       │                │ 7. Log Error   │                │                │
       │                ├───────────────►│                │                │
       │                │                │                │                │
       │                │ 8. Send to     │                │                │
       │                │    Failed      │                │                │
       │                │    Topic       │                │                │
       │                ├───────────────►│                │                │
       │                │                │                │                │
       │                │ 9. Retry       │                │                │
       │                │    Logic       │                │                │
       │                │                │                │                │
       │                │ 10. Max        │                │                │
       │                │     Attempts   │                │                │
       │                │     Exceeded   │                │                │
       │                │                │                │                │
       │                │ 11. Send to    │                │                │
       │                │     Dead       │                │                │
       │                │     Letter     │                │                │
       │                │     Queue      │                │                │
       │                ├───────────────►│                │                │
       │                │                │                │                │
       │                │ 12. Log Final  │                │                │
       │                │     Failure    │                │                │
       │                ├───────────────►│                │                │
       │                │                │                │                │
```

## Circuit Breaker Flow

```
┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│ Diagnostic  │  │ Circuit     │  │ External    │  │ PostgreSQL  │
│ Service     │  │ Breaker     │  │ Service     │  │             │
└─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘
       │                │                │                │
       │                │                │                │
       │ 1. Process     │                │                │
       │    Message     │                │                │
       │                │                │                │
       │ 2. Check       │                │                │
       │    Circuit     │                │                │
       │    State       │                │                │
       ├───────────────►│                │                │
       │                │                │                │
       │ 3. State:      │                │                │
       │    CLOSED      │                │                │
       │◄───────────────┤                │                │
       │                │                │                │
       │ 4. Call        │                │                │
       │    External    │                │                │
       │    Service     │                │                │
       ├─────────────────────────────────►│                │
       │                │                │                │
       │ 5. Success     │                │                │
       │◄─────────────────────────────────┤                │
       │                │                │                │
       │ 6. Log Success │                │                │
       ├─────────────────────────────────────────────────►│
       │                │                │                │
       │ 7. Update      │                │                │
       │    Circuit     │                │                │
       │    Metrics     │                │                │
       ├───────────────►│                │                │
       │                │                │                │
       │                │ 8. State:      │                │
       │                │    CLOSED      │                │
       │                │    (Success)   │                │
       │◄───────────────┤                │                │
       │                │                │                │
```

## Retry Flow

```
┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│ Diagnostic  │  │ Retry       │  │ External    │  │ PostgreSQL  │
│ Service     │  │ Service     │  │ Service     │  │             │
└─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘
       │                │                │                │
       │                │                │                │
       │ 1. Process     │                │                │
       │    Message     │                │                │
       │    Fails       │                │                │
       │                │                │                │
       │ 2. Start       │                │                │
       │    Retry       │                │                │
       ├───────────────►│                │                │
       │                │                │                │
       │ 3. Calculate   │                │                │
       │    Delay       │                │                │
       │                │                │                │
       │ 4. Wait        │                │                │
       │    (500ms)     │                │                │
       │                │                │                │
       │ 5. Retry       │                │                │
       │    Attempt 1   │                │                │
       ├─────────────────────────────────►│                │
       │                │                │                │
       │ 6. Still       │                │                │
       │    Fails       │                │                │
       │◄─────────────────────────────────┤                │
       │                │                │                │
       │ 7. Calculate   │                │                │
       │    Next Delay  │                │                │
       │    (750ms)     │                │                │
       │                │                │                │
       │ 8. Wait        │                │                │
       │                │                │                │
       │ 9. Retry       │                │                │
       │    Attempt 2   │                │                │
       ├─────────────────────────────────►│                │
       │                │                │                │
       │ 10. Still      │                │                │
       │     Fails      │                │                │
       │◄─────────────────────────────────┤                │
       │                │                │                │
       │ 11. Calculate  │                │                │
       │     Next Delay │                │                │
       │     (1125ms)   │                │                │
       │                │                │                │
       │ 12. Wait       │                │                │
       │                │                │                │
       │ 13. Retry      │                │                │
       │     Attempt 3  │                │                │
       ├─────────────────────────────────►│                │
       │                │                │                │
       │ 14. Still      │                │                │
       │     Fails      │                │                │
       │◄─────────────────────────────────┤                │
       │                │                │                │
       │ 15. Max        │                │                │
       │     Attempts   │                │                │
       │     Exceeded   │                │                │
       │                │                │                │
       │ 16. Send to    │                │                │
       │     Dead       │                │                │
       │     Letter     │                │                │
       │     Queue      │                │                │
       │                │                │                │
       │ 17. Log Final  │                │                │
       │     Failure    │                │                │
       ├─────────────────────────────────────────────────►│
       │                │                │                │
```

## API Request Flow

```
┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│ Client      │  │ Diagnostic  │  │ Service     │  │ PostgreSQL  │
│ (Browser/   │  │ Controller  │  │ Layer       │  │             │
│  Script)    │  │             │  │             │  │             │
└─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘
       │                │                │                │
       │                │                │                │
       │ 1. GET /stats  │                │                │
       ├───────────────►│                │                │
       │                │                │                │
       │ 2. Get Health  │                │                │
       │    Status      │                │                │
       ├─────────────────────────────────►│                │
       │                │                │                │
       │ 3. Get Stats   │                │                │
       │    from        │                │                │
       │    Services    │                │                │
       ├─────────────────────────────────►│                │
       │                │                │                │
       │ 4. Query       │                │                │
       │    Database    │                │                │
       ├─────────────────────────────────────────────────►│
       │                │                │                │
       │ 5. Return      │                │                │
       │    Data        │                │                │
       │◄─────────────────────────────────────────────────┤
       │                │                │                │
       │ 6. Aggregate   │                │                │
       │    Statistics  │                │                │
       │◄─────────────────────────────────┤                │
       │                │                │                │
       │ 7. Return      │                │                │
       │    JSON        │                │                │
       │    Response    │                │                │
       │◄───────────────┤                │                │
       │                │                │                │
```

## Database Schema Relationships

```
┌─────────────────┐
│ message_logs    │
│                 │
│ • message_id    │◄─────────────┐
│ • topic         │              │
│ • partition     │              │
│ • offset        │              │
│ • processing_   │              │
│   status        │              │
│ • created_at    │              │
└─────────────────┘              │
                                 │
                                 │ 1:N
                                 │
┌─────────────────┐              │
│ retry_attempts  │              │
│                 │              │
│ • attempt_id    │              │
│ • message_id    ├──────────────┘
│ • attempt_num   │
│ • status        │
│ • delay_ms      │
│ • created_at    │
└─────────────────┘

┌─────────────────┐
│ circuit_breaker │
│ _events         │
│                 │
│ • event_id      │
│ • circuit_      │
│   breaker_name  │
│ • event_type    │
│ • from_state    │
│ • to_state      │
│ • created_at    │
└─────────────────┘

┌─────────────────┐
│ dead_letter_    │
│ messages        │
│                 │
│ • message_id    │
│ • original_     │
│   topic         │
│ • message_      │
│   content       │
│ • error_        │
│   message       │
│ • created_at    │
└─────────────────┘
```

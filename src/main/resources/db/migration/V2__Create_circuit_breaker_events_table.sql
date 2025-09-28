-- Create circuit_breaker_events table for logging circuit breaker events
CREATE TABLE circuit_breaker_events (
    id BIGSERIAL PRIMARY KEY,
    circuit_breaker_name VARCHAR(100) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    from_state VARCHAR(50),
    to_state VARCHAR(50),
    failure_rate DOUBLE PRECISION,
    slow_call_rate DOUBLE PRECISION,
    call_count BIGINT,
    failure_count BIGINT,
    slow_call_count BIGINT,
    details TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for better query performance
CREATE INDEX idx_circuit_breaker_events_name ON circuit_breaker_events(circuit_breaker_name);
CREATE INDEX idx_circuit_breaker_events_type ON circuit_breaker_events(event_type);
CREATE INDEX idx_circuit_breaker_events_created_at ON circuit_breaker_events(created_at);
CREATE INDEX idx_circuit_breaker_events_from_state ON circuit_breaker_events(from_state);
CREATE INDEX idx_circuit_breaker_events_to_state ON circuit_breaker_events(to_state);

-- Add comments for documentation
COMMENT ON TABLE circuit_breaker_events IS 'Logs circuit breaker events and state changes';
COMMENT ON COLUMN circuit_breaker_events.event_type IS 'Type of event: STATE_TRANSITION, FAILURE_RATE_EXCEEDED, SLOW_CALL_RATE_EXCEEDED, CALL_NOT_PERMITTED';
COMMENT ON COLUMN circuit_breaker_events.failure_rate IS 'Failure rate percentage when event occurred';
COMMENT ON COLUMN circuit_breaker_events.slow_call_rate IS 'Slow call rate percentage when event occurred';

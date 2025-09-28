-- Create message_logs table for logging all consumed messages
CREATE TABLE message_logs (
    id BIGSERIAL PRIMARY KEY,
    message_id VARCHAR(255) NOT NULL,
    topic VARCHAR(255) NOT NULL,
    partition INTEGER NOT NULL,
    "offset" BIGINT NOT NULL,
    message_key VARCHAR(255),
    original_message TEXT,
    error_message TEXT,
    error_category VARCHAR(50),
    processing_status VARCHAR(50),
    attempt_count INTEGER,
    max_retries INTEGER,
    circuit_breaker_state VARCHAR(50),
    failure_reason TEXT,
    processing_time_ms BIGINT,
    source_service VARCHAR(255),
    stack_trace TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    retry_scheduled_at TIMESTAMP,
    dlq_sent_at TIMESTAMP
);

-- Create indexes for better query performance
CREATE INDEX idx_message_logs_message_id ON message_logs(message_id);
CREATE INDEX idx_message_logs_topic_partition ON message_logs(topic, partition);
CREATE INDEX idx_message_logs_created_at ON message_logs(created_at);
CREATE INDEX idx_message_logs_processing_status ON message_logs(processing_status);
CREATE INDEX idx_message_logs_error_category ON message_logs(error_category);
CREATE INDEX idx_message_logs_circuit_breaker_state ON message_logs(circuit_breaker_state);

-- Add comments for documentation
COMMENT ON TABLE message_logs IS 'Logs all messages consumed by the diagnostic service';
COMMENT ON COLUMN message_logs.message_id IS 'Unique identifier for the message';
COMMENT ON COLUMN message_logs.processing_status IS 'Current processing status: RECEIVED, PROCESSING, RETRY, DLQ, SUCCESS, FAILED';
COMMENT ON COLUMN message_logs.error_category IS 'Category of error: timeout, validation, permanent, etc.';
COMMENT ON COLUMN message_logs.circuit_breaker_state IS 'Circuit breaker state when message was processed';

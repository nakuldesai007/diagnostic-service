-- Create retry_attempts table for logging retry attempts
CREATE TABLE retry_attempts (
    id BIGSERIAL PRIMARY KEY,
    message_id VARCHAR(255) NOT NULL,
    attempt_number INTEGER NOT NULL,
    status VARCHAR(50),
    error_message TEXT,
    error_category VARCHAR(50),
    delay_ms BIGINT,
    processing_time_ms BIGINT,
    original_message TEXT,
    retry_message TEXT,
    topic VARCHAR(255),
    partition INTEGER,
    offset BIGINT,
    failure_reason TEXT,
    stack_trace TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    scheduled_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP
);

-- Create indexes for better query performance
CREATE INDEX idx_message_id ON retry_attempts(message_id);
CREATE INDEX idx_attempt_number ON retry_attempts(attempt_number);
CREATE INDEX idx_created_at ON retry_attempts(created_at);
CREATE INDEX idx_status ON retry_attempts(status);
CREATE INDEX idx_error_category ON retry_attempts(error_category);

-- Add comments for documentation
COMMENT ON TABLE retry_attempts IS 'Logs retry attempts and their outcomes';
COMMENT ON COLUMN retry_attempts.status IS 'Status of retry: SCHEDULED, IN_PROGRESS, SUCCESS, FAILED, CANCELLED';
COMMENT ON COLUMN retry_attempts.delay_ms IS 'Delay in milliseconds before retry attempt';
COMMENT ON COLUMN retry_attempts.processing_time_ms IS 'Time taken to process the retry attempt';

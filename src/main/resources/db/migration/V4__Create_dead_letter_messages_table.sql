-- Create dead_letter_messages table for logging DLQ messages
CREATE TABLE dead_letter_messages (
    id BIGSERIAL PRIMARY KEY,
    message_id VARCHAR(255) NOT NULL,
    original_message TEXT,
    failure_reason TEXT,
    attempt_count INTEGER NOT NULL,
    error_category VARCHAR(50),
    source_topic VARCHAR(255),
    partition INTEGER,
    "offset" BIGINT,
    source_service VARCHAR(255),
    stack_trace TEXT,
    dlq_message TEXT,
    processing_status VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP,
    failed_at TIMESTAMP
);

-- Create indexes for better query performance
CREATE INDEX idx_dead_letter_messages_message_id ON dead_letter_messages(message_id);
CREATE INDEX idx_dead_letter_messages_error_category ON dead_letter_messages(error_category);
CREATE INDEX idx_dead_letter_messages_created_at ON dead_letter_messages(created_at);
CREATE INDEX idx_dead_letter_messages_processing_status ON dead_letter_messages(processing_status);
CREATE INDEX idx_dead_letter_messages_source_topic ON dead_letter_messages(source_topic);
CREATE INDEX idx_dead_letter_messages_source_service ON dead_letter_messages(source_service);

-- Add comments for documentation
COMMENT ON TABLE dead_letter_messages IS 'Logs messages sent to Dead Letter Queue';
COMMENT ON COLUMN dead_letter_messages.processing_status IS 'Status of DLQ processing: SENT, FAILED, PENDING';
COMMENT ON COLUMN dead_letter_messages.attempt_count IS 'Number of retry attempts before sending to DLQ';
COMMENT ON COLUMN dead_letter_messages.dlq_message IS 'The actual message sent to DLQ';

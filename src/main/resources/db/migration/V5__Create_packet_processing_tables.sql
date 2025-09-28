-- Create packet processing sessions table
CREATE TABLE packet_processing_sessions (
    id BIGSERIAL PRIMARY KEY,
    activity_id VARCHAR(255) NOT NULL,
    application_date DATE NOT NULL,
    activity_type VARCHAR(255),
    activity_status VARCHAR(255),
    endpoint_url VARCHAR(500) NOT NULL,
    packet_size INTEGER NOT NULL,
    total_records INTEGER NOT NULL DEFAULT 0,
    processed_records INTEGER NOT NULL DEFAULT 0,
    failed_records INTEGER NOT NULL DEFAULT 0,
    current_offset INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    error_message TEXT,
    error_category VARCHAR(50),
    failure_reason TEXT,
    total_processing_time_ms BIGINT,
    last_packet_processing_time_ms BIGINT,
    last_processed_record_id VARCHAR(255),
    last_processed_record_data TEXT,
    request_headers TEXT,
    response_headers TEXT,
    http_status_code INTEGER,
    stack_trace TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    last_processed_at TIMESTAMP,
    paused_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    UNIQUE(activity_id, application_date)
);

-- Create indexes for packet processing sessions
CREATE INDEX idx_activity_id ON packet_processing_sessions(activity_id);
CREATE INDEX idx_application_date ON packet_processing_sessions(application_date);
CREATE INDEX idx_activity_type ON packet_processing_sessions(activity_type);
CREATE INDEX idx_status ON packet_processing_sessions(status);
CREATE INDEX idx_created_at ON packet_processing_sessions(created_at);
CREATE INDEX idx_endpoint_url ON packet_processing_sessions(endpoint_url);

-- Create packet processing records table
CREATE TABLE packet_processing_records (
    id BIGSERIAL PRIMARY KEY,
    activity_id VARCHAR(255) NOT NULL,
    application_date DATE NOT NULL,
    record_id VARCHAR(255) NOT NULL,
    packet_number INTEGER NOT NULL,
    record_index INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    record_data TEXT,
    error_message TEXT,
    error_category VARCHAR(50),
    failure_reason TEXT,
    processing_time_ms BIGINT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    stack_trace TEXT,
    request_data TEXT,
    response_data TEXT,
    http_status_code INTEGER,
    request_headers TEXT,
    response_headers TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    failed_at TIMESTAMP,
    retry_scheduled_at TIMESTAMP
);

-- Create indexes for packet processing records
CREATE INDEX idx_packet_activity_id ON packet_processing_records(activity_id);
CREATE INDEX idx_packet_application_date ON packet_processing_records(application_date);
CREATE INDEX idx_packet_record_id ON packet_processing_records(record_id);
CREATE INDEX idx_packet_status ON packet_processing_records(status);
CREATE INDEX idx_packet_created_at ON packet_processing_records(created_at);
CREATE INDEX idx_packet_number ON packet_processing_records(packet_number);

-- Add foreign key constraint
ALTER TABLE packet_processing_records 
ADD CONSTRAINT fk_packet_records_activity 
FOREIGN KEY (activity_id, application_date) REFERENCES packet_processing_sessions(activity_id, application_date) ON DELETE CASCADE;

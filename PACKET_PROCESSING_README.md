# Packet Processing Feature

This document describes the packet processing feature that allows the diagnostic service to call REST endpoints and process records in packets of configurable size with failure handling and resumption capabilities, using activity-based identification.

## Overview

The packet processing feature provides:

- **REST Endpoint Integration**: Call external REST endpoints to fetch data
- **Packet-based Processing**: Process records in configurable packet sizes (default: 10)
- **Activity-based Identification**: Use activity attributes (activity ID, application date) as primary identifiers
- **Header-based Metadata**: Use HTTP headers for packet tracking and metadata exchange
- **Failure Handling**: Track and handle failures at both packet and record levels
- **Resume Capability**: Continue processing from the last failed message
- **Retry Logic**: Automatic retry of failed records with configurable retry counts
- **Session Management**: Track processing sessions with detailed status information
- **Database Persistence**: All processing state is persisted in the database

## Architecture

### Components

1. **PacketProcessingSession**: Tracks overall processing session state
2. **PacketProcessingRecord**: Tracks individual record processing within sessions
3. **RestClientService**: Handles REST endpoint communication with retry logic
4. **PacketProcessingService**: Main service orchestrating packet processing
5. **PacketProcessingController**: REST endpoints for managing packet processing

### Database Tables

- `packet_processing_sessions`: Stores session-level information
- `packet_processing_records`: Stores individual record processing details

## API Endpoints

### Start Packet Processing

```http
POST /api/diagnostic/packet-processing/start
```

**Parameters:**
- `endpointUrl` (required): The REST endpoint URL to fetch data from
- `activityId` (required): Unique identifier for the activity
- `applicationDate` (required): Application date in YYYY-MM-DD format
- `packetSize` (optional, default: 10): Number of records per packet
- `activityType` (optional): Type of activity (e.g., "DATA_PROCESSING")
- `activityStatus` (optional): Status of activity (e.g., "PENDING")
- Headers: Any custom headers to include in requests

**Response:**
```json
{
  "processingId": "ACT-001-2024-01-15",
  "activityId": "ACT-001",
  "applicationDate": "2024-01-15",
  "activityType": "DATA_PROCESSING",
  "activityStatus": "PENDING",
  "endpointUrl": "https://api.example.com/data",
  "packetSize": 10,
  "status": "STARTED",
  "timestamp": "2024-01-01T00:00:00Z"
}
```

### Get Processing Status by Activity

```http
GET /api/diagnostic/packet-processing/activity/{activityId}/date/{applicationDate}/status
```

**Response:**
```json
{
  "activityId": "ACT-001",
  "applicationDate": "2024-01-15",
  "activityType": "DATA_PROCESSING",
  "activityStatus": "PENDING",
  "endpointUrl": "https://api.example.com/data",
  "packetSize": 10,
  "totalRecords": 100,
  "processedRecords": 45,
  "failedRecords": 2,
  "currentOffset": 47,
  "status": "ACTIVE",
  "errorMessage": null,
  "errorCategory": null,
  "httpStatusCode": 200,
  "totalProcessingTimeMs": 5000,
  "lastPacketProcessingTimeMs": 250,
  "lastProcessedRecordId": "record-47",
  "createdAt": "2024-01-01T00:00:00Z",
  "startedAt": "2024-01-01T00:00:00Z",
  "completedAt": null,
  "lastProcessedAt": "2024-01-01T00:00:05Z",
  "pausedAt": null,
  "cancelledAt": null,
  "timestamp": "2024-01-01T00:00:05Z"
}
```

### Pause Processing by Activity

```http
POST /api/diagnostic/packet-processing/activity/{activityId}/date/{applicationDate}/pause
```

### Resume Processing by Activity

```http
POST /api/diagnostic/packet-processing/activity/{activityId}/date/{applicationDate}/resume
```

### Cancel Processing by Activity

```http
POST /api/diagnostic/packet-processing/activity/{activityId}/date/{applicationDate}/cancel
```

### Retry Failed Records by Activity

```http
POST /api/diagnostic/packet-processing/activity/{activityId}/date/{applicationDate}/retry
```

### Get All Active Sessions

```http
GET /api/diagnostic/packet-processing/sessions
```

### Get Sessions by Activity

```http
GET /api/diagnostic/packet-processing/activity/{activityId}/sessions
```

### Get Sessions by Application Date

```http
GET /api/diagnostic/packet-processing/date/{applicationDate}/sessions
```

### Get Sessions by Activity Type

```http
GET /api/diagnostic/packet-processing/activity-type/{activityType}/sessions
```

### Test Header Metadata

```http
POST /api/diagnostic/packet-processing/test-headers
```

**Parameters:**
- `endpointUrl` (required): The REST endpoint URL to fetch data from
- `activityId` (required): Unique identifier for the activity
- `applicationDate` (required): Application date in YYYY-MM-DD format
- `packetSize` (optional, default: 5): Number of records per packet
- `activityType` (optional): Type of activity
- `activityStatus` (optional): Status of activity
- Headers: Custom headers for metadata exchange

**Response:**
```json
{
  "processingId": "ACT-001-2024-01-15",
  "activityId": "ACT-001",
  "applicationDate": "2024-01-15",
  "activityType": "DATA_PROCESSING",
  "activityStatus": "PENDING",
  "endpointUrl": "https://api.example.com/data",
  "packetSize": 5,
  "status": "STARTED",
  "testMode": true,
  "enhancedHeaders": {
    "X-Test-Mode": "true",
    "X-Client-Version": "1.0",
    "X-Request-Source": "diagnostic-service"
  },
  "timestamp": "2024-01-01T00:00:00Z"
}
```

## Header-based Metadata

The system supports using HTTP headers for packet tracking and metadata exchange:

### Request Headers (Sent to External API)

The system automatically adds these headers to requests:

- `X-Packet-Offset`: Current offset for pagination
- `X-Packet-Limit`: Number of records requested
- `X-Packet-Request-Time`: Timestamp of the request
- `X-Packet-Request-Id`: Unique request identifier
- `X-Packet-Client`: Client identifier ("diagnostic-service")
- `X-Packet-Version`: API version ("1.0")

### Response Headers (Expected from External API)

The system can extract metadata from these response headers:

- `X-Total-Records`: Total number of records available
- `X-Has-More-Records`: Boolean indicating if more records are available
- `X-Next-Offset`: Next offset for pagination
- `X-Current-Offset`: Current offset processed
- `X-Packet-Size`: Size of the current packet
- `X-Server-Processing-Time`: Server processing time in milliseconds
- `X-Server-Timestamp`: Server timestamp

### Benefits of Header-based Metadata

1. **Cleaner URLs**: No need to add pagination parameters to URLs
2. **Rich Metadata**: Exchange detailed tracking information
3. **Server Control**: External API can control pagination logic
4. **Performance Tracking**: Monitor server processing times
5. **Flexible Pagination**: Support complex pagination strategies

## Configuration

Add the following configuration to your `application.yml`:

### Basic Configuration

```yaml
# Packet Processing Configuration
packet:
  processing:
    default-packet-size: 10
    max-retries: 3
    retry-delay-ms: 5000
    timeout:
      connect: 5000
      read: 30000
    retry:
      max-attempts: 3
      initial-delay: 1000
      multiplier: 2.0
      max-delay: 10000
      packet-processing:
        max-attempts: 5
        initial-delay: 500
        multiplier: 1.5
        max-delay: 5000
```

### Retry Configuration

The system uses Spring Retry's `@Retryable` annotation for automatic retry with exponential backoff:

#### Standard Retry Settings
- **Max Attempts**: 3
- **Initial Delay**: 1000ms
- **Multiplier**: 2.0 (exponential backoff)
- **Max Delay**: 10000ms
- **Retryable Exceptions**: Server errors (5xx), connection errors, general exceptions
- **Non-Retryable Exceptions**: Client errors (4xx)

#### Packet Processing Retry Settings
- **Max Attempts**: 5 (more aggressive for critical operations)
- **Initial Delay**: 500ms (faster initial retry)
- **Multiplier**: 1.5 (less aggressive backoff)
- **Max Delay**: 5000ms (shorter max delay)

#### Retry Behavior
1. **Automatic Retry**: Failed requests are automatically retried
2. **Exponential Backoff**: Delay between retries increases exponentially
3. **Exception-based**: Only specific exceptions trigger retries
4. **Recovery**: Custom recovery logic handles final failures
5. **Logging**: All retry attempts are logged for monitoring

## Usage Examples

### Basic Usage

1. **Start Processing:**
```bash
curl -X POST "http://localhost:8080/api/diagnostic/packet-processing/start?endpointUrl=https://jsonplaceholder.typicode.com/posts&activityId=ACT-001&applicationDate=2024-01-15&activityType=DATA_PROCESSING&activityStatus=PENDING&packetSize=10"
```

2. **Check Status by Activity:**
```bash
curl -X GET "http://localhost:8080/api/diagnostic/packet-processing/activity/ACT-001/date/2024-01-15/status"
```

3. **Pause Processing:**
```bash
curl -X POST "http://localhost:8080/api/diagnostic/packet-processing/activity/ACT-001/date/2024-01-15/pause"
```

4. **Resume Processing:**
```bash
curl -X POST "http://localhost:8080/api/diagnostic/packet-processing/activity/ACT-001/date/2024-01-15/resume"
```

5. **Cancel Processing:**
```bash
curl -X POST "http://localhost:8080/api/diagnostic/packet-processing/activity/ACT-001/date/2024-01-15/cancel"
```

6. **Retry Failed Records by Activity:**
```bash
curl -X POST "http://localhost:8080/api/diagnostic/packet-processing/activity/ACT-001/date/2024-01-15/retry"
```

7. **Test Header Metadata:**
```bash
curl -X POST "http://localhost:8080/api/diagnostic/packet-processing/test-headers?endpointUrl=https://jsonplaceholder.typicode.com/posts&activityId=ACT-001&applicationDate=2024-01-15&activityType=DATA_PROCESSING&activityStatus=PENDING&packetSize=5" \
  -H "X-Custom-Header: custom-value" \
  -H "X-Client-Id: my-client-123"
```

8. **Start Processing with Custom Headers:**
```bash
curl -X POST "http://localhost:8080/api/diagnostic/packet-processing/start?endpointUrl=https://api.example.com/data&activityId=ACT-001&applicationDate=2024-01-15&activityType=DATA_PROCESSING&activityStatus=PENDING&packetSize=10" \
  -H "X-Client-Version: 2.0" \
  -H "X-Request-Source: web-ui" \
  -H "X-User-Id: user123" \
  -H "X-Correlation-Id: req-456"
```

### Testing Retry Functionality

A dedicated test script is provided to demonstrate retry behavior:

```bash
./test-retry-functionality.sh
```

This script tests:
- Retry with consistently failing endpoints
- Retry with intermittent failures
- Retry with timeout scenarios
- Retry with successful endpoints

### Using the Test Script

A test script is provided to demonstrate the functionality:

```bash
./test-packet-processing.sh
```

This script will:
1. Check if the service is running
2. Start packet processing with a test endpoint
3. Monitor the processing status
4. Demonstrate pause/resume functionality
5. Show retry capabilities

## Error Handling

### Session States

- **ACTIVE**: Processing is currently running
- **PAUSED**: Processing has been paused and can be resumed
- **COMPLETED**: All records have been processed successfully
- **FAILED**: Processing failed due to an error
- **CANCELLED**: Processing was cancelled by user

### Record States

- **PENDING**: Record is waiting to be processed
- **PROCESSING**: Record is currently being processed
- **SUCCESS**: Record was processed successfully
- **FAILED**: Record processing failed
- **SKIPPED**: Record was skipped (e.g., due to validation)

### Error Categories

- **CLIENT_ERROR**: 4xx HTTP errors
- **SERVER_ERROR**: 5xx HTTP errors
- **CONNECTION_ERROR**: Network connectivity issues
- **RESPONSE_PROCESSING_ERROR**: Issues parsing response data
- **PROCESSING_ERROR**: Errors during record processing
- **UNKNOWN_ERROR**: Unexpected errors

## Failure Recovery

### Automatic Retry

- Failed records are automatically retried based on configuration
- Retry count is tracked per record
- Exponential backoff is applied between retries

### Manual Recovery

- Use the retry endpoint to retry failed records
- Resume paused sessions to continue processing
- Cancel sessions that are no longer needed

### Resume from Failure

- Sessions maintain their state in the database
- Processing can be resumed from the last successful offset
- Failed records can be retried individually

## Monitoring

### Database Queries

Monitor processing progress using database queries:

```sql
-- Get session status
SELECT * FROM packet_processing_sessions WHERE session_id = 'your-session-id';

-- Get failed records
SELECT * FROM packet_processing_records 
WHERE session_id = 'your-session-id' AND status = 'FAILED';

-- Get processing statistics
SELECT 
    status,
    COUNT(*) as count,
    AVG(processing_time_ms) as avg_processing_time
FROM packet_processing_records 
WHERE session_id = 'your-session-id'
GROUP BY status;
```

### Health Checks

The service provides health check endpoints:

- `/api/diagnostic/health`: Overall service health
- `/api/diagnostic/stats`: Processing statistics
- `/api/diagnostic/packet-processing/sessions`: Active sessions

## Best Practices

1. **Packet Size**: Choose an appropriate packet size based on:
   - Network latency to the target endpoint
   - Memory constraints
   - Processing time per record

2. **Error Handling**: Monitor failed records and implement appropriate retry strategies

3. **Resource Management**: Use pause/resume for long-running processes to manage resources

4. **Monitoring**: Regularly check session status and failed records

5. **Cleanup**: Cancel completed or failed sessions to free up resources

## Troubleshooting

### Common Issues

1. **Connection Timeouts**: Increase timeout values in configuration
2. **Memory Issues**: Reduce packet size
3. **High Failure Rate**: Check endpoint availability and data format
4. **Stuck Sessions**: Check for database locks or long-running transactions

### Debug Information

Enable debug logging to see detailed processing information:

```yaml
logging:
  level:
    "[com.example.diagnosticservice.service.PacketProcessingService]": DEBUG
    "[com.example.diagnosticservice.service.RestClientService]": DEBUG
```

## Database Schema

### packet_processing_sessions

| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL | Primary key |
| activity_id | VARCHAR(255) | Activity identifier |
| application_date | DATE | Application date |
| activity_type | VARCHAR(255) | Type of activity |
| activity_status | VARCHAR(255) | Status of activity |
| endpoint_url | VARCHAR(500) | Target REST endpoint URL |
| packet_size | INTEGER | Number of records per packet |
| total_records | INTEGER | Total records processed |
| processed_records | INTEGER | Successfully processed records |
| failed_records | INTEGER | Failed records count |
| current_offset | INTEGER | Current processing offset |
| status | VARCHAR(50) | Session status |
| error_message | TEXT | Error details if failed |
| created_at | TIMESTAMP | Session creation time |
| started_at | TIMESTAMP | Processing start time |
| completed_at | TIMESTAMP | Processing completion time |

### packet_processing_records

| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL | Primary key |
| activity_id | VARCHAR(255) | Activity identifier |
| application_date | DATE | Application date |
| record_id | VARCHAR(255) | Unique record identifier |
| packet_number | INTEGER | Packet number within session |
| record_index | INTEGER | Record index within packet |
| status | VARCHAR(50) | Record processing status |
| record_data | TEXT | Record data (JSON) |
| error_message | TEXT | Error details if failed |
| processing_time_ms | BIGINT | Processing time in milliseconds |
| retry_count | INTEGER | Number of retry attempts |
| created_at | TIMESTAMP | Record creation time |
| processed_at | TIMESTAMP | Processing completion time |

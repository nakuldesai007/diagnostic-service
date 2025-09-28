# Test Environment Setup Guide

This guide explains how to set up and run the diagnostic service tests with PostgreSQL and Kafka UI.

## Quick Start

1. **Set up the test environment:**
   ```bash
   ./setup-test-environment.sh
   ```

2. **Run tests:**
   ```bash
   # Comprehensive end-to-end tests
   ./test-e2e-comprehensive.sh
   
   # Performance test with 1000 messages
   ./test-performance-1000.sh
   
   # Simple performance test
   ./test-performance-simple.sh
   
   # Basic functionality test
   ./test-simple.sh
   ```

3. **Monitor with Kafka UI:**
   - Open http://localhost:8081 in your browser
   - View all Kafka topics and messages
   - Monitor message flow and processing

## Services

The setup includes the following services:

### PostgreSQL Database
- **Host:** localhost:5432
- **Database:** diagnostic_service
- **Username:** diagnostic_user
- **Password:** diagnostic_password

### Kafka
- **Bootstrap Server:** localhost:9092
- **Topics:**
  - `projection-processing-queue` - Main processing queue
  - `failed-projection-messages` - Failed messages queue
  - `dead-letter-queue` - Dead letter queue

### Application
- **URL:** http://localhost:8080
- **Health Check:** http://localhost:8080/actuator/health
- **API Stats:** http://localhost:8080/api/diagnostic/stats

### Kafka UI
- **URL:** http://localhost:8081
- **Purpose:** Monitor Kafka topics and messages

## Database Commands

### Connect to PostgreSQL
```bash
docker exec -it postgres psql -U diagnostic_user -d diagnostic_service
```

### View Message Logs
```bash
docker exec postgres psql -U diagnostic_user -d diagnostic_service -c "SELECT * FROM message_logs ORDER BY created_at DESC LIMIT 10;"
```

### View Circuit Breaker Events
```bash
docker exec postgres psql -U diagnostic_user -d diagnostic_service -c "SELECT * FROM circuit_breaker_events ORDER BY created_at DESC LIMIT 10;"
```

### View Retry Attempts
```bash
docker exec postgres psql -U diagnostic_user -d diagnostic_service -c "SELECT * FROM retry_attempts ORDER BY created_at DESC LIMIT 10;"
```

### View Dead Letter Messages
```bash
docker exec postgres psql -U diagnostic_user -d diagnostic_service -c "SELECT * FROM dead_letter_messages ORDER BY created_at DESC LIMIT 10;"
```

## Monitoring

### Application Metrics
- **Metrics:** http://localhost:8080/actuator/metrics
- **Circuit Breaker State:** http://localhost:8080/api/diagnostic/circuit-breaker/state

### Kafka UI
- **URL:** http://localhost:8081
- **Features:**
  - View all topics and their partitions
  - Monitor message flow in real-time
  - Inspect message content and headers
  - View consumer groups and their lag

## Test Scripts

### test-e2e-comprehensive.sh
Comprehensive end-to-end tests that cover:
- Successful message processing
- Retryable errors (transient)
- System errors (database connection)
- Validation errors (non-retryable)
- Permanent errors (user not found)
- Failed projection messages (direct to DLQ)
- Circuit breaker stress test
- Mixed message types

### test-performance-1000.sh
Performance test with 1000 messages:
- Tests different message types (success, timeout, database, validation, user not found, system overload)
- Monitors database insertion performance
- Tracks processing statistics
- Generates detailed performance reports

### test-performance-simple.sh
Simple performance test with fewer messages for quick validation.

### test-simple.sh
Basic functionality test for quick smoke testing.

## Troubleshooting

### Services Not Starting
1. Make sure Docker is running
2. Check if ports 5432, 8080, 8081, and 9092 are available
3. Run `docker-compose logs` to see error messages

### Database Connection Issues
1. Ensure PostgreSQL container is running: `docker ps | grep postgres`
2. Check database logs: `docker logs postgres`
3. Verify database is ready: `docker exec postgres pg_isready -U diagnostic_user -d diagnostic_service`

### Kafka Issues
1. Ensure Kafka container is running: `docker ps | grep kafka`
2. Check Kafka logs: `docker logs kafka`
3. Verify topics exist: `docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list`

### Application Issues
1. Check application logs: `docker logs diagnostic-service`
2. Verify health endpoint: `curl http://localhost:8080/actuator/health`
3. Check if all dependencies are running

## Cleanup

To stop all services:
```bash
docker-compose down
```

To remove all data and start fresh:
```bash
docker-compose down -v
./setup-test-environment.sh
```

## Development

The test scripts automatically detect whether you're using Docker or local installations:
- If Docker containers are running, they use Docker commands
- If local installations are available, they use local commands
- This allows for flexible development and testing environments

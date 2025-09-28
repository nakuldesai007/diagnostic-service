#!/bin/bash

# Small Performance Test Script for 10 Messages
# Tests message processing performance and PostgreSQL data insertion

set -e

echo "🚀 Starting Small Performance Test with 10 Messages"
echo "=================================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

# Test configuration
KAFKA_BOOTSTRAP="localhost:9092"
API_BASE="http://localhost:8080"
TEST_TOPIC="projection-processing-queue"
MESSAGE_COUNT=10

# Helper functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_performance() {
    echo -e "${PURPLE}[PERF]${NC} $1"
}

# Send test messages
send_test_messages() {
    log_info "Sending $MESSAGE_COUNT test messages..."
    
    for ((i=1; i<=MESSAGE_COUNT; i++)); do
        local message_id="test-$(printf "%03d" $i)"
        local message='{"id":"'$message_id'","name":"Test Message '$i'","data":"This is test message '$i'","timestamp":"'$(date -u +%Y-%m-%dT%H:%M:%S.000Z)'","testType":"SUCCESS"}'
        
        echo "$message_id:$message" | docker exec -i kafka kafka-console-producer \
            --bootstrap-server localhost:9092 \
            --topic $TEST_TOPIC \
            --property "key.separator=:" \
            --property "parse.key=true" \
            --property "key.serializer=org.apache.kafka.common.serialization.StringSerializer" \
            --property "value.serializer=org.apache.kafka.common.serialization.StringSerializer" > /dev/null 2>&1
        
        echo -n "."
    done
    echo
    log_success "Sent $MESSAGE_COUNT messages"
}

# Monitor processing
monitor_processing() {
    log_info "Monitoring message processing..."
    
    local start_time=$(date +%s)
    local timeout=60  # 60 seconds timeout
    
    while true; do
        local current_time=$(date +%s)
        local elapsed=$((current_time - start_time))
        
        if [ $elapsed -gt $timeout ]; then
            log_error "Timeout waiting for messages to be processed"
            break
        fi
        
        local processed=$(docker exec postgres psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM message_logs WHERE created_at > NOW() - INTERVAL '5 minutes';" 2>/dev/null | tr -d ' \n')
        
        log_performance "Processed: $processed/$MESSAGE_COUNT messages"
        
        if [ "$processed" -ge "$MESSAGE_COUNT" ]; then
            log_success "All messages processed!"
            break
        fi
        
        sleep 2
    done
}

# Check results
check_results() {
    log_info "Checking results..."
    
    local total_messages=$(docker exec postgres psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM message_logs;" 2>/dev/null | tr -d ' \n')
    local recent_messages=$(docker exec postgres psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM message_logs WHERE created_at > NOW() - INTERVAL '5 minutes';" 2>/dev/null | tr -d ' \n')
    
    echo
    log_performance "=== RESULTS ==="
    echo "Total messages in database: $total_messages"
    echo "Recent messages (last 5 min): $recent_messages"
    echo
    
    if [ "$recent_messages" -ge "$MESSAGE_COUNT" ]; then
        log_success "✅ Test PASSED: All $MESSAGE_COUNT messages were processed and logged to PostgreSQL!"
    else
        log_error "❌ Test FAILED: Only $recent_messages out of $MESSAGE_COUNT messages were processed"
    fi
    
    # Show recent messages
    log_info "Recent messages:"
    docker exec postgres psql -U diagnostic_user -d diagnostic_service -c "SELECT message_id, processing_status, created_at FROM message_logs ORDER BY created_at DESC LIMIT 10;"
}

# Main execution
main() {
    echo "Starting small performance test at $(date)"
    echo
    
    send_test_messages
    monitor_processing
    check_results
    
    echo
    echo "Test completed at $(date)"
}

# Run main function
main "$@"

#!/bin/bash

# Comprehensive End-to-End Test Script for Diagnostic Service
# This script tests multiple use cases including success, retry, circuit breaker, and DLQ scenarios

set -e

echo "🚀 Starting Comprehensive End-to-End Tests for Diagnostic Service"
echo "================================================================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test configuration
KAFKA_BOOTSTRAP="localhost:9092"
API_BASE="http://localhost:8080"
TEST_TOPIC="projection-processing-queue"
FAILED_TOPIC="failed-projection-messages"
DLQ_TOPIC="dead-letter-queue"

# Helper functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if services are running
check_services() {
    log_info "Checking if services are running..."
    
    # Check if application is running
    if ! curl -s "$API_BASE/actuator/health" > /dev/null; then
        log_error "Application is not running on $API_BASE"
        exit 1
    fi
    
    # Check if Kafka is running
    if ! /opt/homebrew/opt/kafka/bin/kafka-topics --bootstrap-server $KAFKA_BOOTSTRAP --list > /dev/null 2>&1; then
        log_error "Kafka is not running on $KAFKA_BOOTSTRAP"
        exit 1
    fi
    
    log_success "All services are running"
}

# Send message to Kafka
send_message() {
    local topic=$1
    local message=$2
    local key=$3
    
    if [ -n "$key" ]; then
        echo "$key:$message" | /opt/homebrew/opt/kafka/bin/kafka-console-producer \
            --bootstrap-server $KAFKA_BOOTSTRAP \
            --topic $topic \
            --property "key.separator=:" \
            --property "parse.key=true" \
            --property "key.serializer=org.apache.kafka.common.serialization.StringSerializer" \
            --property "value.serializer=org.apache.kafka.common.serialization.StringSerializer"
    else
        echo "$message" | /opt/homebrew/opt/kafka/bin/kafka-console-producer \
            --bootstrap-server $KAFKA_BOOTSTRAP \
            --topic $topic \
            --property "value.serializer=org.apache.kafka.common.serialization.StringSerializer"
    fi
}

# Wait for processing
wait_for_processing() {
    local seconds=${1:-3}
    log_info "Waiting $seconds seconds for message processing..."
    sleep $seconds
}

# Get current stats
get_stats() {
    curl -s "$API_BASE/api/diagnostic/stats" | jq .
}

# Test Case 1: Successful Message Processing
test_successful_processing() {
    log_info "Test Case 1: Successful Message Processing"
    echo "----------------------------------------"
    
    local message='{"id":"success-001","name":"Successful Test","data":"This should process successfully","testType":"SUCCESS","timestamp":"'$(date -u +%Y-%m-%dT%H:%M:%S.000Z)'"}'
    
    log_info "Sending successful message..."
    send_message "$TEST_TOPIC" "$message" "success-001"
    
    wait_for_processing 5
    
    log_info "Current stats after successful message:"
    get_stats | jq '.attemptTracker, .retryService'
    
    log_success "Test Case 1 completed"
    echo
}

# Test Case 2: Retryable Error (Transient Error)
test_retryable_error() {
    log_info "Test Case 2: Retryable Error (Transient Error)"
    echo "----------------------------------------"
    
    local message='{"id":"retry-001","name":"Retry Test","data":"Connection timeout error","errorType":"TIMEOUT","timestamp":"'$(date -u +%Y-%m-%dT%H:%M:%S.000Z)'"}'
    
    log_info "Sending retryable error message..."
    send_message "$TEST_TOPIC" "$message" "retry-001"
    
    wait_for_processing 5
    
    log_info "Current stats after retryable error:"
    get_stats | jq '.attemptTracker, .retryService'
    
    log_success "Test Case 2 completed"
    echo
}

# Test Case 3: System Error (Database Connection Failed)
test_system_error() {
    log_info "Test Case 3: System Error (Database Connection Failed)"
    echo "----------------------------------------"
    
    local message='{"id":"system-001","name":"System Error Test","data":"Database connection failed","errorType":"DATABASE","timestamp":"'$(date -u +%Y-%m-%dT%H:%M:%S.000Z)'"}'
    
    log_info "Sending system error message..."
    send_message "$TEST_TOPIC" "$message" "system-001"
    
    wait_for_processing 5
    
    log_info "Current stats after system error:"
    get_stats | jq '.attemptTracker, .retryService'
    
    log_success "Test Case 3 completed"
    echo
}

# Test Case 4: Non-retryable Error (Validation Error)
test_validation_error() {
    log_info "Test Case 4: Non-retryable Error (Validation Error)"
    echo "----------------------------------------"
    
    local message='{"id":"validation-001","name":"Validation Error Test","data":"Validation failed - invalid format","errorType":"VALIDATION","timestamp":"'$(date -u +%Y-%m-%dT%H:%M:%S.000Z)'"}'
    
    log_info "Sending validation error message..."
    send_message "$TEST_TOPIC" "$message" "validation-001"
    
    wait_for_processing 5
    
    log_info "Current stats after validation error:"
    get_stats | jq '.attemptTracker, .retryService'
    
    log_success "Test Case 4 completed"
    echo
}

# Test Case 5: Permanent Error (User Not Found)
test_permanent_error() {
    log_info "Test Case 5: Permanent Error (User Not Found)"
    echo "----------------------------------------"
    
    local message='{"id":"permanent-001","name":"Permanent Error Test","data":"User not found","errorType":"NOT_FOUND","timestamp":"'$(date -u +%Y-%m-%dT%H:%M:%S.000Z)'"}'
    
    log_info "Sending permanent error message..."
    send_message "$TEST_TOPIC" "$message" "permanent-001"
    
    wait_for_processing 5
    
    log_info "Current stats after permanent error:"
    get_stats | jq '.attemptTracker, .retryService'
    
    log_success "Test Case 5 completed"
    echo
}

# Test Case 6: Failed Projection Message (Direct to DLQ)
test_failed_projection_message() {
    log_info "Test Case 6: Failed Projection Message (Direct to DLQ)"
    echo "----------------------------------------"
    
    local message='{"messageId":"failed-proj-001","originalMessage":"Projection processing failed","errorMessage":"System overload","sourceTopic":"projection-processing-queue","partition":0,"offset":100,"failureTimestamp":"'$(date -u +%Y-%m-%dT%H:%M:%S.000Z)'"}'
    
    log_info "Sending failed projection message directly to failed-projection-messages topic..."
    send_message "$FAILED_TOPIC" "$message" "failed-proj-001"
    
    wait_for_processing 5
    
    log_info "Current stats after failed projection message:"
    get_stats | jq '.attemptTracker, .retryService'
    
    log_success "Test Case 6 completed"
    echo
}

# Test Case 7: Circuit Breaker Stress Test
test_circuit_breaker_stress() {
    log_info "Test Case 7: Circuit Breaker Stress Test"
    echo "----------------------------------------"
    
    log_info "Sending multiple rapid failure messages to trigger circuit breaker..."
    
    for i in {1..5}; do
        local message='{"id":"stress-00'$i'","name":"Stress Test '$i'","data":"Rapid failure test","errorType":"OVERLOAD","timestamp":"'$(date -u +%Y-%m-%dT%H:%M:%S.000Z)'"}'
        send_message "$TEST_TOPIC" "$message" "stress-00$i"
        sleep 0.5
    done
    
    wait_for_processing 8
    
    log_info "Circuit breaker state after stress test:"
    curl -s "$API_BASE/api/diagnostic/circuit-breaker/state" | jq .
    
    log_info "Current stats after stress test:"
    get_stats | jq '.circuitBreaker, .attemptTracker'
    
    log_success "Test Case 7 completed"
    echo
}

# Test Case 8: Mixed Message Types
test_mixed_messages() {
    log_info "Test Case 8: Mixed Message Types"
    echo "----------------------------------------"
    
    local messages=(
        '{"id":"mixed-001","name":"Success","data":"This should succeed","testType":"SUCCESS","timestamp":"'$(date -u +%Y-%m-%dT%H:%M:%S.000Z)'"}'
        '{"id":"mixed-002","name":"Timeout","data":"Connection timeout","errorType":"TIMEOUT","timestamp":"'$(date -u +%Y-%m-%dT%H:%M:%S.000Z)'"}'
        '{"id":"mixed-003","name":"Database Error","data":"Database connection failed","errorType":"DATABASE","timestamp":"'$(date -u +%Y-%m-%dT%H:%M:%S.000Z)'"}'
        '{"id":"mixed-004","name":"Validation","data":"Invalid data","errorType":"VALIDATION","timestamp":"'$(date -u +%Y-%m-%dT%H:%M:%S.000Z)'"}'
        '{"id":"mixed-005","name":"Not Found","data":"Resource not found","errorType":"NOT_FOUND","timestamp":"'$(date -u +%Y-%m-%dT%H:%M:%S.000Z)'"}'
    )
    
    log_info "Sending mixed message types..."
    for i in "${!messages[@]}"; do
        local key="mixed-00$((i+1))"
        send_message "$TEST_TOPIC" "${messages[$i]}" "$key"
        sleep 1
    done
    
    wait_for_processing 10
    
    log_info "Final stats after mixed messages:"
    get_stats | jq '.attemptTracker, .retryService, .circuitBreaker'
    
    log_success "Test Case 8 completed"
    echo
}

# Check database state
check_database_state() {
    log_info "Checking database state..."
    echo "----------------------------------------"
    
    export PATH="/opt/homebrew/opt/postgresql@15/bin:$PATH"
    
    log_info "Message logs (last 10):"
    psql -U diagnostic_user -d diagnostic_service -c "SELECT message_id, topic, processing_status, created_at FROM message_logs ORDER BY created_at DESC LIMIT 10;"
    
    log_info "Circuit breaker events (last 5):"
    psql -U diagnostic_user -d diagnostic_service -c "SELECT circuit_breaker_name, event_type, created_at FROM circuit_breaker_events ORDER BY created_at DESC LIMIT 5;"
    
    log_info "Retry attempts (last 5):"
    psql -U diagnostic_user -d diagnostic_service -c "SELECT message_id, attempt_number, error_message, created_at FROM retry_attempts ORDER BY created_at DESC LIMIT 5;"
    
    log_info "Dead letter messages (last 5):"
    psql -U diagnostic_user -d diagnostic_service -c "SELECT message_id, source_topic, failure_reason, created_at FROM dead_letter_messages ORDER BY created_at DESC LIMIT 5;"
}

# Main execution
main() {
    echo "Starting comprehensive end-to-end tests at $(date)"
    echo
    
    check_services
    
    # Run all test cases
    test_successful_processing
    test_retryable_error
    test_system_error
    test_validation_error
    test_permanent_error
    test_failed_projection_message
    test_circuit_breaker_stress
    test_mixed_messages
    
    # Check final state
    log_info "Final Application State:"
    echo "=========================="
    get_stats | jq .
    
    check_database_state
    
    log_success "All comprehensive end-to-end tests completed!"
    echo "Test completed at $(date)"
}

# Run main function
main "$@"

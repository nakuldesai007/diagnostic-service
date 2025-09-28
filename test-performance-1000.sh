#!/bin/bash

# Performance Test Script for 1000 Messages
# Tests message processing performance and PostgreSQL data insertion

set -e

echo "🚀 Starting Performance Test with 1000 Messages"
echo "=============================================="

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
FAILED_TOPIC="failed-projection-messages"
DLQ_TOPIC="dead-letter-queue"
MESSAGE_COUNT=1000
BATCH_SIZE=50
DELAY_BETWEEN_BATCHES=0.1

# Performance tracking variables
START_TIME=""
END_TIME=""
TOTAL_MESSAGES_SENT=0
TOTAL_MESSAGES_PROCESSED=0

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

log_performance() {
    echo -e "${PURPLE}[PERF]${NC} $1"
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
    
    # Check if PostgreSQL is accessible
    export PATH="/opt/homebrew/opt/postgresql@15/bin:$PATH"
    if ! psql -U diagnostic_user -d diagnostic_service -c "SELECT 1;" > /dev/null 2>&1; then
        log_error "PostgreSQL is not accessible"
        exit 1
    fi
    
    log_success "All services are running"
}

# Get baseline database counts
get_baseline_counts() {
    log_info "Getting baseline database counts..."
    
    export PATH="/opt/homebrew/opt/postgresql@15/bin:$PATH"
    
    BASELINE_MESSAGE_LOGS=$(psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM message_logs;")
    BASELINE_CIRCUIT_BREAKER_EVENTS=$(psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM circuit_breaker_events;")
    BASELINE_RETRY_ATTEMPTS=$(psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM retry_attempts;")
    BASELINE_DLQ_MESSAGES=$(psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM dead_letter_messages;")
    
    log_info "Baseline counts:"
    echo "  Message Logs: $BASELINE_MESSAGE_LOGS"
    echo "  Circuit Breaker Events: $BASELINE_CIRCUIT_BREAKER_EVENTS"
    echo "  Retry Attempts: $BASELINE_RETRY_ATTEMPTS"
    echo "  DLQ Messages: $BASELINE_DLQ_MESSAGES"
}

# Send batch of messages
send_batch() {
    local batch_start=$1
    local batch_end=$2
    local batch_type=$3
    
    log_info "Sending batch $batch_start-$batch_end ($batch_type messages)..."
    
    for ((i=batch_start; i<=batch_end; i++)); do
        local message_id="perf-test-$(printf "%04d" $i)"
        local message=""
        
        case $batch_type in
            "success")
                message='{"id":"'$message_id'","name":"Performance Test Success","data":"This should process successfully","timestamp":"'$(date -u +%Y-%m-%dT%H:%M:%SZ)'","testType":"SUCCESS"}'
                ;;
            "timeout")
                message='{"id":"'$message_id'","name":"Performance Test Timeout","data":"Connection timeout error","errorMessage":"Connection timeout","timestamp":"'$(date -u +%Y-%m-%dT%H:%M:%SZ)'","testType":"TIMEOUT"}'
                ;;
            "database")
                message='{"id":"'$message_id'","name":"Performance Test Database","data":"Database connection failed","errorMessage":"Database connection failed","timestamp":"'$(date -u +%Y-%m-%dT%H:%M:%SZ)'","testType":"DATABASE"}'
                ;;
            "validation")
                message='{"id":"'$message_id'","name":"Performance Test Validation","data":"Validation failed - invalid format","errorMessage":"Validation failed","timestamp":"'$(date -u +%Y-%m-%dT%H:%M:%SZ)'","testType":"VALIDATION"}'
                ;;
            "user-not-found")
                message='{"id":"'$message_id'","name":"Performance Test User Not Found","data":"User not found","errorMessage":"User not found","timestamp":"'$(date -u +%Y-%m-%dT%H:%M:%SZ)'","testType":"USER_NOT_FOUND"}'
                ;;
            "system-overload")
                message='{"id":"'$message_id'","name":"Performance Test System Overload","data":"System overload","errorMessage":"System overload","timestamp":"'$(date -u +%Y-%m-%dT%H:%M:%SZ)'","testType":"SYSTEM_OVERLOAD"}'
                ;;
        esac
        
        echo "$message" | /opt/homebrew/opt/kafka/bin/kafka-console-producer \
            --bootstrap-server $KAFKA_BOOTSTRAP \
            --topic $TEST_TOPIC \
            --property "key.separator=:" \
            --property "parse.key=true" \
            --property "key.serializer=org.apache.kafka.common.serialization.StringSerializer" \
            --property "key=$message_id" > /dev/null 2>&1
        
        TOTAL_MESSAGES_SENT=$((TOTAL_MESSAGES_SENT + 1))
    done
    
    log_success "Sent batch $batch_start-$batch_end ($batch_type)"
}

# Monitor processing progress
monitor_progress() {
    local expected_total=$1
    local check_interval=${2:-5}
    
    log_info "Monitoring processing progress (checking every ${check_interval}s)..."
    
    while true; do
        export PATH="/opt/homebrew/opt/postgresql@15/bin:$PATH"
        
        local current_processed=$(psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM message_logs WHERE created_at > NOW() - INTERVAL '10 minutes';")
        current_processed=$(echo $current_processed | tr -d ' ')
        
        local progress_percent=$((current_processed * 100 / expected_total))
        
        log_performance "Progress: $current_processed/$expected_total messages processed ($progress_percent%)"
        
        if [ $current_processed -ge $expected_total ]; then
            break
        fi
        
        sleep $check_interval
    done
}

# Get performance metrics
get_performance_metrics() {
    log_info "Collecting performance metrics..."
    
    export PATH="/opt/homebrew/opt/postgresql@15/bin:$PATH"
    
    # Database metrics
    local final_message_logs=$(psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM message_logs;")
    local final_circuit_breaker_events=$(psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM circuit_breaker_events;")
    local final_retry_attempts=$(psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM retry_attempts;")
    local final_dlq_messages=$(psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM dead_letter_messages;")
    
    # Clean up counts (remove spaces)
    final_message_logs=$(echo $final_message_logs | tr -d ' ')
    final_circuit_breaker_events=$(echo $final_circuit_breaker_events | tr -d ' ')
    final_retry_attempts=$(echo $final_retry_attempts | tr -d ' ')
    final_dlq_messages=$(echo $final_dlq_messages | tr -d ' ')
    
    # Calculate deltas
    local new_message_logs=$((final_message_logs - BASELINE_MESSAGE_LOGS))
    local new_circuit_breaker_events=$((final_circuit_breaker_events - BASELINE_CIRCUIT_BREAKER_EVENTS))
    local new_retry_attempts=$((final_retry_attempts - BASELINE_RETRY_ATTEMPTS))
    local new_dlq_messages=$((final_dlq_messages - BASELINE_DLQ_MESSAGES))
    
    # Processing status breakdown
    local success_count=$(psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM message_logs WHERE processing_status = 'SUCCESS' AND created_at > NOW() - INTERVAL '10 minutes';")
    local failed_count=$(psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM message_logs WHERE processing_status = 'FAILED' AND created_at > NOW() - INTERVAL '10 minutes';")
    local circuit_breaker_count=$(psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM message_logs WHERE processing_status = 'CIRCUIT_BREAKER_OPEN' AND created_at > NOW() - INTERVAL '10 minutes';")
    
    success_count=$(echo $success_count | tr -d ' ')
    failed_count=$(echo $failed_count | tr -d ' ')
    circuit_breaker_count=$(echo $circuit_breaker_count | tr -d ' ')
    
    # Calculate processing time
    local total_seconds=$((END_TIME - START_TIME))
    local messages_per_second=$((TOTAL_MESSAGES_SENT / total_seconds))
    
    # Display results
    echo
    log_performance "=== PERFORMANCE TEST RESULTS ==="
    echo
    log_performance "Test Configuration:"
    echo "  Total Messages Sent: $TOTAL_MESSAGES_SENT"
    echo "  Batch Size: $BATCH_SIZE"
    echo "  Total Test Duration: ${total_seconds}s"
    echo "  Messages per Second: $messages_per_second"
    echo
    
    log_performance "Database Insertion Results:"
    echo "  New Message Logs: $new_message_logs"
    echo "  New Circuit Breaker Events: $new_circuit_breaker_events"
    echo "  New Retry Attempts: $new_retry_attempts"
    echo "  New DLQ Messages: $new_dlq_messages"
    echo
    
    log_performance "Processing Status Breakdown:"
    echo "  Successful: $success_count"
    echo "  Failed: $failed_count"
    echo "  Circuit Breaker Open: $circuit_breaker_count"
    echo
    
    log_performance "Database Performance:"
    echo "  Message Logs Insertion Rate: $((new_message_logs / total_seconds)) per second"
    echo "  Total Database Operations: $((new_message_logs + new_circuit_breaker_events + new_retry_attempts + new_dlq_messages))"
    echo
}

# Generate detailed report
generate_detailed_report() {
    log_info "Generating detailed report..."
    
    export PATH="/opt/homebrew/opt/postgresql@15/bin:$PATH"
    
    echo
    log_performance "=== DETAILED DATABASE REPORT ==="
    echo
    
    # Recent message logs
    log_performance "Recent Message Logs (last 10):"
    psql -U diagnostic_user -d diagnostic_service -c "SELECT message_id, topic, processing_status, processing_time_ms, created_at FROM message_logs ORDER BY created_at DESC LIMIT 10;"
    echo
    
    # Error category breakdown
    log_performance "Error Category Breakdown:"
    psql -U diagnostic_user -d diagnostic_service -c "SELECT error_category, COUNT(*) as count FROM message_logs WHERE created_at > NOW() - INTERVAL '10 minutes' AND error_category IS NOT NULL GROUP BY error_category ORDER BY count DESC;"
    echo
    
    # Processing time statistics
    log_performance "Processing Time Statistics:"
    psql -U diagnostic_user -d diagnostic_service -c "SELECT processing_status, COUNT(*) as count, AVG(processing_time_ms) as avg_time_ms, MIN(processing_time_ms) as min_time_ms, MAX(processing_time_ms) as max_time_ms FROM message_logs WHERE created_at > NOW() - INTERVAL '10 minutes' AND processing_time_ms IS NOT NULL GROUP BY processing_status ORDER BY count DESC;"
    echo
    
    # Circuit breaker events
    log_performance "Circuit Breaker Events (last 10):"
    psql -U diagnostic_user -d diagnostic_service -c "SELECT circuit_breaker_name, event_type, from_state, to_state, created_at FROM circuit_breaker_events ORDER BY created_at DESC LIMIT 10;"
    echo
    
    # Retry attempts summary
    log_performance "Retry Attempts Summary:"
    psql -U diagnostic_user -d diagnostic_service -c "SELECT status, COUNT(*) as count, AVG(delay_ms) as avg_delay_ms FROM retry_attempts WHERE created_at > NOW() - INTERVAL '10 minutes' GROUP BY status ORDER BY count DESC;"
    echo
}

# Main performance test
run_performance_test() {
    log_info "Starting performance test with $MESSAGE_COUNT messages..."
    echo
    
    # Record start time
    START_TIME=$(date +%s)
    
    # Send messages in batches with different types
    local messages_per_type=$((MESSAGE_COUNT / 6))
    local current_msg=1
    
    # Batch 1: Success messages (40%)
    local success_count=$((MESSAGE_COUNT * 40 / 100))
    log_info "Sending $success_count success messages..."
    for ((i=0; i<success_count; i+=BATCH_SIZE)); do
        local end=$((i + BATCH_SIZE - 1))
        if [ $end -gt $((success_count - 1)) ]; then
            end=$((success_count - 1))
        fi
        send_batch $((i + 1)) $((end + 1)) "success"
        sleep $DELAY_BETWEEN_BATCHES
    done
    
    # Batch 2: Timeout messages (20%)
    local timeout_count=$((MESSAGE_COUNT * 20 / 100))
    log_info "Sending $timeout_count timeout messages..."
    for ((i=0; i<timeout_count; i+=BATCH_SIZE)); do
        local end=$((i + BATCH_SIZE - 1))
        if [ $end -gt $((timeout_count - 1)) ]; then
            end=$((timeout_count - 1))
        fi
        send_batch $((success_count + i + 1)) $((success_count + end + 1)) "timeout"
        sleep $DELAY_BETWEEN_BATCHES
    done
    
    # Batch 3: Database error messages (15%)
    local db_count=$((MESSAGE_COUNT * 15 / 100))
    log_info "Sending $db_count database error messages..."
    for ((i=0; i<db_count; i+=BATCH_SIZE)); do
        local end=$((i + BATCH_SIZE - 1))
        if [ $end -gt $((db_count - 1)) ]; then
            end=$((db_count - 1))
        fi
        send_batch $((success_count + timeout_count + i + 1)) $((success_count + timeout_count + end + 1)) "database"
        sleep $DELAY_BETWEEN_BATCHES
    done
    
    # Batch 4: Validation error messages (10%)
    local validation_count=$((MESSAGE_COUNT * 10 / 100))
    log_info "Sending $validation_count validation error messages..."
    for ((i=0; i<validation_count; i+=BATCH_SIZE)); do
        local end=$((i + BATCH_SIZE - 1))
        if [ $end -gt $((validation_count - 1)) ]; then
            end=$((validation_count - 1))
        fi
        send_batch $((success_count + timeout_count + db_count + i + 1)) $((success_count + timeout_count + db_count + end + 1)) "validation"
        sleep $DELAY_BETWEEN_BATCHES
    done
    
    # Batch 5: User not found messages (10%)
    local user_not_found_count=$((MESSAGE_COUNT * 10 / 100))
    log_info "Sending $user_not_found_count user not found messages..."
    for ((i=0; i<user_not_found_count; i+=BATCH_SIZE)); do
        local end=$((i + BATCH_SIZE - 1))
        if [ $end -gt $((user_not_found_count - 1)) ]; then
            end=$((user_not_found_count - 1))
        fi
        send_batch $((success_count + timeout_count + db_count + validation_count + i + 1)) $((success_count + timeout_count + db_count + validation_count + end + 1)) "user-not-found"
        sleep $DELAY_BETWEEN_BATCHES
    done
    
    # Batch 6: System overload messages (5%)
    local overload_count=$((MESSAGE_COUNT - success_count - timeout_count - db_count - validation_count - user_not_found_count))
    log_info "Sending $overload_count system overload messages..."
    for ((i=0; i<overload_count; i+=BATCH_SIZE)); do
        local end=$((i + BATCH_SIZE - 1))
        if [ $end -gt $((overload_count - 1)) ]; then
            end=$((overload_count - 1))
        fi
        send_batch $((success_count + timeout_count + db_count + validation_count + user_not_found_count + i + 1)) $((success_count + timeout_count + db_count + validation_count + user_not_found_count + end + 1)) "system-overload"
        sleep $DELAY_BETWEEN_BATCHES
    done
    
    log_success "All $MESSAGE_COUNT messages sent!"
    
    # Monitor processing
    monitor_progress $MESSAGE_COUNT 10
    
    # Record end time
    END_TIME=$(date +%s)
    
    log_success "Performance test completed!"
}

# Main execution
main() {
    echo "Starting performance test at $(date)"
    echo "Test Configuration:"
    echo "  Messages: $MESSAGE_COUNT"
    echo "  Batch Size: $BATCH_SIZE"
    echo "  Delay Between Batches: ${DELAY_BETWEEN_BATCHES}s"
    echo
    
    check_services
    get_baseline_counts
    
    run_performance_test
    
    get_performance_metrics
    generate_detailed_report
    
    log_success "Performance test completed successfully!"
    echo "Test completed at $(date)"
}

# Run main function
main "$@"

#!/bin/bash

# Simple Performance Test Script for 1000 Messages
# Simplified version to avoid timeout issues

set -e

echo "🚀 Starting Simple Performance Test with 1000 Messages"
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
API_BASE="http://localhost:8082"
TEST_TOPIC="projection-processing-queue"
MESSAGE_COUNT=1000
BATCH_SIZE=100
DELAY_BETWEEN_BATCHES=0.05

# Performance tracking variables
START_TIME=""
END_TIME=""
TOTAL_MESSAGES_SENT=0

# Helper functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_performance() {
    echo -e "${PURPLE}[PERF]${NC} $1"
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

# Get baseline database counts
get_baseline_counts() {
    log_info "Getting baseline database counts..."
    
    export PATH="/opt/homebrew/opt/postgresql@15/bin:$PATH"
    
    BASELINE_MESSAGE_LOGS=$(psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM message_logs;" | tr -d ' ')
    BASELINE_CIRCUIT_BREAKER_EVENTS=$(psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM circuit_breaker_events;" | tr -d ' ')
    BASELINE_RETRY_ATTEMPTS=$(psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM retry_attempts;" | tr -d ' ')
    BASELINE_DLQ_MESSAGES=$(psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM dead_letter_messages;" | tr -d ' ')
    
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

# Get performance metrics
get_performance_metrics() {
    log_info "Collecting performance metrics..."
    
    export PATH="/opt/homebrew/opt/postgresql@15/bin:$PATH"
    
    # Database metrics
    local final_message_logs=$(psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM message_logs;" | tr -d ' ')
    local final_circuit_breaker_events=$(psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM circuit_breaker_events;" | tr -d ' ')
    local final_retry_attempts=$(psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM retry_attempts;" | tr -d ' ')
    local final_dlq_messages=$(psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM dead_letter_messages;" | tr -d ' ')
    
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
    local success_count=$(psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM message_logs WHERE processing_status = 'SUCCESS' AND created_at > NOW() - INTERVAL '10 minutes';" | tr -d ' ')
    local failed_count=$(psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM message_logs WHERE processing_status = 'FAILED' AND created_at > NOW() - INTERVAL '10 minutes';" | tr -d ' ')
    local circuit_breaker_count=$(psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM message_logs WHERE processing_status = 'CIRCUIT_BREAKER_OPEN' AND created_at > NOW() - INTERVAL '10 minutes';" | tr -d ' ')
    
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

# Main performance test
run_performance_test() {
    log_info "Starting performance test with $MESSAGE_COUNT messages..."
    echo
    
    # Record start time
    START_TIME=$(date +%s)
    
    # Send messages in batches with different types
    local success_count=400  # 40%
    local timeout_count=200  # 20%
    local db_count=150       # 15%
    local validation_count=100 # 10%
    local user_not_found_count=100 # 10%
    local overload_count=50  # 5%
    
    # Batch 1: Success messages (40%)
    log_info "Sending $success_count success messages..."
    for ((i=1; i<=success_count; i+=BATCH_SIZE)); do
        local end=$((i + BATCH_SIZE - 1))
        if [ $end -gt $success_count ]; then
            end=$success_count
        fi
        send_batch $i $end "success"
        sleep $DELAY_BETWEEN_BATCHES
    done
    
    # Batch 2: Timeout messages (20%)
    log_info "Sending $timeout_count timeout messages..."
    for ((i=1; i<=timeout_count; i+=BATCH_SIZE)); do
        local end=$((i + BATCH_SIZE - 1))
        if [ $end -gt $timeout_count ]; then
            end=$timeout_count
        fi
        send_batch $((success_count + i)) $((success_count + end)) "timeout"
        sleep $DELAY_BETWEEN_BATCHES
    done
    
    # Batch 3: Database error messages (15%)
    log_info "Sending $db_count database error messages..."
    for ((i=1; i<=db_count; i+=BATCH_SIZE)); do
        local end=$((i + BATCH_SIZE - 1))
        if [ $end -gt $db_count ]; then
            end=$db_count
        fi
        send_batch $((success_count + timeout_count + i)) $((success_count + timeout_count + end)) "database"
        sleep $DELAY_BETWEEN_BATCHES
    done
    
    # Batch 4: Validation error messages (10%)
    log_info "Sending $validation_count validation error messages..."
    for ((i=1; i<=validation_count; i+=BATCH_SIZE)); do
        local end=$((i + BATCH_SIZE - 1))
        if [ $end -gt $validation_count ]; then
            end=$validation_count
        fi
        send_batch $((success_count + timeout_count + db_count + i)) $((success_count + timeout_count + db_count + end)) "validation"
        sleep $DELAY_BETWEEN_BATCHES
    done
    
    # Batch 5: User not found messages (10%)
    log_info "Sending $user_not_found_count user not found messages..."
    for ((i=1; i<=user_not_found_count; i+=BATCH_SIZE)); do
        local end=$((i + BATCH_SIZE - 1))
        if [ $end -gt $user_not_found_count ]; then
            end=$user_not_found_count
        fi
        send_batch $((success_count + timeout_count + db_count + validation_count + i)) $((success_count + timeout_count + db_count + validation_count + end)) "user-not-found"
        sleep $DELAY_BETWEEN_BATCHES
    done
    
    # Batch 6: System overload messages (5%)
    log_info "Sending $overload_count system overload messages..."
    for ((i=1; i<=overload_count; i+=BATCH_SIZE)); do
        local end=$((i + BATCH_SIZE - 1))
        if [ $end -gt $overload_count ]; then
            end=$overload_count
        fi
        send_batch $((success_count + timeout_count + db_count + validation_count + user_not_found_count + i)) $((success_count + timeout_count + db_count + validation_count + user_not_found_count + end)) "system-overload"
        sleep $DELAY_BETWEEN_BATCHES
    done
    
    log_success "All $MESSAGE_COUNT messages sent!"
    
    # Record end time
    END_TIME=$(date +%s)
    
    log_success "Performance test completed!"
}

# Wait for processing to complete
wait_for_processing() {
    log_info "Waiting for message processing to complete..."
    
    local expected_total=$1
    local check_interval=5
    local max_wait_time=300  # 5 minutes max wait
    local waited_time=0
    
    while [ $waited_time -lt $max_wait_time ]; do
        export PATH="/opt/homebrew/opt/postgresql@15/bin:$PATH"
        
        local current_processed=$(psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM message_logs WHERE created_at > NOW() - INTERVAL '10 minutes';" | tr -d ' ')
        current_processed=$(echo $current_processed | tr -d ' ')
        
        local progress_percent=$((current_processed * 100 / expected_total))
        
        log_performance "Progress: $current_processed/$expected_total messages processed ($progress_percent%)"
        
        if [ $current_processed -ge $expected_total ]; then
            break
        fi
        
        sleep $check_interval
        waited_time=$((waited_time + check_interval))
    done
    
    if [ $waited_time -ge $max_wait_time ]; then
        log_warning "Timeout reached, but continuing with results..."
    fi
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
    wait_for_processing $MESSAGE_COUNT
    
    get_performance_metrics
    
    log_success "Performance test completed successfully!"
    echo "Test completed at $(date)"
}

# Run main function
main "$@"

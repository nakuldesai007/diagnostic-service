#!/bin/bash

# Test script for @Retryable annotation functionality
# This script demonstrates the retry behavior with different scenarios

set -e

# Configuration
BASE_URL="http://localhost:8080/api/diagnostic"
ENDPOINT_URL="https://httpstat.us/500"  # This will return 500 error to test retries
ACTIVITY_ID="RETRY-TEST-$(date +%s)"
APPLICATION_DATE=$(date +%Y-%m-%d)
ACTIVITY_TYPE="RETRY_TEST"
ACTIVITY_STATUS="PENDING"
PACKET_SIZE=5

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to make HTTP requests
make_request() {
    local method=$1
    local url=$2
    local headers=$3
    
    if [ -n "$headers" ]; then
        curl -s -X "$method" "$url" -H "$headers"
    else
        curl -s -X "$method" "$url"
    fi
}

# Function to print colored output
print_status() {
    local message=$1
    local color=$2
    echo -e "${color}${message}${NC}"
}

# Function to test retry with failing endpoint
test_retry_with_failing_endpoint() {
    print_status "Testing retry functionality with failing endpoint..." $BLUE
    print_status "Using endpoint that returns 500 error: $ENDPOINT_URL" $YELLOW
    
    local response=$(curl -s -X POST "$BASE_URL/packet-processing/start?endpointUrl=$ENDPOINT_URL&activityId=$ACTIVITY_ID&applicationDate=$APPLICATION_DATE&activityType=$ACTIVITY_TYPE&activityStatus=$ACTIVITY_STATUS&packetSize=$PACKET_SIZE" \
        -H "X-Test-Retry: true" \
        -H "X-Client-Version: 1.0")
    
    echo "Response: $response"
    echo
    
    # Extract processing ID
    PROCESSING_ID=$(echo "$response" | grep -o '"processingId":"[^"]*"' | cut -d'"' -f4)
    if [ -n "$PROCESSING_ID" ]; then
        print_status "✓ Processing started with ID: $PROCESSING_ID" $GREEN
        return 0
    else
        print_status "✗ Failed to start processing" $RED
        return 1
    fi
}

# Function to test retry with intermittent failures
test_retry_with_intermittent_failures() {
    print_status "Testing retry with intermittent failures..." $BLUE
    
    # Use an endpoint that sometimes fails
    local intermittent_endpoint="https://httpstat.us/200,500,200,500,200"
    local intermittent_activity_id="INTERMITTENT-TEST-$(date +%s)"
    
    print_status "Using intermittent endpoint: $intermittent_endpoint" $YELLOW
    
    local response=$(curl -s -X POST "$BASE_URL/packet-processing/start?endpointUrl=$intermittent_endpoint&activityId=$intermittent_activity_id&applicationDate=$APPLICATION_DATE&activityType=$ACTIVITY_TYPE&activityStatus=$ACTIVITY_STATUS&packetSize=3" \
        -H "X-Test-Intermittent: true")
    
    echo "Response: $response"
    echo
    
    # Extract processing ID
    local processing_id=$(echo "$response" | grep -o '"processingId":"[^"]*"' | cut -d'"' -f4)
    if [ -n "$processing_id" ]; then
        print_status "✓ Intermittent test started with ID: $processing_id" $GREEN
        return 0
    else
        print_status "✗ Failed to start intermittent test" $RED
        return 1
    fi
}

# Function to test retry with timeout
test_retry_with_timeout() {
    print_status "Testing retry with timeout..." $BLUE
    
    # Use an endpoint that takes time to respond
    local timeout_endpoint="https://httpstat.us/200?sleep=2000"  # 2 second delay
    local timeout_activity_id="TIMEOUT-TEST-$(date +%s)"
    
    print_status "Using timeout endpoint: $timeout_endpoint" $YELLOW
    
    local response=$(curl -s -X POST "$BASE_URL/packet-processing/start?endpointUrl=$timeout_endpoint&activityId=$timeout_activity_id&applicationDate=$APPLICATION_DATE&activityType=$ACTIVITY_TYPE&activityStatus=$ACTIVITY_STATUS&packetSize=2" \
        -H "X-Test-Timeout: true")
    
    echo "Response: $response"
    echo
    
    # Extract processing ID
    local processing_id=$(echo "$response" | grep -o '"processingId":"[^"]*"' | cut -d'"' -f4)
    if [ -n "$processing_id" ]; then
        print_status "✓ Timeout test started with ID: $processing_id" $GREEN
        return 0
    else
        print_status "✗ Failed to start timeout test" $RED
        return 1
    fi
}

# Function to test retry with successful endpoint
test_retry_with_successful_endpoint() {
    print_status "Testing retry with successful endpoint..." $BLUE
    
    # Use a reliable endpoint
    local success_endpoint="https://jsonplaceholder.typicode.com/posts"
    local success_activity_id="SUCCESS-TEST-$(date +%s)"
    
    print_status "Using successful endpoint: $success_endpoint" $YELLOW
    
    local response=$(curl -s -X POST "$BASE_URL/packet-processing/start?endpointUrl=$success_endpoint&activityId=$success_activity_id&applicationDate=$APPLICATION_DATE&activityType=$ACTIVITY_TYPE&activityStatus=$ACTIVITY_STATUS&packetSize=5" \
        -H "X-Test-Success: true")
    
    echo "Response: $response"
    echo
    
    # Extract processing ID
    local processing_id=$(echo "$response" | grep -o '"processingId":"[^"]*"' | cut -d'"' -f4)
    if [ -n "$processing_id" ]; then
        print_status "✓ Success test started with ID: $processing_id" $GREEN
        return 0
    else
        print_status "✗ Failed to start success test" $RED
        return 1
    fi
}

# Function to check processing status
check_processing_status() {
    local activity_id=$1
    print_status "Checking processing status for activity: $activity_id" $BLUE
    
    local response=$(make_request "GET" "$BASE_URL/packet-processing/activity/$activity_id/date/$APPLICATION_DATE/status")
    echo "Response: $response"
    echo
    
    # Extract status from response
    local status=$(echo "$response" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
    local processed=$(echo "$response" | grep -o '"processedRecords":[0-9]*' | cut -d':' -f2)
    local failed=$(echo "$response" | grep -o '"failedRecords":[0-9]*' | cut -d':' -f2)
    
    print_status "Status: $status" $YELLOW
    print_status "Processed Records: $processed" $YELLOW
    print_status "Failed Records: $failed" $YELLOW
    echo
}

# Function to show retry configuration
show_retry_config() {
    print_status "Retry Configuration:" $BLUE
    echo
    print_status "Standard Retry:" $YELLOW
    echo "  Max Attempts: 3"
    echo "  Initial Delay: 1000ms"
    echo "  Multiplier: 2.0"
    echo "  Max Delay: 10000ms"
    echo
    print_status "Packet Processing Retry:" $YELLOW
    echo "  Max Attempts: 5"
    echo "  Initial Delay: 500ms"
    echo "  Multiplier: 1.5"
    echo "  Max Delay: 5000ms"
    echo
    print_status "Retryable Exceptions:" $YELLOW
    echo "  - HttpServerErrorException (5xx errors)"
    echo "  - ResourceAccessException (connection issues)"
    echo "  - General Exception"
    echo
    print_status "Non-Retryable Exceptions:" $YELLOW
    echo "  - HttpClientErrorException (4xx errors)"
    echo
}

# Main test flow
main() {
    print_status "Starting @Retryable annotation test..." $BLUE
    echo "Activity ID: $ACTIVITY_ID"
    echo "Application Date: $APPLICATION_DATE"
    echo "Packet Size: $PACKET_SIZE"
    echo
    
    # Show retry configuration
    show_retry_config
    
    echo "=========================================="
    echo
    
    # Test 1: Retry with failing endpoint
    print_status "Test 1: Retry with consistently failing endpoint" $BLUE
    if test_retry_with_failing_endpoint; then
        print_status "Waiting 10 seconds to observe retry behavior..." $YELLOW
        sleep 10
        check_processing_status "$ACTIVITY_ID"
    fi
    
    echo "=========================================="
    echo
    
    # Test 2: Retry with intermittent failures
    print_status "Test 2: Retry with intermittent failures" $BLUE
    test_retry_with_intermittent_failures
    
    echo "=========================================="
    echo
    
    # Test 3: Retry with timeout
    print_status "Test 3: Retry with timeout scenarios" $BLUE
    test_retry_with_timeout
    
    echo "=========================================="
    echo
    
    # Test 4: Retry with successful endpoint
    print_status "Test 4: Retry with successful endpoint" $BLUE
    test_retry_with_successful_endpoint
    
    echo "=========================================="
    echo
    
    print_status "Retry functionality test completed!" $GREEN
    print_status "Check the application logs to see retry attempts and backoff behavior." $BLUE
    echo
    print_status "Key observations:" $YELLOW
    echo "  - Failed requests will be retried with exponential backoff"
    echo "  - 4xx errors (client errors) are not retried"
    echo "  - 5xx errors (server errors) are retried"
    echo "  - Connection timeouts are retried"
    echo "  - Packet processing uses more aggressive retry settings"
    echo
}

# Run the test
main "$@"

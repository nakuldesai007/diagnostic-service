#!/bin/bash

# Test script for packet processing functionality
# This script demonstrates how to use the packet processing endpoints

BASE_URL="http://localhost:8080/api/diagnostic"
ENDPOINT_URL="https://jsonplaceholder.typicode.com/posts"
ACTIVITY_ID="ACT-001"
APPLICATION_DATE="2024-01-15"
ACTIVITY_TYPE="DATA_PROCESSING"
ACTIVITY_STATUS="PENDING"

echo "=== Packet Processing Test Script ==="
echo "Base URL: $BASE_URL"
echo "Test Endpoint: $ENDPOINT_URL"
echo

# Function to make HTTP requests
make_request() {
    local method=$1
    local url=$2
    local data=$3
    
    if [ -n "$data" ]; then
        curl -s -X $method "$url" \
            -H "Content-Type: application/json" \
            -d "$data"
    else
        curl -s -X $method "$url" \
            -H "Content-Type: application/json"
    fi
}

# Function to check if service is running
check_service() {
    echo "Checking if service is running..."
    response=$(make_request "GET" "$BASE_URL/health")
    if echo "$response" | grep -q "UP"; then
        echo "✓ Service is running"
        return 0
    else
        echo "✗ Service is not running or not responding"
        return 1
    fi
}

# Function to start packet processing
start_packet_processing() {
    echo "Starting packet processing..."
    echo "Endpoint: $ENDPOINT_URL"
    echo "Activity ID: $ACTIVITY_ID"
    echo "Application Date: $APPLICATION_DATE"
    echo "Activity Type: $ACTIVITY_TYPE"
    echo "Activity Status: $ACTIVITY_STATUS"
    echo "Packet Size: 10"
    echo
    
    response=$(make_request "POST" "$BASE_URL/packet-processing/start?endpointUrl=$ENDPOINT_URL&activityId=$ACTIVITY_ID&applicationDate=$APPLICATION_DATE&activityType=$ACTIVITY_TYPE&activityStatus=$ACTIVITY_STATUS&packetSize=10")
    echo "Response: $response"
    echo
    
    # Extract processing ID from response
    PROCESSING_ID=$(echo "$response" | grep -o '"processingId":"[^"]*"' | cut -d'"' -f4)
    if [ -n "$PROCESSING_ID" ]; then
        echo "✓ Packet processing started with processing ID: $PROCESSING_ID"
        return 0
    else
        echo "✗ Failed to start packet processing"
        return 1
    fi
}

# Function to check processing status by activity
check_processing_status() {
    echo "Checking processing status for activity: $ACTIVITY_ID on date: $APPLICATION_DATE"
    
    response=$(make_request "GET" "$BASE_URL/packet-processing/activity/$ACTIVITY_ID/date/$APPLICATION_DATE/status")
    echo "Response: $response"
    echo
    
    # Extract status from response
    STATUS=$(echo "$response" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
    PROCESSED=$(echo "$response" | grep -o '"processedRecords":[0-9]*' | cut -d':' -f2)
    FAILED=$(echo "$response" | grep -o '"failedRecords":[0-9]*' | cut -d':' -f2)
    
    echo "Status: $STATUS"
    echo "Processed Records: $PROCESSED"
    echo "Failed Records: $FAILED"
    echo
}


# Function to pause processing by activity
pause_processing() {
    echo "Pausing processing for activity: $ACTIVITY_ID on date: $APPLICATION_DATE"
    
    response=$(make_request "POST" "$BASE_URL/packet-processing/activity/$ACTIVITY_ID/date/$APPLICATION_DATE/pause")
    echo "Response: $response"
    echo
}

# Function to resume processing by activity
resume_processing() {
    echo "Resuming processing for activity: $ACTIVITY_ID on date: $APPLICATION_DATE"
    
    response=$(make_request "POST" "$BASE_URL/packet-processing/activity/$ACTIVITY_ID/date/$APPLICATION_DATE/resume")
    echo "Response: $response"
    echo
}

# Function to retry failed records by activity
retry_failed_records() {
    echo "Retrying failed records for activity: $ACTIVITY_ID on date: $APPLICATION_DATE"
    
    response=$(make_request "POST" "$BASE_URL/packet-processing/activity/$ACTIVITY_ID/date/$APPLICATION_DATE/retry")
    echo "Response: $response"
    echo
}

# Function to get all active sessions
get_active_sessions() {
    echo "Getting all active sessions..."
    
    response=$(make_request "GET" "$BASE_URL/packet-processing/sessions")
    echo "Response: $response"
    echo
}

# Function to cancel processing by activity
cancel_processing() {
    echo "Cancelling processing for activity: $ACTIVITY_ID on date: $APPLICATION_DATE"
    
    response=$(make_request "POST" "$BASE_URL/packet-processing/activity/$ACTIVITY_ID/date/$APPLICATION_DATE/cancel")
    echo "Response: $response"
    echo
}

# Main test flow
main() {
    echo "Starting packet processing test..."
    echo
    
    # Check if service is running
    if ! check_service; then
        echo "Please start the diagnostic service first"
        exit 1
    fi
    
    echo "=========================================="
    echo
    
    # Start packet processing
    if start_packet_processing; then
        echo "=========================================="
        echo
        
        # Wait a bit for processing to start
        echo "Waiting 3 seconds for processing to start..."
        sleep 3
        
        # Check initial status
        check_processing_status
        
        echo "=========================================="
        echo
        
        # Wait for some processing
        echo "Waiting 5 seconds for some processing to complete..."
        sleep 5
        
        # Check status again
        check_processing_status
        
        echo "=========================================="
        echo
        
        # Pause the processing
        pause_processing
        
        # Check status after pause
        check_processing_status
        
        echo "=========================================="
        echo
        
        # Wait a bit
        echo "Waiting 2 seconds..."
        sleep 2
        
        # Resume the processing
        resume_processing
        
        # Check status after resume
        check_processing_status
        
        echo "=========================================="
        echo
        
        # Wait for more processing
        echo "Waiting 5 seconds for more processing..."
        sleep 5
        
        # Check final status
        check_processing_status
        
        echo "=========================================="
        echo
        
        # Retry any failed records
        retry_failed_records
        
        echo "=========================================="
        echo
        
        # Get all active sessions
        get_active_sessions
        
        echo "=========================================="
        echo
        
        # Wait for completion
        echo "Waiting 10 seconds for processing to complete..."
        sleep 10
        
        # Final status check
        check_processing_status
        
        echo "=========================================="
        echo
        echo "Test completed!"
        echo "Processing ID: $PROCESSING_ID"
        echo "Activity ID: $ACTIVITY_ID"
        echo "Application Date: $APPLICATION_DATE"
        echo "You can continue monitoring the processing status using:"
        echo "curl -X GET \"$BASE_URL/packet-processing/activity/$ACTIVITY_ID/date/$APPLICATION_DATE/status\""
        
    else
        echo "Failed to start packet processing"
        exit 1
    fi
}

# Run the main function
main "$@"

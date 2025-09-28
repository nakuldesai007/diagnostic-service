#!/bin/bash

# Test script for header-based metadata functionality
# This script demonstrates how to use HTTP headers for packet tracking

set -e

# Configuration
BASE_URL="http://localhost:8080/api/diagnostic"
ENDPOINT_URL="https://jsonplaceholder.typicode.com/posts"
ACTIVITY_ID="HEADER-TEST-$(date +%s)"
APPLICATION_DATE=$(date +%Y-%m-%d)
ACTIVITY_TYPE="HEADER_METADATA_TEST"
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

# Function to test header metadata endpoint
test_header_metadata() {
    print_status "Testing header metadata endpoint..." $BLUE
    
    local headers="X-Custom-Header: custom-value"
    headers="$headers"$'\n'"X-Client-Id: my-client-123"
    headers="$headers"$'\n'"X-Request-Source: test-script"
    headers="$headers"$'\n'"X-User-Id: test-user"
    headers="$headers"$'\n'"X-Correlation-Id: test-$(date +%s)"
    
    local url="$BASE_URL/packet-processing/test-headers?endpointUrl=$ENDPOINT_URL&activityId=$ACTIVITY_ID&applicationDate=$APPLICATION_DATE&activityType=$ACTIVITY_TYPE&activityStatus=$ACTIVITY_STATUS&packetSize=$PACKET_SIZE"
    
    print_status "Request URL: $url" $YELLOW
    print_status "Headers:" $YELLOW
    echo "$headers" | sed 's/^/  /'
    echo
    
    local response=$(curl -s -X POST "$url" \
        -H "X-Custom-Header: custom-value" \
        -H "X-Client-Id: my-client-123" \
        -H "X-Request-Source: test-script" \
        -H "X-User-Id: test-user" \
        -H "X-Correlation-Id: test-$(date +%s)")
    
    echo "Response: $response"
    echo
    
    # Extract processing ID
    PROCESSING_ID=$(echo "$response" | grep -o '"processingId":"[^"]*"' | cut -d'"' -f4)
    if [ -n "$PROCESSING_ID" ]; then
        print_status "✓ Header metadata test started with processing ID: $PROCESSING_ID" $GREEN
        return 0
    else
        print_status "✗ Failed to start header metadata test" $RED
        return 1
    fi
}

# Function to check processing status
check_processing_status() {
    print_status "Checking processing status for activity: $ACTIVITY_ID on date: $APPLICATION_DATE" $BLUE
    
    local response=$(make_request "GET" "$BASE_URL/packet-processing/activity/$ACTIVITY_ID/date/$APPLICATION_DATE/status")
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

# Function to demonstrate custom headers in regular processing
test_custom_headers() {
    print_status "Testing custom headers in regular processing..." $BLUE
    
    local custom_activity_id="CUSTOM-HEADERS-$(date +%s)"
    
    local response=$(curl -s -X POST "$BASE_URL/packet-processing/start?endpointUrl=$ENDPOINT_URL&activityId=$custom_activity_id&applicationDate=$APPLICATION_DATE&activityType=DATA_PROCESSING&activityStatus=PENDING&packetSize=3" \
        -H "X-Client-Version: 2.0" \
        -H "X-Request-Source: web-ui" \
        -H "X-User-Id: user123" \
        -H "X-Correlation-Id: req-$(date +%s)" \
        -H "X-Custom-Metadata: {\"priority\":\"high\",\"region\":\"us-east\"}")
    
    echo "Response: $response"
    echo
    
    # Extract processing ID
    local processing_id=$(echo "$response" | grep -o '"processingId":"[^"]*"' | cut -d'"' -f4)
    if [ -n "$processing_id" ]; then
        print_status "✓ Custom headers processing started with ID: $processing_id" $GREEN
        return 0
    else
        print_status "✗ Failed to start custom headers processing" $RED
        return 1
    fi
}

# Function to show header examples
show_header_examples() {
    print_status "Header-based Metadata Examples:" $BLUE
    echo
    print_status "Request Headers (sent to external API):" $YELLOW
    echo "  X-Packet-Offset: 0"
    echo "  X-Packet-Limit: 10"
    echo "  X-Packet-Request-Time: 1704067200000"
    echo "  X-Packet-Request-Id: req_1704067200000_123"
    echo "  X-Packet-Client: diagnostic-service"
    echo "  X-Packet-Version: 1.0"
    echo
    print_status "Response Headers (expected from external API):" $YELLOW
    echo "  X-Total-Records: 1000"
    echo "  X-Has-More-Records: true"
    echo "  X-Next-Offset: 10"
    echo "  X-Current-Offset: 0"
    echo "  X-Packet-Size: 10"
    echo "  X-Server-Processing-Time: 150"
    echo "  X-Server-Timestamp: 2024-01-01T00:00:00Z"
    echo
}

# Main test flow
main() {
    print_status "Starting header metadata test..." $BLUE
    echo "Activity ID: $ACTIVITY_ID"
    echo "Application Date: $APPLICATION_DATE"
    echo "Endpoint URL: $ENDPOINT_URL"
    echo "Packet Size: $PACKET_SIZE"
    echo
    
    # Show header examples
    show_header_examples
    
    echo "=========================================="
    echo
    
    # Test header metadata endpoint
    if test_header_metadata; then
        echo "=========================================="
        echo
        
        # Wait for processing to start
        print_status "Waiting 3 seconds for processing to start..." $YELLOW
        sleep 3
        
        # Check initial status
        check_processing_status
        
        echo "=========================================="
        echo
        
        # Wait for some processing
        print_status "Waiting 5 seconds for some processing to complete..." $YELLOW
        sleep 5
        
        # Check status again
        check_processing_status
        
        echo "=========================================="
        echo
        
        # Test custom headers
        test_custom_headers
        
        echo "=========================================="
        echo
        
        # Wait for completion
        print_status "Waiting 10 seconds for processing to complete..." $YELLOW
        sleep 10
        
        # Final status check
        check_processing_status
        
        echo "=========================================="
        echo
        print_status "Header metadata test completed!" $GREEN
        print_status "Processing ID: $PROCESSING_ID" $GREEN
        print_status "Activity ID: $ACTIVITY_ID" $GREEN
        print_status "Application Date: $APPLICATION_DATE" $GREEN
        echo
        print_status "You can continue monitoring the processing status using:" $BLUE
        echo "curl -X GET \"$BASE_URL/packet-processing/activity/$ACTIVITY_ID/date/$APPLICATION_DATE/status\""
        
    else
        print_status "Failed to start header metadata test" $RED
        exit 1
    fi
}

# Run the test
main "$@"

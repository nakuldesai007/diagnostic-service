#!/bin/bash

# Database Performance Monitor Script
# This script monitors database performance and shows real-time statistics

set -e

echo "📊 Database Performance Monitor"
echo "=============================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

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

log_metric() {
    echo -e "${PURPLE}[METRIC]${NC} $1"
}

# Configuration
INTERVAL=${1:-5}  # Default 5 seconds
DURATION=${2:-60} # Default 60 seconds

# Check if PostgreSQL is running
check_postgres() {
    if docker ps | grep -q postgres; then
        if docker exec postgres psql -U diagnostic_user -d diagnostic_service -c "SELECT 1;" > /dev/null 2>&1; then
            return 0
        else
            log_error "PostgreSQL container is running but not accessible"
            return 1
        fi
    else
        log_error "PostgreSQL container not found. Make sure to run ./setup-test-environment.sh first"
        return 1
    fi
}

# Get database statistics
get_db_stats() {
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    
    # Get table counts
    local message_logs=$(docker exec postgres psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM message_logs;" 2>/dev/null | tr -d ' \n')
    local circuit_breaker_events=$(docker exec postgres psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM circuit_breaker_events;" 2>/dev/null | tr -d ' \n')
    local retry_attempts=$(docker exec postgres psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM retry_attempts;" 2>/dev/null | tr -d ' \n')
    local dlq_messages=$(docker exec postgres psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM dead_letter_messages;" 2>/dev/null | tr -d ' \n')
    
    # Get recent activity (last 5 minutes)
    local recent_logs=$(docker exec postgres psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM message_logs WHERE created_at > NOW() - INTERVAL '5 minutes';" 2>/dev/null | tr -d ' \n')
    local recent_events=$(docker exec postgres psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM circuit_breaker_events WHERE created_at > NOW() - INTERVAL '5 minutes';" 2>/dev/null | tr -d ' \n')
    local recent_retries=$(docker exec postgres psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM retry_attempts WHERE created_at > NOW() - INTERVAL '5 minutes';" 2>/dev/null | tr -d ' \n')
    local recent_dlq=$(docker exec postgres psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM dead_letter_messages WHERE created_at > NOW() - INTERVAL '5 minutes';" 2>/dev/null | tr -d ' \n')
    
    # Get processing status breakdown
    local success_count=$(docker exec postgres psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM message_logs WHERE processing_status = 'SUCCESS' AND created_at > NOW() - INTERVAL '5 minutes';" 2>/dev/null | tr -d ' \n')
    local failed_count=$(docker exec postgres psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM message_logs WHERE processing_status = 'FAILED' AND created_at > NOW() - INTERVAL '5 minutes';" 2>/dev/null | tr -d ' \n')
    local circuit_breaker_count=$(docker exec postgres psql -U diagnostic_user -d diagnostic_service -t -c "SELECT COUNT(*) FROM message_logs WHERE processing_status = 'CIRCUIT_BREAKER_OPEN' AND created_at > NOW() - INTERVAL '5 minutes';" 2>/dev/null | tr -d ' \n')
    
    # Get average processing time
    local avg_processing_time=$(docker exec postgres psql -U diagnostic_user -d diagnostic_service -t -c "SELECT ROUND(AVG(processing_time_ms), 2) FROM message_logs WHERE processing_time_ms IS NOT NULL AND created_at > NOW() - INTERVAL '5 minutes';" 2>/dev/null | tr -d ' \n')
    
    # Display statistics
    echo "┌─────────────────────────────────────────────────────────────────┐"
    echo "│ Database Performance Monitor - $timestamp"
    echo "├─────────────────────────────────────────────────────────────────┤"
    echo "│ Total Records:"
    echo "│   Message Logs: $message_logs"
    echo "│   Circuit Breaker Events: $circuit_breaker_events"
    echo "│   Retry Attempts: $retry_attempts"
    echo "│   Dead Letter Messages: $dlq_messages"
    echo "├─────────────────────────────────────────────────────────────────┤"
    echo "│ Recent Activity (Last 5 minutes):"
    echo "│   Message Logs: $recent_logs"
    echo "│   Circuit Breaker Events: $recent_events"
    echo "│   Retry Attempts: $recent_retries"
    echo "│   Dead Letter Messages: $recent_dlq"
    echo "├─────────────────────────────────────────────────────────────────┤"
    echo "│ Processing Status (Last 5 minutes):"
    echo "│   Successful: $success_count"
    echo "│   Failed: $failed_count"
    echo "│   Circuit Breaker Open: $circuit_breaker_count"
    echo "│   Avg Processing Time: ${avg_processing_time}ms"
    echo "└─────────────────────────────────────────────────────────────────┘"
    echo
}

# Get detailed performance metrics
get_detailed_metrics() {
    log_metric "Detailed Performance Metrics:"
    echo "----------------------------------------"
    
    # Top error categories
    echo "Top Error Categories (Last 5 minutes):"
    docker exec postgres psql -U diagnostic_user -d diagnostic_service -c "SELECT error_category, COUNT(*) as count FROM message_logs WHERE created_at > NOW() - INTERVAL '5 minutes' AND error_category IS NOT NULL GROUP BY error_category ORDER BY count DESC LIMIT 5;"
    echo
    
    # Processing time statistics
    echo "Processing Time Statistics (Last 5 minutes):"
    docker exec postgres psql -U diagnostic_user -d diagnostic_service -c "SELECT processing_status, COUNT(*) as count, ROUND(AVG(processing_time_ms), 2) as avg_time_ms, MIN(processing_time_ms) as min_time_ms, MAX(processing_time_ms) as max_time_ms FROM message_logs WHERE created_at > NOW() - INTERVAL '5 minutes' AND processing_time_ms IS NOT NULL GROUP BY processing_status ORDER BY count DESC;"
    echo
    
    # Recent circuit breaker events
    echo "Recent Circuit Breaker Events (Last 10):"
    docker exec postgres psql -U diagnostic_user -d diagnostic_service -c "SELECT circuit_breaker_name, event_type, from_state, to_state, created_at FROM circuit_breaker_events ORDER BY created_at DESC LIMIT 10;"
    echo
}

# Monitor loop
monitor_loop() {
    local start_time=$(date +%s)
    local end_time=$((start_time + DURATION))
    
    log_info "Starting database performance monitoring..."
    log_info "Interval: ${INTERVAL}s, Duration: ${DURATION}s"
    echo
    
    while [ $(date +%s) -lt $end_time ]; do
        get_db_stats
        sleep $INTERVAL
    done
    
    log_success "Monitoring completed"
    echo
    get_detailed_metrics
}

# Show help
show_help() {
    echo "Usage: $0 [INTERVAL] [DURATION]"
    echo
    echo "Arguments:"
    echo "  INTERVAL    Update interval in seconds (default: 5)"
    echo "  DURATION    Total monitoring duration in seconds (default: 60)"
    echo
    echo "Examples:"
    echo "  $0                    # Monitor for 60 seconds with 5-second intervals"
    echo "  $0 2 30              # Monitor for 30 seconds with 2-second intervals"
    echo "  $0 10 120            # Monitor for 2 minutes with 10-second intervals"
    echo
    echo "Press Ctrl+C to stop monitoring early"
}

# Main execution
main() {
    # Parse arguments
    if [ "$1" = "-h" ] || [ "$1" = "--help" ]; then
        show_help
        exit 0
    fi
    
    if [ $# -gt 0 ]; then
        INTERVAL=$1
    fi
    
    if [ $# -gt 1 ]; then
        DURATION=$2
    fi
    
    # Check if PostgreSQL is running
    if ! check_postgres; then
        exit 1
    fi
    
    # Start monitoring
    monitor_loop
}

# Handle Ctrl+C
trap 'echo; log_info "Monitoring stopped by user"; exit 0' INT

# Run main function
main "$@"
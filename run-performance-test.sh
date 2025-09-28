#!/bin/bash

# Comprehensive Performance Test Runner
# Orchestrates the 1000 message test with database monitoring

set -e

echo "🚀 Starting Comprehensive Performance Test"
echo "========================================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PERFORMANCE_TEST_SCRIPT="$SCRIPT_DIR/test-performance-1000.sh"
MONITOR_SCRIPT="$SCRIPT_DIR/monitor-db-performance.sh"
API_BASE="http://localhost:8080"

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

# Check prerequisites
check_prerequisites() {
    log_info "Checking prerequisites..."
    
    # Check if scripts exist
    if [ ! -f "$PERFORMANCE_TEST_SCRIPT" ]; then
        log_error "Performance test script not found: $PERFORMANCE_TEST_SCRIPT"
        exit 1
    fi
    
    if [ ! -f "$MONITOR_SCRIPT" ]; then
        log_error "Monitor script not found: $MONITOR_SCRIPT"
        exit 1
    fi
    
    # Check if application is running
    if ! curl -s "$API_BASE/actuator/health" > /dev/null; then
        log_error "Application is not running on $API_BASE"
        exit 1
    fi
    
    # Check if Kafka is running
    if ! /opt/homebrew/opt/kafka/bin/kafka-topics --bootstrap-server localhost:9092 --list > /dev/null 2>&1; then
        log_error "Kafka is not running on localhost:9092"
        exit 1
    fi
    
    # Check if PostgreSQL is accessible
    export PATH="/opt/homebrew/opt/postgresql@15/bin:$PATH"
    if ! psql -U diagnostic_user -d diagnostic_service -c "SELECT 1;" > /dev/null 2>&1; then
        log_error "PostgreSQL is not accessible"
        exit 1
    fi
    
    log_success "All prerequisites met"
}

# Get initial system state
get_initial_state() {
    log_info "Capturing initial system state..."
    
    # Application health
    log_info "Application Health:"
    curl -s "$API_BASE/actuator/health" | jq . || echo "Health check failed"
    echo
    
    # Application metrics
    log_info "Application Metrics:"
    curl -s "$API_BASE/actuator/metrics" | jq '.names[]' | head -10 || echo "Metrics check failed"
    echo
    
    # Database initial state
    log_info "Database Initial State:"
    export PATH="/opt/homebrew/opt/postgresql@15/bin:$PATH"
    psql -U diagnostic_user -d diagnostic_service -c "SELECT 'message_logs' as table_name, COUNT(*) as count FROM message_logs UNION ALL SELECT 'circuit_breaker_events', COUNT(*) FROM circuit_breaker_events UNION ALL SELECT 'retry_attempts', COUNT(*) FROM retry_attempts UNION ALL SELECT 'dead_letter_messages', COUNT(*) FROM dead_letter_messages ORDER BY table_name;"
    echo
}

# Start database monitoring
start_monitoring() {
    log_info "Starting database performance monitoring..."
    
    # Start monitoring in background
    "$MONITOR_SCRIPT" --background &
    MONITOR_PID=$!
    
    log_success "Database monitoring started (PID: $MONITOR_PID)"
    sleep 2  # Give monitoring a moment to start
}

# Run performance test
run_performance_test() {
    log_info "Starting 1000 message performance test..."
    echo
    
    # Run the performance test
    "$PERFORMANCE_TEST_SCRIPT"
    
    log_success "Performance test completed"
}

# Stop monitoring
stop_monitoring() {
    log_info "Stopping database monitoring..."
    
    if [ -n "$MONITOR_PID" ]; then
        kill $MONITOR_PID 2>/dev/null || true
        log_success "Database monitoring stopped"
    fi
}

# Generate comprehensive report
generate_comprehensive_report() {
    log_info "Generating comprehensive performance report..."
    
    echo
    echo -e "${CYAN}=== COMPREHENSIVE PERFORMANCE REPORT ===${NC}"
    echo
    
    # Final application state
    log_performance "Final Application State:"
    curl -s "$API_BASE/api/diagnostic/stats" | jq . || echo "Stats unavailable"
    echo
    
    # Circuit breaker state
    log_performance "Circuit Breaker State:"
    curl -s "$API_BASE/api/diagnostic/circuit-breaker/state" | jq . || echo "Circuit breaker state unavailable"
    echo
    
    # Database final state
    log_performance "Database Final State:"
    export PATH="/opt/homebrew/opt/postgresql@15/bin:$PATH"
    psql -U diagnostic_user -d diagnostic_service -c "SELECT 'message_logs' as table_name, COUNT(*) as count FROM message_logs UNION ALL SELECT 'circuit_breaker_events', COUNT(*) FROM circuit_breaker_events UNION ALL SELECT 'retry_attempts', COUNT(*) FROM retry_attempts UNION ALL SELECT 'dead_letter_messages', COUNT(*) FROM dead_letter_messages ORDER BY table_name;"
    echo
    
    # Performance summary
    log_performance "Performance Summary:"
    psql -U diagnostic_user -d diagnostic_service -c "SELECT processing_status, COUNT(*) as count, AVG(processing_time_ms) as avg_time_ms, MIN(processing_time_ms) as min_time_ms, MAX(processing_time_ms) as max_time_ms FROM message_logs WHERE created_at > NOW() - INTERVAL '1 hour' AND processing_time_ms IS NOT NULL GROUP BY processing_status ORDER BY count DESC;"
    echo
    
    # Error analysis
    log_performance "Error Analysis:"
    psql -U diagnostic_user -d diagnostic_service -c "SELECT error_category, COUNT(*) as count FROM message_logs WHERE created_at > NOW() - INTERVAL '1 hour' AND error_category IS NOT NULL GROUP BY error_category ORDER BY count DESC;"
    echo
    
    # Timing analysis
    log_performance "Timing Analysis:"
    psql -U diagnostic_user -d diagnostic_service -c "SELECT DATE_TRUNC('minute', created_at) as minute, COUNT(*) as messages_per_minute FROM message_logs WHERE created_at > NOW() - INTERVAL '1 hour' GROUP BY DATE_TRUNC('minute', created_at) ORDER BY minute;"
    echo
}

# Cleanup function
cleanup() {
    echo
    log_info "Performing cleanup..."
    
    stop_monitoring
    
    # Wait a moment for monitoring to fully stop
    sleep 2
    
    log_info "Cleanup completed"
}

# Set up signal handlers
trap cleanup EXIT INT TERM

# Main execution
main() {
    echo "Starting comprehensive performance test at $(date)"
    echo "This test will:"
    echo "  1. Send 1000 messages with various error types"
    echo "  2. Monitor PostgreSQL performance in real-time"
    echo "  3. Track message processing performance"
    echo "  4. Generate detailed reports"
    echo
    
    check_prerequisites
    get_initial_state
    
    start_monitoring
    run_performance_test
    stop_monitoring
    
    generate_comprehensive_report
    
    echo
    log_success "Comprehensive performance test completed successfully!"
    echo "Test completed at $(date)"
    echo
    log_info "Check the following files for detailed results:"
    log_info "  - Performance test output (above)"
    log_info "  - Database monitoring log: db-performance-*.log"
    log_info "  - Monitor output: monitor-*.out"
}

# Run main function
main "$@"

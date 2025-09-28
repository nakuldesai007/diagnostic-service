#!/bin/bash

# PostgreSQL Performance Monitoring Script
# Monitors database performance during the 1000 message test

set -e

echo "📊 Starting PostgreSQL Performance Monitoring"
echo "============================================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
DB_USER="diagnostic_user"
DB_NAME="diagnostic_service"
MONITOR_INTERVAL=5
LOG_FILE="db-performance-$(date +%Y%m%d-%H%M%S).log"

# Set PostgreSQL path
export PATH="/opt/homebrew/opt/postgresql@15/bin:$PATH"

# Helper functions
log_info() {
    local message="[$(date '+%Y-%m-%d %H:%M:%S')] $1"
    echo -e "${BLUE}[INFO]${NC} $1"
    echo "$message" >> "$LOG_FILE"
}

log_performance() {
    local message="[$(date '+%Y-%m-%d %H:%M:%S')] PERF: $1"
    echo -e "${PURPLE}[PERF]${NC} $1"
    echo "$message" >> "$LOG_FILE"
}

log_error() {
    local message="[$(date '+%Y-%m-%d %H:%M:%S')] ERROR: $1"
    echo -e "${RED}[ERROR]${NC} $1"
    echo "$message" >> "$LOG_FILE"
}

# Get initial database state
get_initial_state() {
    log_info "Getting initial database state..."
    
    INITIAL_MESSAGE_LOGS=$(psql -U $DB_USER -d $DB_NAME -t -c "SELECT COUNT(*) FROM message_logs;" | tr -d ' ')
    INITIAL_CIRCUIT_BREAKER_EVENTS=$(psql -U $DB_USER -d $DB_NAME -t -c "SELECT COUNT(*) FROM circuit_breaker_events;" | tr -d ' ')
    INITIAL_RETRY_ATTEMPTS=$(psql -U $DB_USER -d $DB_NAME -t -c "SELECT COUNT(*) FROM retry_attempts;" | tr -d ' ')
    INITIAL_DLQ_MESSAGES=$(psql -U $DB_USER -d $DB_NAME -t -c "SELECT COUNT(*) FROM dead_letter_messages;" | tr -d ' ')
    
    log_performance "Initial counts:"
    log_performance "  Message Logs: $INITIAL_MESSAGE_LOGS"
    log_performance "  Circuit Breaker Events: $INITIAL_CIRCUIT_BREAKER_EVENTS"
    log_performance "  Retry Attempts: $INITIAL_RETRY_ATTEMPTS"
    log_performance "  DLQ Messages: $INITIAL_DLQ_MESSAGES"
    echo
}

# Monitor database connections
monitor_connections() {
    local connections=$(psql -U $DB_USER -d $DB_NAME -t -c "SELECT COUNT(*) FROM pg_stat_activity WHERE datname = '$DB_NAME';" | tr -d ' ')
    local active_connections=$(psql -U $DB_USER -d $DB_NAME -t -c "SELECT COUNT(*) FROM pg_stat_activity WHERE datname = '$DB_NAME' AND state = 'active';" | tr -d ' ')
    
    log_performance "Connections: Total=$connections, Active=$active_connections"
}

# Monitor table sizes
monitor_table_sizes() {
    local message_logs_size=$(psql -U $DB_USER -d $DB_NAME -t -c "SELECT pg_size_pretty(pg_total_relation_size('message_logs'));" | tr -d ' ')
    local cb_events_size=$(psql -U $DB_USER -d $DB_NAME -t -c "SELECT pg_size_pretty(pg_total_relation_size('circuit_breaker_events'));" | tr -d ' ')
    local retry_attempts_size=$(psql -U $DB_USER -d $DB_NAME -t -c "SELECT pg_size_pretty(pg_total_relation_size('retry_attempts'));" | tr -d ' ')
    local dlq_size=$(psql -U $DB_USER -d $DB_NAME -t -c "SELECT pg_size_pretty(pg_total_relation_size('dead_letter_messages'));" | tr -d ' ')
    
    log_performance "Table Sizes: message_logs=$message_logs_size, cb_events=$cb_events_size, retry_attempts=$retry_attempts_size, dlq=$dlq_size"
}

# Monitor insert rates
monitor_insert_rates() {
    local current_message_logs=$(psql -U $DB_USER -d $DB_NAME -t -c "SELECT COUNT(*) FROM message_logs;" | tr -d ' ')
    local current_cb_events=$(psql -U $DB_USER -d $DB_NAME -t -c "SELECT COUNT(*) FROM circuit_breaker_events;" | tr -d ' ')
    local current_retry_attempts=$(psql -U $DB_USER -d $DB_NAME -t -c "SELECT COUNT(*) FROM retry_attempts;" | tr -d ' ')
    local current_dlq=$(psql -U $DB_USER -d $DB_NAME -t -c "SELECT COUNT(*) FROM dead_letter_messages;" | tr -d ' ')
    
    local new_message_logs=$((current_message_logs - INITIAL_MESSAGE_LOGS))
    local new_cb_events=$((current_cb_events - INITIAL_CIRCUIT_BREAKER_EVENTS))
    local new_retry_attempts=$((current_retry_attempts - INITIAL_RETRY_ATTEMPTS))
    local new_dlq=$((current_dlq - INITIAL_DLQ_MESSAGES))
    
    log_performance "New Records: message_logs=+$new_message_logs, cb_events=+$new_cb_events, retry_attempts=+$new_retry_attempts, dlq=+$new_dlq"
}

# Monitor recent activity
monitor_recent_activity() {
    local recent_messages=$(psql -U $DB_USER -d $DB_NAME -t -c "SELECT COUNT(*) FROM message_logs WHERE created_at > NOW() - INTERVAL '1 minute';" | tr -d ' ')
    local recent_processing=$(psql -U $DB_USER -d $DB_NAME -t -c "SELECT COUNT(*) FROM message_logs WHERE processed_at > NOW() - INTERVAL '1 minute';" | tr -d ' ')
    
    log_performance "Recent Activity (last 1 min): Messages=$recent_messages, Processed=$recent_processing"
}

# Monitor database performance metrics
monitor_db_performance() {
    # Get database statistics
    local db_size=$(psql -U $DB_USER -d $DB_NAME -t -c "SELECT pg_size_pretty(pg_database_size('$DB_NAME'));" | tr -d ' ')
    
    # Get cache hit ratio
    local cache_hit_ratio=$(psql -U $DB_USER -d $DB_NAME -t -c "SELECT round(100.0 * sum(blks_hit) / (sum(blks_hit) + sum(blks_read)), 2) FROM pg_stat_database WHERE datname = '$DB_NAME';" | tr -d ' ')
    
    # Get transaction statistics
    local transactions=$(psql -U $DB_USER -d $DB_NAME -t -c "SELECT xact_commit + xact_rollback FROM pg_stat_database WHERE datname = '$DB_NAME';" | tr -d ' ')
    
    log_performance "DB Performance: Size=$db_size, Cache Hit Ratio=${cache_hit_ratio}%, Total Transactions=$transactions"
}

# Monitor slow queries
monitor_slow_queries() {
    local slow_queries=$(psql -U $DB_USER -d $DB_NAME -t -c "SELECT COUNT(*) FROM pg_stat_statements WHERE mean_exec_time > 1000;" 2>/dev/null | tr -d ' ' || echo "N/A")
    log_performance "Slow Queries (>1s): $slow_queries"
}

# Monitor locks
monitor_locks() {
    local lock_count=$(psql -U $DB_USER -d $DB_NAME -t -c "SELECT COUNT(*) FROM pg_locks WHERE NOT granted;" | tr -d ' ')
    if [ "$lock_count" -gt 0 ]; then
        log_performance "⚠️  Blocked Locks: $lock_count"
    fi
}

# Main monitoring loop
monitor_loop() {
    log_info "Starting monitoring loop (interval: ${MONITOR_INTERVAL}s)..."
    log_info "Logging to: $LOG_FILE"
    echo
    
    local iteration=0
    
    while true; do
        iteration=$((iteration + 1))
        
        echo -e "${CYAN}=== Monitoring Iteration $iteration ($(date '+%H:%M:%S')) ===${NC}"
        
        monitor_connections
        monitor_table_sizes
        monitor_insert_rates
        monitor_recent_activity
        monitor_db_performance
        monitor_slow_queries
        monitor_locks
        
        echo
        
        sleep $MONITOR_INTERVAL
    done
}

# Generate final report
generate_final_report() {
    log_info "Generating final performance report..."
    
    local final_message_logs=$(psql -U $DB_USER -d $DB_NAME -t -c "SELECT COUNT(*) FROM message_logs;" | tr -d ' ')
    local final_cb_events=$(psql -U $DB_USER -d $DB_NAME -t -c "SELECT COUNT(*) FROM circuit_breaker_events;" | tr -d ' ')
    local final_retry_attempts=$(psql -U $DB_USER -d $DB_NAME -t -c "SELECT COUNT(*) FROM retry_attempts;" | tr -d ' ')
    local final_dlq=$(psql -U $DB_USER -d $DB_NAME -t -c "SELECT COUNT(*) FROM dead_letter_messages;" | tr -d ' ')
    
    echo
    echo -e "${GREEN}=== FINAL PERFORMANCE REPORT ===${NC}"
    echo "Final Database State:"
    echo "  Message Logs: $final_message_logs (+$((final_message_logs - INITIAL_MESSAGE_LOGS)))"
    echo "  Circuit Breaker Events: $final_cb_events (+$((final_cb_events - INITIAL_CIRCUIT_BREAKER_EVENTS)))"
    echo "  Retry Attempts: $final_retry_attempts (+$((final_retry_attempts - INITIAL_RETRY_ATTEMPTS)))"
    echo "  DLQ Messages: $final_dlq (+$((final_dlq - INITIAL_DLQ_MESSAGES)))"
    echo
    
    # Performance summary
    local total_new_records=$((final_message_logs - INITIAL_MESSAGE_LOGS + final_cb_events - INITIAL_CIRCUIT_BREAKER_EVENTS + final_retry_attempts - INITIAL_RETRY_ATTEMPTS + final_dlq - INITIAL_DLQ_MESSAGES))
    echo "Total New Records Created: $total_new_records"
    echo "Performance Log: $LOG_FILE"
}

# Handle cleanup on exit
cleanup() {
    echo
    log_info "Monitoring stopped. Generating final report..."
    generate_final_report
    echo
    log_info "Database performance monitoring completed!"
}

# Set up signal handlers
trap cleanup EXIT INT TERM

# Main execution
main() {
    echo "Starting database performance monitoring at $(date)"
    echo
    
    get_initial_state
    monitor_loop
}

# Check if running in background mode
if [ "$1" = "--background" ]; then
    echo "Starting monitoring in background..."
    nohup "$0" > "monitor-$(date +%Y%m%d-%H%M%S).out" 2>&1 &
    echo "Monitoring started in background. PID: $!"
    echo "Log file: $LOG_FILE"
    exit 0
fi

# Run main function
main "$@"

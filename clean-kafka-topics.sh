#!/bin/bash

# Clean Kafka Topics Script
# This script removes all messages from Kafka topics and resets consumer offsets

set -e

echo "🧹 Cleaning Kafka Topics"
echo "========================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
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

# Configuration
KAFKA_BOOTSTRAP="localhost:9092"
TOPICS=("projection-processing-queue" "failed-projection-messages" "dead-letter-queue")

# Check if Kafka is running
check_kafka() {
    log_info "Checking if Kafka is running..."
    if docker ps | grep -q kafka; then
        if docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list > /dev/null 2>&1; then
            log_success "Kafka is running and accessible"
            return 0
        else
            log_error "Kafka container is running but not accessible"
            return 1
        fi
    else
        log_error "Kafka container not found. Make sure to run ./setup-test-environment.sh first"
        return 1
    fi
}

# Get topic information before cleaning
get_topic_info() {
    log_info "Getting topic information before cleaning..."
    echo
    
    for topic in "${TOPICS[@]}"; do
        log_info "Topic: $topic"
        
        # Get partition count
        local partitions=$(docker exec kafka kafka-topics --bootstrap-server localhost:9092 --describe --topic "$topic" | grep "PartitionCount" | awk '{print $4}')
        
        # Get message count for each partition
        for ((partition=0; partition<partitions; partition++)); do
            local offset=$(docker exec kafka kafka-run-class kafka.tools.GetOffsetShell --bootstrap-server localhost:9092 --topic "$topic" --partitions "$partition" --time -1 | cut -d: -f3)
            echo "  Partition $partition: $offset messages"
        done
        echo
    done
}

# Clean topics by deleting and recreating them
clean_topics() {
    log_info "Cleaning Kafka topics..."
    
    for topic in "${TOPICS[@]}"; do
        log_info "Cleaning topic: $topic"
        
        # Delete the topic
        docker exec kafka kafka-topics --bootstrap-server localhost:9092 --delete --topic "$topic" || true
        
        # Wait a moment
        sleep 2
        
        # Recreate the topic
        docker exec kafka kafka-topics --bootstrap-server localhost:9092 \
            --create --topic "$topic" \
            --partitions 3 --replication-factor 1 || true
        
        log_success "Topic $topic cleaned and recreated"
    done
}

# Reset consumer group offsets (if any)
reset_consumer_offsets() {
    log_info "Resetting consumer group offsets..."
    
    # Get all consumer groups
    local groups=$(docker exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --list 2>/dev/null || echo "")
    
    if [ -n "$groups" ]; then
        echo "$groups" | while read -r group; do
            if [ -n "$group" ]; then
                log_info "Resetting offsets for consumer group: $group"
                docker exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 \
                    --group "$group" --reset-offsets --to-earliest --all-topics --execute || true
            fi
        done
    else
        log_info "No consumer groups found to reset"
    fi
}

# Verify topics are clean
verify_clean_topics() {
    log_info "Verifying topics are clean..."
    echo
    
    for topic in "${TOPICS[@]}"; do
        log_info "Topic: $topic"
        
        # Get partition count
        local partitions=$(docker exec kafka kafka-topics --bootstrap-server localhost:9092 --describe --topic "$topic" | grep "PartitionCount" | awk '{print $4}')
        
        # Check message count for each partition
        local total_messages=0
        for ((partition=0; partition<partitions; partition++)); do
            local offset=$(docker exec kafka kafka-run-class kafka.tools.GetOffsetShell --bootstrap-server localhost:9092 --topic "$topic" --partitions "$partition" --time -1 | cut -d: -f3)
            echo "  Partition $partition: $offset messages"
            total_messages=$((total_messages + offset))
        done
        
        if [ $total_messages -eq 0 ]; then
            log_success "Topic $topic is clean (0 messages)"
        else
            log_warning "Topic $topic still has $total_messages messages"
        fi
        echo
    done
}

# Clean database tables (optional)
clean_database() {
    log_info "Cleaning database tables..."
    
    # Truncate all tables
    docker exec postgres psql -U diagnostic_user -d diagnostic_service -c "TRUNCATE TABLE message_logs, circuit_breaker_events, retry_attempts, dead_letter_messages RESTART IDENTITY CASCADE;"
    
    log_success "Database tables cleaned"
}

# Main execution
main() {
    echo "Starting Kafka cleanup at $(date)"
    echo
    
    # Check if Kafka is running
    if ! check_kafka; then
        exit 1
    fi
    
    # Get topic information before cleaning
    get_topic_info
    
    # Ask for confirmation
    echo "⚠️  This will delete ALL messages from the following topics:"
    for topic in "${TOPICS[@]}"; do
        echo "   - $topic"
    done
    echo
    echo "It will also clean the database tables."
    echo
    read -p "Are you sure you want to continue? (y/N): " -n 1 -r
    echo
    
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        log_info "Cleanup cancelled by user"
        exit 0
    fi
    
    # Clean topics
    clean_topics
    
    # Reset consumer offsets
    reset_consumer_offsets
    
    # Clean database
    clean_database
    
    # Verify topics are clean
    verify_clean_topics
    
    log_success "🎉 Kafka topics and database cleaned successfully!"
    echo
    echo "📊 Ready to run tests:"
    echo "  ./test-e2e-comprehensive.sh"
    echo "  ./test-performance-1000.sh"
    echo "  ./test-performance-simple.sh"
    echo "  ./test-simple.sh"
    echo
    echo "Cleanup completed at $(date)"
}

# Run main function
main "$@"

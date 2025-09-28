#!/bin/bash

# View Kafka Messages Script
# This script allows you to view messages in Kafka topics

set -e

echo "📨 Kafka Message Viewer"
echo "======================"

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

log_topic() {
    echo -e "${PURPLE}[TOPIC]${NC} $1"
}

# Configuration
KAFKA_BOOTSTRAP="localhost:9092"
TOPICS=("projection-processing-queue" "failed-projection-messages" "dead-letter-queue")

# Check if Kafka is running
check_kafka() {
    if docker ps | grep -q kafka; then
        if docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list > /dev/null 2>&1; then
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

# List available topics
list_topics() {
    log_info "Available topics:"
    docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list | sed 's/^/  /'
    echo
}

# View messages from a topic
view_topic_messages() {
    local topic=$1
    local count=${2:-10}
    
    log_topic "Viewing last $count messages from $topic"
    echo "----------------------------------------"
    
    if command -v /opt/homebrew/opt/kafka/bin/kafka-console-consumer > /dev/null 2>&1; then
        # Local Kafka installation
        timeout 10s /opt/homebrew/opt/kafka/bin/kafka-console-consumer \
            --bootstrap-server $KAFKA_BOOTSTRAP \
            --topic $topic \
            --from-beginning \
            --max-messages $count \
            --property "print.key=true" \
            --property "print.value=true" \
            --property "key.separator=: " \
            --property "value.deserializer=org.apache.kafka.common.serialization.StringDeserializer" \
            --property "key.deserializer=org.apache.kafka.common.serialization.StringDeserializer" 2>/dev/null || true
    else
        # Docker Kafka
        timeout 10s docker exec -i kafka kafka-console-consumer \
            --bootstrap-server localhost:9092 \
            --topic $topic \
            --from-beginning \
            --max-messages $count \
            --property "print.key=true" \
            --property "print.value=true" \
            --property "key.separator=: " \
            --property "value.deserializer=org.apache.kafka.common.serialization.StringDeserializer" \
            --property "key.deserializer=org.apache.kafka.common.serialization.StringDeserializer" 2>/dev/null || true
    fi
    echo
}

# View all topics
view_all_topics() {
    local count=${1:-5}
    
    for topic in "${TOPICS[@]}"; do
        view_topic_messages "$topic" "$count"
    done
}

# Interactive mode
interactive_mode() {
    echo "Interactive mode - Select a topic to view:"
    echo
    
    # Get available topics
    local available_topics=($(docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list))
    
    # Display menu
    for i in "${!available_topics[@]}"; do
        echo "$((i+1)). ${available_topics[$i]}"
    done
    echo "$((${#available_topics[@]}+1)). View all topics"
    echo "$((${#available_topics[@]}+2)). Exit"
    echo
    
    read -p "Enter your choice (1-$((${#available_topics[@]}+2))): " choice
    
    if [ "$choice" -ge 1 ] && [ "$choice" -le "${#available_topics[@]}" ]; then
        local selected_topic="${available_topics[$((choice-1))]}"
        read -p "How many messages to view? (default: 10): " count
        count=${count:-10}
        view_topic_messages "$selected_topic" "$count"
    elif [ "$choice" -eq "$((${#available_topics[@]}+1))" ]; then
        read -p "How many messages per topic? (default: 5): " count
        count=${count:-5}
        view_all_topics "$count"
    elif [ "$choice" -eq "$((${#available_topics[@]}+2))" ]; then
        log_info "Exiting..."
        exit 0
    else
        log_error "Invalid choice"
        exit 1
    fi
}

# Show help
show_help() {
    echo "Usage: $0 [OPTIONS]"
    echo
    echo "Options:"
    echo "  -t, --topic TOPIC     View messages from specific topic"
    echo "  -c, --count COUNT     Number of messages to view (default: 10)"
    echo "  -a, --all             View messages from all topics"
    echo "  -i, --interactive     Interactive mode"
    echo "  -l, --list            List available topics"
    echo "  -h, --help            Show this help message"
    echo
    echo "Examples:"
    echo "  $0 -t projection-processing-queue -c 20"
    echo "  $0 -a -c 5"
    echo "  $0 -i"
    echo "  $0 -l"
}

# Main execution
main() {
    # Parse command line arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            -t|--topic)
                TOPIC="$2"
                shift 2
                ;;
            -c|--count)
                COUNT="$2"
                shift 2
                ;;
            -a|--all)
                VIEW_ALL=true
                shift
                ;;
            -i|--interactive)
                INTERACTIVE=true
                shift
                ;;
            -l|--list)
                LIST_TOPICS=true
                shift
                ;;
            -h|--help)
                show_help
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    # Check if Kafka is running
    if ! check_kafka; then
        exit 1
    fi
    
    # Handle different modes
    if [ "$LIST_TOPICS" = true ]; then
        list_topics
    elif [ "$INTERACTIVE" = true ]; then
        interactive_mode
    elif [ "$VIEW_ALL" = true ]; then
        view_all_topics "${COUNT:-5}"
    elif [ -n "$TOPIC" ]; then
        view_topic_messages "$TOPIC" "${COUNT:-10}"
    else
        # Default: show help
        show_help
    fi
}

# Run main function
main "$@"

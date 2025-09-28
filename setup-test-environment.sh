#!/bin/bash

# Setup Test Environment Script for Diagnostic Service
# This script sets up the complete test environment with PostgreSQL, Kafka, and Kafka UI

set -e

echo "🚀 Setting up Test Environment for Diagnostic Service"
echo "===================================================="

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

log_step() {
    echo -e "${PURPLE}[STEP]${NC} $1"
}

# Check if Docker is running
check_docker() {
    log_info "Checking if Docker is running..."
    if ! docker info > /dev/null 2>&1; then
        log_error "Docker is not running. Please start Docker and try again."
        exit 1
    fi
    log_success "Docker is running"
}

# Check if Docker Compose is available
check_docker_compose() {
    log_info "Checking Docker Compose..."
    if ! command -v docker-compose > /dev/null 2>&1; then
        log_error "Docker Compose is not installed. Please install Docker Compose and try again."
        exit 1
    fi
    log_success "Docker Compose is available"
}

# Stop existing containers
stop_existing_containers() {
    log_step "Stopping existing containers..."
    docker-compose down --remove-orphans || true
    log_success "Existing containers stopped"
}

# Start services
start_services() {
    log_step "Starting services with Docker Compose..."
    docker-compose up -d postgres zookeeper kafka kafka-ui pgadmin
    
    log_info "Waiting for services to be ready..."
    sleep 10
    
    # Wait for PostgreSQL to be ready
    log_info "Waiting for PostgreSQL to be ready..."
    until docker exec postgres pg_isready -U diagnostic_user -d diagnostic_service > /dev/null 2>&1; do
        echo -n "."
        sleep 2
    done
    echo
    log_success "PostgreSQL is ready"
    
    # Wait for Kafka to be ready
    log_info "Waiting for Kafka to be ready..."
    until docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list > /dev/null 2>&1; do
        echo -n "."
        sleep 2
    done
    echo
    log_success "Kafka is ready"
    
    # Wait for Kafka UI to be ready
    log_info "Waiting for Kafka UI to be ready..."
    until curl -s http://localhost:8081 > /dev/null 2>&1; do
        echo -n "."
        sleep 2
    done
    echo
    log_success "Kafka UI is ready"
    
    # Wait for pgAdmin to be ready
    log_info "Waiting for pgAdmin to be ready..."
    until curl -s http://localhost:8082 > /dev/null 2>&1; do
        echo -n "."
        sleep 2
    done
    echo
    log_success "pgAdmin is ready"
}

# Create Kafka topics
create_kafka_topics() {
    log_step "Creating Kafka topics..."
    
    # Create projection-processing-queue topic
    docker exec kafka kafka-topics --bootstrap-server localhost:9092 \
        --create --topic projection-processing-queue \
        --partitions 3 --replication-factor 1 || true
    
    # Create failed-projection-messages topic
    docker exec kafka kafka-topics --bootstrap-server localhost:9092 \
        --create --topic failed-projection-messages \
        --partitions 3 --replication-factor 1 || true
    
    # Create dead-letter-queue topic
    docker exec kafka kafka-topics --bootstrap-server localhost:9092 \
        --create --topic dead-letter-queue \
        --partitions 3 --replication-factor 1 || true
    
    log_success "Kafka topics created"
}

# Build and start the application
build_and_start_app() {
    log_step "Building and starting the diagnostic service..."
    docker-compose up -d --build diagnostic-service
    
    log_info "Waiting for application to be ready..."
    until curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; do
        echo -n "."
        sleep 5
    done
    echo
    log_success "Diagnostic service is ready"
}

# Verify all services
verify_services() {
    log_step "Verifying all services..."
    
    # Check PostgreSQL
    if docker exec postgres psql -U diagnostic_user -d diagnostic_service -c "SELECT 1;" > /dev/null 2>&1; then
        log_success "PostgreSQL connection verified"
    else
        log_error "PostgreSQL connection failed"
        exit 1
    fi
    
    # Check Kafka
    if docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list > /dev/null 2>&1; then
        log_success "Kafka connection verified"
    else
        log_error "Kafka connection failed"
        exit 1
    fi
    
    # Check Application
    if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
        log_success "Application health check passed"
    else
        log_error "Application health check failed"
        exit 1
    fi
    
    # Check Kafka UI
    if curl -s http://localhost:8081 > /dev/null 2>&1; then
        log_success "Kafka UI is accessible"
    else
        log_error "Kafka UI is not accessible"
        exit 1
    fi
    
    # Check pgAdmin
    if curl -s http://localhost:8082 > /dev/null 2>&1; then
        log_success "pgAdmin is accessible"
    else
        log_error "pgAdmin is not accessible"
        exit 1
    fi
}

# Display service information
display_service_info() {
    echo
    log_success "🎉 Test Environment Setup Complete!"
    echo
    echo "📋 Service Information:"
    echo "======================"
    echo "PostgreSQL:"
    echo "  Host: localhost:5432"
    echo "  Database: diagnostic_service"
    echo "  Username: diagnostic_user"
    echo "  Password: diagnostic_password"
    echo
    echo "Kafka:"
    echo "  Bootstrap Server: localhost:9092"
    echo "  Topics: projection-processing-queue, failed-projection-messages, dead-letter-queue"
    echo
    echo "Application:"
    echo "  URL: http://localhost:8080"
    echo "  Health Check: http://localhost:8080/actuator/health"
    echo "  API Stats: http://localhost:8080/api/diagnostic/stats"
    echo
    echo "Kafka UI:"
    echo "  URL: http://localhost:8081"
    echo "  Use this to monitor Kafka topics and messages"
    echo
    echo "pgAdmin (PostgreSQL UI):"
    echo "  URL: http://localhost:8082"
    echo "  Email: admin@diagnostic-service.com"
    echo "  Password: admin123"
    echo "  Use this to manage PostgreSQL database"
    echo
    echo "📊 Available Test Scripts:"
    echo "========================="
    echo "  ./test-e2e-comprehensive.sh  - Comprehensive end-to-end tests"
    echo "  ./test-performance-1000.sh   - Performance test with 1000 messages"
    echo "  ./test-performance-simple.sh - Simple performance test"
    echo "  ./test-simple.sh             - Basic functionality test"
    echo
    echo "🔧 Database Commands:"
    echo "===================="
    echo "  Connect to PostgreSQL:"
    echo "    docker exec -it postgres psql -U diagnostic_user -d diagnostic_service"
    echo
    echo "  View message logs:"
    echo "    docker exec postgres psql -U diagnostic_user -d diagnostic_service -c \"SELECT * FROM message_logs ORDER BY created_at DESC LIMIT 10;\""
    echo
    echo "  View circuit breaker events:"
    echo "    docker exec postgres psql -U diagnostic_user -d diagnostic_service -c \"SELECT * FROM circuit_breaker_events ORDER BY created_at DESC LIMIT 10;\""
    echo
    echo "  View retry attempts:"
    echo "    docker exec postgres psql -U diagnostic_user -d diagnostic_service -c \"SELECT * FROM retry_attempts ORDER BY created_at DESC LIMIT 10;\""
    echo
    echo "  View dead letter messages:"
    echo "    docker exec postgres psql -U diagnostic_user -d diagnostic_service -c \"SELECT * FROM dead_letter_messages ORDER BY created_at DESC LIMIT 10;\""
    echo
    echo "📈 Monitoring:"
    echo "============="
    echo "  Application Metrics: http://localhost:8080/actuator/metrics"
    echo "  Circuit Breaker State: http://localhost:8080/api/diagnostic/circuit-breaker/state"
    echo "  Kafka UI: http://localhost:8081"
    echo "  pgAdmin (PostgreSQL UI): http://localhost:8082"
    echo
    echo "🛑 To stop all services:"
    echo "======================="
    echo "  docker-compose down"
    echo
}

# Main execution
main() {
    echo "Starting test environment setup at $(date)"
    echo
    
    check_docker
    check_docker_compose
    stop_existing_containers
    start_services
    create_kafka_topics
    build_and_start_app
    verify_services
    display_service_info
    
    log_success "Setup completed at $(date)"
}

# Run main function
main "$@"

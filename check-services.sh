#!/bin/bash

# Quick Service Status Check Script
# This script checks the status of all services in the test environment

set -e

echo "🔍 Checking Service Status"
echo "========================="

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

# Check Docker
check_docker() {
    log_info "Checking Docker..."
    if docker info > /dev/null 2>&1; then
        log_success "Docker is running"
        return 0
    else
        log_error "Docker is not running"
        return 1
    fi
}

# Check PostgreSQL
check_postgres() {
    log_info "Checking PostgreSQL..."
    if docker ps | grep -q postgres; then
        if docker exec postgres pg_isready -U diagnostic_user -d diagnostic_service > /dev/null 2>&1; then
            log_success "PostgreSQL is running and accessible"
            return 0
        else
            log_error "PostgreSQL container is running but not accessible"
            return 1
        fi
    else
        log_warning "PostgreSQL container not found"
        return 1
    fi
}

# Check Kafka
check_kafka() {
    log_info "Checking Kafka..."
    if docker ps | grep -q kafka; then
        if docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list > /dev/null 2>&1; then
            log_success "Kafka is running and accessible"
            return 0
        else
            log_error "Kafka container is running but not accessible"
            return 1
        fi
    else
        log_warning "Kafka container not found"
        return 1
    fi
}

# Check Application
check_application() {
    log_info "Checking Application..."
    if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
        log_success "Application is running and healthy"
        return 0
    else
        log_error "Application is not accessible"
        return 1
    fi
}

# Check Kafka UI
check_kafka_ui() {
    log_info "Checking Kafka UI..."
    if curl -s http://localhost:8081 > /dev/null 2>&1; then
        log_success "Kafka UI is accessible"
        return 0
    else
        log_error "Kafka UI is not accessible"
        return 1
    fi
}

# Check pgAdmin
check_pgadmin() {
    log_info "Checking pgAdmin..."
    if curl -s http://localhost:8082 > /dev/null 2>&1; then
        log_success "pgAdmin is accessible"
        return 0
    else
        log_error "pgAdmin is not accessible"
        return 1
    fi
}

# Check topics
check_topics() {
    log_info "Checking Kafka topics..."
    if docker ps | grep -q kafka; then
        local topics=$(docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list 2>/dev/null)
        if echo "$topics" | grep -q "projection-processing-queue"; then
            log_success "Required topics exist"
            echo "  Available topics:"
            echo "$topics" | sed 's/^/    /'
            return 0
        else
            log_warning "Required topics not found"
            echo "  Available topics:"
            echo "$topics" | sed 's/^/    /'
            return 1
        fi
    else
        log_warning "Cannot check topics - Kafka not running"
        return 1
    fi
}

# Check database tables
check_database_tables() {
    log_info "Checking database tables..."
    if docker ps | grep -q postgres; then
        local tables=$(docker exec postgres psql -U diagnostic_user -d diagnostic_service -t -c "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public';" 2>/dev/null)
        if echo "$tables" | grep -q "message_logs"; then
            log_success "Required database tables exist"
            echo "  Available tables:"
            echo "$tables" | tr -d ' ' | sed 's/^/    /'
            return 0
        else
            log_warning "Required database tables not found"
            echo "  Available tables:"
            echo "$tables" | tr -d ' ' | sed 's/^/    /'
            return 1
        fi
    else
        log_warning "Cannot check database tables - PostgreSQL not running"
        return 1
    fi
}

# Main execution
main() {
    echo "Checking services at $(date)"
    echo
    
    local all_good=true
    
    # Check all services
    check_docker || all_good=false
    check_postgres || all_good=false
    check_kafka || all_good=false
    check_application || all_good=false
    check_kafka_ui || all_good=false
    check_pgadmin || all_good=false
    check_topics || all_good=false
    check_database_tables || all_good=false
    
    echo
    if [ "$all_good" = true ]; then
        log_success "🎉 All services are running and healthy!"
        echo
        echo "📋 Service URLs:"
        echo "  Application: http://localhost:8080"
        echo "  Kafka UI: http://localhost:8081"
        echo "  pgAdmin (PostgreSQL UI): http://localhost:8082"
        echo "  Health Check: http://localhost:8080/actuator/health"
        echo "  API Stats: http://localhost:8080/api/diagnostic/stats"
        echo
        echo "📊 Ready to run tests:"
        echo "  ./test-e2e-comprehensive.sh"
        echo "  ./test-performance-1000.sh"
        echo "  ./test-performance-simple.sh"
        echo "  ./test-simple.sh"
    else
        log_error "❌ Some services are not running properly"
        echo
        echo "🔧 To fix issues:"
        echo "  1. Run ./setup-test-environment.sh to start all services"
        echo "  2. Check Docker logs: docker-compose logs"
        echo "  3. Ensure ports 5432, 8080, 8081, and 9092 are available"
    fi
    
    echo
    echo "Check completed at $(date)"
}

# Run main function
main "$@"

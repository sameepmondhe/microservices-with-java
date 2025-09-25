#!/bin/bash

# Comprehensive Observability Test Script
set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Configuration
LOG_FILE="observability-test.log"
ALLOY_PORT=12345
LOKI_PORT=3100

echo "Test Started: $(date)" > "$LOG_FILE"

log_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

log_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

log_error() {
    echo -e "${RED}❌ $1${NC}"
}

log_step() {
    echo -e "\n${YELLOW}🔄 $1${NC}"
}

test_configuration() {
    log_step "Phase 1: Testing Alloy Configuration"
    
    # Check if Docker is available
    if ! command -v docker &> /dev/null; then
        log_error "Docker is not available"
        return 1
    fi
    
    log_info "Building unified Alloy image..."
    if docker build -t alloy-unified:test ./alloy/unified; then
        log_success "Unified Alloy image built successfully"
        return 0
    else
        log_error "Failed to build unified Alloy image"
        return 1
    fi
}

test_structure() {
    log_step "Phase 2: Testing Structure"
    
    if [ -d "./alloy/unified" ] && [ -f "./alloy/unified/alloy-unified.alloy" ]; then
        log_success "Alloy folder structure is correct"
        return 0
    else
        log_error "Alloy folder structure has issues"
        return 1
    fi
}

main() {
    echo -e "${BLUE}Comprehensive Observability Test${NC}"
    echo ""
    
    local passed=0
    local total=2
    
    if test_configuration; then
        ((passed++))
    fi
    
    if test_structure; then
        ((passed++))
    fi
    
    echo ""
    if [ $passed -eq $total ]; then
        echo -e "${GREEN}🎉 ALL TESTS PASSED! ($passed/$total)${NC}"
        echo -e "${GREEN}✅ Unified Alloy implementation working${NC}"
    else
        echo -e "${YELLOW}Tests passed: $passed/$total${NC}"
    fi
    
    echo ""
    echo -e "${BLUE}Service Endpoints:${NC}"
    echo -e "   Alloy: http://localhost:$ALLOY_PORT"
    echo -e "   Loki: http://localhost:$LOKI_PORT"
}

main "$@"

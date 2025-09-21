#!/bin/bash

# Streamlined microservices orchestrator

set -eo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR"  # Main script is in repo root

# Source modules
source "$SCRIPT_DIR/scripts/common.sh"
source "$SCRIPT_DIR/scripts/observability.sh" 
source "$SCRIPT_DIR/scripts/microservices.sh"

# Cleanup on exit
cleanup_on_exit() {
  local code=$?
  if [[ $code -ne 0 ]]; then
    log_error "Script exited unexpectedly (code=$code). Some services may still be running."
    log_step "Use './stop-services.sh' to clean up."
  fi
}
trap cleanup_on_exit EXIT

# Main execution
main() {
  clear
  log_step "🚀 Starting Microservices Environment..."
  
  # Prerequisites 
  check_prerequisites || exit 1
  
  # Start observability stack first
  start_observability_stack || {
    log_error "Failed to start observability stack."
    exit 1
  }
  
  # Start microservices 
  start_microservices || {
    log_error "Failed to start microservices."
    exit 1
  }
  
  # Success summary
  echo -e "\n🎉 Environment started successfully!"
  echo -e "\n📈 Observability: Traces flow from microservices → OTEL Collector → Tempo → Grafana"
  echo -e "\n🧪 Test with: curl http://localhost:8081/actuator/health"
  echo -e "🌟 Deployment complete! Your environment is ready."
}

main "$@"
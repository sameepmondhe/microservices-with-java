#!/bin/bash

# Common functions and configuration for microservices

# Prevent double-sourcing
if [[ -n "$COMMON_SOURCED" ]]; then
  return 0
fi
COMMON_SOURCED=true

DEBUG=true  # Set to true for verbose logs

# Logging helpers
log_step() { echo -e "\n🔹 $1"; }
log_success() { echo -e "  ✅ $1"; }
log_error() { echo -e "  ❌ $1"; }
log_debug() { [[ "$DEBUG" == "true" ]] && echo "  🐛 $1"; }

# Function to stop a local Java process running on a specific port
stop_local_service() {
  local port=$1
  local name=${2:-"process"}
  local pid
  pid=$(lsof -ti:$port || true)
  if [[ -n "$pid" ]]; then
    log_step "Stopping $name running on port $port (PID: $pid)..."
    if kill "$pid" 2>/dev/null; then
      sleep 1
      if lsof -ti:$port >/dev/null 2>&1; then
        log_debug "Process still alive, sending SIGKILL..."
        kill -9 "$pid" 2>/dev/null || true
        sleep 1
      fi
      if lsof -ti:$port >/dev/null 2>&1; then
        log_error "Failed to free port $port (PID $pid persists)."
        return 1
      fi
      log_success "$name stopped."
    else
      log_error "Failed to send SIGTERM to PID $pid on port $port."
      return 1
    fi
  fi
  return 0
}

# Function to stop a Docker container
stop_container() {
  local container_name=$1
  docker rm -f "$container_name" > /dev/null 2>&1 && log_success "$container_name container stopped." || true
}

# Check if port is free
ensure_port_free() {
  local port=$1
  local name=${2:-"unknown"}
  if lsof -ti:"$port" >/dev/null 2>&1; then
    stop_local_service "$port" "$name" || true
    if lsof -ti:"$port" >/dev/null 2>&1; then
      log_error "Port $port still in use after attempted stop. Aborting."
      return 1
    fi
  fi
}

# Create Docker network if it doesn't exist
ensure_docker_network() {
  if ! docker network ls | grep -q "microservices-network"; then
    log_step "Creating Docker network for microservices..."
    docker network create microservices-network > /dev/null 2>&1 || true
    log_success "Docker network created."
  fi
}

# Check required commands
check_prerequisites() {
  log_step "Checking prerequisites..."
  
  REQUIRED_CMDS=(java docker jq curl lsof)
  missing=()
  for cmd in "${REQUIRED_CMDS[@]}"; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
      missing+=("$cmd")
    fi
  done
  if [[ ${#missing[@]} -gt 0 ]]; then
    log_error "Missing required commands: ${missing[*]}"
    echo "Install them and retry. (brew install openjdk jq curl lsof docker)"
    return 1
  fi
  
  # Docker daemon check
  if ! docker info >/dev/null 2>&1; then
    log_error "Docker daemon not reachable. Start Docker Desktop and retry."
    return 1
  fi
  
  log_success "Prerequisites OK."
  return 0
}
#!/bin/bash

# Removed 'set -e' to prevent the script from exiting on errors
# Instead, we'll handle errors more gracefully

echo "🔄 Stopping all microservices..."

# Function to stop a local Java process running on a specific port
stop_local_service() {
  local port=$1
  local name=$2
  local pids=($(lsof -ti:$port || true))

  if [[ ${#pids[@]} -gt 0 ]]; then
    echo "  - Found ${#pids[@]} process(es) on port $port:"

    # List the processes with more details and filter for Java processes only
    echo "    Process details:"
    lsof -i:$port

    # Only kill Java processes, avoid Docker processes
    for pid in "${pids[@]}"; do
      # Check if this is a Java process (our microservices)
      local process_cmd=$(ps -p "$pid" -o comm= 2>/dev/null || echo "")
      local process_args=$(ps -p "$pid" -o args= 2>/dev/null || echo "")
      
      if [[ "$process_cmd" == "java" ]] && [[ "$process_args" == *".jar"* ]]; then
        echo "  - Stopping Java process $pid on port $port..."

        # First try graceful termination
        kill "$pid" 2>/dev/null || true

        # Give it a moment to terminate
        sleep 2

        # Check if it's still running
        if ps -p "$pid" > /dev/null 2>&1; then
          echo "    ⚠️ Java process $pid still running, forcing termination..."
          kill -9 "$pid" 2>/dev/null || true
          sleep 1
        fi
      else
        echo "  - Skipping non-Java process $pid (likely Docker-related): $process_cmd"
      fi
    done

    # Check if any Java processes are still running on this port
    local remaining_java_pids=()
    for pid in $(lsof -ti:$port 2>/dev/null || true); do
      local process_cmd=$(ps -p "$pid" -o comm= 2>/dev/null || echo "")
      local process_args=$(ps -p "$pid" -o args= 2>/dev/null || echo "")
      if [[ "$process_cmd" == "java" ]] && [[ "$process_args" == *".jar"* ]]; then
        remaining_java_pids+=("$pid")
      fi
    done

    if [[ ${#remaining_java_pids[@]} -gt 0 ]]; then
      echo "    ❌ Failed to stop Java processes on port $port: ${remaining_java_pids[*]}"
      return 1
    else
      echo "    ✅ All Java processes on port $port stopped (Docker processes left running)"
    fi
  else
    echo "  - No processes found running on port $port"
  fi
}

# Function to stop a Docker container
stop_container() {
  local container_name=$1
  echo "  - Stopping container $container_name..."
  if docker ps --format "{{.Names}}" | grep -E "$container_name" &>/dev/null; then
    docker rm -f "$container_name" 2>/dev/null || true
    echo "    ✅ $container_name stopped"
  else
    echo "    $container_name was not running"
  fi
}

# Stop config-server running locally
echo "🛑 Stopping config-server..."
stop_local_service 8888 "config-server" || echo "  ⚠️ Failed to stop config-server, continuing..."

# Stop Docker containers (no need to kill ports since they're containerized)
echo -e "\n🛑 Stopping microservices running in Docker containers..."
stop_container "otel-collector-service" || echo "  ⚠️ Failed to stop otel-collector-service, continuing..."
stop_container "tempo-service" || echo "  ⚠️ Failed to stop tempo-service, continuing..."
stop_container "prometheus-service" || echo "  ⚠️ Failed to stop prometheus-service, continuing..."
stop_container "grafana-service" || echo "  ⚠️ Failed to stop grafana-service, continuing..."
stop_container "eureka-server-service" || echo "  ⚠️ Failed to stop eureka-server-service, continuing..."
stop_container "accounts-service" || echo "  ⚠️ Failed to stop accounts-service, continuing..."
stop_container "cards-service" || echo "  ⚠️ Failed to stop cards-service, continuing..."
stop_container "loans-service" || echo "  ⚠️ Failed to stop loans-service, continuing..."
stop_container "customers-service" || echo "  ⚠️ Failed to stop customers-service, continuing..."
stop_container "gateway-server-service" || echo "  ⚠️ Failed to stop gateway-server-service, continuing..."

# Clean up Docker network
echo -e "\n🛑 Cleaning up Docker network..."
if docker network ls | grep -q "microservices-network"; then
  docker network rm microservices-network 2>/dev/null || echo "  ⚠️ Could not remove network (containers may still be using it)"
  echo "  ✅ Docker network cleanup attempted"
else
  echo "  No microservices network found"
fi

echo -e "\nℹ️ Observability stack removal logic skipped (stack not in this baseline)."

echo -e "\n🎉 All services have been stopped!"

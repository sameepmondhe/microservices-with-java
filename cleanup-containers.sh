#!/bin/bash

echo "Stopping and removing observability containers..."

# List of container names to stop
CONTAINERS=("prometheus" "grafana" "loki" "promtail" "cadvisor" "node-exporter")

# Stop each container by name
for container in "${CONTAINERS[@]}"; do
  echo "Removing container: $container"
  docker rm -f "$container" 2>/dev/null || true
  echo "Container $container removed"
done

# Also search for any partial matches (in case of composite names)
echo "Checking for any remaining containers with matching names..."
docker ps -a --format '{{.Names}}' | grep -E 'prometheus|grafana|loki|promtail|cadvisor|node-exporter' | while read -r container; do
  echo "Removing container with partial match: $container"
  docker rm -f "$container" 2>/dev/null || true
  echo "Container $container removed"
done

echo "All observability containers removed"

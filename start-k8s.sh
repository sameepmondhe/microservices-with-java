#!/bin/bash

clear
set -e

DEBUG=true  # Set to true for verbose logs

# Logging helpers
log_step() {
  echo -e "\n🔹 $1"
}

log_success() {
  echo -e "  ✅ $1"
}

log_error() {
  echo -e "  ❌ $1"
}

log_debug() {
  if [[ "$DEBUG" == "true" ]]; then
    echo "  🐛 $1"
  fi
}

# Function to check if a command exists
command_exists() {
  command -v "$1" >/dev/null 2>&1
}

# Function to check if kubectl is installed
check_kubectl() {
  if ! command_exists kubectl; then
    log_error "kubectl is not installed. Please install it first."
    echo "Visit https://kubernetes.io/docs/tasks/tools/"
    exit 1
  fi
}

# Function to check if a local K8s cluster is running
check_k8s_cluster() {
  if ! kubectl cluster-info &>/dev/null; then
    log_error "Kubernetes cluster is not running."
    echo "Please start a local Kubernetes cluster (minikube, kind, Docker Desktop, etc.) first."
    exit 1
  fi
  log_success "Kubernetes cluster is running."
}

# Function to stop a local Java process running on a specific port
stop_local_service() {
  local port=$1
  local name=$2
  local pid=$(lsof -ti:$port || true)
  if [[ -n "$pid" ]]; then
    log_step "Stopping $name on port $port (PID: $pid)..."
    kill "$pid" 2>/dev/null || true
    sleep 2
    log_success "$name stopped."
  fi
}

# Function to wait for service readiness
wait_for_service() {
  local service_name=$1
  local port=$2
  local health_url="http://localhost:$port/actuator/health/readiness"
  local retries=30

  log_step "Waiting for $service_name readiness on port $port..."
  while [[ $retries -gt 0 ]]; do
    local response=$(curl -sf "$health_url" 2>/dev/null || echo '')
    local status=$(echo "$response" | jq -r '.status' 2>/dev/null || echo '')

    if [[ "$status" == "UP" ]]; then
      log_success "$service_name is UP."
      return 0
    fi

    retries=$((retries - 1))
    sleep 2
    echo -n "."
  done

  echo ""
  log_error "$service_name did not become ready in time."
  return 1
}

# Function to start a local service using Gradle
start_local_service() {
  local service_name=$1
  local port=$2

  log_step "Starting $service_name locally on port $port..."
  # Use the root Gradle wrapper instead of the service-specific one
  (./gradlew :"$service_name":bootRun > /dev/null 2>&1 &)  # background + quiet
  wait_for_service "$service_name" "$port"
}

# Function to detect if using Minikube
is_minikube() {
  if kubectl config current-context 2>/dev/null | grep -q "minikube"; then
    return 0  # True, it's minikube
  else
    return 1  # False, not minikube
  fi
}

# Function to build Docker image for a service
build_docker_image() {
  local service_name=$1
  local image_name="${service_name}-service:latest"

  log_step "Building Docker image for $service_name..."

  if is_minikube; then
    # For Minikube, use its Docker environment
    log_debug "Using Minikube Docker environment"
    eval $(minikube -p minikube docker-env)

    if docker build -t "$image_name" "./$service_name" > /dev/null 2>&1; then
      log_success "$service_name image built in Minikube's Docker registry."
    else
      log_error "Failed to build Docker image for $service_name in Minikube."
      exit 1
    fi
  else
    # For other environments like Docker Desktop
    if docker build -t "$image_name" "./$service_name" > /dev/null 2>&1; then
      log_success "$service_name image built."
    else
      log_error "Failed to build Docker image for $service_name."
      exit 1
    fi
  fi
}

# Function to create K8s deployment and service yaml
create_k8s_yaml() {
  local service_name=$1
  local port=$2
  local yaml_file="k8s/${service_name}-deployment.yaml"

  mkdir -p k8s

  cat > "$yaml_file" <<EOL
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ${service_name}
spec:
  replicas: 1
  selector:
    matchLabels:
      app: ${service_name}
  template:
    metadata:
      labels:
        app: ${service_name}
    spec:
      containers:
      - name: ${service_name}
        image: ${service_name}-service:latest
        imagePullPolicy: Never
        ports:
        - containerPort: ${port}
        env:
        - name: CONFIG_SERVER_URL
          value: "http://host.docker.internal:8888"
        - name: SPRING_PROFILES_ACTIVE
          value: "k8s"
---
apiVersion: v1
kind: Service
metadata:
  name: ${service_name}
spec:
  selector:
    app: ${service_name}
  ports:
  - port: ${port}
    targetPort: ${port}
  type: LoadBalancer
EOL

  log_success "Created K8s deployment file for ${service_name}."
}

# Function to deploy a service to K8s
deploy_to_k8s() {
  local service_name=$1
  local port=$2
  local yaml_file="k8s/${service_name}-deployment.yaml"

  # Apply the K8s configuration
  log_step "Deploying ${service_name} to Kubernetes..."
  if kubectl apply -f "$yaml_file" > /dev/null 2>&1; then
    log_success "${service_name} deployed to Kubernetes."
  else
    log_error "Failed to deploy ${service_name} to Kubernetes."
    exit 1
  fi

  # Wait for deployment to be ready
  log_step "Waiting for ${service_name} deployment to be ready..."
  kubectl rollout status deployment/${service_name} --timeout=90s
  log_success "${service_name} deployment is ready."
}

# Function to wait for k8s service to be accessible
wait_for_k8s_service() {
  local service_name=$1
  local port=$2
  local retries=30

  log_step "Waiting for ${service_name} K8s service to be accessible..."
  while [[ $retries -gt 0 ]]; do
    # Get service port mapping
    local mapped_port=$(kubectl get svc ${service_name} -o jsonpath='{.spec.ports[0].port}' 2>/dev/null || echo '')

    if [[ -n "$mapped_port" ]]; then
      log_success "${service_name} service is accessible on port ${mapped_port}."
      return 0
    fi

    retries=$((retries - 1))
    sleep 2
    echo -n "."
  done

  log_error "${service_name} service did not become accessible in time."
  return 1
}

# Function to cleanup K8s deployments
cleanup_k8s() {
  log_step "Cleaning up existing K8s resources..."

  # Delete deployments if they exist
  kubectl delete deployment eureka-server --ignore-not-found=true
  kubectl delete deployment accounts --ignore-not-found=true
  kubectl delete deployment cards --ignore-not-found=true
  kubectl delete deployment loans --ignore-not-found=true
  kubectl delete deployment customers --ignore-not-found=true
  kubectl delete deployment gateway-server --ignore-not-found=true

  # Delete services if they exist
  kubectl delete service eureka-server --ignore-not-found=true
  kubectl delete service accounts --ignore-not-found=true
  kubectl delete service cards --ignore-not-found=true
  kubectl delete service loans --ignore-not-found=true
  kubectl delete service customers --ignore-not-found=true
  kubectl delete service gateway-server --ignore-not-found=true

  log_success "K8s cleanup completed."
}

# Main script execution
echo -e "\n🔄 Starting microservices K8s deployment..."

# Step 1: Check prerequisites
check_kubectl
check_k8s_cluster

# Step 2: Build all services using root Gradle wrapper
log_step "Building all services..."
if ./gradlew clean build -x test > /dev/null 2>&1; then
  log_success "All services built successfully."
else
  log_error "Failed to build services."
  exit 1
fi

# Step 3: Cleanup
log_step "Stopping any existing services..."
stop_local_service 8888 "config-server"
cleanup_k8s

# Step 4: Start config-server locally
start_local_service "config-server" 8888
sleep 5  # Give it time to fully initialize

# Step 5: Build Docker images for all services
build_docker_image "eureka-server"
build_docker_image "accounts"
build_docker_image "loans"
build_docker_image "cards"
build_docker_image "customers"
build_docker_image "gateway-server"

# Step 6: Create K8s deployment files
create_k8s_yaml "eureka-server" 8761
create_k8s_yaml "accounts" 8081
create_k8s_yaml "loans" 8082
create_k8s_yaml "cards" 8083
create_k8s_yaml "customers" 8084
create_k8s_yaml "gateway-server" 8072

# Step 7: Deploy to K8s
deploy_to_k8s "eureka-server" 8761
sleep 10  # Give Eureka time to initialize

deploy_to_k8s "accounts" 8081
deploy_to_k8s "loans" 8082
deploy_to_k8s "cards" 8083
deploy_to_k8s "customers" 8084
deploy_to_k8s "gateway-server" 8072

# Step 8: Wait for services to be accessible
wait_for_k8s_service "eureka-server" 8761
wait_for_k8s_service "accounts" 8081
wait_for_k8s_service "loans" 8082
wait_for_k8s_service "cards" 8083
wait_for_k8s_service "customers" 8084
wait_for_k8s_service "gateway-server" 8072

# Final Summary
EUREKA_IP=$(kubectl get svc eureka-server -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "localhost")
EUREKA_PORT=$(kubectl get svc eureka-server -o jsonpath='{.spec.ports[0].port}' 2>/dev/null || echo "8761")

GATEWAY_IP=$(kubectl get svc gateway-server -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "localhost")
GATEWAY_PORT=$(kubectl get svc gateway-server -o jsonpath='{.spec.ports[0].port}' 2>/dev/null || echo "8072")

echo -e "\n🎉 All services deployed to Kubernetes successfully!"
echo "
  - 🛠  Config Server:  http://localhost:8888 (Running locally)
  - 🌐 Eureka Server:  http://$EUREKA_IP:$EUREKA_PORT (In Kubernetes)
  - 🚪 Gateway Server: http://$GATEWAY_IP:$GATEWAY_PORT (In Kubernetes)
  - 💰 Accounts:       In Kubernetes
  - 💳 Cards:          In Kubernetes
  - 🏦 Loans:          In Kubernetes
  - 👥 Customers:       In Kubernetes"

echo -e "\n📝 To view all deployed resources in Kubernetes:"
echo "   kubectl get all"

echo -e "\n📊 To view logs for a service (example):"
echo "   kubectl logs deployment/accounts"

echo -e "\n🛠 To port-forward a service directly (example):"
echo "   kubectl port-forward svc/accounts 8081:8081"

echo -e "\n🔄 To restart a deployment (example):"
echo "   kubectl rollout restart deployment accounts"

echo -e "\n🚮 To clean up all resources:"
echo "   ./cleanup-k8s.sh"

echo -e "\n🌟 Deployment complete! Your microservices environment is ready in Kubernetes."

#!/bin/bash

# Script to update Kubernetes manifests with actual ACR details
# Run this after setting up Azure resources

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Load ACR details
if [ -f "../acr-details.env" ]; then
    source ../acr-details.env
    log_info "Loaded ACR configuration:"
    log_info "  ACR Login Server: $ACR_LOGIN_SERVER"
else
    log_error "ACR configuration file not found. Please run setup-azure-resources.sh first."
    exit 1
fi

# Function to update image references in manifests
update_manifests() {
    log_info "Updating Kubernetes manifests with ACR details..."
    
    # List of manifest files that need updating
    MANIFEST_FILES=(
        "../manifests/02-config-server.yaml"
        "../manifests/03-eureka-server.yaml"
        "../manifests/04-accounts.yaml"
        "../manifests/05-cards.yaml"
        "../manifests/06-loans.yaml"
        "../manifests/07-customers.yaml"
        "../manifests/08-gateway-server.yaml"
    )
    
    for manifest_file in "${MANIFEST_FILES[@]}"; do
        if [ -f "$manifest_file" ]; then
            log_info "Updating $manifest_file..."
            
            # Replace placeholder with actual ACR login server
            sed -i.bak "s|YOUR_ACR_LOGIN_SERVER|$ACR_LOGIN_SERVER|g" "$manifest_file"
            
            # Remove backup file
            rm -f "$manifest_file.bak"
            
            log_success "Updated $manifest_file"
        else
            log_error "Manifest file not found: $manifest_file"
        fi
    done
}

# Function to create deployment script
create_deployment_script() {
    log_info "Creating deployment script..."
    
    cat > deploy-to-aks.sh << 'EOF'
#!/bin/bash

# Script to deploy banking microservices to AKS
# Run this after building and pushing images

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if kubectl is connected to AKS
check_kubectl_connection() {
    log_info "Checking kubectl connection..."
    
    if ! kubectl cluster-info &> /dev/null; then
        log_error "kubectl is not connected to a cluster. Please run:"
        log_error "  az aks get-credentials --resource-group YOUR_RG --name YOUR_AKS_CLUSTER"
        exit 1
    fi
    
    # Check if we're connected to AKS
    CURRENT_CONTEXT=$(kubectl config current-context)
    if [[ $CURRENT_CONTEXT != *"aks"* ]]; then
        log_error "kubectl is not connected to an AKS cluster. Current context: $CURRENT_CONTEXT"
        exit 1
    fi
    
    log_success "kubectl is connected to AKS cluster: $CURRENT_CONTEXT"
}

# Deploy manifests in order
deploy_manifests() {
    log_info "Deploying manifests to AKS..."
    
    # Array of manifest files in deployment order
    MANIFESTS=(
        "../manifests/00-namespaces.yaml"
        "../manifests/01-configmaps-secrets.yaml"
        "../manifests/02-config-server.yaml"
        "../manifests/03-eureka-server.yaml"
        "../manifests/04-accounts.yaml"
        "../manifests/05-cards.yaml"
        "../manifests/06-loans.yaml"
        "../manifests/07-customers.yaml"
        "../manifests/08-gateway-server.yaml"
        "../manifests/09-ingress.yaml"
    )
    
    for manifest in "${MANIFESTS[@]}"; do
        if [ -f "$manifest" ]; then
            log_info "Applying $manifest..."
            kubectl apply -f "$manifest"
            
            # Wait a bit between critical deployments
            if [[ "$manifest" == *"config-server"* ]] || [[ "$manifest" == *"eureka-server"* ]]; then
                log_info "Waiting for $(basename $manifest) to be ready..."
                sleep 30
            fi
        else
            log_error "Manifest file not found: $manifest"
            exit 1
        fi
    done
    
    log_success "All manifests applied successfully"
}

# Wait for deployments to be ready
wait_for_deployments() {
    log_info "Waiting for deployments to be ready..."
    
    DEPLOYMENTS=(
        "config-server"
        "eureka-server"
        "accounts"
        "cards"
        "loans"
        "customers"
        "gateway-server"
    )
    
    for deployment in "${DEPLOYMENTS[@]}"; do
        log_info "Waiting for $deployment deployment..."
        kubectl rollout status deployment/$deployment -n banking-microservices --timeout=300s
        log_success "$deployment is ready"
    done
}

# Get service information
get_service_info() {
    log_info "Getting service information..."
    
    echo "=================================="
    echo "Banking Microservices Deployment"
    echo "=================================="
    echo
    
    # Get pods status
    echo "Pods Status:"
    kubectl get pods -n banking-microservices -o wide
    echo
    
    # Get services
    echo "Services:"
    kubectl get svc -n banking-microservices
    echo
    
    # Get ingress
    echo "Ingress:"
    kubectl get ingress -n banking-microservices
    echo
    
    # Get external IP for gateway-server LoadBalancer
    GATEWAY_IP=$(kubectl get svc gateway-server -n banking-microservices -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "Pending")
    if [ "$GATEWAY_IP" != "Pending" ] && [ ! -z "$GATEWAY_IP" ]; then
        log_success "Gateway Server is accessible at: http://$GATEWAY_IP"
        log_info "Eureka Dashboard: http://$GATEWAY_IP/eureka"
    else
        log_info "Gateway LoadBalancer IP is still pending. Check later with:"
        log_info "  kubectl get svc gateway-server -n banking-microservices"
    fi
}

# Main execution
main() {
    echo "========================================="
    echo "Deploying Banking Microservices to AKS"
    echo "========================================="
    echo
    
    check_kubectl_connection
    deploy_manifests
    wait_for_deployments
    get_service_info
    
    echo
    log_success "================================================"
    log_success "Banking Microservices deployed successfully!"
    log_success "================================================"
    echo
    log_info "Next steps:"
    log_info "1. Test the services using the gateway LoadBalancer IP"
    log_info "2. Monitor the deployment: kubectl get pods -n banking-microservices -w"
    log_info "3. Check logs: kubectl logs -f deployment/SERVICE_NAME -n banking-microservices"
}

# Run main function
main "$@"
EOF

    chmod +x deploy-to-aks.sh
    log_success "Created deploy-to-aks.sh script"
}

# Function to create monitoring setup script
create_monitoring_script() {
    log_info "Creating monitoring setup script..."
    
    cat > setup-monitoring.sh << 'EOF'
#!/bin/bash

# Script to set up monitoring stack (Prometheus, Grafana, Loki) on AKS

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Install monitoring stack using Helm
install_monitoring() {
    log_info "Installing monitoring stack..."
    
    # Add Helm repositories
    helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
    helm repo add grafana https://grafana.github.io/helm-charts
    helm repo update
    
    # Install Prometheus stack (includes Grafana)
    log_info "Installing Prometheus and Grafana..."
    helm install monitoring prometheus-community/kube-prometheus-stack \
        --namespace monitoring \
        --create-namespace \
        --set grafana.service.type=LoadBalancer \
        --set prometheus.service.type=LoadBalancer
    
    # Install Loki
    log_info "Installing Loki..."
    helm install loki grafana/loki-stack \
        --namespace monitoring \
        --set grafana.enabled=false \
        --set promtail.enabled=true
    
    log_success "Monitoring stack installed successfully"
}

# Get monitoring information
get_monitoring_info() {
    log_info "Waiting for monitoring services to be ready..."
    
    # Wait for services
    kubectl wait --for=condition=ready pod -l "app.kubernetes.io/name=grafana" -n monitoring --timeout=300s
    
    echo "========================="
    echo "Monitoring Stack Status"
    echo "========================="
    echo
    
    # Get services
    kubectl get svc -n monitoring
    echo
    
    # Get Grafana admin password
    GRAFANA_PASSWORD=$(kubectl get secret monitoring-grafana -n monitoring -o jsonpath='{.data.admin-password}' | base64 --decode)
    GRAFANA_IP=$(kubectl get svc monitoring-grafana -n monitoring -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "Pending")
    
    log_info "Grafana Credentials:"
    log_info "  Username: admin"
    log_info "  Password: $GRAFANA_PASSWORD"
    
    if [ "$GRAFANA_IP" != "Pending" ] && [ ! -z "$GRAFANA_IP" ]; then
        log_success "Grafana Dashboard: http://$GRAFANA_IP"
    else
        log_info "Grafana LoadBalancer IP is pending. Check later with:"
        log_info "  kubectl get svc monitoring-grafana -n monitoring"
    fi
}

# Main execution
main() {
    echo "============================="
    echo "Setting up Monitoring Stack"
    echo "============================="
    echo
    
    # Check if Helm is installed
    if ! command -v helm &> /dev/null; then
        log_error "Helm is not installed. Please install it first:"
        log_error "  brew install helm (macOS)"
        exit 1
    fi
    
    install_monitoring
    get_monitoring_info
    
    log_success "Monitoring stack setup completed!"
}

# Run main function
main "$@"
EOF

    chmod +x setup-monitoring.sh
    log_success "Created setup-monitoring.sh script"
}

# Main execution
main() {
    echo "======================================="
    echo "Updating Kubernetes Manifests"
    echo "======================================="
    echo
    
    update_manifests
    create_deployment_script
    create_monitoring_script
    
    echo
    log_success "======================================="
    log_success "Manifests updated successfully!"
    log_success "======================================="
    echo
    log_info "Files created/updated:"
    log_info "  - All manifest files updated with ACR details"
    log_info "  - deploy-to-aks.sh (deployment script)"
    log_info "  - setup-monitoring.sh (monitoring setup)"
    echo
    log_info "Next steps:"
    log_info "1. Build and push images: ./build-and-push-images.sh"
    log_info "2. Deploy to AKS: ./deploy-to-aks.sh"
    log_info "3. Setup monitoring: ./setup-monitoring.sh"
}

# Run main function
main "$@"
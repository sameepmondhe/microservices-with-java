#!/bin/bash

# Azure AKS Deployment Setup Script for Banking Microservices
# Run this after installing Azure CLI

set -e

# Configuration variables - MODIFY THESE AS NEEDED
RESOURCE_GROUP="rg-banking-microservices"
LOCATION="eastus"
ACR_NAME="acrbankingms$(date +%s)"  # Unique ACR name with timestamp
AKS_CLUSTER_NAME="aks-banking-microservices"
NODE_COUNT=3
NODE_SIZE="Standard_D2s_v3"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
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

# Function to check if Azure CLI is installed and user is logged in
check_prerequisites() {
    log_info "Checking prerequisites..."
    
    # Check if Azure CLI is installed
    if ! command -v az &> /dev/null; then
        log_error "Azure CLI is not installed. Please install it first:"
        log_error "  macOS: brew install azure-cli"
        log_error "  Or visit: https://docs.microsoft.com/en-us/cli/azure/install-azure-cli"
        exit 1
    fi
    
    # Check if user is logged in
    if ! az account show &> /dev/null; then
        log_warning "You are not logged in to Azure. Please run 'az login' first."
        read -p "Do you want to login now? (y/n): " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            az login
        else
            log_error "Please login to Azure first using 'az login'"
            exit 1
        fi
    fi
    
    log_success "Prerequisites check completed"
}

# Function to create resource group
create_resource_group() {
    log_info "Creating resource group '$RESOURCE_GROUP' in location '$LOCATION'..."
    
    if az group create --name $RESOURCE_GROUP --location $LOCATION --output none; then
        log_success "Resource group '$RESOURCE_GROUP' created successfully"
    else
        log_error "Failed to create resource group"
        exit 1
    fi
}

# Function to create Azure Container Registry
create_acr() {
    log_info "Creating Azure Container Registry '$ACR_NAME'..."
    
    if az acr create \
        --resource-group $RESOURCE_GROUP \
        --name $ACR_NAME \
        --sku Basic \
        --admin-enabled true \
        --output none; then
        log_success "ACR '$ACR_NAME' created successfully"
        
        # Get ACR login server
        ACR_LOGIN_SERVER=$(az acr show --name $ACR_NAME --resource-group $RESOURCE_GROUP --query "loginServer" --output tsv)
        log_info "ACR Login Server: $ACR_LOGIN_SERVER"
        
        # Save ACR details to file for later use
        cat > ../acr-details.env << EOF
ACR_NAME=$ACR_NAME
ACR_LOGIN_SERVER=$ACR_LOGIN_SERVER
RESOURCE_GROUP=$RESOURCE_GROUP
EOF
        log_success "ACR details saved to acr-details.env"
    else
        log_error "Failed to create ACR"
        exit 1
    fi
}

# Function to create AKS cluster
create_aks_cluster() {
    log_info "Creating AKS cluster '$AKS_CLUSTER_NAME' with $NODE_COUNT nodes..."
    
    if az aks create \
        --resource-group $RESOURCE_GROUP \
        --name $AKS_CLUSTER_NAME \
        --node-count $NODE_COUNT \
        --node-vm-size $NODE_SIZE \
        --attach-acr $ACR_NAME \
        --enable-managed-identity \
        --generate-ssh-keys \
        --enable-addons monitoring \
        --output none; then
        log_success "AKS cluster '$AKS_CLUSTER_NAME' created successfully"
    else
        log_error "Failed to create AKS cluster"
        exit 1
    fi
}

# Function to configure kubectl
configure_kubectl() {
    log_info "Configuring kubectl to connect to AKS cluster..."
    
    if az aks get-credentials \
        --resource-group $RESOURCE_GROUP \
        --name $AKS_CLUSTER_NAME \
        --overwrite-existing; then
        log_success "kubectl configured successfully"
        
        # Test kubectl connection
        log_info "Testing kubectl connection..."
        kubectl get nodes
        log_success "kubectl is working correctly"
    else
        log_error "Failed to configure kubectl"
        exit 1
    fi
}

# Function to install NGINX Ingress Controller
install_ingress_controller() {
    log_info "Installing NGINX Ingress Controller..."
    
    # Add ingress-nginx repository
    helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
    helm repo update
    
    # Install NGINX Ingress Controller
    if helm install ingress-nginx ingress-nginx/ingress-nginx \
        --create-namespace \
        --namespace ingress-nginx \
        --set controller.service.type=LoadBalancer; then
        log_success "NGINX Ingress Controller installed successfully"
        
        log_info "Waiting for LoadBalancer IP to be assigned..."
        kubectl wait --namespace ingress-nginx \
            --for=condition=ready pod \
            --selector=app.kubernetes.io/component=controller \
            --timeout=120s
            
        # Get the external IP
        EXTERNAL_IP=$(kubectl get svc ingress-nginx-controller -n ingress-nginx -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
        if [ ! -z "$EXTERNAL_IP" ]; then
            log_success "Ingress LoadBalancer IP: $EXTERNAL_IP"
            echo "INGRESS_IP=$EXTERNAL_IP" >> ../acr-details.env
        else
            log_warning "LoadBalancer IP not yet assigned. Check later with: kubectl get svc -n ingress-nginx"
        fi
    else
        log_error "Failed to install NGINX Ingress Controller"
        exit 1
    fi
}

# Function to create namespace for microservices
create_namespaces() {
    log_info "Creating Kubernetes namespaces..."
    
    kubectl create namespace banking-microservices --dry-run=client -o yaml | kubectl apply -f -
    kubectl create namespace monitoring --dry-run=client -o yaml | kubectl apply -f -
    
    log_success "Namespaces created successfully"
}

# Main execution
main() {
    echo "========================================"
    echo "Azure AKS Setup for Banking Microservices"
    echo "========================================"
    echo
    
    check_prerequisites
    
    echo
    log_info "Configuration:"
    log_info "  Resource Group: $RESOURCE_GROUP"
    log_info "  Location: $LOCATION"
    log_info "  ACR Name: $ACR_NAME"
    log_info "  AKS Cluster: $AKS_CLUSTER_NAME"
    log_info "  Node Count: $NODE_COUNT"
    log_info "  Node Size: $NODE_SIZE"
    echo
    
    read -p "Do you want to proceed with this configuration? (y/n): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        log_info "Setup cancelled by user"
        exit 0
    fi
    
    create_resource_group
    create_acr
    create_aks_cluster
    configure_kubectl
    
    # Check if Helm is installed
    if command -v helm &> /dev/null; then
        install_ingress_controller
    else
        log_warning "Helm is not installed. Skipping NGINX Ingress Controller installation."
        log_info "To install Helm: brew install helm (macOS) or visit https://helm.sh/docs/intro/install/"
    fi
    
    create_namespaces
    
    echo
    log_success "========================================="
    log_success "Azure AKS setup completed successfully!"
    log_success "========================================="
    echo
    log_info "Next steps:"
    log_info "1. Build and push Docker images: ./build-and-push-images.sh"
    log_info "2. Deploy microservices: kubectl apply -f ../manifests/"
    log_info "3. Check deployment status: kubectl get pods -n banking-microservices"
    echo
    log_info "Configuration saved in: ../acr-details.env"
}

# Run main function
main "$@"
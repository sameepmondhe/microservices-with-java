#!/bin/bash

# Script to build and push Docker images to Azure Container Registry
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
    log_info "  ACR Name: $ACR_NAME"
    log_info "  ACR Login Server: $ACR_LOGIN_SERVER"
else
    log_error "ACR configuration file not found. Please run setup-azure-resources.sh first."
    exit 1
fi

# Microservices to build
SERVICES=("config-server" "eureka-server" "accounts" "cards" "loans" "customers" "gateway-server")

# Function to build Gradle project
build_gradle_project() {
    log_info "Building all microservices with Gradle..."
    
    cd ../../
    
    if ./gradlew clean build -x test; then
        log_success "All services built successfully"
    else
        log_error "Failed to build services"
        exit 1
    fi
    
    cd k8s-aks/scripts/
}

# Function to login to ACR
login_to_acr() {
    log_info "Logging in to Azure Container Registry..."
    
    if az acr login --name $ACR_NAME; then
        log_success "Successfully logged in to ACR"
    else
        log_error "Failed to login to ACR"
        exit 1
    fi
}

# Function to build and push Docker image
build_and_push_image() {
    local service_name=$1
    local image_tag="$ACR_LOGIN_SERVER/$service_name:latest"
    local image_tag_versioned="$ACR_LOGIN_SERVER/$service_name:v1.0.0"
    
    log_info "Building Docker image for $service_name..."
    
    cd ../../$service_name
    
    # Build Docker image
    if docker build -t $image_tag -t $image_tag_versioned .; then
        log_success "Docker image built for $service_name"
    else
        log_error "Failed to build Docker image for $service_name"
        cd ../k8s-aks/scripts/
        exit 1
    fi
    
    # Push both tags to ACR
    log_info "Pushing $service_name image to ACR..."
    
    if docker push $image_tag && docker push $image_tag_versioned; then
        log_success "Successfully pushed $service_name image to ACR"
    else
        log_error "Failed to push $service_name image to ACR"
        cd ../k8s-aks/scripts/
        exit 1
    fi
    
    cd ../k8s-aks/scripts/
}

# Function to verify images in ACR
verify_images() {
    log_info "Verifying images in ACR..."
    
    for service in "${SERVICES[@]}"; do
        if az acr repository show --name $ACR_NAME --repository $service &> /dev/null; then
            log_success "$service image found in ACR"
        else
            log_error "$service image not found in ACR"
        fi
    done
}

# Function to create image pull secret for Kubernetes
create_image_pull_secret() {
    log_info "Creating image pull secret for Kubernetes..."
    
    # Get ACR credentials
    ACR_USERNAME=$(az acr credential show --name $ACR_NAME --query "username" --output tsv)
    ACR_PASSWORD=$(az acr credential show --name $ACR_NAME --query "passwords[0].value" --output tsv)
    
    # Create secret in banking-microservices namespace
    if kubectl create secret docker-registry acr-secret \
        --docker-server=$ACR_LOGIN_SERVER \
        --docker-username=$ACR_USERNAME \
        --docker-password=$ACR_PASSWORD \
        --namespace=banking-microservices \
        --dry-run=client -o yaml | kubectl apply -f -; then
        log_success "Image pull secret created successfully"
    else
        log_error "Failed to create image pull secret"
        exit 1
    fi
}

# Main execution
main() {
    echo "============================================"
    echo "Building and Pushing Images to Azure ACR"
    echo "============================================"
    echo
    
    # Check if Docker is running
    if ! docker info &> /dev/null; then
        log_error "Docker is not running. Please start Docker Desktop."
        exit 1
    fi
    
    build_gradle_project
    login_to_acr
    
    echo
    log_info "Building and pushing images for ${#SERVICES[@]} services..."
    echo
    
    for service in "${SERVICES[@]}"; do
        build_and_push_image $service
        echo
    done
    
    verify_images
    create_image_pull_secret
    
    echo
    log_success "=========================================="
    log_success "All images built and pushed successfully!"
    log_success "=========================================="
    echo
    log_info "Images available in ACR:"
    for service in "${SERVICES[@]}"; do
        log_info "  $ACR_LOGIN_SERVER/$service:latest"
        log_info "  $ACR_LOGIN_SERVER/$service:v1.0.0"
    done
    echo
    log_info "Next step: Deploy to AKS using kubectl apply -f ../manifests/"
}

# Run main function
main "$@"
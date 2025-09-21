# Banking Microservices Deployment to Azure Kubernetes Service (AKS)

This guide provides step-by-step instructions to deploy the Java-based banking microservices application to Azure Kubernetes Service (AKS).

## 📋 Prerequisites

Before starting the deployment, ensure you have the following tools installed:

### Required Tools
- **Azure CLI** - For Azure resource management
- **Docker Desktop** - For building container images
- **kubectl** - For Kubernetes cluster management
- **Helm** (optional) - For monitoring stack deployment

### Installation Commands (macOS)
```bash
# Install Azure CLI
brew install azure-cli

# Install kubectl
brew install kubectl

# Install Helm (optional, for monitoring)
brew install helm

# Verify Docker Desktop is running
docker info
```

### Azure Subscription
- Active Azure subscription with permissions to create:
  - Resource Groups
  - Azure Container Registry (ACR)
  - Azure Kubernetes Service (AKS)
  - Virtual Networks and Load Balancers

## 🏗️ Architecture Overview

The application consists of the following microservices:

### Core Services
- **config-server** (8888) - Centralized configuration management
- **eureka-server** (8761) - Service discovery and registration
- **gateway-server** (8072) - API Gateway and load balancer

### Business Services
- **accounts** (8081) - Account management service
- **cards** (8082) - Credit/Debit card management
- **loans** (8083) - Loan processing service
- **customers** (8084) - Customer information management

### External Dependencies
- **MongoDB/Cosmos DB** - Primary database
- **Prometheus** - Metrics collection
- **Grafana** - Monitoring dashboards
- **Loki** - Log aggregation

## 🚀 Deployment Steps

### Step 1: Setup Azure Resources

1. **Login to Azure**
   ```bash
   az login
   ```

2. **Run the Azure setup script**
   ```bash
   cd k8s-aks/scripts
   ./setup-azure-resources.sh
   ```

   This script will:
   - Create a Resource Group
   - Create Azure Container Registry (ACR)
   - Create AKS cluster with 3 nodes
   - Configure kubectl to connect to AKS
   - Install NGINX Ingress Controller
   - Create necessary namespaces

   **Configuration (modify in script if needed):**
   - Resource Group: `rg-banking-microservices`
   - Location: `eastus`
   - ACR Name: `acrbankingms{timestamp}`
   - AKS Cluster: `aks-banking-microservices`
   - Node Count: 3 (Standard_D2s_v3)

### Step 2: Update Kubernetes Manifests

After Azure resources are created, update the manifests with actual ACR details:

```bash
./update-manifests.sh
```

This script will:
- Replace placeholder ACR URLs with actual ACR login server
- Create deployment script (`deploy-to-aks.sh`)
- Create monitoring setup script (`setup-monitoring.sh`)

### Step 3: Build and Push Docker Images

Build all microservices and push images to Azure Container Registry:

```bash
./build-and-push-images.sh
```

This script will:
- Build all microservices using Gradle
- Create Docker images for each service
- Push images to ACR with `latest` and `v1.0.0` tags
- Create Kubernetes image pull secret

**Images created:**
- `{acr-name}.azurecr.io/config-server:latest`
- `{acr-name}.azurecr.io/eureka-server:latest`
- `{acr-name}.azurecr.io/accounts:latest`
- `{acr-name}.azurecr.io/cards:latest`
- `{acr-name}.azurecr.io/loans:latest`
- `{acr-name}.azurecr.io/customers:latest`
- `{acr-name}.azurecr.io/gateway-server:latest`

### Step 4: Configure External Dependencies

Before deploying, update the database configuration:

1. **Setup MongoDB/Cosmos DB**
   - Create Azure Cosmos DB for MongoDB API account
   - Update connection details in `manifests/01-configmaps-secrets.yaml`:
     ```yaml
     MONGODB_HOST: "your-cosmosdb-account.mongo.cosmos.azure.com"
     MONGODB_USERNAME: "your-username"
     MONGODB_PASSWORD: "your-password"
     MONGODB_CONNECTION_STRING: "mongodb://..."
     ```

2. **Apply the updated configuration**
   ```bash
   kubectl apply -f ../manifests/01-configmaps-secrets.yaml
   ```

### Step 5: Deploy Microservices to AKS

Deploy all services to the AKS cluster:

```bash
./deploy-to-aks.sh
```

This script will:
- Deploy services in the correct order (config-server → eureka-server → business services)
- Wait for each critical service to be ready
- Configure ingress for external access
- Display deployment status and service URLs

**Deployment Order:**
1. Namespaces and ConfigMaps
2. Config Server
3. Eureka Server (waits for Config Server)
4. Business Services (Accounts, Cards, Loans, Customers)
5. Gateway Server
6. Ingress Controller

### Step 6: Setup Monitoring (Optional)

Deploy Prometheus, Grafana, and Loki for monitoring:

```bash
./setup-monitoring.sh
```

This will install:
- **Prometheus** - Metrics collection and alerting
- **Grafana** - Monitoring dashboards
- **Loki** - Log aggregation
- **Promtail** - Log forwarding

## 📊 Accessing the Application

### External Access

After deployment, the application will be accessible through:

1. **Gateway Server LoadBalancer**
   ```bash
   kubectl get svc gateway-server -n banking-microservices
   ```
   Access at: `http://{EXTERNAL-IP}`

2. **Ingress Controller** (if configured with domain)
   ```bash
   kubectl get ingress -n banking-microservices
   ```

### Service Endpoints

| Service | Internal URL | External Path |
|---------|-------------|---------------|
| Gateway Server | http://gateway-server:8072 | / |
| Eureka Dashboard | http://eureka-server:8761 | /eureka |
| Accounts API | http://accounts:8081 | /accounts |
| Cards API | http://cards:8082 | /cards |
| Loans API | http://loans:8083 | /loans |
| Customers API | http://customers:8084 | /customers |

### Monitoring Access

| Service | URL | Credentials |
|---------|-----|-------------|
| Grafana | http://{GRAFANA-IP} | admin / {generated-password} |
| Prometheus | http://{PROMETHEUS-IP} | - |

Get Grafana password:
```bash
kubectl get secret monitoring-grafana -n monitoring -o jsonpath='{.data.admin-password}' | base64 --decode
```

## 🔧 Management Commands

### Check Deployment Status
```bash
# View all pods
kubectl get pods -n banking-microservices

# Watch pod status
kubectl get pods -n banking-microservices -w

# Check service status
kubectl get svc -n banking-microservices

# View ingress
kubectl get ingress -n banking-microservices
```

### View Logs
```bash
# View logs for a specific service
kubectl logs -f deployment/accounts -n banking-microservices

# View logs for all pods with a label
kubectl logs -f -l app=accounts -n banking-microservices

# View previous pod logs
kubectl logs deployment/accounts -n banking-microservices --previous
```

### Scale Services
```bash
# Scale a service
kubectl scale deployment accounts --replicas=3 -n banking-microservices

# Auto-scale based on CPU
kubectl autoscale deployment accounts --cpu-percent=50 --min=1 --max=10 -n banking-microservices
```

### Debug Services
```bash
# Describe a deployment
kubectl describe deployment accounts -n banking-microservices

# Get detailed pod information
kubectl describe pod POD_NAME -n banking-microservices

# Execute commands in a pod
kubectl exec -it POD_NAME -n banking-microservices -- /bin/bash
```

## 🔄 Update Deployment

### Update Docker Images
1. Build new images with updated version tags
2. Update the deployment:
   ```bash
   kubectl set image deployment/accounts accounts={acr-name}.azurecr.io/accounts:v1.1.0 -n banking-microservices
   ```

### Rolling Updates
```bash
# Check rollout status
kubectl rollout status deployment/accounts -n banking-microservices

# Rollback to previous version
kubectl rollout undo deployment/accounts -n banking-microservices

# View rollout history
kubectl rollout history deployment/accounts -n banking-microservices
```

## 🧹 Cleanup Resources

### Delete Application
```bash
# Delete all microservices
kubectl delete namespace banking-microservices

# Delete monitoring stack
helm uninstall monitoring -n monitoring
helm uninstall loki -n monitoring
kubectl delete namespace monitoring
```

### Delete Azure Resources
```bash
# Delete entire resource group (WARNING: This deletes everything)
az group delete --name rg-banking-microservices --yes --no-wait
```

## 🚨 Troubleshooting

### Common Issues

1. **Pod CrashLoopBackOff**
   ```bash
   kubectl describe pod POD_NAME -n banking-microservices
   kubectl logs POD_NAME -n banking-microservices
   ```

2. **Service Discovery Issues**
   - Check Eureka server logs
   - Verify network policies
   - Ensure services are registered in Eureka

3. **Database Connection Issues**
   - Verify MongoDB/Cosmos DB credentials
   - Check network connectivity
   - Review security groups and firewall rules

4. **Image Pull Errors**
   - Verify ACR authentication
   - Check image names and tags
   - Ensure image pull secret is created

5. **LoadBalancer IP Pending**
   - Check Azure subscription limits
   - Verify AKS cluster networking
   - Review Azure Load Balancer configuration

### Health Checks

All services expose actuator endpoints:
```bash
# Check service health
curl http://{SERVICE-IP}:PORT/actuator/health

# Check service metrics
curl http://{SERVICE-IP}:PORT/actuator/prometheus
```

## 📝 Configuration Files

### Key Configuration Files
- `k8s-aks/manifests/` - Kubernetes deployment manifests
- `k8s-aks/scripts/` - Deployment and management scripts
- `acr-details.env` - ACR configuration (generated)

### Environment Variables
Services can be configured using environment variables in ConfigMaps and Secrets:
- Database connection strings
- Service discovery URLs
- Logging levels
- Feature flags

## 🔐 Security Considerations

1. **Network Security**
   - Services communicate within cluster network
   - External access through LoadBalancer/Ingress only
   - Network policies can be added for additional isolation

2. **Secret Management**
   - Database credentials stored in Kubernetes Secrets
   - ACR authentication through service principal
   - Consider Azure Key Vault integration

3. **RBAC**
   - Configure appropriate RBAC policies
   - Use service accounts with minimal permissions
   - Enable audit logging

## 📚 Additional Resources

- [Azure Kubernetes Service Documentation](https://docs.microsoft.com/en-us/azure/aks/)
- [Azure Container Registry Documentation](https://docs.microsoft.com/en-us/azure/container-registry/)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Spring Boot on Kubernetes](https://spring.io/guides/topicals/spring-on-kubernetes/)

---

**Note:** Replace placeholder values (like `{acr-name}`, `{EXTERNAL-IP}`) with actual values from your deployment.
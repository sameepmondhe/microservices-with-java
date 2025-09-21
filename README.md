# Microservices With Java (Clean Baseline)

## Overview
This repository contains a simplified baseline of a Java/Spring microservices system with the following services:

- Config Server (`config-server`)
- Service Discovery (`eureka-server`)
- API Gateway (`gateway-server`)
- Domain Services: `accounts`, `cards`, `loans`, `customers`

All observability (Prometheus, Grafana, Loki, cAdvisor, etc.) and auxiliary tooling have been intentionally removed to allow incremental re-introduction one pillar at a time.

## Current State
- Java Toolchain: 21 (all modules now aligned)
- Actuator exposure: only `health` and `info`
- No metrics/logs/traces exporters enabled
- `start-services.sh` orchestrates a clean local run with preflight checks

## Running Locally
```
./start-services.sh
```
The script will:
1. Run preflight checks (commands, Java version, Docker daemon, port availability)
2. Build all services (`gradlew clean build -x test`)
3. Start `config-server` locally, others in Docker containers

Services:
- Config Server: http://localhost:8888
- Eureka: http://localhost:8761
- Accounts: http://localhost:8081
- Cards: http://localhost:8083
- Loans: http://localhost:8082
- Customers: http://localhost:8084
- Gateway: http://localhost:8072

## Stopping
```
./stop-services.sh
```

## Next Increment Options
Choose one to proceed when ready:
1. Reintroduce minimal metrics (Prometheus endpoint + single scrape)
2. Add structured logging strategy
3. Introduce tracing (OpenTelemetry Java agent + collector)
4. Prepare Kubernetes (AKS) manifests
5. Harden build (tests, profiles, dependency convergence)

## Branching Recommendation
Use feature branches for each observability pillar to keep diffs reviewable:
- `feature/metrics-baseline`
- `feature/logging-centralized`
- `feature/tracing`

## Notes
- If `java -version` shows 17, update to JDK 21 before continuing.
- Config Server pulls configs from `configs/` directory (Git-backed option retained in its `application.yml`).

---
Minimal baseline established. Expand intentionally.

## Tracing (Step 2: OpenTelemetry Collector Integration)

Status: ENABLED for `config-server` (local JVM) using OpenTelemetry Java agent with OTLP exporter targeting a local Collector.

What was added:
- Always-on Java agent (`otel/opentelemetry-javaagent.jar`) attached via `start-services.sh`.
- Collector config: `otel/collector-config.yaml` with OTLP receiver (gRPC:4317, HTTP:4318) and logging exporter (prints received spans).
- `docker-compose.yml` now includes an `otel-collector` service exposing 4317/4318.
- JVM params switched from logging exporter to OTLP:
	- `-Dotel.traces.exporter=otlp`
	- `-Dotel.exporter.otlp.endpoint=http://localhost:4317`
	- Explicit service identity: `-Dotel.service.name=config-server` plus resource attributes.

How to view spans:
1. Start stack: `./start-services.sh` (ensure Docker Desktop is running).
2. Trigger activity (e.g., `curl -s http://localhost:8888/actuator/health > /dev/null`).
3. Check collector logs (Docker):
	 ```bash
	 docker logs -f otel-collector | grep span
	 ```
	 You should see JSON-ish log lines describing spans forwarded from the agent.

Next planned enhancements:
- Add Jaeger or Tempo exporter to the Collector for UI visualization.
- Enable metrics pipeline (OTLP -> Prometheus exporter) and basic JVM metrics.
- Introduce log signal (OTel logs or structured log shipping).
- Roll agent attachment to remaining services (possibly via Docker images or sidecar).

Troubleshooting tips:
- If no spans appear: confirm port 4317 is listening (`lsof -nP -iTCP:4317 -sTCP:LISTEN`).
- Ensure the agent jar exists at `otel/opentelemetry-javaagent.jar`.
- Collector health endpoint: `curl -s http://localhost:13133/healthz` (should return `OK`).
- Increase agent debugging by adding `-Dotel.javaagent.debug=true` temporarily.


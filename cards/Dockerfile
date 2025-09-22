FROM eclipse-temurin:21-jre

ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar

# Copy OpenTelemetry agent
COPY opentelemetry-javaagent.jar opentelemetry-javaagent.jar

# Add JVM tuning parameters and OpenTelemetry agent
ENTRYPOINT ["java", \
    "-javaagent:opentelemetry-javaagent.jar", \
    "-Dotel.service.name=cards", \
    "-Dotel.resource.attributes=service.name=cards,deployment.environment=docker", \
    "-Dotel.traces.exporter=otlp", \
    "-Dotel.exporter.otlp.traces.endpoint=http://otel-collector-service:4318/v1/traces", \
    "-Dotel.exporter.otlp.protocol=http/protobuf", \
    "-Dotel.instrumentation.spring-boot.enabled=true", \
    "-Dotel.instrumentation.spring-webmvc.enabled=true", \
    "-Dotel.instrumentation.micrometer.enabled=false", \
    "-Dotel.metrics.exporter=none", \
    "-Dotel.logs.exporter=none", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "/app.jar"]

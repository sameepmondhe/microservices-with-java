FROM eclipse-temurin:17-jre

# Copy configs from the project root into the image
COPY configs/ /workspace/configs/

ARG JAR_FILE=config-server/build/libs/*.jar
COPY ${JAR_FILE} app.jar

# Add JVM tuning parameters
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app.jar"]

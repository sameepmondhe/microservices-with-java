FROM eclipse-temurin:21-jre

LABEL maintainer="example.com"

ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar

# Add JVM tuning parameters
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app.jar"]

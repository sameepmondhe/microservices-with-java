FROM eclipse-temurin:21-jre

WORKDIR /app

COPY build/libs/eureka-server-0.0.1-SNAPSHOT.jar eureka-server.jar

EXPOSE 8761

# Add JVM tuning parameters
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "eureka-server.jar"]

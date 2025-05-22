FROM eclipse-temurin:17-jre

# Copy configs from the project root into the image
COPY configs/ /workspace/configs/

ARG JAR_FILE=config-server/build/libs/*.jar
COPY ${JAR_FILE} app.jar

ENTRYPOINT ["java", "-jar", "/app.jar"]


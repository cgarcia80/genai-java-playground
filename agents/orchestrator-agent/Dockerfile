FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY target/orchestrator-agent-*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]

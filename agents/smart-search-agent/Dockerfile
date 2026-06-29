FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY target/smart-search-agent-*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]

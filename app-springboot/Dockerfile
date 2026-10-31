# Build first:  mvn -pl app-springboot -am clean package
# Then:         docker build -t command-app app-springboot
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/command-app.jar app.jar
EXPOSE 8080
# JAVA_TOOL_OPTIONS (set in the ECS task definition) can carry a -javaagent for tracing.
ENTRYPOINT ["java", "-jar", "app.jar"]

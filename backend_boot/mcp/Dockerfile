# Build Stage
FROM gradle:8.5-jdk21 AS builder
WORKDIR /app
COPY . .
RUN gradle build -x test --no-daemon

# Run Stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8095
ENTRYPOINT ["java", "-jar", "app.jar"]

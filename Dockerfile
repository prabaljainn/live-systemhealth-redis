FROM maven:3.8.6-openjdk-17-slim AS build

# Set working directory
WORKDIR /app

# Copy Maven configuration
COPY pom.xml .

# Download dependencies (leverages Docker caching)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build application
RUN mvn package -DskipTests

# Create runtime image
FROM eclipse-temurin:17-jre-alpine

# Install Docker CLI for container monitoring
RUN apk add --no-cache docker-cli

# Set non-root user for better security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Set working directory
WORKDIR /app

# Copy jar from build stage
COPY --from=build /app/target/system-health-monitoring-*.jar /app/app.jar

# Expose service port
EXPOSE 8088

# Set entry point
ENTRYPOINT ["java", "-jar", "/app/app.jar"] 
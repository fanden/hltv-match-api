# Multi-stage build for HLTV API
# Stage 1: Build the application
FROM gradle:8.5-jdk17 AS builder

WORKDIR /app

# Copy gradle files first for dependency caching
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle

# Download dependencies (cached layer)
RUN gradle dependencies --no-daemon || true

# Copy source code
COPY src ./src

# Build the application
RUN gradle build --no-daemon -x test

# Extract the Spring Boot JAR layers to avoid fat JAR issues
# Use the actual fat JAR (not the -plain.jar)
RUN mkdir -p /app/extracted && \
    cd /app/extracted && \
    java -Djarmode=layertools -jar /app/build/libs/hltv-api-1.0.0.jar extract

# Stage 2: Create runtime image
FROM eclipse-temurin:17-jdk-jammy

# Install dependencies for Playwright
RUN apt-get update && apt-get install -y \
    wget \
    ca-certificates \
    nodejs \
    npm \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy the extracted layers (this avoids nested JAR issues)
COPY --from=builder /app/extracted/dependencies/ ./
COPY --from=builder /app/extracted/spring-boot-loader/ ./
COPY --from=builder /app/extracted/snapshot-dependencies/ ./
COPY --from=builder /app/extracted/application/ ./

# Set Playwright environment variables
ENV PLAYWRIGHT_BROWSERS_PATH=/app/.cache/ms-playwright \
    PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 \
    PLAYWRIGHT_SKIP_VALIDATE_HOST_REQUIREMENTS=true

# Expose the application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/matches || exit 1

# Run the application using the extracted layout
# Use JarLauncher to run from exploded directory structure
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

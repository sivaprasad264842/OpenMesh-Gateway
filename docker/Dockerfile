# Stage 1: Build Application
FROM maven:3.9.8-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Build source
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Minimal Production JRE Runtime
FROM eclipse-temurin:21-jre-alpine
LABEL maintainer="OpenMesh Architecture Series <core@openmesh.io>"
LABEL description="Cloud-Native Multi-Tenant API Gateway & Traffic Control Plane"

WORKDIR /app

# Create non-root runtime group and user
RUN addgroup -S openmesh && adduser -S openmesh -G openmesh

# Copy compiled executable jar
COPY --from=builder /workspace/target/openmesh-gateway-*.jar app.jar

# Set permissions
RUN chown -R openmesh:openmesh /app

USER openmesh

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseG1GC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "/app/app.jar"]

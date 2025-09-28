FROM eclipse-temurin:21-jre-alpine

# Set working directory
WORKDIR /app

# Create logs directory
RUN mkdir -p /var/log/diagnostic-service

# Copy the JAR file
COPY target/diagnostic-service-0.0.1-SNAPSHOT.jar app.jar

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

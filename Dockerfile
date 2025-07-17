 # Use Eclipse Temurin JRE as the base image
FROM eclipse-temurin:17-jre

# Set working directory
WORKDIR /app

# Copy the JAR file
COPY target/*.jar app.jar

# Expose the port the app runs on
EXPOSE 8080

# Command to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
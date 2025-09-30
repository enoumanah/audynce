# --- Stage 1: The Builder ---
# This stage uses a full JDK and Maven to build the application JAR file.
FROM maven:3.9.6-eclipse-temurin-21 AS builder

# Set the working directory inside the container
WORKDIR /app

# Copy the pom.xml first to leverage Docker's layer caching.
# This dependency layer is only rebuilt if the pom.xml changes.
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy the rest of your application's source code
COPY src ./src

# Build the application, skipping the tests for a faster CI/CD build.
# Tests should be run in a separate step in your pipeline.
RUN mvn package -B -DskipTests


# --- Stage 2: The Final Image ---
# This stage uses a minimal Java Runtime Environment (JRE) for the final image.
# It results in a smaller, more secure container.
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Copy ONLY the built JAR file from the 'builder' stage.
COPY --from=builder /app/target/*.jar app.jar

# Expose the port your application runs on
EXPOSE 8080

# Command to run the application when the container starts
ENTRYPOINT ["java", "-jar", "app.jar"]
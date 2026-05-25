#This file is to create the image of the srpingboot app. How to create the Docker image for your application



# Stage 1: Build — compile the Java code
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
# Download dependencies first (Docker caches this layer — faster rebuilds)
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests
# -DskipTests: don't run tests during build (saves time)

# Stage 2: Run — only the final .jar, no Maven needed
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Copy only the built .jar from stage 1

EXPOSE 8080
# Tell Docker this container listens on port 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
# Command to start the app   When container starts,
                             #execute:
                             #java -jar app.jar
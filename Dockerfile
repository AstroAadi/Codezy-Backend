# Use Java 17 base image with build tools
FROM eclipse-temurin:17-jdk as build

WORKDIR /app

# Copy Maven wrapper and make it executable
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

RUN chmod +x mvnw \
 && ./mvnw dependency:go-offline

# Copy the rest of your project
COPY src ./src

# Package the Spring Boot app
RUN ./mvnw package -DskipTests

# Final image for running the jar
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]

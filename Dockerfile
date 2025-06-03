
FROM eclipse-temurin:17-jdk as build

WORKDIR /app

# Copy Maven wrapper and pom
COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
RUN ./mvnw dependency:go-offline

# Copy the rest of the source
COPY src ./src

# Package the app
RUN ./mvnw package -DskipTests

# Second stage - lighter runtime image
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the jar from the build stage
COPY --from=build /app/target/*.jar app.jar

# Run the jar
ENTRYPOINT ["java", "-jar", "app.jar"]


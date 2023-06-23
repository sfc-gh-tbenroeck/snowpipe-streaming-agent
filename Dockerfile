# Use the official maven/Java 8 image to create a build artifact.
FROM maven:3.8.6-jdk-8 as builder

# Copy local code to the container image.
WORKDIR /app
COPY src ./src
COPY pom.xml .
COPY config.json .

# Build a release artifact.
RUN mvn package -DskipTests

# Use AdoptOpenJDK for base image.
FROM adoptopenjdk:8-jdk-hotspot

# Copy the jar to the production image from the builder stage.
COPY --from=builder /app/target/Snowpipe-Streaming-Agent-1.0-SNAPSHOT-jar-with-dependencies.jar /snowflake-streaming-agent.jar

# Run the web service on container startup.
CMD ["java", "-jar", "/snowflake-streaming-agent.jar"]

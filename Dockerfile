# Use the official maven/Java 8 image to create a build artifact.
FROM maven:3.9.2-amazoncorretto-8 as builder

# Copy local code to the container image.
WORKDIR /app
COPY src ./src
COPY pom.xml .

# Build a release artifact.
RUN mvn clean package

# Use AdoptOpenJDK for base image.
FROM adoptopenjdk:8-jdk-hotspot

# Create the /config directory
RUN mkdir /config

# Copy the config.json file to /config
COPY config/config.json /config/config.json

# Set the environment variable
ENV CONFIG_FILE_PATH=/config/config.json

# Create a volume for the config directory
VOLUME /config

# Copy the jar to the production image from the builder stage.
COPY --from=builder /app/target/Snowpipe-Streaming-Agent-1.0-SNAPSHOT-jar-with-dependencies.jar /snowflake-streaming-agent.jar

# Run the web service on container startup.
CMD ["java", "-jar", "/snowflake-streaming-agent.jar"]

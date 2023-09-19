package snowflake;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javafaker.Faker;

import snowflake.SSAConfig.*;

import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.*;

public class SnowpipeStreamingAgent {

  private static final Logger logger = Logger.getLogger(SnowpipeStreamingAgent.class.getName());

  public static void main(String[] args) {
    try {
      logger.info("Main class started");

      try {
        Level logLevel = Level.INFO;
        String logLevelEnv = System.getenv("LOG_LEVEL");
        if (logLevelEnv != null && !logLevelEnv.isEmpty()) {
          logLevel = Level.parse(logLevelEnv.toUpperCase());
        }

        logger.setLevel(logLevel);

        Handler[] handlers = logger.getHandlers();
        if (handlers.length == 0) {
          // If there are no handlers, add a new handler with the desired log level
          ConsoleHandler handler = new ConsoleHandler();
          handler.setLevel(logLevel);
          logger.addHandler(handler);
        } else {
          // If there are existing handlers, set their log levels to the logger's log
          // level
          for (Handler handler : handlers) {
            handler.setLevel(logLevel);
          }
        }

        logger.info("Log level set to: " + logger.getLevel());

      } catch (IllegalArgumentException e) {
        logger.warning("Invalid log level in environment variable, using default log level: INFO");
      }

      // Load the config file path from an environment variable
      String configFilePath = System.getenv("CONFIG_FILE_PATH");
      if (configFilePath == null || configFilePath.isEmpty()) {
        throw new IllegalArgumentException("CONFIG_FILE_PATH environment variable is not set or is empty");
      }

      // Load the config file
      ObjectMapper objectMapper = new ObjectMapper();
      SSAConfig config = objectMapper.readValue(new File(configFilePath), SSAConfig.class);

      RuntimeConfig runtimeConfig = config.getRuntimeConfig();
      FlushConfig flushConfig = config.getFlushConfig();

      int rowsInBatch = flushConfig.getRowsInBatch();
      long minMS = runtimeConfig.getRowGenerationMinMs();
      long maxMS = runtimeConfig.getRowGenerationMaxMs();
      int maxJsonPayloadInMB = flushConfig.getMaxJsonPayloadInMB();
      int flushTimeInMinutes = flushConfig.getTimeSinceLastFlushInMinutes();

      long runFor = runtimeConfig.getRunfor();
      String runForUnit = runtimeConfig.getRunforUnit();
      long startTime = System.currentTimeMillis();
      long runTimeLimit = runForUnit.equals("minutes") ? runFor * 60 * 1000 : -1;
      int maxRows = runForUnit.equals("rows") ? (int) runFor : Integer.MAX_VALUE;

      SnowflakeStreamingService snowflakeStreamingService = new SnowflakeStreamingService();
      snowflakeStreamingService.setVariantColumn("EVENTJSON");

      List<Map<String, Object>> batch = new ArrayList<>();
      Random rand = new Random();
      int generatedRowCount = 0;

      long lastFlushTime = System.currentTimeMillis(); // New variable to track the last flush time

      while ((runTimeLimit == -1 || System.currentTimeMillis() - startTime < runTimeLimit)
          && generatedRowCount < maxRows) {

        try {
          Thread.sleep(rand.nextInt((int) (maxMS - minMS)) + minMS);

          // Generate random data using the `generateItems` method
          Map<String, Object> data = generateItems(config.recordsConfig);

          logger.fine("Data created: " + data.toString());

          Map<String, Object> row = snowflakeStreamingService.createRow(data);
          batch.add(row);

          String jsonPayload = batch.toString();
          int payloadSize = jsonPayload.getBytes().length;

          long timeSinceLastFlushInMilliseconds = System.currentTimeMillis() - lastFlushTime;

          if (batch.size() >= rowsInBatch || payloadSize >= (maxJsonPayloadInMB * 1024 * 1024)
              || timeSinceLastFlushInMilliseconds >= (flushTimeInMinutes * 1000 * 60)) {
            if (batch.size() >= rowsInBatch) {
              logger.info("Flushing because the batch size reached the maximum number of rows in batch ("
                  + rowsInBatch + ")");
            } else if (payloadSize >= (maxJsonPayloadInMB * 1024 * 1024)) {
              logger.info("Flushing because the payload size reached the maximum allowed size in MB ("
                  + maxJsonPayloadInMB + " MB)");
            } else if (timeSinceLastFlushInMilliseconds >= (flushTimeInMinutes * 1000 * 60)) {
              logger.info("Flushing because the time since last flush ("
                  + flushTimeInMinutes + " minutes)");
            }

            snowflakeStreamingService.sendToSnowpipeBatch(batch);
            logger.fine("Batch sent: " + batch.toString());

            // Reset the batch and time
            batch = new ArrayList<>();
            lastFlushTime = System.currentTimeMillis(); // Update the last flush time
          }

        } catch (InterruptedException e) {
          logger.log(Level.SEVERE, "An error occurred", e);
        }
        generatedRowCount++;
      }
      // Send remaining rows if any
      if (!batch.isEmpty()) {
        snowflakeStreamingService.sendToSnowpipeBatch(batch);
        logger.fine("Remaining batch sent: " + batch.toString());
      }

      // Determine why the loop ended
      if (System.currentTimeMillis() - startTime >= runTimeLimit && runTimeLimit != -1) {
        logger.info("Loop ended because the runtime reached the specified limit of " + runFor + " " + runForUnit);
      } else if (generatedRowCount >= maxRows) {
        logger
            .info("Loop ended because the row generation reached the specified limit of " + runFor + " " + runForUnit);
      }

      // Closing resources
      logger.info("Closing Snowflake Channel");
      snowflakeStreamingService.closeClientAndChannel();
      logger.info("Snowflake Channel Closed");
      logger.info("Main class completed");

    } catch (Exception e) {
      logger.log(Level.SEVERE, "An error occurred", e);
    }
  }

  // Add your generateItems method here (unchanged)
  private static Map<String, Object> generateItems(JsonNode recordsNode) {
    Random random = new Random();
    Map<String, Object> itemrow = new HashMap<>();
    Iterator<JsonNode> recordsIt = recordsNode.elements();
    Faker faker = new Faker();
    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    while (recordsIt.hasNext()) {
      JsonNode record = recordsIt.next();
      String columnName = record.get("columnName").asText();
      String randomType = record.get("randomType").asText();

      Object value = null;
      switch (randomType) {
        case "UUID":
          value = UUID.randomUUID().toString();
          break;
        case "Int":
          int min = record.has("minInt") ? record.get("minInt").asInt() : 0;
          int max = record.has("maxInt") ? record.get("maxInt").asInt() : Integer.MAX_VALUE - 100;
          int randomInt = random.nextInt(max - min + 1) + min;
          value = record.get("valueFormat").asText().replace("{random}", Integer.toString(randomInt));
          break;
        case "CurrentDate":
          value = ZonedDateTime.ofInstant(Instant.ofEpochMilli(System.currentTimeMillis()), ZoneId.systemDefault());
          break;
        case "RandomDate":
          LocalDate minDate = LocalDate.parse(record.get("minDate").asText(), dateFormatter);
          LocalDate maxDate = LocalDate.parse(record.get("maxDate").asText(), dateFormatter);
          long minDay = minDate.toEpochDay();
          long maxDay = maxDate.toEpochDay();
          long randomDay = ThreadLocalRandom.current().nextLong(minDay, maxDay);

          LocalTime minTime = LocalTime.parse(record.get("minTime").asText(), timeFormatter);
          LocalTime maxTime = LocalTime.parse(record.get("maxTime").asText(), timeFormatter);
          int minSecondOfDay = minTime.toSecondOfDay();
          int maxSecondOfDay = maxTime.toSecondOfDay();
          int randomTime = ThreadLocalRandom.current().nextInt(minSecondOfDay, maxSecondOfDay);

          value = LocalDateTime.ofEpochSecond(
              LocalDate.ofEpochDay(randomDay).atStartOfDay().toEpochSecond(ZoneOffset.UTC) + randomTime,
              0,
              ZoneOffset.UTC);
          break;
        case "Choice":
          value = record.get("choices").get(random.nextInt(record.get("choices").size())).asText();
          break;
        case "Faker":
          ArrayNode fakerMethods = (ArrayNode) record.get("fakerMethods");

          try {
            Object methodResult = faker; // Start with the faker object
            for (JsonNode methodNode : fakerMethods) {
              String methodName = methodNode.asText();
              Method method = methodResult.getClass().getMethod(methodName);
              methodResult = method.invoke(methodResult); // The output becomes the input for the next method
            }

            value = methodResult; // The output of the last method is the final value
          } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            logger.log(Level.SEVERE, "An error occurred", e);
          }
          break;
      }

      itemrow.put(columnName, value);
    }

    return itemrow;
  }

}

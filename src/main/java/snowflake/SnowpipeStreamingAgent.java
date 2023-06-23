package snowflake;

import com.google.gson.Gson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import com.github.javafaker.Faker;

import net.snowflake.ingest.streaming.InsertValidationResponse;
import net.snowflake.ingest.streaming.OpenChannelRequest;
import net.snowflake.ingest.streaming.SnowflakeStreamingIngestChannel;
import net.snowflake.ingest.streaming.SnowflakeStreamingIngestClient;
import net.snowflake.ingest.streaming.SnowflakeStreamingIngestClientFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;

public class SnowpipeStreamingAgent {
  private static Random random = new Random();
  private static final boolean DEBUG = Boolean.parseBoolean(System.getenv("DEBUG"));

  private static final String CONFIGURATION_FILE_PATH = System.getenv("CONFIGURATION_FILE_PATH") != null
      ? System.getenv("CONFIGURATION_FILE_PATH")
      : "config.json";
  private static final ObjectMapper mapper = new ObjectMapper();
  private static boolean sendItemAsJson = false;

  public static void main(String[] args) {
    try {
      JsonNode root = mapper.readTree(new String(Files.readAllBytes(Paths.get(CONFIGURATION_FILE_PATH))));
      Properties connectionDetails = getConnectionDetails(root);
      JsonNode itemConfiguration = getItemConfiguration(root);
      int totalItemsToSend = root.get("ItemsToSend").asInt();
      sendItemAsJson = root.has("SendItemAsJson") ? root.get("SendItemAsJson").asBoolean() : false;

      try (SnowflakeStreamingIngestClient client = buildClient(connectionDetails)) {
        sendItems(client, connectionDetails, itemConfiguration, totalItemsToSend);
      }
    } catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
      System.exit(1);
    }
  }

  private static Properties getConnectionDetails(JsonNode root) throws Exception {
    JsonNode connectionNode = root.get("ConnectionDetails");
    Properties connectionDetails = getPropertiesFromJsonNode(connectionNode);

    if (connectionDetails.getProperty("private_key_file") != null) {
      String keyfile = connectionDetails.getProperty("private_key_file");
      File key = new File(keyfile);
      if (!key.exists()) {
        throw new Exception("Unable to find key file:  " + keyfile);
      }

      String pkey = readPrivateKey(key);
      connectionDetails.setProperty("private_key", pkey);
    }

    return connectionDetails;
  }

  private static JsonNode getItemConfiguration(JsonNode root) throws Exception {
    JsonNode ItemDefinitionNode = root.get("ItemDefinition");
    return ItemDefinitionNode;
  }

  private static Properties getPropertiesFromJsonNode(JsonNode node) {
    Properties properties = new Properties();
    Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
    while (iterator.hasNext()) {
      Map.Entry<String, JsonNode> prop = iterator.next();
      properties.put(prop.getKey(), prop.getValue().asText());
    }
    return properties;
  }

  private static SnowflakeStreamingIngestClient buildClient(Properties connectionDetails) throws Exception {
    return SnowflakeStreamingIngestClientFactory
        .builder(connectionDetails.getProperty("streamingClient"))
        .setProperties(connectionDetails)
        .build();
  }

  private static void sendItems(SnowflakeStreamingIngestClient client, Properties connectionDetails,
      JsonNode itemConfiguration, int totalItemsToSend) throws Exception {
    OpenChannelRequest request = OpenChannelRequest.builder(connectionDetails.getProperty("streamingChannel"))
        .setDBName(connectionDetails.getProperty("database"))
        .setSchemaName(connectionDetails.getProperty("schema"))
        .setTableName(connectionDetails.getProperty("table"))
        .setOnErrorOption(OpenChannelRequest.OnErrorOption.CONTINUE)
        .build();

    SnowflakeStreamingIngestChannel channel = client.openChannel(request);

    for (int i = 0; i < totalItemsToSend; i++) {
      Map<String, Object> row = generateItems(itemConfiguration);

      if (sendItemAsJson) {
        Gson gson = new Gson();
        String rowJson = gson.toJson(row);

        row = new HashMap<>();
        row.put("jsonValue", rowJson);
      }

      InsertValidationResponse response = channel.insertRow(row, String.valueOf(i));

      if (response.hasErrors()) {
        throw new Exception(response.getInsertErrors().get(0).getException());
      }
    }

    channel.close().get();
  }

  private static String readPrivateKey(File file) throws Exception {
    String key = new String(Files.readAllBytes(file.toPath()), Charset.defaultCharset());
    String privateKeyPEM = key
        .replace("-----BEGIN PRIVATE KEY-----", "")
        .replaceAll(System.lineSeparator(), "")
        .replace("-----END PRIVATE KEY-----", "");
    if (DEBUG) { // check key file is valid
      byte[] encoded = Base64.getDecoder().decode(privateKeyPEM);
      KeyFactory keyFactory = KeyFactory.getInstance("RSA");
      PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
      RSAPrivateKey k = (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
      System.out.println("* DEBUG: Provided Private Key is Valid:  ");
    }
    return privateKeyPEM;
  }

  private static Map<String, Object> generateItems(JsonNode recordsNode) {
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
            e.printStackTrace();
          }
          break;
      }

      itemrow.put(columnName, value);
    }

    return itemrow;
  }

}

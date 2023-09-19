package snowflake;

import net.snowflake.ingest.streaming.InsertValidationResponse;
import net.snowflake.ingest.streaming.OpenChannelRequest;
import net.snowflake.ingest.streaming.SnowflakeStreamingIngestChannel;
import net.snowflake.ingest.streaming.SnowflakeStreamingIngestClient;
import net.snowflake.ingest.streaming.SnowflakeStreamingIngestClientFactory;

import java.util.*;
import java.util.logging.*;

import com.fasterxml.jackson.databind.ObjectMapper;

public class SnowflakeStreamingService {

    private static final Logger logger = Logger.getLogger(SnowflakeStreamingService.class.getName());


    private static SnowflakeStreamingIngestClient client;
    private static SnowflakeStreamingIngestChannel channel;

    private Properties connectionDetails = new Properties();
    private String variantColumn = "jsonValue"; // default value
    private ObjectMapper objectMapper = new ObjectMapper();


    static {
        Logger parentLogger = logger.getParent();
        if (parentLogger != null) {
            Handler[] handlers = parentLogger.getHandlers();
            if (handlers.length == 0) {
                ConsoleHandler handler = new ConsoleHandler();
                handler.setLevel(Level.ALL);
                parentLogger.addHandler(handler);
                parentLogger.setLevel(Level.ALL);
            }
        } else {
            ConsoleHandler handler = new ConsoleHandler();
            handler.setLevel(Level.ALL);
            logger.addHandler(handler);
            logger.setLevel(Level.ALL);
        }
    }

    public SnowflakeStreamingService() {
        initializeClientAndChannel();
    }

    public void setVariantColumn(String variantColumn) {
        if (variantColumn != null) {
            this.variantColumn = variantColumn;
        }
    }

    public void sendToSnowpipeBatch(List<Map<String, Object>> rowsBatch) {
        String batchUUID = UUID.randomUUID().toString();
        logger.info("Sending batch with UUID: " + batchUUID);
        InsertValidationResponse response = channel.insertRows(rowsBatch, batchUUID);

        if (response.hasErrors()) {
            logger.severe("Failed to send data to Snowpipe: " + response.getInsertErrors().get(0).getException());
        }
    }

    public Map<String, Object> createRow(Object data) {
        Map<String, Object> row = new HashMap<>();
        try {
            String jsonString = objectMapper.writeValueAsString(data);
            row.put(variantColumn, jsonString);
        } catch (Exception e) {
            logger.severe("Failed to convert object to JSON: " + e.getMessage());
        }
        return row;
    }

    private void initializeClientAndChannel() {
        try {
            // Check if client and channel are null
            if (client == null || channel == null) {

                // Fetch values from environment variables
                String account = System.getenv("SNOWPIPE_CLIENT_ACCOUNT");
                String user = System.getenv("SNOWPIPE_CLIENT_USER");
                String password = System.getenv("SNOWPIPE_CLIENT_PASSWORD");
                String private_key = System.getenv("SNOWPIPE_CLIENT_PRIVATE_KEY");

                String warehouse = System.getenv("SNOWPIPE_CLIENT_WAREHOUSE");
                String role = System.getenv("SNOWPIPE_CLIENT_ROLE");

                String streamingClient = System.getenv("SNOWPIPE_CLIENT_STREAMING_CLIENT");
                String streamingChannel = System.getenv("SNOWPIPE_EVENT_CHANNEL_NAME");

                String snowpipeTable = System.getenv("SNOWPIPE_DB_SCHEMA_TABLE");

                String host = account + ".snowflakecomputing.com";
                String baseURL = "https://" + host + ":443";
                String connect_string = "jdbc:snowflake://" + baseURL;

                // Split the SNOWPIPE_TABLE environment variable into database, schema, and
                // table
                if (snowpipeTable != null && snowpipeTable.split("\\.").length == 3) {
                    String[] tableDetails = snowpipeTable.split("\\.");
                    String database = tableDetails[0];
                    String schema = tableDetails[1];
                    String table = tableDetails[2];

                    // Set properties
                    connectionDetails.setProperty("account", account);
                    connectionDetails.setProperty("user", user);
                    if (private_key != null) {
                        connectionDetails.setProperty("private_key", private_key);
                    }

                    if (password != null) {
                        connectionDetails.setProperty("password", password);
                    }

                    connectionDetails.setProperty("url", baseURL);
                    connectionDetails.setProperty("host", host);
                    connectionDetails.setProperty("database", database);
                    connectionDetails.setProperty("schema", schema);
                    connectionDetails.setProperty("table", table);

                    if (streamingClient == null) {
                        // set a default
                        connectionDetails.setProperty("streamingClient", "streamingClient");
                    }
                    if (streamingChannel == null) {
                        // set a default
                        connectionDetails.setProperty("streamingChannel", "streamingChannel");
                    }
                    if (warehouse != null) {
                        connectionDetails.setProperty("warehouse", warehouse);
                    }

                    if (role != null) {
                        connectionDetails.setProperty("role", role);
                    }

                    connectionDetails.setProperty("connect_string", connect_string);
                    connectionDetails.setProperty("ssl", "on");
                    connectionDetails.setProperty("port", "443");
                    connectionDetails.setProperty("scheme", "https");

                    logger.info("Properties Set");

                    client = SnowflakeStreamingIngestClientFactory
                            .builder(streamingClient)
                            .setProperties(connectionDetails)
                            .build();

                    logger.info("Client Created");

                    // Create an OpenChannelRequest to send data
                    OpenChannelRequest requestChannel = OpenChannelRequest
                            .builder(streamingClient)
                            .setDBName(database)
                            .setSchemaName(schema)
                            .setTableName(table)
                            .setOnErrorOption(OpenChannelRequest.OnErrorOption.CONTINUE)
                            .build();

                    channel = client.openChannel(requestChannel);

                    logger.info("Channel created");
                } else {
                    logger.severe("Invalid SNOWPIPE_TABLE format. Expected format: database.schema.table");
                }
            }
        } catch (Exception e) {
            logger.severe("Failed to initialize Snowflake client and channel: " + e.getMessage());
        }
    }

    public void closeClientAndChannel() {
        if (!channel.isClosed()) {
            channel.close();
        }

        if (!client.isClosed()){
        }
    }

}

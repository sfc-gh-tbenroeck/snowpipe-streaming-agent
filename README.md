# Snowpipe-Streaming-Agent
The Snowpipe Streaming Agent is a Java application that generates and sends random data as JSON payloads to Snowflake's Snowpipe. It allows for customized data generation with a range of different data types and formats.

## Optional: open the folder in the .devcontainer
- Install the [Dev Containers](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers) extension
- Press F1 and select `Dev Containers: Open Folder In Container`

## Optional: Update POM.xml to the latest Snowflake Ingest SDK
Get the [Latest snowflake-ingest-sdk version](https://mvnrepository.com/artifact/net.snowflake/snowflake-ingest-sdk)

## Snowflake Configuration

### Create Table in Snowflake
```sql
CREATE OR REPLACE TABLE TESTINGDB.PUBLIC.STREAMINGDATA (
    JSONVALUE variant
);
```

### Generate Public and Private Keys for Snowflake Authentication
   * From your desktop's Command Line / Terminal window, navigate to your working directory and run these two commands:
      ```bash
      openssl genrsa 2048 | openssl pkcs8 -topk8 -inform PEM -out rsa_key.p8 -nocrypt
      openssl rsa -in rsa_key.p8 -pubout -out rsa_key.pub
      ```

   * Run the following command to get the public key value that needs to be updated to the Snowflake user:
      ```bash
      cat rsa_key.pub | tr -d '\n' | tr -d ' ' | sed -e 's/-----BEGINPUBLICKEY-----//' -e 's/-----ENDPUBLICKEY-----//'
      ```
* In Snowflake update your user with the public key copied above:
   ```sql
   alter user <my_user> set rsa_public_key='MIIBIjANBgkqh...';
   ````

## Running locally (or from the Dev Container Terminal)
Run the following commands to build the jar, load the env variables, and run the program.

```bash
mvn clean package
source .env
java -jar target/Snowpipe-Streaming-Agent-1.0-SNAPSHOT-jar-with-dependencies.jar
```

### Docker
Rename and update `docker-compose.yml.template` to `docker-compose.yml`.  Build the docker image by running `docker compose build` and run the application by calling `docker compose up`

### VS Code Debugging
Rename and update `.env.template` to `.env` and launching the code from VS Code Run menu.  The `.env` file is loaded as part of the launch.json configuration.

## Configuration

The configuration for the Snowpipe Streaming Agent is done through Environment Variables and a JSON configuration file. The structure and options of the JSON configuration file are as follows:

### `runtimeConfig`

- `runfor`: The duration for which the program will run. Can be specified in terms of minutes or number of rows to generate.
- `runforUnit`: The unit for the `runfor` parameter. Can be "minutes" or "rows".
- `rowGenerationMinMs`: The minimum milliseconds to wait before generating a new row.
- `rowGenerationMaxMs`: The maximum milliseconds to wait before generating a new row.

### `flushConfig`

- `maxJsonPayloadInMB`: The maximum size in megabytes of the JSON payload to be sent in a single batch.
- `rowsInBatch`: The maximum number of rows to include in a single batch.
- `timeSinceLastFlushInMinutes`: The time in minutes to wait before flushing the batch.

### `recordsConfig`

An array of objects representing the configuration for generating individual records in a row Each object has the following properties:

- `columnName`: The name of the column.
- `randomType`: The type of random data to generate. Can be one of the following values:
  - "String"
  - "Int"
  - "Faker"
- `columnType`: The data type of the column. (This property seems to be unused in the code, it may be removed or you can detail its purpose)
- Specific to `randomType` "Choice":
  - `choices`: An array of strings from which a random value will be chosen.
- Specific to `randomType` "Int":
  - `minInt`: The minimum value of the random integer.
  - `maxInt`: The maximum value of the random integer.
  - `valueFormat`: The format for the generated integer value, with "{random}" being replaced by the generated integer.
- Specific to `randomType` "Faker":
  - `fakerMethods`: An array of method names to be invoked sequentially on a `Faker` instance to generate a value.

There are sample configurations and information about using the Faker package below.

## Logging

The logging level can be controlled using the "LOG_LEVEL" environment variable. Valid values are "SEVERE", "WARNING", "INFO", "CONFIG", "FINE", "FINER", "FINEST", "ALL", or "OFF".

## Environment Variables

Before running the application, you need to configure it using the following environment variables:

| Variable                                | Description                                                                                                         | Example Value                             |
|-----------------------------------------|---------------------------------------------------------------------------------------------------------------------|-------------------------------------------|
| `SNOWPIPE_CLIENT_ACCOUNT`               | Your Snowflake account name                                                                                         | `"wp48969.west-us-2.azure"`               |
| `SNOWPIPE_CLIENT_USER`                  | The Snowflake username                                                                                              | `"streamingUser"`                         |
| `SNOWPIPE_CLIENT_PASSWORD`              | The Snowflake password. Either this or `SNOWPIPE_CLIENT_PRIVATE_KEY` should be set, not both                         | `"P@s$W0Rd!"`                             |
| `SNOWPIPE_CLIENT_PRIVATE_KEY`           | The private key for Snowflake. Either this or `SNOWPIPE_CLIENT_PASSWORD` should be set, not both                     | `"MIIEvAIB___NOT_REAL__g=="`             |
| `SNOWPIPE_CLIENT_WAREHOUSE`             | The Snowflake warehouse                                                                                             | `"XSMALL"`                               |
| `SNOWPIPE_CLIENT_ROLE`                  | The Snowflake role                                                                                                  | `"datawriter"`                           |
| `SNOWPIPE_CLIENT_STREAMING_CLIENT`      | The Snowflake streaming client name                                                                                 | `"streamingClient"`                      |
| `SNOWPIPE_EVENT_CHANNEL_NAME`           | The name for the Snowpipe event channel                                                                             | `"streamingAgent"`                       |
| `SNOWPIPE_DB_SCHEMA_TABLE`              | The Snowflake database schema and table                                                                             | `"TESTINGDB.PUBLIC.STREAMINGDATA"`       |
| `SNOWPIPE_EVENT_CHANNEL_VARIANT_COLUMN` | The variant column in the event channel                                                                             | `"JSONVALUE"`                            |
| `CONFIG_FILE_PATH`                      | The path to the configuration file                                                                                  | `"./config/config.json"`                        |
| `LOG_LEVEL`                             | The logging level (e.g., INFO, FINE, etc.)                                                                          | `"FINE"`                                 |


### Understanding Faker

Faker is a library that assists in generating large amounts of fake, but realistic data for a variety of purposes, such as testing, populating databases, and more. In your context, it seems to be used for generating random data for various columns in the record configurations. In the examples provided, you are using the Java Faker library to populate fields with random yet realistic internet information, like user agents and IP addresses, and personal details like full names and email addresses.

The available methods and properties to generate data using Faker can be found in the [Java Faker Documentation](https://dius.github.io/java-faker/apidocs/index.html).

### Sample Record Configurations

Here, we'll delve into two sample record configurations - Clickstream and Truck Dispatch - to understand their structure and significance in detail.

#### Clickstream Record Configuration

This configuration is set to generate random data in the context of internet browsing information. Below are the details:

- **Browser**: A random choice of browser names will be selected from a predefined list: Chrome, Safari, Mozilla, or Edge.
- **eventDuration**: A random integer between 500 and 5000 representing the duration of an event in milliseconds.
- **UserAgent**: This utilizes Faker to generate a random but realistic user agent string that represents software acting on behalf of a user (like a web browser).
- **Email**: Uses Faker to generate a random but realistic email address.
- **IpAddress**: Utilizes Faker to generate a random public IPv4 address.
- **MacAddress**: Utilizes Faker to generate a random but realistic MAC address.
```json
"recordsConfig": [
        {
            "columnName": "Browser",
            "columnType": "String",
            "randomType": "Choice",
            "choices": [
                "Chrome",
                "Safari",
                "Mozila",
                "Edge"
            ]
        },
        {
            "columnName": "eventDuration",
            "randomType": "Int",
            "minInt": 500,
            "maxInt": 5000,
            "valueFormat": "{random}"
        },
        {
            "columnName": "UserAgent",
            "randomType": "Faker",
            "fakerMethods": [
                "internet",
                "userAgentAny"
            ]
        },
        {
            "columnName": "Email",
            "randomType": "Faker",
            "fakerMethods": [
                "internet",
                "emailAddress"
            ]
        },
        {
            "columnName": "IpAddress",
            "randomType": "Faker",
            "fakerMethods": [
                "internet",
                "publicIpV4Address"
            ]
        },
        {
            "columnName": "MacAddress",
            "randomType": "Faker",
            "fakerMethods": [
                "internet",
                "macAddress"
            ]
        }
    ]
```

#### Truck Dispatch Record Configuration

This configuration generates random data simulating a truck dispatch system, which involves various entities like replenishment IDs, truck IDs, distribution center IDs, etc. Here are the details:

- **ReplenishmentID**: A unique identifier generated using UUID for each replenishment instance.
- **TruckID**: A unique identifier for trucks generated using a formatted string with a random integer appended to the prefix "Truck-".
- **DistrbutionCenterId**: A unique identifier for distribution centers generated using UUID.
- **StoreID**: A unique identifier for stores generated using UUID.
- **ProductID**: A formatted string with a random integer between 1 and 1000 appended to the prefix "Product-".
- **QuantityRequested**: A random integer between 500 and 10000 representing the quantity of products requested.
- **DateRequested**: The current date indicating when the request was made.
- **RequestedByEmployee**: Utilizes Faker to generate a random full name representing the employee who requested the replenishment.
- **ReplenishmentDate**: A random date and time between specified limits representing the date of replenishment.
- **ReplenishmentType**: A random choice between "regular" and "emergency" to indicate the type of replenishment.

```json
"recordsConfig": [
        {
            "columnName": "ReplenishmentID",
            "columnType": "String",
            "randomType": "UUID"
        },
        {
            "columnName": "TruckID",
            "randomType": "Int",
            "valueFormat": "Truck-{random}"
        },
        {
            "columnName": "DistrbutionCenterId",
            "randomType": "UUID"
        },
        {
            "columnName": "StoreID",
            "randomType": "UUID"
        },
        {
            "columnName": "ProductID",
            "randomType": "Int",
            "valueFormat": "Product-{random}",
            "minInt": 1,
            "maxInt": 1000
        },
        {
            "columnName": "QuantityRequested",
            "randomType": "Int",
            "valueFormat": "{random}",
            "minInt": 500,
            "maxInt": 10000
        },
        {
            "columnName": "DateRequested",
            "randomType": "CurrentDate"
        },
        {
            "columnName": "RequestedByEmployee",
            "randomType": "Faker",
            "fakerMethods": [
                "name",
                "fullName"
            ]
        },
        {
            "columnName": "ReplenishmentDate",
            "randomType": "RandomDate",
            "minDate": "2022-01-01",
            "maxDate": "2023-12-31",
            "minTime": "08:00:00",
            "maxTime": "18:00:00"
        },
        {
            "columnName": "ReplenishmentType",
            "randomType": "Choice",
            "choices": [
                "regular",
                "emergency"
            ]
        }
    ]
```
These record configurations can be instrumental in creating realistic and random data sets for testing and development processes. Each field in the configurations is generated based on the specifications given, thus providing a wide array of data types and values to work with.

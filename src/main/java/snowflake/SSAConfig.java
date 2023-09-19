package snowflake;

import com.fasterxml.jackson.databind.JsonNode;

public class SSAConfig {

    private RuntimeConfig runtimeConfig;
    private FlushConfig flushConfig;
    public JsonNode recordsConfig;

    // Getters and Setters
    public RuntimeConfig getRuntimeConfig() {
        return runtimeConfig;
    }

    public void setRuntimeConfig(RuntimeConfig runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public FlushConfig getFlushConfig() {
        return flushConfig;
    }

    public void setFlushConfig(FlushConfig flushConfig) {
        this.flushConfig = flushConfig;
    }

    // Inner classes representing the different config sections
    public static class RuntimeConfig {

        private int runfor;
        private String runforUnit;
        private long rowGenerationMinMs;
        private long rowGenerationMaxMs;

        // Getters and Setters
        public int getRunfor() {
            return runfor;
        }

        public void setRunfor(int runfor) {
            this.runfor = runfor;
        }

        public String getRunforUnit() {
            return runforUnit;
        }

        public void setRunforUnit(String runforUnit) {
            this.runforUnit = runforUnit;
        }

        public long getRowGenerationMinMs() {
            return rowGenerationMinMs;
        }

        public void setRowGenerationMinMs(long rowGenerationMinMs) {
            this.rowGenerationMinMs = rowGenerationMinMs;
        }

        public long getRowGenerationMaxMs() {
            return rowGenerationMaxMs;
        }

        public void setRowGenerationMaxMs(long rowGenerationMaxMs) {
            this.rowGenerationMaxMs = rowGenerationMaxMs;
        }
    }

    public static class FlushConfig {

        private int maxJsonPayloadInMB;
        private int rowsInBatch;
        private int timeSinceLastFlushInMinutes;

        // Getters and Setters
        public int getMaxJsonPayloadInMB() {
            return maxJsonPayloadInMB;
        }

        public void setMaxJsonPayloadInMB(int maxJsonPayloadInMB) {
            this.maxJsonPayloadInMB = maxJsonPayloadInMB;
        }

        public int getRowsInBatch() {
            return rowsInBatch;
        }

        public void setRowsInBatch(int rowsInBatch) {
            this.rowsInBatch = rowsInBatch;
        }

        public int getTimeSinceLastFlushInMinutes() {
            return timeSinceLastFlushInMinutes;
        }

        public void setTimeSinceLastFlushInMinutes(int timeSinceLastFlushInMinutes) {
            this.timeSinceLastFlushInMinutes = timeSinceLastFlushInMinutes;
        }
    }
}

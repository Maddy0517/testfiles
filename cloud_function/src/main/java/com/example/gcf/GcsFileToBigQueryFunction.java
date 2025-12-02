package com.example.gcf;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.InsertAllRequest;
import com.google.cloud.bigquery.InsertAllResponse;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.functions.CloudEventsFunction;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.events.cloud.storage.v1.StorageObjectData;
import com.google.protobuf.util.Timestamps;
import io.cloudevents.CloudEvent;
import com.google.protobuf.util.JsonFormat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Google Cloud Function that processes files uploaded to GCS and loads data to BigQuery.
 * 
 * This function:
 * 1. Triggers when a file is uploaded to a GCS bucket
 * 2. Extracts HUM code (e.g., HUM-100, HUM-200) from the filename
 * 3. Reads employee_id from the file content (CSV or JSON)
 * 4. Loads the data into a BigQuery table
 * 
 * Environment Variables:
 * - GCP_PROJECT_ID: Your GCP project ID
 * - BQ_DATASET_ID: BigQuery dataset ID
 * - BQ_TABLE_ID: BigQuery table ID
 */
public class GcsFileToBigQueryFunction implements CloudEventsFunction {

    private static final Logger logger = Logger.getLogger(GcsFileToBigQueryFunction.class.getName());

    // Configuration from environment variables
    private static final String PROJECT_ID = getEnvOrDefault("GCP_PROJECT_ID", "your-project-id");
    private static final String DATASET_ID = getEnvOrDefault("BQ_DATASET_ID", "your_dataset");
    private static final String TABLE_ID = getEnvOrDefault("BQ_TABLE_ID", "file_uploads");

    // Pattern to extract HUM codes like HUM-100, HUM-200, HUM-300
    private static final Pattern HUM_CODE_PATTERN = Pattern.compile("(HUM-\\d+)", Pattern.CASE_INSENSITIVE);

    // Supported file extensions
    private static final List<String> SUPPORTED_EXTENSIONS = List.of(".csv", ".json", ".txt");

    private final Storage storage;
    private final BigQuery bigQuery;
    private final FileParser fileParser;

    public GcsFileToBigQueryFunction() {
        this.storage = StorageOptions.getDefaultInstance().getService();
        this.bigQuery = BigQueryOptions.getDefaultInstance().getService();
        this.fileParser = new FileParser();
    }

    // Constructor for testing with injected dependencies
    public GcsFileToBigQueryFunction(Storage storage, BigQuery bigQuery, FileParser fileParser) {
        this.storage = storage;
        this.bigQuery = bigQuery;
        this.fileParser = fileParser;
    }

    @Override
    public void accept(CloudEvent event) throws Exception {
        logger.info("Received CloudEvent: " + event.getId());

        // Parse the CloudEvent data
        String cloudEventData = new String(event.getData().toBytes(), StandardCharsets.UTF_8);
        
        StorageObjectData.Builder builder = StorageObjectData.newBuilder();
        JsonFormat.parser().ignoringUnknownFields().merge(cloudEventData, builder);
        StorageObjectData storageObject = builder.build();

        String bucketName = storageObject.getBucket();
        String fileName = storageObject.getName();
        
        logger.info(String.format("Processing file: gs://%s/%s", bucketName, fileName));

        // Skip unsupported file types
        if (!isSupportedFileType(fileName)) {
            logger.info("Skipping unsupported file type: " + fileName);
            return;
        }

        // Extract HUM code from filename
        String humCode = extractHumCode(fileName);
        logger.info("Extracted HUM code: " + humCode);

        // Get upload timestamp
        String uploadDate = formatTimestamp(storageObject.getTimeCreated());

        // Read file content from GCS
        String fileContent = readFileContent(bucketName, fileName);
        if (fileContent == null || fileContent.isEmpty()) {
            logger.warning("File is empty or could not be read: " + fileName);
            return;
        }

        // Extract employee IDs from file content
        List<String> employeeIds = fileParser.extractEmployeeIds(fileContent, fileName);
        logger.info("Found " + employeeIds.size() + " employee IDs");

        // If no employee IDs found, create a placeholder record
        if (employeeIds.isEmpty()) {
            employeeIds.add("NONE");
        }

        // Insert records to BigQuery
        insertToBigQuery(employeeIds, uploadDate, fileName, humCode, bucketName);

        logger.info("Successfully processed file: " + fileName);
    }

    /**
     * Extracts HUM code (e.g., HUM-100, HUM-200) from the filename.
     */
    public String extractHumCode(String fileName) {
        Matcher matcher = HUM_CODE_PATTERN.matcher(fileName);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        return "UNKNOWN";
    }

    /**
     * Checks if the file has a supported extension.
     */
    private boolean isSupportedFileType(String fileName) {
        String lowerFileName = fileName.toLowerCase();
        return SUPPORTED_EXTENSIONS.stream().anyMatch(lowerFileName::endsWith);
    }

    /**
     * Reads file content from GCS bucket.
     */
    private String readFileContent(String bucketName, String fileName) {
        try {
            Blob blob = storage.get(bucketName, fileName);
            if (blob == null) {
                logger.warning("Blob not found: " + fileName);
                return null;
            }
            byte[] content = blob.getContent();
            return new String(content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error reading file from GCS", e);
            return null;
        }
    }

    /**
     * Formats the protobuf timestamp to a string.
     */
    private String formatTimestamp(com.google.protobuf.Timestamp timestamp) {
        if (timestamp == null || timestamp.getSeconds() == 0) {
            return Instant.now().atOffset(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos())
                .atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * Inserts records to BigQuery table.
     */
    private void insertToBigQuery(List<String> employeeIds, String uploadDate, 
                                   String fileName, String humCode, String bucketName) {
        
        TableId tableId = TableId.of(PROJECT_ID, DATASET_ID, TABLE_ID);
        String processedAt = Instant.now().atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        List<InsertAllRequest.RowToInsert> rows = new ArrayList<>();

        for (String employeeId : employeeIds) {
            Map<String, Object> rowContent = new HashMap<>();
            rowContent.put("employee_id", employeeId);
            rowContent.put("upload_date", uploadDate);
            rowContent.put("file_name", fileName);
            rowContent.put("hum_code", humCode);
            rowContent.put("bucket_name", bucketName);
            rowContent.put("processed_at", processedAt);

            rows.add(InsertAllRequest.RowToInsert.of(rowContent));
        }

        InsertAllRequest insertRequest = InsertAllRequest.newBuilder(tableId)
                .setRows(rows)
                .build();

        InsertAllResponse response = bigQuery.insertAll(insertRequest);

        if (response.hasErrors()) {
            response.getInsertErrors().forEach((key, errors) -> {
                errors.forEach(error -> 
                    logger.severe("Error inserting row " + key + ": " + error.getMessage()));
            });
            throw new RuntimeException("Failed to insert rows to BigQuery");
        }

        logger.info("Successfully inserted " + rows.size() + " rows to BigQuery");
    }

    /**
     * Gets environment variable or returns default value.
     */
    private static String getEnvOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }
}

package com.example.gcf;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.InsertAllRequest;
import com.google.cloud.bigquery.InsertAllResponse;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

import java.io.BufferedWriter;
import java.io.IOException;
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
 * Google Cloud Function that processes CSV files from GCS and loads data to BigQuery.
 * 
 * This function:
 * 1. Triggered manually via HTTP request or scheduled job
 * 2. Scans the configured GCS bucket for CSV files
 * 3. Extracts HUM code (e.g., HUM-100, HUM-200) from the filename
 * 4. Reads employee_id from the CSV file content
 * 5. Loads the data into a BigQuery table
 * 
 * Configuration is read from application.properties file.
 */
public class GcsFileToBigQueryFunction implements HttpFunction {

    private static final Logger logger = Logger.getLogger(GcsFileToBigQueryFunction.class.getName());

    // Pattern to extract HUM codes like HUM-100, HUM-200, HUM-300
    private static final Pattern HUM_CODE_PATTERN = Pattern.compile("(HUM-\\d+)", Pattern.CASE_INSENSITIVE);

    private final Storage storage;
    private final BigQuery bigQuery;
    private final CsvFileParser csvParser;
    private final ConfigProperties config;

    /**
     * Default constructor - loads configuration and initializes GCP clients.
     */
    public GcsFileToBigQueryFunction() {
        this.config = new ConfigProperties();
        this.storage = StorageOptions.newBuilder()
                .setProjectId(config.getProjectId())
                .build()
                .getService();
        this.bigQuery = BigQueryOptions.newBuilder()
                .setProjectId(config.getProjectId())
                .build()
                .getService();
        this.csvParser = new CsvFileParser();
        
        logger.info("Initialized with config - Project: " + config.getProjectId() + 
                    ", Bucket: " + config.getBucketName());
    }

    /**
     * Constructor for testing with injected dependencies.
     */
    public GcsFileToBigQueryFunction(Storage storage, BigQuery bigQuery, 
                                      CsvFileParser csvParser, ConfigProperties config) {
        this.storage = storage;
        this.bigQuery = bigQuery;
        this.csvParser = csvParser;
        this.config = config;
    }

    @Override
    public void service(HttpRequest request, HttpResponse response) throws IOException {
        logger.info("Cloud Function triggered - scanning bucket for CSV files");

        BufferedWriter writer = response.getWriter();
        response.setContentType("application/json");

        try {
            // Process all CSV files in the bucket
            ProcessingResult result = processAllFilesInBucket();

            // Build response
            String jsonResponse = buildJsonResponse(result);
            response.setStatusCode(200);
            writer.write(jsonResponse);

            logger.info("Processing completed - " + result.getTotalFilesProcessed() + 
                       " files, " + result.getTotalRowsInserted() + " rows inserted");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing files", e);
            response.setStatusCode(500);
            writer.write("{\"status\": \"error\", \"message\": \"" + 
                        escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * Scans the GCS bucket and processes all CSV files.
     *
     * @return ProcessingResult containing summary of processing
     */
    public ProcessingResult processAllFilesInBucket() {
        ProcessingResult result = new ProcessingResult();
        String bucketName = config.getBucketName();
        String prefix = config.getFilePrefix();

        logger.info("Scanning bucket: " + bucketName + " with prefix: " + prefix);

        // List all blobs in the bucket
        Iterable<Blob> blobs;
        if (prefix != null && !prefix.isEmpty()) {
            blobs = storage.list(bucketName, Storage.BlobListOption.prefix(prefix)).iterateAll();
        } else {
            blobs = storage.list(bucketName).iterateAll();
        }

        for (Blob blob : blobs) {
            String fileName = blob.getName();

            // Skip if not a CSV file
            if (!fileName.toLowerCase().endsWith(".csv")) {
                logger.fine("Skipping non-CSV file: " + fileName);
                continue;
            }

            // Skip directories
            if (fileName.endsWith("/")) {
                continue;
            }

            try {
                FileProcessingResult fileResult = processFile(blob, bucketName);
                result.addFileResult(fileResult);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error processing file: " + fileName, e);
                result.addError(fileName, e.getMessage());
            }
        }

        return result;
    }

    /**
     * Processes a single CSV file from GCS.
     *
     * @param blob       The GCS blob to process
     * @param bucketName The bucket name
     * @return FileProcessingResult with details of processing
     */
    private FileProcessingResult processFile(Blob blob, String bucketName) {
        String fileName = blob.getName();
        logger.info("Processing file: " + fileName);

        // Extract HUM code from filename
        String humCode = extractHumCode(fileName);
        logger.info("Extracted HUM code: " + humCode);

        // Get file upload timestamp
        Long createTime = blob.getCreateTime();
        String uploadDate = formatTimestamp(createTime);

        // Read file content
        String content = new String(blob.getContent(), StandardCharsets.UTF_8);
        if (content.isEmpty()) {
            logger.warning("File is empty: " + fileName);
            return new FileProcessingResult(fileName, humCode, 0, "File is empty");
        }

        // Parse CSV to extract employee IDs
        List<String> employeeIds = csvParser.extractEmployeeIds(content);
        logger.info("Found " + employeeIds.size() + " employee IDs in file: " + fileName);

        if (employeeIds.isEmpty()) {
            return new FileProcessingResult(fileName, humCode, 0, "No employee IDs found");
        }

        // Insert records to BigQuery
        int rowsInserted = insertToBigQuery(employeeIds, uploadDate, fileName, humCode, bucketName);

        return new FileProcessingResult(fileName, humCode, rowsInserted, "Success");
    }

    /**
     * Extracts HUM code (e.g., HUM-100, HUM-200) from the filename.
     *
     * @param fileName The name of the file
     * @return Extracted HUM code or "UNKNOWN" if not found
     */
    public String extractHumCode(String fileName) {
        Matcher matcher = HUM_CODE_PATTERN.matcher(fileName);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        return "UNKNOWN";
    }

    /**
     * Formats timestamp to string.
     */
    private String formatTimestamp(Long epochMillis) {
        if (epochMillis == null || epochMillis == 0) {
            return Instant.now().atOffset(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        return Instant.ofEpochMilli(epochMillis)
                .atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * Inserts records to BigQuery table.
     *
     * @return Number of rows inserted
     */
    private int insertToBigQuery(List<String> employeeIds, String uploadDate,
                                  String fileName, String humCode, String bucketName) {

        TableId tableId = TableId.of(config.getProjectId(), config.getDatasetId(), config.getTableId());
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
            response.getInsertErrors().forEach((key, errors) ->
                    errors.forEach(error ->
                            logger.severe("Error inserting row " + key + ": " + error.getMessage())));
            throw new RuntimeException("Failed to insert some rows to BigQuery");
        }

        logger.info("Inserted " + rows.size() + " rows to BigQuery for file: " + fileName);
        return rows.size();
    }

    /**
     * Builds JSON response string.
     */
    private String buildJsonResponse(ProcessingResult result) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"status\": \"").append(result.hasErrors() ? "partial_success" : "success").append("\",\n");
        json.append("  \"totalFilesProcessed\": ").append(result.getTotalFilesProcessed()).append(",\n");
        json.append("  \"totalRowsInserted\": ").append(result.getTotalRowsInserted()).append(",\n");
        json.append("  \"files\": [\n");

        List<FileProcessingResult> fileResults = result.getFileResults();
        for (int i = 0; i < fileResults.size(); i++) {
            FileProcessingResult fr = fileResults.get(i);
            json.append("    {\n");
            json.append("      \"fileName\": \"").append(escapeJson(fr.getFileName())).append("\",\n");
            json.append("      \"humCode\": \"").append(escapeJson(fr.getHumCode())).append("\",\n");
            json.append("      \"rowsInserted\": ").append(fr.getRowsInserted()).append(",\n");
            json.append("      \"status\": \"").append(escapeJson(fr.getStatus())).append("\"\n");
            json.append("    }").append(i < fileResults.size() - 1 ? "," : "").append("\n");
        }

        json.append("  ]\n");
        json.append("}");

        return json.toString();
    }

    /**
     * Escapes special characters for JSON string.
     */
    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

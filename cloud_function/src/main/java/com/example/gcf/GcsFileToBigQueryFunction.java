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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Google Cloud Function - Processes CSV files from GCS bucket and loads to BigQuery.
 * 
 * Trigger: HTTP (manual or scheduled job)
 * Configuration: Reads from config/application-dev.properties file
 */
public class GcsFileToBigQueryFunction implements HttpFunction {

    private static final Logger logger = Logger.getLogger(GcsFileToBigQueryFunction.class.getName());
    private static final String CONFIG_FILE = "config/application-dev.properties";
    private static final Pattern HUM_CODE_PATTERN = Pattern.compile("(HUM-\\d+)", Pattern.CASE_INSENSITIVE);

    private final CsvFileParser csvParser = new CsvFileParser();

    public GcsFileToBigQueryFunction() {
        logger.info("GcsFileToBigQueryFunction initialized");
    }

    @Override
    public void service(HttpRequest request, HttpResponse response) throws Exception {
        logger.info("Function triggered - scanning bucket for CSV files");
        BufferedWriter writer = response.getWriter();
        response.setContentType("application/json");

        try {
            // Load configuration
            Properties config = loadConfig();
            String projectId = config.getProperty("gcp.project.id");
            String bucketName = config.getProperty("gcs.bucket.name");
            String datasetId = config.getProperty("bigquery.dataset.id");
            String tableId = config.getProperty("bigquery.table.id");
            String filePrefix = config.getProperty("gcs.file.prefix", "");

            logger.info("Config loaded - Project: " + projectId + ", Bucket: " + bucketName + 
                       ", Dataset: " + datasetId + ", Table: " + tableId);

            // Initialize GCP clients
            Storage storage = StorageOptions.newBuilder().setProjectId(projectId).build().getService();
            BigQuery bigQuery = BigQueryOptions.newBuilder().setProjectId(projectId).build().getService();

            int totalFiles = 0;
            int totalRows = 0;
            List<Map<String, Object>> fileResults = new ArrayList<>();

            // List all blobs in bucket
            Iterable<Blob> blobs = (filePrefix != null && !filePrefix.isEmpty())
                    ? storage.list(bucketName, Storage.BlobListOption.prefix(filePrefix)).iterateAll()
                    : storage.list(bucketName).iterateAll();

            for (Blob blob : blobs) {
                String fileName = blob.getName();

                // Skip non-CSV files and directories
                if (!fileName.toLowerCase().endsWith(".csv") || fileName.endsWith("/")) {
                    continue;
                }

                try {
                    logger.info("Processing file: " + fileName);

                    // Extract HUM code from filename
                    String humCode = extractHumCode(fileName);
                    logger.info("Extracted HUM code: " + humCode);

                    // Get upload date as epoch seconds (for BigQuery TIMESTAMP)
                    long uploadDateEpoch = getUploadDateEpoch(blob.getCreateTime());

                    // Read and parse CSV content
                    String content = new String(blob.getContent(), StandardCharsets.UTF_8);
                    List<String> employeeIds = csvParser.extractEmployeeIds(content);

                    logger.info("Found " + employeeIds.size() + " employee IDs in file: " + fileName);

                    if (employeeIds.isEmpty()) {
                        logger.info("No employee IDs found in: " + fileName);
                        continue;
                    }

                    // Insert to BigQuery
                    int rowsInserted = insertToBigQuery(bigQuery, projectId, datasetId, tableId,
                            employeeIds, uploadDateEpoch, fileName, humCode, bucketName);

                    totalFiles++;
                    totalRows += rowsInserted;

                    Map<String, Object> result = new HashMap<>();
                    result.put("file", fileName);
                    result.put("humCode", humCode);
                    result.put("rows", rowsInserted);
                    fileResults.add(result);

                    logger.info("Processed: " + fileName + " | HUM: " + humCode + " | Rows: " + rowsInserted);

                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error processing file: " + fileName + " - " + e.getMessage(), e);
                }
            }

            // Build response
            String jsonResponse = buildResponse("success", totalFiles, totalRows, fileResults);
            response.setStatusCode(200);
            writer.write(jsonResponse);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error: " + e.getMessage(), e);
            response.setStatusCode(500);
            writer.write("{\"status\":\"error\",\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * Loads configuration from properties file.
     */
    private Properties loadConfig() {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (is == null) {
                throw new RuntimeException("Config file not found: " + CONFIG_FILE);
            }
            props.load(is);
            return props;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts HUM code (e.g., HUM-100, HUM-200) from filename.
     */
    private String extractHumCode(String fileName) {
        Matcher matcher = HUM_CODE_PATTERN.matcher(fileName);
        return matcher.find() ? matcher.group(1).toUpperCase() : "UNKNOWN";
    }

    /**
     * Gets upload date as epoch seconds for BigQuery TIMESTAMP.
     */
    private long getUploadDateEpoch(Long epochMillis) {
        if (epochMillis == null || epochMillis == 0) {
            return Instant.now().getEpochSecond();
        }
        return epochMillis / 1000; // Convert millis to seconds
    }

    /**
     * Inserts employee records to BigQuery.
     */
    private int insertToBigQuery(BigQuery bigQuery, String projectId, String datasetId,
                                  String tableId, List<String> employeeIds, long uploadDateEpoch,
                                  String fileName, String humCode, String bucketName) {

        TableId table = TableId.of(projectId, datasetId, tableId);
        long processedAtEpoch = Instant.now().getEpochSecond();

        List<InsertAllRequest.RowToInsert> rows = new ArrayList<>();
        for (String empId : employeeIds) {
            Map<String, Object> row = new HashMap<>();
            row.put("employee_id", empId);
            row.put("upload_date", uploadDateEpoch);      // epoch seconds for TIMESTAMP
            row.put("file_name", fileName);
            row.put("hum_code", humCode);
            row.put("bucket_name", bucketName);
            row.put("processed_at", processedAtEpoch);    // epoch seconds for TIMESTAMP
            rows.add(InsertAllRequest.RowToInsert.of(row));
        }

        logger.info("Inserting " + rows.size() + " rows to " + projectId + "." + datasetId + "." + tableId);

        InsertAllResponse resp = bigQuery.insertAll(InsertAllRequest.newBuilder(table).setRows(rows).build());
        
        if (resp.hasErrors()) {
            // Log detailed errors
            StringBuilder errorMsg = new StringBuilder("BigQuery insert errors:\n");
            resp.getInsertErrors().forEach((index, errors) -> {
                errors.forEach(error -> {
                    errorMsg.append("Row ").append(index).append(": ")
                           .append(error.getMessage())
                           .append(" (reason: ").append(error.getReason()).append(")\n");
                });
            });
            logger.severe(errorMsg.toString());
            throw new RuntimeException("BigQuery insert failed: " + errorMsg);
        }
        
        return rows.size();
    }

    /**
     * Builds JSON response.
     */
    private String buildResponse(String status, int files, int rows, List<Map<String, Object>> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"status\":\"").append(status).append("\",");
        sb.append("\"filesProcessed\":").append(files).append(",");
        sb.append("\"rowsInserted\":").append(rows).append(",");
        sb.append("\"files\":[");
        for (int i = 0; i < results.size(); i++) {
            Map<String, Object> r = results.get(i);
            sb.append("{\"file\":\"").append(escapeJson(String.valueOf(r.get("file")))).append("\",");
            sb.append("\"humCode\":\"").append(escapeJson(String.valueOf(r.get("humCode")))).append("\",");
            sb.append("\"rows\":").append(r.get("rows")).append("}");
            if (i < results.size() - 1) sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
    }

    /**
     * Escapes special characters for JSON.
     */
    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

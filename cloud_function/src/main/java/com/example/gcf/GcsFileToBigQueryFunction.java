package com.example.gcf;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.FormatOptions;
import com.google.cloud.bigquery.Job;
import com.google.cloud.bigquery.JobInfo;
import com.google.cloud.bigquery.LoadJobConfiguration;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

import java.io.BufferedWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Google Cloud Function - Processes CSV files from GCS bucket and loads to BigQuery.
 * Uses BigQuery Load Job (batch) instead of streaming insert.
 */
public class GcsFileToBigQueryFunction implements HttpFunction {

    private static final Logger logger = Logger.getLogger(GcsFileToBigQueryFunction.class.getName());
    private static final String CONFIG_FILE = "config/application-dev.properties";
    private static final Pattern HUM_CODE_PATTERN = Pattern.compile("(HUM-\\d+)", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
            String tempFolder = config.getProperty("gcs.temp.folder", "temp_bq_load");

            logger.info("Config loaded - Project: " + projectId + ", Bucket: " + bucketName);

            // Initialize GCP clients
            Storage storage = StorageOptions.newBuilder().setProjectId(projectId).build().getService();
            BigQuery bigQuery = BigQueryOptions.newBuilder().setProjectId(projectId).build().getService();

            int totalFiles = 0;
            int totalRows = 0;
            List<Map<String, Object>> fileResults = new ArrayList<>();
            List<String> allCsvRows = new ArrayList<>();

            // List all blobs in bucket
            Iterable<Blob> blobs = (filePrefix != null && !filePrefix.isEmpty())
                    ? storage.list(bucketName, Storage.BlobListOption.prefix(filePrefix)).iterateAll()
                    : storage.list(bucketName).iterateAll();

            for (Blob blob : blobs) {
                String fileName = blob.getName();

                // Skip non-CSV files, directories, and temp folder
                if (!fileName.toLowerCase().endsWith(".csv") || fileName.endsWith("/") 
                    || fileName.startsWith(tempFolder)) {
                    continue;
                }

                try {
                    logger.info("Processing file: " + fileName);

                    // Extract HUM code from filename
                    String humCode = extractHumCode(fileName);

                    // Get upload date
                    String uploadDate = formatTimestamp(blob.getCreateTime());
                    String processedAt = formatTimestamp(System.currentTimeMillis());

                    // Read and parse CSV content
                    String content = new String(blob.getContent(), StandardCharsets.UTF_8);
                    List<String> employeeIds = csvParser.extractEmployeeIds(content);

                    logger.info("Found " + employeeIds.size() + " employee IDs in file: " + fileName);

                    if (employeeIds.isEmpty()) {
                        continue;
                    }

                    // Build CSV rows for BigQuery load
                    for (String empId : employeeIds) {
                        String csvRow = String.join(",",
                                escapeCSV(empId),
                                uploadDate,
                                escapeCSV(fileName),
                                escapeCSV(humCode),
                                escapeCSV(bucketName),
                                processedAt
                        );
                        allCsvRows.add(csvRow);
                    }

                    totalFiles++;
                    totalRows += employeeIds.size();

                    Map<String, Object> result = new HashMap<>();
                    result.put("file", fileName);
                    result.put("humCode", humCode);
                    result.put("rows", employeeIds.size());
                    fileResults.add(result);

                    logger.info("Processed: " + fileName + " | HUM: " + humCode + " | Rows: " + employeeIds.size());

                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error processing file: " + fileName, e);
                }
            }

            // Load all rows to BigQuery in one batch
            if (!allCsvRows.isEmpty()) {
                loadToBigQuery(storage, bigQuery, projectId, bucketName, tempFolder, 
                               datasetId, tableId, allCsvRows);
                logger.info("Successfully loaded " + totalRows + " rows to BigQuery");
            } else {
                logger.info("No records to load");
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
     * Loads data to BigQuery using Load Job (batch insert).
     */
    private void loadToBigQuery(Storage storage, BigQuery bigQuery, String projectId,
                                 String bucketName, String tempFolder, String datasetId,
                                 String tableId, List<String> csvRows) throws Exception {

        // Create CSV content with header
        StringBuilder csvContent = new StringBuilder();
        csvContent.append("employee_id,upload_date,file_name,hum_code,bucket_name,processed_at\n");
        for (String row : csvRows) {
            csvContent.append(row).append("\n");
        }

        // Upload temp CSV file to GCS
        String tempFileName = tempFolder + "/load_" + UUID.randomUUID() + ".csv";
        BlobId blobId = BlobId.of(bucketName, tempFileName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("text/csv").build();
        storage.create(blobInfo, csvContent.toString().getBytes(StandardCharsets.UTF_8));
        
        logger.info("Created temp file: gs://" + bucketName + "/" + tempFileName);

        try {
            // Configure load job
            TableId table = TableId.of(projectId, datasetId, tableId);
            String sourceUri = "gs://" + bucketName + "/" + tempFileName;

            LoadJobConfiguration loadConfig = LoadJobConfiguration.newBuilder(table, sourceUri)
                    .setFormatOptions(FormatOptions.csv())
                    .setSkipLeadingRows(1)  // Skip header row
                    .setWriteDisposition(JobInfo.WriteDisposition.WRITE_APPEND)
                    .build();

            // Run load job
            Job job = bigQuery.create(JobInfo.of(loadConfig));
            logger.info("Started BigQuery load job: " + job.getJobId().getJob());

            // Wait for job to complete
            job = job.waitFor();

            if (job.isDone()) {
                if (job.getStatus().getError() == null) {
                    logger.info("BigQuery load job completed successfully");
                } else {
                    throw new RuntimeException("BigQuery load failed: " + job.getStatus().getError().getMessage());
                }
            } else {
                throw new RuntimeException("BigQuery load job did not complete");
            }

        } finally {
            // Delete temp file
            storage.delete(blobId);
            logger.info("Deleted temp file: " + tempFileName);
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
     * Extracts HUM code from filename.
     */
    private String extractHumCode(String fileName) {
        Matcher matcher = HUM_CODE_PATTERN.matcher(fileName);
        return matcher.find() ? matcher.group(1).toUpperCase() : "UNKNOWN";
    }

    /**
     * Formats timestamp for BigQuery.
     */
    private String formatTimestamp(Long epochMillis) {
        if (epochMillis == null || epochMillis == 0) {
            epochMillis = System.currentTimeMillis();
        }
        return Instant.ofEpochMilli(epochMillis)
                .atOffset(ZoneOffset.UTC)
                .format(TIMESTAMP_FORMAT);
    }

    /**
     * Escapes value for CSV.
     */
    private String escapeCSV(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
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

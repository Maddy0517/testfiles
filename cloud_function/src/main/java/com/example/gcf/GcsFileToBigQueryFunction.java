package com.equinix.it.platform.helix.workdaydataretentioncloudfunctions;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
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
    private static final String CONFIG_FILE = "config/dev.properties";
    private static final Pattern HUM_CODE_PATTERN = Pattern.compile("(HUM-\\d+)", Pattern.CASE_INSENSITIVE);

    private String projectId;
    private String bucketName;
    private String datasetId;
    private String tableId;
    private String filePrefix;
    private String tempFolder;

    private Storage storage;
    private BigQuery bigQuery;
    private CsvFileParser csvParser;

    // Constructor: Initializes GCP clients and loads configuration from properties file
    public GcsFileToBigQueryFunction() {
        loadConfig();
        this.storage = StorageOptions.newBuilder().setProjectId(projectId).build().getService();
        this.bigQuery = BigQueryOptions.newBuilder().setProjectId(projectId).build().getService();
        this.csvParser = new CsvFileParser();
        logger.info("Initialized - Project: " + projectId + ", Bucket: " + bucketName);
    }

    // Loads configuration values from properties file
    private void loadConfig() {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (is == null) {
                throw new RuntimeException("Config file not found: " + CONFIG_FILE);
            }
            props.load(is);
            this.projectId = props.getProperty("gcp.project.id");
            this.bucketName = props.getProperty("gcs.bucket.name");
            this.datasetId = props.getProperty("bigquery.dataset.id");
            this.tableId = props.getProperty("bigquery.table.id");
            this.filePrefix = props.getProperty("gcs.file.prefix", "");
            this.tempFolder = props.getProperty("gcs.temp.folder", "temp_bq_load");
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config: " + e.getMessage(), e);
        }
    }

    // Main entry point: Scans GCS bucket for CSV files, extracts data, and loads to BigQuery
    @Override
    public void service(HttpRequest request, HttpResponse response) throws Exception {
        logger.info("Function triggered - scanning bucket for CSV files");
        BufferedWriter writer = response.getWriter();
        response.setContentType("application/json");

        int totalFiles = 0;
        int totalRows = 0;
        List<Map<String, Object>> fileResults = new ArrayList<>();
        List<DataRetentionAuditRecord> allRecords = new ArrayList<>();

        try {
            // List all files in the GCS bucket
            Iterable<Blob> blobs = (filePrefix != null && !filePrefix.isEmpty())
                    ? storage.list(bucketName, Storage.BlobListOption.prefix(filePrefix)).iterateAll()
                    : storage.list(bucketName).iterateAll();

            // Process each CSV file in the bucket
            for (Blob blob : blobs) {
                String fileName = blob.getName();

                // Skip non-CSV files, directories, and temp folder
                if (!fileName.toLowerCase().endsWith(".csv") || fileName.endsWith("/")
                        || fileName.startsWith(tempFolder)) {
                    continue;
                }

                try {
                    // Extract HUM code from filename
                    String humCode = extractHumCode(fileName);
                    
                    // Get timestamps
                    String fileUploadDate = DataRetentionAuditRecord.formatTimestamp(blob.getCreateTime());
                    String deletionDate = DataRetentionAuditRecord.formatTimestamp(System.currentTimeMillis());

                    // Read and parse CSV content
                    String content = new String(blob.getContent(), StandardCharsets.UTF_8);
                    List<String> employeeIds = csvParser.extractEmployeeIds(content);

                    if (employeeIds.isEmpty()) {
                        logger.info("No employee IDs found in: " + fileName);
                        continue;
                    }

                    // Create POJO records for each employee ID
                    for (String empId : employeeIds) {
                        DataRetentionAuditRecord record = DataRetentionAuditRecord.builder()
                                .employeeId(empId)
                                .retentionPolicyName(humCode)
                                .sourceFileName(fileName)
                                .fileUploadDate(fileUploadDate)
                                .deletionDate(deletionDate)
                                .build();
                        allRecords.add(record);
                    }

                    totalFiles++;
                    totalRows += employeeIds.size();

                    // Store result for response
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

            // Load all records to BigQuery
            if (!allRecords.isEmpty()) {
                loadToBigQuery(allRecords);
                logger.info("Successfully loaded " + totalRows + " rows to BigQuery");
            } else {
                logger.info("No records to load");
            }

            // Return success response
            String jsonResponse = buildResponse("success", totalFiles, totalRows, fileResults);
            response.setStatusCode(200);
            writer.write(jsonResponse);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing files", e);
            response.setStatusCode(500);
            writer.write("{\"status\":\"error\",\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    // Creates temp CSV file from POJO records and loads to BigQuery using batch load job
    private void loadToBigQuery(List<DataRetentionAuditRecord> records) throws Exception {
        // Build CSV content from POJO records
        StringBuilder csvContent = new StringBuilder();
        for (DataRetentionAuditRecord record : records) {
            csvContent.append(record.toCsvRow()).append("\n");
        }

        // Log sample for debugging
        if (!records.isEmpty()) {
            logger.info("Sample record: " + records.get(0));
            logger.info("Sample CSV row: " + records.get(0).toCsvRow());
        }

        // Upload temp CSV file to GCS
        String tempFileName = tempFolder + "/load_" + UUID.randomUUID() + ".csv";
        BlobId blobId = BlobId.of(bucketName, tempFileName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("text/csv").build();
        storage.create(blobInfo, csvContent.toString().getBytes(StandardCharsets.UTF_8));

        logger.info("Created temp file: gs://" + bucketName + "/" + tempFileName);

        try {
            // Configure BigQuery load job using schema from config class
            TableId table = TableId.of(projectId, datasetId, tableId);
            String sourceUri = "gs://" + bucketName + "/" + tempFileName;

            LoadJobConfiguration loadConfig = LoadJobConfiguration.newBuilder(table, sourceUri)
                    .setFormatOptions(BigQuerySchemaConfig.getCsvOptions(false))
                    .setSchema(BigQuerySchemaConfig.getSchema())
                    .setWriteDisposition(JobInfo.WriteDisposition.WRITE_APPEND)
                    .build();

            // Execute load job
            Job job = bigQuery.create(JobInfo.of(loadConfig));
            logger.info("Started BigQuery load job: " + job.getJobId().getJob());

            // Wait for completion
            job = job.waitFor();

            // Check job status
            if (job.isDone()) {
                if (job.getStatus().getError() == null) {
                    logger.info("BigQuery load job completed successfully");
                } else {
                    logger.severe("BigQuery error: " + job.getStatus().getError());
                    if (job.getStatus().getExecutionErrors() != null) {
                        job.getStatus().getExecutionErrors().forEach(err -> 
                            logger.severe("Execution error: " + err.getMessage()));
                    }
                    throw new RuntimeException("BigQuery load failed: " + job.getStatus().getError().getMessage());
                }
            }

        } finally {
            // Clean up temp file
            storage.delete(blobId);
            logger.info("Deleted temp file: " + tempFileName);
        }
    }

    // Extracts HUM code (e.g., HUM-100) from filename using regex
    private String extractHumCode(String fileName) {
        Matcher matcher = HUM_CODE_PATTERN.matcher(fileName);
        return matcher.find() ? matcher.group(1).toUpperCase() : "UNKNOWN";
    }

    // Escapes special characters for JSON string values
    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // Builds JSON response with processing summary
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
}

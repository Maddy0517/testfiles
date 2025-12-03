package com.equinix.it.platform.helix.workdaydataretentioncloudfunctions;

import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.CsvOptions;

/**
 * BigQuery schema configuration for data retention audit table.
 * Centralizes schema definition - change here if table structure changes.
 */
public class BigQuerySchemaConfig {

    // CSV header matching BigQuery table column order
    public static final String CSV_HEADER = 
            "employee_id,retention_policy_name,source_file_name,file_upload_date,deletion_date";

    // Private constructor - utility class
    private BigQuerySchemaConfig() {
    }

    // Returns BigQuery schema matching the table structure
    public static Schema getSchema() {
        return Schema.of(
                Field.of("employee_id", StandardSQLTypeName.STRING),
                Field.of("retention_policy_name", StandardSQLTypeName.STRING),
                Field.of("source_file_name", StandardSQLTypeName.STRING),
                Field.of("file_upload_date", StandardSQLTypeName.TIMESTAMP),
                Field.of("deletion_date", StandardSQLTypeName.TIMESTAMP)
        );
    }

    // Returns CSV options for BigQuery load job
    public static CsvOptions getCsvOptions(boolean hasHeader) {
        return CsvOptions.newBuilder()
                .setSkipLeadingRows(hasHeader ? 1 : 0)
                .build();
    }

    // Returns column names as array (useful for validation)
    public static String[] getColumnNames() {
        return new String[]{
                "employee_id",
                "retention_policy_name",
                "source_file_name",
                "file_upload_date",
                "deletion_date"
        };
    }

    // Returns number of columns in schema
    public static int getColumnCount() {
        return 5;
    }
}

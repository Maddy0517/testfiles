package com.example.gcf;

/**
 * Represents the result of processing a single file.
 */
public class FileProcessingResult {

    private final String fileName;
    private final String humCode;
    private final int rowsInserted;
    private final String status;

    /**
     * Creates a new FileProcessingResult.
     *
     * @param fileName     The name of the processed file
     * @param humCode      The extracted HUM code
     * @param rowsInserted Number of rows inserted to BigQuery
     * @param status       Status message (Success, Error, etc.)
     */
    public FileProcessingResult(String fileName, String humCode, int rowsInserted, String status) {
        this.fileName = fileName;
        this.humCode = humCode;
        this.rowsInserted = rowsInserted;
        this.status = status;
    }

    /**
     * Gets the file name.
     *
     * @return File name
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Gets the extracted HUM code.
     *
     * @return HUM code
     */
    public String getHumCode() {
        return humCode;
    }

    /**
     * Gets the number of rows inserted.
     *
     * @return Number of rows inserted
     */
    public int getRowsInserted() {
        return rowsInserted;
    }

    /**
     * Gets the status message.
     *
     * @return Status message
     */
    public String getStatus() {
        return status;
    }

    /**
     * Checks if the file was processed successfully.
     *
     * @return true if successful
     */
    public boolean isSuccess() {
        return "Success".equalsIgnoreCase(status);
    }

    @Override
    public String toString() {
        return "FileProcessingResult{" +
                "fileName='" + fileName + '\'' +
                ", humCode='" + humCode + '\'' +
                ", rowsInserted=" + rowsInserted +
                ", status='" + status + '\'' +
                '}';
    }
}

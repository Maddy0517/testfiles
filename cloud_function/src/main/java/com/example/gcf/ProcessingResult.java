package com.example.gcf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the overall result of processing all files in the bucket.
 */
public class ProcessingResult {

    private final List<FileProcessingResult> fileResults;
    private final Map<String, String> errors;
    private int totalRowsInserted;

    public ProcessingResult() {
        this.fileResults = new ArrayList<>();
        this.errors = new HashMap<>();
        this.totalRowsInserted = 0;
    }

    /**
     * Adds a file processing result.
     *
     * @param result The file processing result
     */
    public void addFileResult(FileProcessingResult result) {
        fileResults.add(result);
        totalRowsInserted += result.getRowsInserted();
    }

    /**
     * Adds an error for a file.
     *
     * @param fileName     The file name
     * @param errorMessage The error message
     */
    public void addError(String fileName, String errorMessage) {
        errors.put(fileName, errorMessage);
    }

    /**
     * Gets the total number of files processed.
     *
     * @return Number of files processed
     */
    public int getTotalFilesProcessed() {
        return fileResults.size();
    }

    /**
     * Gets the total number of rows inserted across all files.
     *
     * @return Total rows inserted
     */
    public int getTotalRowsInserted() {
        return totalRowsInserted;
    }

    /**
     * Gets the list of file processing results.
     *
     * @return List of file results
     */
    public List<FileProcessingResult> getFileResults() {
        return new ArrayList<>(fileResults);
    }

    /**
     * Gets the map of file errors.
     *
     * @return Map of file name to error message
     */
    public Map<String, String> getErrors() {
        return new HashMap<>(errors);
    }

    /**
     * Checks if there were any errors during processing.
     *
     * @return true if there were errors
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    @Override
    public String toString() {
        return "ProcessingResult{" +
                "filesProcessed=" + getTotalFilesProcessed() +
                ", totalRowsInserted=" + totalRowsInserted +
                ", errors=" + errors.size() +
                '}';
    }
}

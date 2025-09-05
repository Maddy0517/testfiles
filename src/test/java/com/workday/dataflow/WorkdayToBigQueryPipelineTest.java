package com.workday.dataflow;

import org.apache.beam.sdk.testing.PAssert;
import org.apache.beam.sdk.testing.TestPipeline;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.values.PCollection;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.List;

/**
 * Tests for WorkdayToBigQueryPipeline.
 */
@RunWith(JUnit4.class)
public class WorkdayToBigQueryPipelineTest {

    @Rule
    public final transient TestPipeline pipeline = TestPipeline.create();

    @Test
    public void testWorkerDataCreation() {
        // Test data creation and basic functionality
        WorkerData worker1 = new WorkerData();
        worker1.setWorkerId("WD-001");
        worker1.setEmployeeId("EMP-001");
        worker1.setFirstName("John");
        worker1.setLastName("Doe");
        worker1.setEmail("john.doe@company.com");
        worker1.setJobTitle("Software Engineer");
        worker1.setDepartment("Engineering");
        worker1.setStatus("Active");
        worker1.setSalary(75000.0);

        WorkerData worker2 = new WorkerData();
        worker2.setWorkerId("WD-002");
        worker2.setEmployeeId("EMP-002");
        worker2.setFirstName("Jane");
        worker2.setLastName("Smith");
        worker2.setEmail("jane.smith@company.com");
        worker2.setJobTitle("Product Manager");
        worker2.setDepartment("Product");
        worker2.setStatus("Active");
        worker2.setSalary(85000.0);

        List<WorkerData> inputWorkers = Arrays.asList(worker1, worker2);

        PCollection<WorkerData> input = pipeline.apply(Create.of(inputWorkers));

        // Test that the data passes through correctly
        PAssert.that(input).containsInAnyOrder(inputWorkers);

        pipeline.run().waitUntilFinish();
    }

    @Test
    public void testWorkerDataTransform() {
        // Create test worker data
        WorkerData worker = new WorkerData();
        worker.setWorkerId("WD-TEST");
        worker.setEmployeeId("EMP-TEST");
        worker.setFirstName("Test");
        worker.setLastName("User");
        worker.setEmail("test.user@company.com");
        worker.setJobTitle("Test Engineer");
        worker.setDepartment("QA");
        worker.setHireDate("2020-01-15");
        worker.setStatus("Active");
        worker.setSalary(70000.0);
        worker.setLocation("New York");
        worker.setManagerId("MGR-001");
        worker.setLastModified(System.currentTimeMillis());

        PCollection<WorkerData> input = pipeline.apply(Create.of(worker));
        
        // Apply the transformation
        PCollection<com.google.api.services.bigquery.model.TableRow> output = 
            input.apply(new WorkerDataTransform());

        // Verify the output contains expected fields
        PAssert.that(output)
            .satisfies(tableRows -> {
                for (com.google.api.services.bigquery.model.TableRow row : tableRows) {
                    // Check required fields
                    assert row.get("worker_id").equals("WD-TEST");
                    assert row.get("employee_id").equals("EMP-TEST");
                    assert row.get("full_name").equals("Test User");
                    assert row.get("salary_band") != null;
                    assert row.get("processed_timestamp") != null;
                }
                return null;
            });

        pipeline.run().waitUntilFinish();
    }

    @Test
    public void testDateFilterValidation() {
        // Test that date filter validation works correctly
        WorkdayToBigQueryOptions options = PipelineOptionsFactory.as(WorkdayToBigQueryOptions.class);
        
        // Set valid date filters
        options.setEffectiveFromDate("2024-01-01");
        options.setEffectiveToDate("2024-12-31");
        options.setSoapApiUrl("https://test.workday.com");
        options.setUsername("test@tenant");
        options.setPassword("password");
        
        // This should not throw an exception
        try {
            WorkdayApiClient client = new WorkdayApiClient(options);
            // If we get here, validation passed
            assert true;
        } catch (Exception e) {
            // Should not reach here with valid dates
            assert false : "Valid date filters should not throw exception: " + e.getMessage();
        }
    }

    @Test
    public void testInvalidDateFilterValidation() {
        // Test that invalid date formats are rejected
        WorkdayToBigQueryOptions options = PipelineOptionsFactory.as(WorkdayToBigQueryOptions.class);
        
        // Set invalid date format
        options.setEffectiveFromDate("01-01-2024"); // Wrong format
        options.setEffectiveToDate("2024-12-31");
        options.setSoapApiUrl("https://test.workday.com");
        options.setUsername("test@tenant");
        options.setPassword("password");
        
        // This should throw an exception
        try {
            WorkdayApiClient client = new WorkdayApiClient(options);
            assert false : "Invalid date format should throw exception";
        } catch (IllegalArgumentException e) {
            // Expected behavior
            assert e.getMessage().contains("Invalid effective from date format");
        } catch (Exception e) {
            assert false : "Should throw IllegalArgumentException, not " + e.getClass().getSimpleName();
        }
    }
}
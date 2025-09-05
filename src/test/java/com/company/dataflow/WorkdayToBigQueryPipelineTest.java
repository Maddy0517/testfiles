package com.company.dataflow;

import com.company.dataflow.model.WorkdayEmployee;
import com.company.dataflow.transform.TransformEmployeeData;
import com.google.api.services.bigquery.model.TableRow;
import org.apache.beam.sdk.testing.PAssert;
import org.apache.beam.sdk.testing.TestPipeline;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.values.PCollection;
import org.junit.Rule;
import org.junit.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Test class for WorkdayToBigQueryPipeline.
 */
public class WorkdayToBigQueryPipelineTest {
    
    @Rule
    public final transient TestPipeline pipeline = TestPipeline.create();
    
    @Test
    public void testTransformEmployeeData() {
        // Create test data
        List<WorkdayEmployee> employees = Arrays.asList(
            createTestEmployee("EMP001", "John", "Doe", "john.doe@company.com"),
            createTestEmployee("EMP002", "Jane", "Smith", "jane.smith@company.com")
        );
        
        // Apply transformation
        PCollection<TableRow> results = pipeline
            .apply("Create Test Data", Create.of(employees))
            .apply("Transform Data", new TransformEmployeeData());
        
        // Assert results
        PAssert.that(results)
            .satisfies(rows -> {
                int count = 0;
                for (TableRow row : rows) {
                    count++;
                    
                    // Verify required fields
                    assert row.get("employee_id") != null;
                    assert row.get("data_source").equals("workday_soap_api");
                    assert row.get("ingestion_timestamp") != null;
                    assert row.get("is_complete") != null;
                }
                
                assert count == 2; // Should have 2 records
                return null;
            });
        
        pipeline.run();
    }
    
    @Test
    public void testTransformEmployeeDataWithNullValues() {
        // Create test data with null values
        WorkdayEmployee employee = new WorkdayEmployee();
        employee.setEmployeeId("EMP003");
        employee.setFirstName(null);
        employee.setLastName("Test");
        
        List<WorkdayEmployee> employees = Arrays.asList(employee);
        
        // Apply transformation
        PCollection<TableRow> results = pipeline
            .apply("Create Test Data", Create.of(employees))
            .apply("Transform Data", new TransformEmployeeData());
        
        // Assert results handle null values correctly
        PAssert.that(results)
            .satisfies(rows -> {
                for (TableRow row : rows) {
                    assert row.get("employee_id").equals("EMP003");
                    assert row.get("first_name") == null;
                    assert row.get("last_name").equals("Test");
                }
                return null;
            });
        
        pipeline.run();
    }
    
    private WorkdayEmployee createTestEmployee(String id, String firstName, String lastName, String email) {
        WorkdayEmployee employee = new WorkdayEmployee(id, firstName, lastName, email);
        employee.setDepartment("Engineering");
        employee.setJobTitle("Software Engineer");
        employee.setStatus("Active");
        employee.setHireDate("2023-01-01T00:00:00Z");
        employee.setSalary(75000.0);
        employee.setIngestionTimestamp(Instant.now());
        return employee;
    }
}
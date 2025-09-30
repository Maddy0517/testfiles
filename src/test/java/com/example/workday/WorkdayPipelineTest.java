package com.example.workday;

import com.example.workday.model.Employee;
import com.example.workday.transform.EmployeeTransform;
import com.google.api.services.bigquery.model.TableRow;
import org.apache.beam.sdk.testing.PAssert;
import org.apache.beam.sdk.testing.TestPipeline;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.PCollection;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for Workday Pipeline components
 */
@RunWith(JUnit4.class)
public class WorkdayPipelineTest {
    
    @Rule
    public final transient TestPipeline pipeline = TestPipeline.create();
    
    @Test
    public void testEmployeeTransform() {
        // Create test employee
        Employee testEmployee = createTestEmployee();
        testEmployee.setFirstName("  John  ");
        testEmployee.setLastName("  Doe  ");
        testEmployee.setEmail("  JOHN.DOE@EXAMPLE.COM  ");
        testEmployee.setPhoneNumber("(123) 456-7890");
        
        // Create pipeline
        PCollection<Employee> input = pipeline.apply(Create.of(testEmployee));
        PCollection<Employee> output = input.apply(ParDo.of(new EmployeeTransform()));
        
        // Assert transformations
        PAssert.that(output).satisfies(employees -> {
            Employee transformed = employees.iterator().next();
            assertEquals("John", transformed.getFirstName());
            assertEquals("Doe", transformed.getLastName());
            assertEquals("john.doe@example.com", transformed.getEmail());
            assertEquals("1234567890", transformed.getPhoneNumber());
            return null;
        });
        
        pipeline.run();
    }
    
    @Test
    public void testEmployeeToBigQueryRow() {
        Employee employee = createTestEmployee();
        
        PCollection<Employee> input = pipeline.apply(Create.of(employee));
        PCollection<TableRow> output = input.apply(ParDo.of(new DoFn<Employee, TableRow>() {
            @ProcessElement
            public void processElement(ProcessContext c) {
                c.output(c.element().toBigQueryRow());
            }
        }));
        
        PAssert.that(output).satisfies(rows -> {
            TableRow row = rows.iterator().next();
            assertEquals("EMP001", row.get("employee_id"));
            assertEquals("John", row.get("first_name"));
            assertEquals("Doe", row.get("last_name"));
            assertEquals("john.doe@example.com", row.get("email"));
            assertEquals("Engineering", row.get("department"));
            return null;
        });
        
        pipeline.run();
    }
    
    @Test
    public void testEmployeeValidation() {
        // Create employee with missing ID
        Employee invalidEmployee = createTestEmployee();
        invalidEmployee.setEmployeeId(null);
        
        Employee validEmployee = createTestEmployee();
        
        List<Employee> testEmployees = Arrays.asList(invalidEmployee, validEmployee);
        
        PCollection<Employee> input = pipeline.apply(Create.of(testEmployees));
        PCollection<Employee> output = input.apply(ParDo.of(new EmployeeTransform()));
        
        // Should only output valid employee
        PAssert.that(output).satisfies(employees -> {
            int count = 0;
            for (Employee emp : employees) {
                count++;
                assertNotNull(emp.getEmployeeId());
            }
            assertEquals(1, count);
            return null;
        });
        
        pipeline.run();
    }
    
    @Test
    public void testEmploymentStatusStandardization() {
        Employee employee1 = createTestEmployee();
        employee1.setEmploymentStatus("active");
        
        Employee employee2 = createTestEmployee();
        employee2.setEmploymentStatus("TERMINATED");
        
        Employee employee3 = createTestEmployee();
        employee3.setEmploymentStatus("on leave");
        
        List<Employee> testEmployees = Arrays.asList(employee1, employee2, employee3);
        
        PCollection<Employee> input = pipeline.apply(Create.of(testEmployees));
        PCollection<Employee> output = input.apply(ParDo.of(new EmployeeTransform()));
        
        PAssert.that(output).satisfies(employees -> {
            for (Employee emp : employees) {
                assertTrue(emp.getEmploymentStatus().equals("ACTIVE") || 
                          emp.getEmploymentStatus().equals("TERMINATED") || 
                          emp.getEmploymentStatus().equals("ON_LEAVE"));
            }
            return null;
        });
        
        pipeline.run();
    }
    
    @Test
    public void testPreferredNameGeneration() {
        Employee employee = createTestEmployee();
        employee.setPreferredName(null);
        employee.setFirstName("John");
        
        PCollection<Employee> input = pipeline.apply(Create.of(employee));
        PCollection<Employee> output = input.apply(ParDo.of(new EmployeeTransform()));
        
        PAssert.that(output).satisfies(employees -> {
            Employee transformed = employees.iterator().next();
            assertEquals("John", transformed.getPreferredName());
            return null;
        });
        
        pipeline.run();
    }
    
    private Employee createTestEmployee() {
        Employee employee = new Employee();
        employee.setEmployeeId("EMP001");
        employee.setWorkerReferenceId("WRK001");
        employee.setFirstName("John");
        employee.setLastName("Doe");
        employee.setEmail("john.doe@example.com");
        employee.setPhoneNumber("1234567890");
        employee.setJobTitle("Software Engineer");
        employee.setDepartment("Engineering");
        employee.setLocation("New York");
        employee.setManagerId("MGR001");
        employee.setManagerName("Jane Smith");
        employee.setEmploymentStatus("ACTIVE");
        employee.setHireDate(LocalDate.of(2020, 1, 15));
        employee.setEmployeeType("SALARIED");
        employee.setCostCenter("CC100");
        employee.setBusinessUnit("Technology");
        employee.setSalary(100000.0);
        employee.setCurrency("USD");
        employee.setPayFrequency("MONTHLY");
        employee.setEffectiveDate(LocalDate.now());
        employee.setLastModifiedDate(LocalDateTime.now());
        
        return employee;
    }
}
-- Usage Example for Employee Timesheet Processing Stored Procedure

-- 1. First, create your input tables (example structure)

-- Schedule Table Example
CREATE OR REPLACE TABLE `your_project.your_dataset.schedule_data` (
  EMPLOYEE_NAME STRING,
  EMPLOYEE_ID STRING,
  PAY_CODE STRING,
  AS_JOB STRING,
  WORK_DATE DATE,
  SCHEDULED_START_DTTM DATETIME,
  SCHEDULED_END_DTTM DATETIME,
  MANAGER_NAME STRING,
  MODEL_NAME STRING,
  REASON_CODE STRING
);

-- Timesheet Table Example
CREATE OR REPLACE TABLE `your_project.your_dataset.timesheet_data` (
  EMPLOYEE_NAME STRING,
  EMPLOYEE_ID STRING,
  PAY_CODE STRING,
  WORK_DATE DATE,
  LOCATION STRING,
  COST_CENTER STRING,
  START_DTTM DATETIME,
  END_DTTM DATETIME,
  COMMENTS STRING,
  MANAGER_NAME STRING,
  MODEL_NAME STRING,
  REASON_CODE STRING
);

-- 2. Call the stored procedure
CALL `your_project.your_dataset.process_employee_timesheet`(
  'your_project.your_dataset.schedule_data',
  'your_project.your_dataset.timesheet_data',
  'your_project.your_dataset.processed_timesheet_output'
);

-- 3. Query the results
SELECT * FROM `your_project.your_dataset.processed_timesheet_output`
ORDER BY EMPLOYEE_ID, WORK_DATE;

-- Sample data insertion for testing
INSERT INTO `your_project.your_dataset.schedule_data` VALUES
('John Doe', 'EMP001', 'REG', 'Day Shift', DATE('2024-01-15'), 
 DATETIME('2024-01-15 08:00:00'), DATETIME('2024-01-15 17:00:00'), 
 'Manager Smith', 'Model A', 'SCHEDULED');

INSERT INTO `your_project.your_dataset.timesheet_data` VALUES
('John Doe', 'EMP001', 'REG', DATE('2024-01-15'), 'Main Office', 'CC001',
 DATETIME('2024-01-15 08:00:00'), DATETIME('2024-01-15 17:30:00'), 
 'Regular work day', 'Manager Smith', 'Model A', 'WORKED');

-- Example with overnight shift
INSERT INTO `your_project.your_dataset.timesheet_data` VALUES
('Jane Smith', 'EMP002', 'REG', DATE('2024-01-15'), 'Night Office', 'CC002',
 DATETIME('2024-01-15 23:00:00'), DATETIME('2024-01-16 07:00:00'), 
 'Night shift work', 'Manager Jones', 'Model B', 'WORKED');
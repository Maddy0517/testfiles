-- Usage Examples for Timesheet Transformation Procedure

-- 1. Basic Usage - Transform data for a specific date
CALL `your_project.your_dataset.transform_timesheet_data`(
  DATE('2024-01-15'), 
  'your_project.your_dataset.transformed_timesheet_20240115'
);

-- 2. Transform data for current date
CALL `your_project.your_dataset.transform_timesheet_data`(
  CURRENT_DATE(), 
  'your_project.your_dataset.transformed_timesheet_current'
);

-- 3. Transform data for yesterday
CALL `your_project.your_dataset.transform_timesheet_data`(
  DATE_SUB(CURRENT_DATE(), INTERVAL 1 DAY), 
  'your_project.your_dataset.transformed_timesheet_yesterday'
);

-- 4. Query the transformed results
SELECT 
  Employee_Name,
  Employee_ID,
  Pay_Code,
  WORK_DATE,
  Hours_Worked,
  Regular,
  Overtime,
  Double_Time,
  in_punch1,
  out_punch1,
  in_punch2,
  out_punch2
FROM `your_project.your_dataset.transformed_timesheet_20240115`
WHERE Employee_Name = 'John Smith'
ORDER BY WORK_DATE;

-- 5. Sample data structure for testing
-- Create sample TIME_SHEET_DETAIL data
CREATE OR REPLACE TABLE `your_project.your_dataset.TIME_SHEET_DETAIL_SAMPLE` AS
SELECT 
  'John Smith' as EMPLOYEE_NAME,
  'REG' as PAY_CODE,
  DATE('2024-01-15') as WORK_DATE,
  'Office Building A' as LOCATION,
  'CC001' as COST_CENTER,
  DATETIME('2024-01-15 08:00:00') as START_DTTM,
  DATETIME('2024-01-15 12:00:00') as END_DTTM,
  'Manager A' as MANAGER_NAME,
  'EMP001' as EMPLOYEE_ID

UNION ALL

SELECT 
  'John Smith' as EMPLOYEE_NAME,
  'OT_15' as PAY_CODE,
  DATE('2024-01-15') as WORK_DATE,
  'Office Building A' as LOCATION,
  'CC001' as COST_CENTER,
  DATETIME('2024-01-15 13:00:00') as START_DTTM,
  DATETIME('2024-01-15 17:00:00') as END_DTTM,
  'Manager A' as MANAGER_NAME,
  'EMP001' as EMPLOYEE_ID

UNION ALL

SELECT 
  'Jane Doe' as EMPLOYEE_NAME,
  'REG' as PAY_CODE,
  DATE('2024-01-15') as WORK_DATE,
  'Office Building B' as LOCATION,
  'CC002' as COST_CENTER,
  DATETIME('2024-01-15 09:00:00') as START_DTTM,
  DATETIME('2024-01-15 17:00:00') as END_DTTM,
  'Manager B' as MANAGER_NAME,
  'EMP002' as EMPLOYEE_ID;

-- Create sample SCHEDULE_DETAIL data
CREATE OR REPLACE TABLE `your_project.your_dataset.SCHEDULE_DETAIL_SAMPLE` AS
SELECT 
  'John Smith' as EMPLOYEE_NAME,
  'EMP001' as EMPLOYEE_ID,
  'SCHEDULE_HOURS' as PAY_CODE,
  'JOB001' as AS_JOB,
  DATE('2024-01-15') as WORK_DATE,
  DATETIME('2024-01-15 08:00:00') as SCHEDULED_START_DTTM,
  DATETIME('2024-01-15 17:00:00') as SCHEDULED_END_DTTM,
  'Manager A' as MANAGER_NAME

UNION ALL

SELECT 
  'John Smith' as EMPLOYEE_NAME,
  'EMP001' as EMPLOYEE_ID,
  'SCHEDULE_MEAL_BREAK' as PAY_CODE,
  'JOB001' as AS_JOB,
  DATE('2024-01-15') as WORK_DATE,
  DATETIME('2024-01-15 12:00:00') as SCHEDULED_START_DTTM,
  DATETIME('2024-01-15 13:00:00') as SCHEDULED_END_DTTM,
  'Manager A' as MANAGER_NAME

UNION ALL

SELECT 
  'Jane Doe' as EMPLOYEE_NAME,
  'EMP002' as EMPLOYEE_ID,
  'SCHEDULE_HOURS' as PAY_CODE,
  'JOB002' as AS_JOB,
  DATE('2024-01-15') as WORK_DATE,
  DATETIME('2024-01-15 09:00:00') as SCHEDULED_START_DTTM,
  DATETIME('2024-01-15 17:00:00') as SCHEDULED_END_DTTM,
  'Manager B' as MANAGER_NAME;

-- 6. Test the procedure with sample data
-- First, update the procedure to use the sample tables
-- Then run:
CALL `your_project.your_dataset.transform_timesheet_data`(
  DATE('2024-01-15'), 
  'your_project.your_dataset.test_transformed_timesheet'
);

-- 7. Verify results
SELECT * FROM `your_project.your_dataset.test_transformed_timesheet`;

-- 8. Create a view for easier access to transformed data
CREATE OR REPLACE VIEW `your_project.your_dataset.v_transformed_timesheet` AS
SELECT 
  Employee_Name,
  Employee_ID,
  Pay_Code,
  WORK_DATE,
  LOCATION,
  COST_CENTER,
  MANAGER_NAME,
  Hours_Worked,
  Hours_Worked_Outside_of_Schedule,
  On_Call,
  Regular,
  Overtime,
  Double_Time,
  Double_Time_Night,
  Double_Time_Swing,
  -- Only show punch times, not empty comment fields
  in_punch1,
  out_punch1,
  in_punch2,
  out_punch2,
  in_punch3,
  out_punch3,
  in_punch4,
  out_punch4
FROM `your_project.your_dataset.transformed_timesheet_current`
WHERE WORK_DATE >= DATE_SUB(CURRENT_DATE(), INTERVAL 7 DAY);
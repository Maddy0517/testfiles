-- BigQuery Stored Procedure for Timesheet Data Transformation
-- Transforms TIME_SHEET_DETAIL and SCHEDULE_DETAIL tables into columnar format

CREATE OR REPLACE PROCEDURE `your_project.your_dataset.transform_timesheet_data`(
  IN input_date DATE,
  IN output_table_name STRING
)
BEGIN
  
  -- Declare variables for dynamic SQL
  DECLARE sql_query STRING;
  
  -- Create the transformation query
  SET sql_query = FORMAT("""
    CREATE OR REPLACE TABLE `%s` AS
    WITH 
    -- Step 1: Get schedule data aggregated by employee and date
    schedule_data AS (
      SELECT 
        EMPLOYEE_NAME,
        EMPLOYEE_ID,
        WORK_DATE,
        MANAGER_NAME,
        -- Aggregate scheduled hours by pay code type
        SUM(CASE 
          WHEN PAY_CODE = 'SCHEDULE_HOURS' 
          THEN DATETIME_DIFF(SCHEDULED_END_DTTM, SCHEDULED_START_DTTM, MINUTE) / 60.0 
          ELSE 0 
        END) as scheduled_hours,
        SUM(CASE 
          WHEN PAY_CODE IN ('SCHEDULE_HOURS', 'SCHEDULE_JS') 
          THEN DATETIME_DIFF(SCHEDULED_END_DTTM, SCHEDULED_START_DTTM, MINUTE) / 60.0 
          ELSE 0 
        END) as scheduled_total,
        -- Get the primary pay code (excluding meal breaks)
        STRING_AGG(
          CASE WHEN PAY_CODE != 'SCHEDULE_MEAL_BREAK' THEN PAY_CODE END, 
          ', ' 
          ORDER BY SCHEDULED_START_DTTM
        ) as schedule_pay_codes
      FROM `your_project.your_dataset.SCHEDULE_DETAIL`
      WHERE WORK_DATE = @input_date
      GROUP BY EMPLOYEE_NAME, EMPLOYEE_ID, WORK_DATE, MANAGER_NAME
    ),
    
    -- Step 2: Get timesheet data with hours calculated and pivoted
    timesheet_pivoted AS (
      SELECT 
        EMPLOYEE_NAME,
        EMPLOYEE_ID,
        WORK_DATE,
        LOCATION,
        COST_CENTER,
        MANAGER_NAME,
        -- Calculate total hours worked
        SUM(DATETIME_DIFF(END_DTTM, START_DTTM, MINUTE) / 60.0) as total_hours_worked,
        
        -- Pivot pay codes to columns
        SUM(CASE 
          WHEN PAY_CODE = 'SCHEDULE_OUTSIDE' 
          THEN DATETIME_DIFF(END_DTTM, START_DTTM, MINUTE) / 60.0 
          ELSE 0 
        END) as hours_worked_outside_of_schedule,
        
        SUM(CASE 
          WHEN PAY_CODE = 'ON_CALL' 
          THEN DATETIME_DIFF(END_DTTM, START_DTTM, MINUTE) / 60.0 
          ELSE 0 
        END) as on_call,
        
        0.0 as pager, -- Placeholder as no pager pay code mentioned
        
        SUM(CASE 
          WHEN PAY_CODE = 'REG' 
          THEN DATETIME_DIFF(END_DTTM, START_DTTM, MINUTE) / 60.0 
          ELSE 0 
        END) as regular,
        
        SUM(CASE 
          WHEN PAY_CODE = 'OT_15' 
          THEN DATETIME_DIFF(END_DTTM, START_DTTM, MINUTE) / 60.0 
          ELSE 0 
        END) as overtime,
        
        SUM(CASE 
          WHEN PAY_CODE = 'DT_20' 
          THEN DATETIME_DIFF(END_DTTM, START_DTTM, MINUTE) / 60.0 
          ELSE 0 
        END) as double_time,
        
        SUM(CASE 
          WHEN PAY_CODE = 'DT_20_NIGHT' 
          THEN DATETIME_DIFF(END_DTTM, START_DTTM, MINUTE) / 60.0 
          ELSE 0 
        END) as double_time_night,
        
        SUM(CASE 
          WHEN PAY_CODE = 'DT_20_SWING' 
          THEN DATETIME_DIFF(END_DTTM, START_DTTM, MINUTE) / 60.0 
          ELSE 0 
        END) as double_time_swing
        
      FROM `your_project.your_dataset.TIME_SHEET_DETAIL`
      WHERE WORK_DATE = @input_date
      GROUP BY EMPLOYEE_NAME, EMPLOYEE_ID, WORK_DATE, LOCATION, COST_CENTER, MANAGER_NAME
    ),
    
    -- Step 3: Get punch in/out data with row numbers for multiple punches
    punch_data AS (
      SELECT 
        EMPLOYEE_NAME,
        EMPLOYEE_ID,
        WORK_DATE,
        START_DTTM as punch_time,
        'IN' as punch_type,
        ROW_NUMBER() OVER (
          PARTITION BY EMPLOYEE_NAME, EMPLOYEE_ID, WORK_DATE 
          ORDER BY START_DTTM
        ) as punch_sequence
      FROM `your_project.your_dataset.TIME_SHEET_DETAIL`
      WHERE WORK_DATE = @input_date
      
      UNION ALL
      
      SELECT 
        EMPLOYEE_NAME,
        EMPLOYEE_ID,
        WORK_DATE,
        END_DTTM as punch_time,
        'OUT' as punch_type,
        ROW_NUMBER() OVER (
          PARTITION BY EMPLOYEE_NAME, EMPLOYEE_ID, WORK_DATE 
          ORDER BY END_DTTM
        ) as punch_sequence
      FROM `your_project.your_dataset.TIME_SHEET_DETAIL`
      WHERE WORK_DATE = @input_date
    ),
    
    -- Step 4: Pivot punch data to get up to 8 punch pairs
    punch_pivoted AS (
      SELECT 
        EMPLOYEE_NAME,
        EMPLOYEE_ID,
        WORK_DATE,
        -- Punch 1
        MAX(CASE WHEN punch_type = 'IN' AND punch_sequence = 1 THEN punch_time END) as in_punch1,
        '' as in_comments1,
        MAX(CASE WHEN punch_type = 'OUT' AND punch_sequence = 1 THEN punch_time END) as out_punch1,
        '' as out_comments1,
        -- Punch 2
        MAX(CASE WHEN punch_type = 'IN' AND punch_sequence = 2 THEN punch_time END) as in_punch2,
        '' as in_comments2,
        MAX(CASE WHEN punch_type = 'OUT' AND punch_sequence = 2 THEN punch_time END) as out_punch2,
        '' as out_comments2,
        -- Punch 3
        MAX(CASE WHEN punch_type = 'IN' AND punch_sequence = 3 THEN punch_time END) as in_punch3,
        '' as in_comments3,
        MAX(CASE WHEN punch_type = 'OUT' AND punch_sequence = 3 THEN punch_time END) as out_punch3,
        '' as out_comments3,
        -- Punch 4
        MAX(CASE WHEN punch_type = 'IN' AND punch_sequence = 4 THEN punch_time END) as in_punch4,
        '' as in_comments4,
        MAX(CASE WHEN punch_type = 'OUT' AND punch_sequence = 4 THEN punch_time END) as out_punch4,
        '' as out_comments4,
        -- Punch 5
        MAX(CASE WHEN punch_type = 'IN' AND punch_sequence = 5 THEN punch_time END) as in_punch5,
        '' as in_comments5,
        MAX(CASE WHEN punch_type = 'OUT' AND punch_sequence = 5 THEN punch_time END) as out_punch5,
        '' as out_comments5,
        -- Punch 6
        MAX(CASE WHEN punch_type = 'IN' AND punch_sequence = 6 THEN punch_time END) as in_punch6,
        '' as in_comments6,
        MAX(CASE WHEN punch_type = 'OUT' AND punch_sequence = 6 THEN punch_time END) as out_punch6,
        '' as out_comments6,
        -- Punch 7
        MAX(CASE WHEN punch_type = 'IN' AND punch_sequence = 7 THEN punch_time END) as in_punch7,
        '' as in_comments7,
        MAX(CASE WHEN punch_type = 'OUT' AND punch_sequence = 7 THEN punch_time END) as out_punch7,
        '' as out_comments7,
        -- Punch 8
        MAX(CASE WHEN punch_type = 'IN' AND punch_sequence = 8 THEN punch_time END) as in_punch8,
        '' as in_comments8,
        MAX(CASE WHEN punch_type = 'OUT' AND punch_sequence = 8 THEN punch_time END) as out_punch8,
        '' as out_comments8
      FROM punch_data
      GROUP BY EMPLOYEE_NAME, EMPLOYEE_ID, WORK_DATE
    )
    
    -- Step 5: Final join and output
    SELECT 
      COALESCE(ts.EMPLOYEE_NAME, sd.EMPLOYEE_NAME) as Employee_Name,
      COALESCE(ts.EMPLOYEE_ID, sd.EMPLOYEE_ID) as Employee_ID,
      COALESCE(sd.schedule_pay_codes, 'SCHEDULE') as Pay_Code, -- From SCHEDULE_DETAIL table
      COALESCE(ts.WORK_DATE, sd.WORK_DATE) as WORK_DATE,
      sd.scheduled_hours as Schedule_Hours,
      sd.scheduled_total as Scheduled_Total,
      ts.LOCATION,
      ts.COST_CENTER,
      COALESCE(ts.MANAGER_NAME, sd.MANAGER_NAME) as MANAGER_NAME,
      COALESCE(ts.total_hours_worked, 0.0) as Hours_Worked,
      COALESCE(ts.hours_worked_outside_of_schedule, 0.0) as Hours_Worked_Outside_of_Schedule,
      COALESCE(ts.on_call, 0.0) as On_Call,
      COALESCE(ts.pager, 0.0) as Pager,
      COALESCE(ts.regular, 0.0) as Regular,
      COALESCE(ts.overtime, 0.0) as Overtime,
      COALESCE(ts.double_time, 0.0) as Double_Time,
      COALESCE(ts.double_time_night, 0.0) as Double_Time_Night,
      COALESCE(ts.double_time_swing, 0.0) as Double_Time_Swing,
      -- Punch data
      pd.in_punch1, pd.in_comments1, pd.out_punch1, pd.out_comments1,
      pd.in_punch2, pd.in_comments2, pd.out_punch2, pd.out_comments2,
      pd.in_punch3, pd.in_comments3, pd.out_punch3, pd.out_comments3,
      pd.in_punch4, pd.in_comments4, pd.out_punch4, pd.out_comments4,
      pd.in_punch5, pd.in_comments5, pd.out_punch5, pd.out_comments5,
      pd.in_punch6, pd.in_comments6, pd.out_punch6, pd.out_comments6,
      pd.in_punch7, pd.in_comments7, pd.out_punch7, pd.out_comments7,
      pd.in_punch8, pd.in_comments8, pd.out_punch8, pd.out_comments8
      
    FROM schedule_data sd
    FULL OUTER JOIN timesheet_pivoted ts 
      ON sd.EMPLOYEE_ID = ts.EMPLOYEE_ID 
      AND sd.WORK_DATE = ts.WORK_DATE
    LEFT JOIN punch_pivoted pd 
      ON COALESCE(ts.EMPLOYEE_ID, sd.EMPLOYEE_ID) = pd.EMPLOYEE_ID 
      AND COALESCE(ts.WORK_DATE, sd.WORK_DATE) = pd.WORK_DATE
    ORDER BY Employee_Name, WORK_DATE
  """, output_table_name);
  
  -- Execute the dynamic SQL with the input date parameter
  EXECUTE IMMEDIATE sql_query USING input_date as input_date;
  
  -- Log completion
  SELECT FORMAT('Timesheet transformation completed for date: %s. Results stored in: %s', 
                CAST(input_date AS STRING), output_table_name) as completion_message;
  
END;
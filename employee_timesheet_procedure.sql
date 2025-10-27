CREATE OR REPLACE PROCEDURE `your_project.your_dataset.process_employee_timesheet`(
  schedule_table STRING,
  timesheet_table STRING,
  output_table STRING
)
BEGIN
  -- Build dynamic SQL with proper table name substitution
  EXECUTE IMMEDIATE FORMAT("""
  CREATE OR REPLACE TEMP TABLE temp_combined_data AS
  WITH 
  -- Parse schedule data
  schedule_parsed AS (
    SELECT 
      EMPLOYEE_NAME,
      EMPLOYEE_ID,
      PAY_CODE,
      AS_JOB,
      WORK_DATE,
      CAST(SCHEDULED_START_DTTM AS DATETIME) AS SCHEDULED_START_DTTM,
      CAST(SCHEDULED_END_DTTM AS DATETIME) AS SCHEDULED_END_DTTM,
      MANAGER_NAME,
      MODEL_NAME,
      REASON_CODE,
      -- Calculate scheduled hours
      DATETIME_DIFF(CAST(SCHEDULED_END_DTTM AS DATETIME), CAST(SCHEDULED_START_DTTM AS DATETIME), MINUTE) / 60.0 AS scheduled_hours
    FROM `%s`
  ),
  
  -- Parse timesheet data and create punch records
  timesheet_parsed AS (
    SELECT 
      EMPLOYEE_NAME,
      EMPLOYEE_ID,
      PAY_CODE,
      WORK_DATE,
      LOCATION,
      COST_CENTER,
      CAST(START_DTTM AS DATETIME) AS START_DTTM,
      CAST(END_DTTM AS DATETIME) AS END_DTTM,
      COMMENTS,
      MANAGER_NAME,
      MODEL_NAME,
      REASON_CODE,
      -- Calculate worked hours (handling overnight shifts)
      CASE 
        WHEN DATE(CAST(END_DTTM AS DATETIME)) > DATE(CAST(START_DTTM AS DATETIME)) THEN
          -- Overnight shift: calculate hours properly
          DATETIME_DIFF(
            DATETIME_ADD(DATETIME(DATE(CAST(START_DTTM AS DATETIME)), TIME(23, 59, 59)), INTERVAL 1 MINUTE),
            CAST(START_DTTM AS DATETIME), 
            MINUTE
          ) / 60.0 +
          DATETIME_DIFF(
            CAST(END_DTTM AS DATETIME),
            DATETIME(DATE(CAST(END_DTTM AS DATETIME)), TIME(0, 0, 0)),
            MINUTE
          ) / 60.0
        ELSE
          DATETIME_DIFF(CAST(END_DTTM AS DATETIME), CAST(START_DTTM AS DATETIME), MINUTE) / 60.0
      END AS worked_hours,
      
      -- Row number for punch sequencing
      ROW_NUMBER() OVER (
        PARTITION BY EMPLOYEE_ID, WORK_DATE 
        ORDER BY CAST(START_DTTM AS DATETIME)
      ) AS punch_sequence
    FROM `%s`
  ),
  
  -- Aggregate timesheet data by employee and date
  timesheet_aggregated AS (
    SELECT 
      EMPLOYEE_NAME,
      EMPLOYEE_ID,
      WORK_DATE,
      MAX(LOCATION) AS WORKED_LOCATION,
      MAX(COST_CENTER) AS COST_CENTER,
      MAX(PAY_CODE) AS WORKED_PROJECT,
      MAX(MANAGER_NAME) AS WORKED_SUPERVISOR,
      SUM(worked_hours) AS total_worked_hours,
      
      -- Punch data (up to 8 punches)
      CAST(MAX(CASE WHEN punch_sequence = 1 THEN START_DTTM END) AS DATETIME) AS IN_PUNCH_1,
      MAX(CASE WHEN punch_sequence = 1 THEN COMMENTS END) AS IN_PUNCH_COMMENTS_NOTES_1,
      CAST(MAX(CASE WHEN punch_sequence = 1 THEN END_DTTM END) AS DATETIME) AS OUT_PUNCH_1,
      MAX(CASE WHEN punch_sequence = 1 THEN COMMENTS END) AS OUT_PUNCH_COMMENTS_NOTES_1,
      
      CAST(MAX(CASE WHEN punch_sequence = 2 THEN START_DTTM END) AS DATETIME) AS IN_PUNCH_2,
      MAX(CASE WHEN punch_sequence = 2 THEN COMMENTS END) AS IN_PUNCH_COMMENTS_NOTES_2,
      CAST(MAX(CASE WHEN punch_sequence = 2 THEN END_DTTM END) AS DATETIME) AS OUT_PUNCH_2,
      MAX(CASE WHEN punch_sequence = 2 THEN COMMENTS END) AS OUT_PUNCH_COMMENTS_NOTES_2,
      
      CAST(MAX(CASE WHEN punch_sequence = 3 THEN START_DTTM END) AS DATETIME) AS IN_PUNCH_3,
      MAX(CASE WHEN punch_sequence = 3 THEN COMMENTS END) AS IN_PUNCH_COMMENTS_NOTES_3,
      CAST(MAX(CASE WHEN punch_sequence = 3 THEN END_DTTM END) AS DATETIME) AS OUT_PUNCH_3,
      MAX(CASE WHEN punch_sequence = 3 THEN COMMENTS END) AS OUT_PUNCH_COMMENTS_NOTES_3,
      
      CAST(MAX(CASE WHEN punch_sequence = 4 THEN START_DTTM END) AS DATETIME) AS IN_PUNCH_4,
      MAX(CASE WHEN punch_sequence = 4 THEN COMMENTS END) AS IN_PUNCH_COMMENTS_NOTES_4,
      CAST(MAX(CASE WHEN punch_sequence = 4 THEN END_DTTM END) AS DATETIME) AS OUT_PUNCH_4,
      MAX(CASE WHEN punch_sequence = 4 THEN COMMENTS END) AS OUT_PUNCH_COMMENTS_NOTES_4,
      
      CAST(MAX(CASE WHEN punch_sequence = 5 THEN START_DTTM END) AS DATETIME) AS IN_PUNCH_5,
      MAX(CASE WHEN punch_sequence = 5 THEN COMMENTS END) AS IN_PUNCH_COMMENTS_NOTES_5,
      CAST(MAX(CASE WHEN punch_sequence = 5 THEN END_DTTM END) AS DATETIME) AS OUT_PUNCH_5,
      MAX(CASE WHEN punch_sequence = 5 THEN COMMENTS END) AS OUT_PUNCH_COMMENTS_NOTES_5,
      
      CAST(MAX(CASE WHEN punch_sequence = 6 THEN START_DTTM END) AS DATETIME) AS IN_PUNCH_6,
      MAX(CASE WHEN punch_sequence = 6 THEN COMMENTS END) AS IN_PUNCH_COMMENTS_NOTES_6,
      CAST(MAX(CASE WHEN punch_sequence = 6 THEN END_DTTM END) AS DATETIME) AS OUT_PUNCH_6,
      MAX(CASE WHEN punch_sequence = 6 THEN COMMENTS END) AS OUT_PUNCH_COMMENTS_NOTES_6,
      
      CAST(MAX(CASE WHEN punch_sequence = 7 THEN START_DTTM END) AS DATETIME) AS IN_PUNCH_7,
      MAX(CASE WHEN punch_sequence = 7 THEN COMMENTS END) AS IN_PUNCH_COMMENTS_NOTES_7,
      CAST(MAX(CASE WHEN punch_sequence = 7 THEN END_DTTM END) AS DATETIME) AS OUT_PUNCH_7,
      MAX(CASE WHEN punch_sequence = 7 THEN COMMENTS END) AS OUT_PUNCH_COMMENTS_NOTES_7,
      
      CAST(MAX(CASE WHEN punch_sequence = 8 THEN START_DTTM END) AS DATETIME) AS IN_PUNCH_8,
      MAX(CASE WHEN punch_sequence = 8 THEN COMMENTS END) AS IN_PUNCH_COMMENTS_NOTES_8,
      CAST(MAX(CASE WHEN punch_sequence = 8 THEN END_DTTM END) AS DATETIME) AS OUT_PUNCH_8,
      MAX(CASE WHEN punch_sequence = 8 THEN COMMENTS END) AS OUT_PUNCH_COMMENTS_NOTES_8
      
    FROM timesheet_parsed
    GROUP BY 
      EMPLOYEE_NAME, EMPLOYEE_ID, WORK_DATE
  ),
  
  -- Calculate hours worked inside and outside of schedule
  hours_calculation AS (
    SELECT 
      t.*,
      s.scheduled_hours,
      s.SCHEDULED_START_DTTM,
      s.SCHEDULED_END_DTTM,
      s.PAY_CODE AS SCHEDULE,
      
      -- Calculate hours worked within scheduled time
      CASE 
        WHEN t.IN_PUNCH_1 IS NOT NULL AND t.OUT_PUNCH_1 IS NOT NULL AND s.SCHEDULED_START_DTTM IS NOT NULL THEN
          GREATEST(0, 
            LEAST(
              DATETIME_DIFF(t.OUT_PUNCH_1, t.IN_PUNCH_1, MINUTE) / 60.0,
              DATETIME_DIFF(
                LEAST(t.OUT_PUNCH_1, s.SCHEDULED_END_DTTM),
                GREATEST(t.IN_PUNCH_1, s.SCHEDULED_START_DTTM),
                MINUTE
              ) / 60.0
            )
          )
        ELSE 0
      END AS hours_within_schedule,
      
      -- Calculate hours worked outside of schedule
      CASE 
        WHEN t.IN_PUNCH_1 IS NOT NULL AND t.OUT_PUNCH_1 IS NOT NULL AND s.SCHEDULED_START_DTTM IS NOT NULL THEN
          t.total_worked_hours - 
          GREATEST(0, 
            LEAST(
              DATETIME_DIFF(t.OUT_PUNCH_1, t.IN_PUNCH_1, MINUTE) / 60.0,
              DATETIME_DIFF(
                LEAST(t.OUT_PUNCH_1, s.SCHEDULED_END_DTTM),
                GREATEST(t.IN_PUNCH_1, s.SCHEDULED_START_DTTM),
                MINUTE
              ) / 60.0
            )
          )
        ELSE t.total_worked_hours
      END AS hours_outside_schedule
      
    FROM timesheet_aggregated t
    LEFT JOIN schedule_parsed s
      ON t.EMPLOYEE_ID = s.EMPLOYEE_ID 
      AND t.WORK_DATE = s.WORK_DATE
  ),
  
  -- Calculate pay categories
  final_calculation AS (
    SELECT 
      *,
      -- Regular hours (first 8 hours)
      LEAST(total_worked_hours, 8.0) AS REGULAR,
      
      -- Overtime (hours 8.01 to 12)
      CASE 
        WHEN total_worked_hours > 8.0 THEN 
          LEAST(total_worked_hours - 8.0, 4.0)
        ELSE 0
      END AS OVERTIME,
      
      -- Double time (hours over 12)
      CASE 
        WHEN total_worked_hours > 12.0 THEN 
          total_worked_hours - 12.0
        ELSE 0
      END AS DOUBLE_TIME,
      
      -- Double time night shift (assuming 10 PM to 6 AM)
      CASE 
        WHEN EXTRACT(HOUR FROM IN_PUNCH_1) >= 22 OR EXTRACT(HOUR FROM IN_PUNCH_1) < 6 THEN
          total_worked_hours
        ELSE 0
      END AS DOUBLE_TIME_NIGHT,
      
      -- Double time swing shift (assuming 3 PM to 11 PM)
      CASE 
        WHEN EXTRACT(HOUR FROM IN_PUNCH_1) >= 15 AND EXTRACT(HOUR FROM IN_PUNCH_1) < 23 THEN
          total_worked_hours
        ELSE 0
      END AS DOUBLE_TIME_SWING,
      
      -- On-call and pager pay (placeholder logic - adjust based on business rules)
      0.0 AS ON_CALL,
      0.0 AS PAGER_PAY
      
    FROM hours_calculation
  )
  
  SELECT 
    EMPLOYEE_NAME,
    EMPLOYEE_ID,
    WORK_DATE,
    SCHEDULE,
    scheduled_hours AS SCHEDULED_TOTAL,
    WORKED_LOCATION,
    COST_CENTER,
    WORKED_PROJECT,
    WORKED_SUPERVISOR,
    total_worked_hours AS HOURS_WORKED,
    hours_outside_schedule AS HOURS_WORKED_OUTSIDE_OF_SCHEDULE,
    ON_CALL,
    PAGER_PAY,
    REGULAR,
    OVERTIME,
    DOUBLE_TIME,
    DOUBLE_TIME_NIGHT,
    DOUBLE_TIME_SWING,
    IN_PUNCH_1,
    IN_PUNCH_COMMENTS_NOTES_1,
    OUT_PUNCH_1,
    OUT_PUNCH_COMMENTS_NOTES_1,
    IN_PUNCH_2,
    IN_PUNCH_COMMENTS_NOTES_2,
    OUT_PUNCH_2,
    OUT_PUNCH_COMMENTS_NOTES_2,
    IN_PUNCH_3,
    IN_PUNCH_COMMENTS_NOTES_3,
    OUT_PUNCH_3,
    OUT_PUNCH_COMMENTS_NOTES_3,
    IN_PUNCH_4,
    IN_PUNCH_COMMENTS_NOTES_4,
    OUT_PUNCH_4,
    OUT_PUNCH_COMMENTS_NOTES_4,
    IN_PUNCH_5,
    IN_PUNCH_COMMENTS_NOTES_5,
    OUT_PUNCH_5,
    OUT_PUNCH_COMMENTS_NOTES_5,
    IN_PUNCH_6,
    IN_PUNCH_COMMENTS_NOTES_6,
    OUT_PUNCH_6,
    OUT_PUNCH_COMMENTS_NOTES_6,
    IN_PUNCH_7,
    IN_PUNCH_COMMENTS_NOTES_7,
    OUT_PUNCH_7,
    OUT_PUNCH_COMMENTS_NOTES_7,
    IN_PUNCH_8,
    IN_PUNCH_COMMENTS_NOTES_8,
    OUT_PUNCH_8,
    OUT_PUNCH_COMMENTS_NOTES_8
  FROM final_calculation
  """, schedule_table, timesheet_table);
  
  -- Insert results into output table
  EXECUTE IMMEDIATE FORMAT("""
    CREATE OR REPLACE TABLE `%s` AS
    SELECT * FROM temp_combined_data
  """, output_table);
  
END;
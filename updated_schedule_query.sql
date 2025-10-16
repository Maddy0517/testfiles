SELECT 
    EMPLOYEE_NAME,
    EMPLOYEE_ID,
    WORK_DATE,
    MANAGER_NAME,
    -- Create schedule time range (e.g., "8AM - 5PM")
    CONCAT(
        FORMAT_DATETIME('%l%p', MIN(CASE WHEN PAY_CODE != 'SCHEDULE_MEAL_BREAK' THEN SCHEDULED_START_DTTM END)),
        ' - ',
        FORMAT_DATETIME('%l%p', MAX(CASE WHEN PAY_CODE != 'SCHEDULE_MEAL_BREAK' THEN SCHEDULED_END_DTTM END))
    ) as SCHEDULE,
    -- Calculate total scheduled hours (excluding meal breaks)
    CAST(
        SUM(CASE 
            WHEN PAY_CODE IN ('SCHEDULE_HOURS', 'SCHEDULE_JS') 
            THEN DATETIME_DIFF(SCHEDULED_END_DTTM, SCHEDULED_START_DTTM, MINUTE) / 60.0 
            ELSE 0 
        END) AS INT64
    ) as SCHEDULED_TOTAL
FROM `it-helix-workday-dev.helix_wfs_stg.STG_SCHEDULE_REC`
--WHERE WORK_DATE = @input_date
GROUP BY EMPLOYEE_NAME, EMPLOYEE_ID, WORK_DATE, MANAGER_NAME
HAVING SCHEDULED_TOTAL > 0  -- Only include records with actual scheduled hours
ORDER BY EMPLOYEE_NAME, WORK_DATE;
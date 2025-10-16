-- Option 1: Use DISTINCT in STRING_AGG to remove duplicates
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
    ) as SCHEDULED_TOTAL,
    -- Get unique pay codes only (no duplicates)
    STRING_AGG(
        DISTINCT CASE WHEN PAY_CODE != 'SCHEDULE_MEAL_BREAK' THEN PAY_CODE END, 
        ', ' 
        ORDER BY PAY_CODE
    ) as schedule_pay_codes
FROM `it-helix-workday-dev.helix_wfs_stg.STG_SCHEDULE_REC`
--WHERE WORK_DATE = @input_date
GROUP BY EMPLOYEE_NAME, EMPLOYEE_ID, WORK_DATE, MANAGER_NAME
HAVING SCHEDULED_TOTAL > 0
ORDER BY EMPLOYEE_NAME, WORK_DATE;

-- Option 2: If you only want to show "SCHEDULE" when all pay codes are the same
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
    ) as SCHEDULED_TOTAL,
    -- Show single pay code if all are the same, otherwise show all unique codes
    CASE 
        WHEN COUNT(DISTINCT CASE WHEN PAY_CODE != 'SCHEDULE_MEAL_BREAK' THEN PAY_CODE END) = 1
        THEN MAX(CASE WHEN PAY_CODE != 'SCHEDULE_MEAL_BREAK' THEN PAY_CODE END)
        ELSE STRING_AGG(
            DISTINCT CASE WHEN PAY_CODE != 'SCHEDULE_MEAL_BREAK' THEN PAY_CODE END, 
            ', ' 
            ORDER BY PAY_CODE
        )
    END as schedule_pay_codes
FROM `it-helix-workday-dev.helix_wfs_stg.STG_SCHEDULE_REC`
--WHERE WORK_DATE = @input_date
GROUP BY EMPLOYEE_NAME, EMPLOYEE_ID, WORK_DATE, MANAGER_NAME
HAVING SCHEDULED_TOTAL > 0
ORDER BY EMPLOYEE_NAME, WORK_DATE;

-- Option 3: Simplest - Just use the first pay code found
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
    ) as SCHEDULED_TOTAL,
    -- Get just one pay code (the first one alphabetically)
    MIN(CASE WHEN PAY_CODE != 'SCHEDULE_MEAL_BREAK' THEN PAY_CODE END) as schedule_pay_codes
FROM `it-helix-workday-dev.helix_wfs_stg.STG_SCHEDULE_REC`
--WHERE WORK_DATE = @input_date
GROUP BY EMPLOYEE_NAME, EMPLOYEE_ID, WORK_DATE, MANAGER_NAME
HAVING SCHEDULED_TOTAL > 0
ORDER BY EMPLOYEE_NAME, WORK_DATE;
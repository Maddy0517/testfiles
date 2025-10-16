-- Modified query to get schedule data in the desired format
-- Output: EMPLOYEE_NAME, EMPLOYEE_ID, WORK_DATE, MANAGER_NAME, SCHEDULE, SCHEDULED_TOTAL

SELECT 
    EMPLOYEE_NAME,
    EMPLOYEE_ID,
    WORK_DATE,
    MANAGER_NAME,
    -- Create schedule time range (e.g., "8AM - 5PM")
    CONCAT(
        TRIM(FORMAT_DATETIME('%l%p', MIN(CASE WHEN PAY_CODE != 'SCHEDULE_MEAL_BREAK' THEN SCHEDULED_START_DTTM END))),
        ' - ',
        TRIM(FORMAT_DATETIME('%l%p', MAX(CASE WHEN PAY_CODE != 'SCHEDULE_MEAL_BREAK' THEN SCHEDULED_END_DTTM END)))
    ) as SCHEDULE,
    -- Calculate total scheduled hours (excluding meal breaks)
    ROUND(
        SUM(CASE 
            WHEN PAY_CODE IN ('SCHEDULE_HOURS', 'SCHEDULE_JS') 
            THEN DATETIME_DIFF(SCHEDULED_END_DTTM, SCHEDULED_START_DTTM, MINUTE) / 60.0 
            ELSE 0 
        END), 1
    ) as SCHEDULED_TOTAL
FROM `it-helix-workday-dev.helix_wfs_stg.STG_SCHEDULE_REC`
-- WHERE WORK_DATE = DATE('2025-10-01')  -- Uncomment and modify date as needed
GROUP BY EMPLOYEE_NAME, EMPLOYEE_ID, WORK_DATE, MANAGER_NAME
HAVING SCHEDULED_TOTAL > 0  -- Only include records with actual scheduled hours
ORDER BY EMPLOYEE_NAME, WORK_DATE;

-- Alternative version with cleaner time formatting (removes extra spaces)
-- Use this if you prefer more consistent formatting

SELECT 
    EMPLOYEE_NAME,
    EMPLOYEE_ID,
    WORK_DATE,
    MANAGER_NAME,
    -- Create schedule time range with cleaner formatting
    CONCAT(
        CASE 
            WHEN EXTRACT(HOUR FROM MIN(CASE WHEN PAY_CODE != 'SCHEDULE_MEAL_BREAK' THEN SCHEDULED_START_DTTM END)) = 0 
            THEN '12AM'
            WHEN EXTRACT(HOUR FROM MIN(CASE WHEN PAY_CODE != 'SCHEDULE_MEAL_BREAK' THEN SCHEDULED_START_DTTM END)) <= 12
            THEN CONCAT(
                CAST(
                    CASE WHEN EXTRACT(HOUR FROM MIN(CASE WHEN PAY_CODE != 'SCHEDULE_MEAL_BREAK' THEN SCHEDULED_START_DTTM END)) = 0 
                    THEN 12 
                    ELSE EXTRACT(HOUR FROM MIN(CASE WHEN PAY_CODE != 'SCHEDULE_MEAL_BREAK' THEN SCHEDULED_START_DTTM END)) 
                    END AS STRING
                ),
                CASE WHEN EXTRACT(HOUR FROM MIN(CASE WHEN PAY_CODE != 'SCHEDULE_MEAL_BREAK' THEN SCHEDULED_START_DTTM END)) < 12 THEN 'AM' ELSE 'PM' END
            )
            ELSE CONCAT(
                CAST(EXTRACT(HOUR FROM MIN(CASE WHEN PAY_CODE != 'SCHEDULE_MEAL_BREAK' THEN SCHEDULED_START_DTTM END)) - 12 AS STRING),
                'PM'
            )
        END,
        ' - ',
        CASE 
            WHEN EXTRACT(HOUR FROM MAX(CASE WHEN PAY_CODE != 'SCHEDULE_MEAL_BREAK' THEN SCHEDULED_END_DTTM END)) = 0 
            THEN '12AM'
            WHEN EXTRACT(HOUR FROM MAX(CASE WHEN PAY_CODE != 'SCHEDULE_MEAL_BREAK' THEN SCHEDULED_END_DTTM END)) <= 12
            THEN CONCAT(
                CAST(
                    CASE WHEN EXTRACT(HOUR FROM MAX(CASE WHEN PAY_CODE != 'SCHEDULE_MEAL_BREAK' THEN SCHEDULED_END_DTTM END)) = 0 
                    THEN 12 
                    ELSE EXTRACT(HOUR FROM MAX(CASE WHEN PAY_CODE != 'SCHEDULE_MEAL_BREAK' THEN SCHEDULED_END_DTTM END)) 
                    END AS STRING
                ),
                CASE WHEN EXTRACT(HOUR FROM MAX(CASE WHEN PAY_CODE != 'SCHEDULE_MEAL_BREAK' THEN SCHEDULED_END_DTTM END)) < 12 THEN 'AM' ELSE 'PM' END
            )
            ELSE CONCAT(
                CAST(EXTRACT(HOUR FROM MAX(CASE WHEN PAY_CODE != 'SCHEDULE_MEAL_BREAK' THEN SCHEDULED_END_DTTM END)) - 12 AS STRING),
                'PM'
            )
        END
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
-- WHERE WORK_DATE = DATE('2025-10-01')  -- Uncomment and modify date as needed
GROUP BY EMPLOYEE_NAME, EMPLOYEE_ID, WORK_DATE, MANAGER_NAME
HAVING SCHEDULED_TOTAL > 0  -- Only include records with actual scheduled hours
ORDER BY EMPLOYEE_NAME, WORK_DATE;

-- Simplified version (recommended) - uses FORMAT_DATETIME with TRIM for clean output
SELECT 
    EMPLOYEE_NAME,
    EMPLOYEE_ID,
    WORK_DATE,
    MANAGER_NAME,
    -- Simple and clean schedule format
    REPLACE(
        REPLACE(
            CONCAT(
                FORMAT_DATETIME('%I%p', MIN(CASE WHEN PAY_CODE != 'SCHEDULE_MEAL_BREAK' THEN SCHEDULED_START_DTTM END)),
                ' - ',
                FORMAT_DATETIME('%I%p', MAX(CASE WHEN PAY_CODE != 'SCHEDULE_MEAL_BREAK' THEN SCHEDULED_END_DTTM END))
            ),
            ' 0', ' '  -- Remove leading zeros
        ),
        ' ', ''  -- Remove spaces between number and AM/PM
    ) as SCHEDULE,
    -- Total hours as integer
    CAST(
        SUM(CASE 
            WHEN PAY_CODE IN ('SCHEDULE_HOURS', 'SCHEDULE_JS') 
            THEN DATETIME_DIFF(SCHEDULED_END_DTTM, SCHEDULED_START_DTTM, MINUTE) / 60.0 
            ELSE 0 
        END) AS INT64
    ) as SCHEDULED_TOTAL
FROM `it-helix-workday-dev.helix_wfs_stg.STG_SCHEDULE_REC`
-- WHERE WORK_DATE = DATE('2025-10-01')  -- Uncomment and modify date as needed
GROUP BY EMPLOYEE_NAME, EMPLOYEE_ID, WORK_DATE, MANAGER_NAME
HAVING SCHEDULED_TOTAL > 0
ORDER BY EMPLOYEE_NAME, WORK_DATE;
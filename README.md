# Employee Timesheet Processing - BigQuery Stored Procedure

This BigQuery stored procedure transforms employee schedule and timesheet data into a comprehensive final format for payroll and time tracking purposes.

## Overview

The procedure combines schedule data and timesheet data to calculate:
- Hours worked within and outside of scheduled time
- Pay categories (Regular, Overtime, Double Time, etc.)
- Punch in/out records (up to 8 punches per day)
- Overnight shift handling
- Various pay differentials

## Input Data Structure

### Schedule Data Columns
```
EMPLOYEE_NAME, EMPLOYEE_ID, PAY_CODE, AS_JOB, WORK_DATE, 
SCHEDULED_START_DTTM, SCHEDULED_END_DTTM, MANAGER_NAME, 
MODEL_NAME, REASON_CODE
```

### Timesheet Data Columns
```
EMPLOYEE_NAME, EMPLOYEE_ID, PAY_CODE, WORK_DATE, LOCATION, 
COST_CENTER, START_DTTM, END_DTTM, COMMENTS, MANAGER_NAME, 
MODEL_NAME, REASON_CODE
```

## Output Data Structure

The final output includes 42 columns:
- Employee identification and work date
- Schedule information and totals
- Location and cost center details
- Hours worked calculations
- Pay category breakdowns (Regular, Overtime, Double Time variations)
- Up to 8 punch in/out records with comments

## Key Features

### 1. Hours Calculation Logic
- **Regular Hours**: First 8 hours worked
- **Overtime**: Hours 8.01 to 12
- **Double Time**: Hours over 12
- **Double Time Night**: Work between 10 PM and 6 AM
- **Double Time Swing**: Work between 3 PM and 11 PM

### 2. Overnight Shift Handling
The procedure properly calculates hours for shifts that span midnight by:
- Splitting the calculation at midnight
- Adding hours from both days
- Ensuring accurate total hours calculation

### 3. Schedule Compliance
- Calculates hours worked within scheduled time
- Identifies hours worked outside of schedule
- Compares actual vs. scheduled hours

### 4. Multiple Punch Support
- Handles up to 8 punch in/out pairs per day
- Preserves comments for each punch
- Sequences punches chronologically

## Usage

```sql
CALL `your_project.your_dataset.process_employee_timesheet`(
  'your_project.your_dataset.schedule_data',
  'your_project.your_dataset.timesheet_data',
  'your_project.your_dataset.processed_output'
);
```

## Parameters

1. **schedule_table**: Full table name for schedule data
2. **timesheet_table**: Full table name for timesheet data  
3. **output_table**: Full table name for processed results

## Business Rules Implemented

1. **Overtime Calculation**: Based on daily hours (8+ hours = overtime)
2. **Double Time**: Multiple categories based on total hours and shift times
3. **Overnight Shifts**: Proper hour calculation across date boundaries
4. **Schedule Variance**: Tracking of worked time vs. scheduled time
5. **Multiple Punches**: Support for complex work patterns with breaks

## Customization Notes

- Adjust shift time definitions in the procedure for your specific business rules
- Modify pay calculation logic based on your organization's policies
- Update ON_CALL and PAGER_PAY logic as needed
- Customize overtime thresholds if different from 8/12 hour standards

## Error Handling

The procedure includes:
- Null value handling for missing data
- Date boundary calculations for overnight shifts
- Proper aggregation for multiple timesheet entries per day

## Performance Considerations

- Uses temp tables for intermediate calculations
- Optimized joins between schedule and timesheet data
- Efficient aggregation for punch data consolidation
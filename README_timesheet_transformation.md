# Timesheet Data Transformation - BigQuery Stored Procedure

This solution transforms timesheet and schedule data from a row-based format to a columnar format suitable for reporting and analysis.

## Overview

The stored procedure `transform_timesheet_data` combines data from two source tables:
- `TIME_SHEET_DETAIL` - Contains actual worked hours with various pay codes
- `SCHEDULE_DETAIL` - Contains scheduled hours and job assignments

## Key Features

### 1. Pay Code Transformation
Converts row-based pay codes to columns:
- `REG` → `Regular` column
- `OT_15` → `Overtime` column  
- `DT_20` → `Double_Time` column
- `DT_20_NIGHT` → `Double_Time_Night` column
- `DT_20_SWING` → `Double_Time_Swing` column
- `ON_CALL` → `On_Call` column
- `SCHEDULE_OUTSIDE` → `Hours_Worked_Outside_of_Schedule` column

### 2. Punch Data Handling
- Supports up to 8 punch in/out pairs per employee per day
- Automatically sequences punches chronologically
- Provides placeholder columns for punch comments

### 3. Schedule Integration
- Combines scheduled hours with actual worked hours
- Calculates scheduled totals excluding meal breaks
- Preserves job assignment information

## Table Structures

### Source Tables

#### TIME_SHEET_DETAIL
```sql
EMPLOYEE_NAME, PAY_CODE, WORK_DATE, LOCATION, COST_CENTER, 
START_DTTM, END_DTTM, MANAGER_NAME, EMPLOYEE_ID
```

#### SCHEDULE_DETAIL  
```sql
EMPLOYEE_NAME, EMPLOYEE_ID, PAY_CODE, AS_JOB, WORK_DATE, 
SCHEDULED_START_DTTM, SCHEDULED_END_DTTM, MANAGER_NAME
```

### Output Table
```sql
Employee_Name, Employee_ID, Pay_Code, WORK_DATE, Schedule_Hours, 
Scheduled_Total, LOCATION, COST_CENTER, MANAGER_NAME, Hours_Worked, 
Hours_Worked_Outside_of_Schedule, On_Call, Pager, Regular, Overtime, 
Double_Time, Double_Time_Night, Double_Time_Swing, In_Punch1, 
in_Comments1, Out_Punch1, Out_Comments1, ..., In_Punch8, 
in_Comments8, Out_Punch8, Out_Comments8
```

## Usage

### Basic Usage
```sql
CALL `your_project.your_dataset.transform_timesheet_data`(
  DATE('2024-01-15'), 
  'your_project.your_dataset.transformed_timesheet_20240115'
);
```

### Parameters
- `input_date` (DATE): The work date to process
- `output_table_name` (STRING): Full table name for the output

## Setup Instructions

1. **Update Project/Dataset Names**: Replace `your_project.your_dataset` with your actual BigQuery project and dataset names in:
   - `timesheet_transformation_procedure.sql`
   - `usage_examples.sql`

2. **Create the Procedure**:
   ```sql
   -- Run the contents of timesheet_transformation_procedure.sql
   ```

3. **Test with Sample Data**:
   ```sql
   -- Run the sample data creation from usage_examples.sql
   -- Then test the procedure
   ```

## Data Processing Logic

### 1. Schedule Data Aggregation
- Groups schedule records by employee and date
- Calculates total scheduled hours (excluding meal breaks)
- Concatenates pay codes for reference

### 2. Timesheet Pivoting
- Calculates hours for each pay code type
- Converts datetime differences to decimal hours
- Aggregates multiple records per employee/date

### 3. Punch Processing
- Extracts all START_DTTM and END_DTTM values
- Sequences them chronologically as punch pairs
- Supports up to 8 punch pairs per day

### 4. Final Join
- Full outer join ensures all employees appear (scheduled or worked)
- Coalesces data from both sources
- Maintains referential integrity

## Key Assumptions

1. **Pay Code Mapping**: 
   - `SCHEDULE_OUTSIDE` maps to outside schedule hours
   - Standard overtime rules apply to `OT_15`
   - Double time variants are properly categorized

2. **Punch Logic**:
   - START_DTTM = Punch In
   - END_DTTM = Punch Out
   - Punches are paired sequentially

3. **Schedule Priority**:
   - Pay codes from SCHEDULE_DETAIL take precedence
   - Meal breaks are excluded from paid time calculations

## Performance Considerations

- Use date partitioning on source tables if processing large datasets
- Consider creating indexes on EMPLOYEE_ID and WORK_DATE
- The procedure processes one date at a time for optimal performance

## Error Handling

- Full outer joins prevent data loss
- COALESCE functions handle missing data gracefully
- Date parameters ensure consistent processing scope

## Maintenance

- Update pay code mappings as business rules change
- Extend punch pairs if more than 8 per day are needed
- Add new calculated columns as requirements evolve
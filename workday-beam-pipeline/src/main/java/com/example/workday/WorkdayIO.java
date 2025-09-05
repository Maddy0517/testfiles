package com.example.workday;

import org.apache.beam.sdk.io.Read;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.values.PBegin;
import org.apache.beam.sdk.values.PCollection;

import java.util.Map;

/**
 * Beam IO Transform for reading from Workday
 */
public class WorkdayIO {
    
    /**
     * Create a read transform for Workday data
     */
    public static Read read() {
        return new Read();
    }
    
    /**
     * Read transform implementation
     */
    public static class Read extends PTransform<PBegin, PCollection<WorkdayRecord>> {
        private WorkdayConfiguration config;
        private String serviceName;
        private String operationName;
        private Map<String, Object> requestParams;
        
        private Read() {}
        
        public Read withConfiguration(WorkdayConfiguration config) {
            return toBuilder().setConfig(config).build();
        }
        
        public Read withServiceName(String serviceName) {
            return toBuilder().setServiceName(serviceName).build();
        }
        
        public Read withOperationName(String operationName) {
            return toBuilder().setOperationName(operationName).build();
        }
        
        public Read withRequestParams(Map<String, Object> requestParams) {
            return toBuilder().setRequestParams(requestParams).build();
        }
        
        @Override
        public PCollection<WorkdayRecord> expand(PBegin input) {
            if (config == null) {
                throw new IllegalArgumentException("Workday configuration is required");
            }
            if (serviceName == null || serviceName.trim().isEmpty()) {
                throw new IllegalArgumentException("Service name is required");
            }
            if (operationName == null || operationName.trim().isEmpty()) {
                throw new IllegalArgumentException("Operation name is required");
            }
            
            WorkdaySource source = new WorkdaySource(config, serviceName, operationName, requestParams);
            return input.apply(org.apache.beam.sdk.io.Read.from(source));
        }
        
        private Builder toBuilder() {
            return new Builder()
                    .setConfig(config)
                    .setServiceName(serviceName)
                    .setOperationName(operationName)
                    .setRequestParams(requestParams);
        }
        
        private static class Builder {
            private WorkdayConfiguration config;
            private String serviceName;
            private String operationName;
            private Map<String, Object> requestParams;
            
            public Builder setConfig(WorkdayConfiguration config) {
                this.config = config;
                return this;
            }
            
            public Builder setServiceName(String serviceName) {
                this.serviceName = serviceName;
                return this;
            }
            
            public Builder setOperationName(String operationName) {
                this.operationName = operationName;
                return this;
            }
            
            public Builder setRequestParams(Map<String, Object> requestParams) {
                this.requestParams = requestParams;
                return this;
            }
            
            public Read build() {
                Read read = new Read();
                read.config = this.config;
                read.serviceName = this.serviceName;
                read.operationName = this.operationName;
                read.requestParams = this.requestParams;
                return read;
            }
        }
    }
}
package com.example.diagnosticservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class RestClientService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${packet.processing.timeout.connect:5000}")
    private int connectTimeoutMs;

    @Value("${packet.processing.timeout.read:30000}")
    private int readTimeoutMs;

    @Value("${packet.processing.max-retries:3}")
    private int maxRetries;

    public RestClientService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Fetches records from a REST endpoint with pagination support
     * Uses @Retryable annotation for automatic retry with exponential backoff
     *
     * @param endpointUrl The REST endpoint URL
     * @param offset The offset for pagination
     * @param limit The maximum number of records to fetch
     * @param headers Optional headers to include in the request
     * @return RestClientResponse containing the fetched data and metadata
     */
    @Retryable(
        value = {HttpServerErrorException.class, ResourceAccessException.class, Exception.class},
        exclude = {HttpClientErrorException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000)
    )
    public RestClientResponse fetchRecords(String endpointUrl, int offset, int limit, Map<String, String> headers) {
        log.debug("Fetching records from {} with offset={}, limit={}", endpointUrl, offset, limit);

        // Prepare headers
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        
        if (headers != null) {
            headers.forEach(httpHeaders::set);
        }

        // Add packet tracking metadata headers
        addPacketTrackingHeaders(httpHeaders, offset, limit);

        // Add pagination parameters to URL
        String urlWithParams = buildUrlWithPagination(endpointUrl, offset, limit);
        
        HttpEntity<String> entity = new HttpEntity<>(httpHeaders);

        // Make the request
        ResponseEntity<String> response = restTemplate.exchange(
            urlWithParams, 
            HttpMethod.GET, 
            entity, 
            String.class
        );

        return processResponse(response, endpointUrl, offset, limit);
    }

    /**
     * Recovery method called when all retry attempts fail
     */
    @Recover
    public RestClientResponse recoverFetchRecords(Exception ex, String endpointUrl, int offset, int limit, Map<String, String> headers) {
        log.error("All retry attempts failed for endpoint {}: {}", endpointUrl, ex.getMessage());
        
        String errorMessage;
        String errorCategory;
        int httpStatusCode = 0;
        
        if (ex instanceof HttpClientErrorException e) {
            errorMessage = "Client error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString();
            errorCategory = "CLIENT_ERROR";
            httpStatusCode = e.getStatusCode().value();
        } else if (ex instanceof HttpServerErrorException e) {
            errorMessage = "Server error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString();
            errorCategory = "SERVER_ERROR";
            httpStatusCode = e.getStatusCode().value();
        } else if (ex instanceof ResourceAccessException) {
            errorMessage = "Connection error: " + ex.getMessage();
            errorCategory = "CONNECTION_ERROR";
        } else {
            errorMessage = "Unexpected error: " + ex.getMessage();
            errorCategory = "UNKNOWN_ERROR";
        }
        
        return RestClientResponse.builder()
            .success(false)
            .errorMessage(errorMessage)
            .errorCategory(errorCategory)
            .httpStatusCode(httpStatusCode)
            .endpointUrl(endpointUrl)
            .offset(offset)
            .limit(limit)
            .timestamp(Instant.now())
            .build();
    }

    /**
     * Fetches records with retry logic (delegates to @Retryable method)
     */
    public RestClientResponse fetchRecordsWithRetry(String endpointUrl, int offset, int limit, Map<String, String> headers) {
        log.debug("Fetching records with retry from {} with offset={}, limit={}", endpointUrl, offset, limit);
        return fetchRecords(endpointUrl, offset, limit, headers);
    }

    /**
     * Fetches records with aggressive retry for packet processing
     * Uses more retry attempts and faster backoff for critical operations
     */
    @Retryable(
        value = {HttpServerErrorException.class, ResourceAccessException.class, Exception.class},
        exclude = {HttpClientErrorException.class},
        maxAttempts = 5,
        backoff = @Backoff(delay = 500, multiplier = 1.5, maxDelay = 5000)
    )
    public RestClientResponse fetchRecordsForPacketProcessing(String endpointUrl, int offset, int limit, Map<String, String> headers) {
        log.debug("Fetching records for packet processing from {} with offset={}, limit={}", endpointUrl, offset, limit);
        return fetchRecords(endpointUrl, offset, limit, headers);
    }

    /**
     * Recovery method for packet processing retries
     */
    @Recover
    public RestClientResponse recoverFetchRecordsForPacketProcessing(Exception ex, String endpointUrl, int offset, int limit, Map<String, String> headers) {
        log.error("All packet processing retry attempts failed for endpoint {}: {}", endpointUrl, ex.getMessage());
        
        String errorMessage;
        String errorCategory;
        int httpStatusCode = 0;
        
        if (ex instanceof HttpClientErrorException e) {
            errorMessage = "Client error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString();
            errorCategory = "CLIENT_ERROR";
            httpStatusCode = e.getStatusCode().value();
        } else if (ex instanceof HttpServerErrorException e) {
            errorMessage = "Server error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString();
            errorCategory = "SERVER_ERROR";
            httpStatusCode = e.getStatusCode().value();
        } else if (ex instanceof ResourceAccessException) {
            errorMessage = "Connection error: " + ex.getMessage();
            errorCategory = "CONNECTION_ERROR";
        } else {
            errorMessage = "Unexpected error: " + ex.getMessage();
            errorCategory = "UNKNOWN_ERROR";
        }
        
        return RestClientResponse.builder()
            .success(false)
            .errorMessage(errorMessage)
            .errorCategory(errorCategory)
            .httpStatusCode(httpStatusCode)
            .endpointUrl(endpointUrl)
            .offset(offset)
            .limit(limit)
            .timestamp(Instant.now())
            .build();
    }

    private String buildUrlWithPagination(String baseUrl, int offset, int limit) {
        StringBuilder url = new StringBuilder(baseUrl);
        
        if (baseUrl.contains("?")) {
            url.append("&");
        } else {
            url.append("?");
        }
        
        url.append("offset=").append(offset)
           .append("&limit=").append(limit);
        
        return url.toString();
    }

    private RestClientResponse processResponse(ResponseEntity<String> response, String endpointUrl, int offset, int limit) {
        try {
            String responseBody = response.getBody();
            HttpHeaders responseHeaders = response.getHeaders();
            
            // Parse the response to extract records
            List<Map<String, Object>> records = parseRecordsFromResponse(responseBody);
            
            // Extract packet metadata from response headers
            PacketMetadata packetMetadata = extractPacketMetadata(responseHeaders);
            
            // Use header metadata if available, otherwise fallback to calculated values
            int totalRecords = packetMetadata.getTotalRecords() > 0 ? packetMetadata.getTotalRecords() : records.size();
            boolean hasMoreRecords = packetMetadata.isHasMoreRecords() || (records.size() == limit);
            
            return RestClientResponse.builder()
                .success(true)
                .records(records)
                .totalRecords(totalRecords)
                .hasMoreRecords(hasMoreRecords)
                .nextOffset(packetMetadata.getNextOffset() > 0 ? packetMetadata.getNextOffset() : offset + records.size())
                .packetMetadata(packetMetadata)
                .httpStatusCode(response.getStatusCode().value())
                .responseHeaders(convertHeadersToString(responseHeaders))
                .endpointUrl(endpointUrl)
                .offset(offset)
                .limit(limit)
                .timestamp(Instant.now())
                .build();

        } catch (Exception e) {
            log.error("Error processing response from {}: {}", endpointUrl, e.getMessage(), e);
            return RestClientResponse.builder()
                .success(false)
                .errorMessage("Response processing error: " + e.getMessage())
                .errorCategory("RESPONSE_PROCESSING_ERROR")
                .httpStatusCode(response.getStatusCode().value())
                .endpointUrl(endpointUrl)
                .offset(offset)
                .limit(limit)
                .timestamp(Instant.now())
                .build();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseRecordsFromResponse(String responseBody) throws Exception {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return List.of();
        }

        Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
        
        // Try to extract records from common response patterns
        if (responseMap.containsKey("data")) {
            Object data = responseMap.get("data");
            if (data instanceof List) {
                return (List<Map<String, Object>>) data;
            }
        } else if (responseMap.containsKey("records")) {
            Object records = responseMap.get("records");
            if (records instanceof List) {
                return (List<Map<String, Object>>) records;
            }
        } else if (responseMap.containsKey("items")) {
            Object items = responseMap.get("items");
            if (items instanceof List) {
                return (List<Map<String, Object>>) items;
            }
        } else if (responseMap instanceof List) {
            return (List<Map<String, Object>>) responseMap;
        }
        
        // If no standard pattern found, wrap the entire response as a single record
        return List.of(responseMap);
    }

    private String convertHeadersToString(HttpHeaders headers) {
        try {
            return objectMapper.writeValueAsString(headers.toSingleValueMap());
        } catch (Exception e) {
            log.warn("Failed to convert headers to string: {}", e.getMessage());
            return "{}";
        }
    }


    /**
     * Adds packet tracking metadata headers to the request
     */
    private void addPacketTrackingHeaders(HttpHeaders headers, int offset, int limit) {
        // Standard packet tracking headers
        headers.set("X-Packet-Offset", String.valueOf(offset));
        headers.set("X-Packet-Limit", String.valueOf(limit));
        headers.set("X-Packet-Request-Time", String.valueOf(System.currentTimeMillis()));
        headers.set("X-Packet-Request-Id", generateRequestId());
        
        // Optional: Add more metadata headers
        headers.set("X-Packet-Client", "diagnostic-service");
        headers.set("X-Packet-Version", "1.0");
    }

    /**
     * Generates a unique request ID for tracking
     */
    private String generateRequestId() {
        return "req_" + System.currentTimeMillis() + "_" + Thread.currentThread().threadId();
    }

    /**
     * Extracts packet metadata from response headers
     */
    private PacketMetadata extractPacketMetadata(HttpHeaders responseHeaders) {
        return PacketMetadata.builder()
                .totalRecords(extractHeaderAsInt(responseHeaders, "X-Total-Records"))
                .hasMoreRecords(extractHeaderAsBoolean(responseHeaders, "X-Has-More-Records"))
                .nextOffset(extractHeaderAsInt(responseHeaders, "X-Next-Offset"))
                .currentOffset(extractHeaderAsInt(responseHeaders, "X-Current-Offset"))
                .packetSize(extractHeaderAsInt(responseHeaders, "X-Packet-Size"))
                .serverProcessingTime(extractHeaderAsLong(responseHeaders, "X-Server-Processing-Time"))
                .serverTimestamp(extractHeaderAsString(responseHeaders, "X-Server-Timestamp"))
                .build();
    }

    private int extractHeaderAsInt(HttpHeaders headers, String headerName) {
        String value = headers.getFirst(headerName);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                log.warn("Failed to parse header {} as int: {}", headerName, value);
            }
        }
        return 0;
    }

    private long extractHeaderAsLong(HttpHeaders headers, String headerName) {
        String value = headers.getFirst(headerName);
        if (value != null) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                log.warn("Failed to parse header {} as long: {}", headerName, value);
            }
        }
        return 0L;
    }

    private boolean extractHeaderAsBoolean(HttpHeaders headers, String headerName) {
        String value = headers.getFirst(headerName);
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private String extractHeaderAsString(HttpHeaders headers, String headerName) {
        return headers.getFirst(headerName);
    }

    /**
     * Response class for REST client operations
     */
    public static class RestClientResponse {
        private boolean success;
        private List<Map<String, Object>> records;
        private int totalRecords;
        private boolean hasMoreRecords;
        private int nextOffset;
        private PacketMetadata packetMetadata;
        private String errorMessage;
        private String errorCategory;
        private int httpStatusCode;
        private String responseHeaders;
        private String endpointUrl;
        private int offset;
        private int limit;
        private Instant timestamp;

        // Builder pattern
        public static RestClientResponseBuilder builder() {
            return new RestClientResponseBuilder();
        }

        public static class RestClientResponseBuilder {
            private boolean success;
            private List<Map<String, Object>> records;
            private int totalRecords;
            private boolean hasMoreRecords;
            private int nextOffset;
            private PacketMetadata packetMetadata;
            private String errorMessage;
            private String errorCategory;
            private int httpStatusCode;
            private String responseHeaders;
            private String endpointUrl;
            private int offset;
            private int limit;
            private Instant timestamp;

            public RestClientResponseBuilder success(boolean success) {
                this.success = success;
                return this;
            }

            public RestClientResponseBuilder records(List<Map<String, Object>> records) {
                this.records = records;
                return this;
            }

            public RestClientResponseBuilder totalRecords(int totalRecords) {
                this.totalRecords = totalRecords;
                return this;
            }

            public RestClientResponseBuilder hasMoreRecords(boolean hasMoreRecords) {
                this.hasMoreRecords = hasMoreRecords;
                return this;
            }

            public RestClientResponseBuilder nextOffset(int nextOffset) {
                this.nextOffset = nextOffset;
                return this;
            }

            public RestClientResponseBuilder packetMetadata(PacketMetadata packetMetadata) {
                this.packetMetadata = packetMetadata;
                return this;
            }

            public RestClientResponseBuilder errorMessage(String errorMessage) {
                this.errorMessage = errorMessage;
                return this;
            }

            public RestClientResponseBuilder errorCategory(String errorCategory) {
                this.errorCategory = errorCategory;
                return this;
            }

            public RestClientResponseBuilder httpStatusCode(int httpStatusCode) {
                this.httpStatusCode = httpStatusCode;
                return this;
            }

            public RestClientResponseBuilder responseHeaders(String responseHeaders) {
                this.responseHeaders = responseHeaders;
                return this;
            }

            public RestClientResponseBuilder endpointUrl(String endpointUrl) {
                this.endpointUrl = endpointUrl;
                return this;
            }

            public RestClientResponseBuilder offset(int offset) {
                this.offset = offset;
                return this;
            }

            public RestClientResponseBuilder limit(int limit) {
                this.limit = limit;
                return this;
            }

            public RestClientResponseBuilder timestamp(Instant timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public RestClientResponse build() {
                RestClientResponse response = new RestClientResponse();
                response.success = this.success;
                response.records = this.records;
                response.totalRecords = this.totalRecords;
                response.hasMoreRecords = this.hasMoreRecords;
                response.nextOffset = this.nextOffset;
                response.packetMetadata = this.packetMetadata;
                response.errorMessage = this.errorMessage;
                response.errorCategory = this.errorCategory;
                response.httpStatusCode = this.httpStatusCode;
                response.responseHeaders = this.responseHeaders;
                response.endpointUrl = this.endpointUrl;
                response.offset = this.offset;
                response.limit = this.limit;
                response.timestamp = this.timestamp;
                return response;
            }
        }

        // Getters
        public boolean isSuccess() { return success; }
        public List<Map<String, Object>> getRecords() { return records; }
        public int getTotalRecords() { return totalRecords; }
        public boolean isHasMoreRecords() { return hasMoreRecords; }
        public int getNextOffset() { return nextOffset; }
        public PacketMetadata getPacketMetadata() { return packetMetadata; }
        public String getErrorMessage() { return errorMessage; }
        public String getErrorCategory() { return errorCategory; }
        public int getHttpStatusCode() { return httpStatusCode; }
        public String getResponseHeaders() { return responseHeaders; }
        public String getEndpointUrl() { return endpointUrl; }
        public int getOffset() { return offset; }
        public int getLimit() { return limit; }
        public Instant getTimestamp() { return timestamp; }
    }

    /**
     * Packet metadata extracted from response headers
     */
    public static class PacketMetadata {
        private int totalRecords;
        private boolean hasMoreRecords;
        private int nextOffset;
        private int currentOffset;
        private int packetSize;
        private long serverProcessingTime;
        private String serverTimestamp;

        // Builder pattern
        public static PacketMetadataBuilder builder() {
            return new PacketMetadataBuilder();
        }

        public static class PacketMetadataBuilder {
            private int totalRecords;
            private boolean hasMoreRecords;
            private int nextOffset;
            private int currentOffset;
            private int packetSize;
            private long serverProcessingTime;
            private String serverTimestamp;

            public PacketMetadataBuilder totalRecords(int totalRecords) {
                this.totalRecords = totalRecords;
                return this;
            }

            public PacketMetadataBuilder hasMoreRecords(boolean hasMoreRecords) {
                this.hasMoreRecords = hasMoreRecords;
                return this;
            }

            public PacketMetadataBuilder nextOffset(int nextOffset) {
                this.nextOffset = nextOffset;
                return this;
            }

            public PacketMetadataBuilder currentOffset(int currentOffset) {
                this.currentOffset = currentOffset;
                return this;
            }

            public PacketMetadataBuilder packetSize(int packetSize) {
                this.packetSize = packetSize;
                return this;
            }

            public PacketMetadataBuilder serverProcessingTime(long serverProcessingTime) {
                this.serverProcessingTime = serverProcessingTime;
                return this;
            }

            public PacketMetadataBuilder serverTimestamp(String serverTimestamp) {
                this.serverTimestamp = serverTimestamp;
                return this;
            }

            public PacketMetadata build() {
                PacketMetadata metadata = new PacketMetadata();
                metadata.totalRecords = this.totalRecords;
                metadata.hasMoreRecords = this.hasMoreRecords;
                metadata.nextOffset = this.nextOffset;
                metadata.currentOffset = this.currentOffset;
                metadata.packetSize = this.packetSize;
                metadata.serverProcessingTime = this.serverProcessingTime;
                metadata.serverTimestamp = this.serverTimestamp;
                return metadata;
            }
        }

        // Getters
        public int getTotalRecords() { return totalRecords; }
        public boolean isHasMoreRecords() { return hasMoreRecords; }
        public int getNextOffset() { return nextOffset; }
        public int getCurrentOffset() { return currentOffset; }
        public int getPacketSize() { return packetSize; }
        public long getServerProcessingTime() { return serverProcessingTime; }
        public String getServerTimestamp() { return serverTimestamp; }
    }
}

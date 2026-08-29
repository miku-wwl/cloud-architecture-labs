package com.example.eventlab;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record S3EventBridgeEvent(
        String version,
        String id,
        @JsonProperty("detail-type") String detailType,
        String source,
        String account,
        String time,
        String region,
        List<String> resources,
        Detail detail) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Detail(
            String version,
            Bucket bucket,
            ObjectInfo object,
            @JsonProperty("request-id") String requestId,
            @JsonProperty("requester") String requester,
            @JsonProperty("source-ip-address") String sourceIpAddress,
            String reason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Bucket(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ObjectInfo(
            String key,
            Long size,
            String etag,
            String versionId,
            String sequencer) {
    }
}

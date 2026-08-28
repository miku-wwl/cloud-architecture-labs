package com.example.securemcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mcp")
public class McpProperties {

    private String awsEndpointUrl = "http://localhost:4566";
    private String awsRegion = "us-east-1";
    private String awsAccessKeyId = "test";
    private String awsSecretAccessKey = "test";
    private String userDataTable = "mcp-user-data";
    private boolean localOnly = true;
    private boolean allowDebugPrincipal = false;

    public String getAwsEndpointUrl() {
        return awsEndpointUrl;
    }

    public void setAwsEndpointUrl(String awsEndpointUrl) {
        this.awsEndpointUrl = awsEndpointUrl;
    }

    public String getAwsRegion() {
        return awsRegion;
    }

    public void setAwsRegion(String awsRegion) {
        this.awsRegion = awsRegion;
    }

    public String getAwsAccessKeyId() {
        return awsAccessKeyId;
    }

    public void setAwsAccessKeyId(String awsAccessKeyId) {
        this.awsAccessKeyId = awsAccessKeyId;
    }

    public String getAwsSecretAccessKey() {
        return awsSecretAccessKey;
    }

    public void setAwsSecretAccessKey(String awsSecretAccessKey) {
        this.awsSecretAccessKey = awsSecretAccessKey;
    }

    public String getUserDataTable() {
        return userDataTable;
    }

    public void setUserDataTable(String userDataTable) {
        this.userDataTable = userDataTable;
    }

    public boolean isLocalOnly() {
        return localOnly;
    }

    public void setLocalOnly(boolean localOnly) {
        this.localOnly = localOnly;
    }

    public boolean isAllowDebugPrincipal() {
        return allowDebugPrincipal;
    }

    public void setAllowDebugPrincipal(boolean allowDebugPrincipal) {
        this.allowDebugPrincipal = allowDebugPrincipal;
    }
}

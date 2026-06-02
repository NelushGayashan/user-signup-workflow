package com.example.usersignupworkflow.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowRequest {

    @JsonProperty("UserSignupProcessRequest")
    private UserSignupProcessRequest userSignupProcessRequest;

    public String getWorkflowReference() {
        if (userSignupProcessRequest == null) return null;
        return userSignupProcessRequest.getWorkflowExternalRef();
    }

    public String getUsername() {
        if (userSignupProcessRequest == null) return null;
        return userSignupProcessRequest.getUserName();
    }

    public String getTenantDomain() {
        if (userSignupProcessRequest == null) return null;
        return userSignupProcessRequest.getTenantDomain();
    }

    public String getCallBackURL() {
        if (userSignupProcessRequest == null) return null;
        return userSignupProcessRequest.getCallBackURL();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserSignupProcessRequest {
        private String userName;
        private String tenantDomain;
        private String workflowExternalRef;
        private String callBackURL;
    }
}
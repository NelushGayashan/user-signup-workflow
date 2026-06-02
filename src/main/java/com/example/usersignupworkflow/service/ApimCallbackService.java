package com.example.usersignupworkflow.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApimCallbackService {

    private final RestTemplate restTemplate;

    @Value("${wso2.apim.workflow-callback-url}")
    private String callbackUrl;

    @Value("${wso2.apim.admin-username}")
    private String adminUsername;

    @Value("${wso2.apim.admin-password}")
    private String adminPassword;

    // -------------------------------------------------------------------------

    public void approveSignup(String workflowReference, String description) {
        log.info("Approving signup — ref: {}", workflowReference);
        sendCallback(workflowReference, "APPROVED", description);
    }

    public void rejectSignup(String workflowReference, String description) {
        log.warn("Rejecting signup — ref: {}", workflowReference);
        sendCallback(workflowReference, "REJECTED", description);
    }

    // -------------------------------------------------------------------------

    private void sendCallback(String workflowReference,
                              String status,
                              String description) {

        // workflowReferenceId goes as QUERY PARAMETER not in body
        String urlWithParam = UriComponentsBuilder
                .fromHttpUrl(callbackUrl)
                .queryParam("workflowReferenceId", workflowReference)
                .build()
                .toUriString();

        // Body only contains status and description
        Map<String, String> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("description", description);

        log.info("APIM callback URL : {}", urlWithParam);
        log.info("APIM callback body: {}", body);

        HttpHeaders headers = buildAuthHeaders();
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    urlWithParam,
                    HttpMethod.POST,
                    entity,
                    String.class
            );
            log.info("APIM callback success — HTTP: {} | body: {}",
                    response.getStatusCode(), response.getBody());

        } catch (HttpClientErrorException e) {
            log.error("APIM callback 4xx [{}] | body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new RuntimeException(
                    "APIM callback client error: " + e.getStatusCode(), e);

        } catch (HttpServerErrorException e) {
            log.error("APIM callback 5xx [{}] | body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new RuntimeException(
                    "APIM callback server error: " + e.getStatusCode(), e);

        } catch (Exception e) {
            log.error("APIM callback failed: {}", e.getMessage(), e);
            throw new RuntimeException(
                    "APIM callback failed: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------

    private HttpHeaders buildAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        headers.set(HttpHeaders.AUTHORIZATION, buildBasicAuth());
        return headers;
    }

    private String buildBasicAuth() {
        String credentials = adminUsername + ":" + adminPassword;
        return "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes());
    }
}
package com.example.usersignupworkflow.service;

import com.example.usersignupworkflow.model.ScimUserResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WsoUserService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${wso2.is.host}")
    private String isHost;

    @Value("${wso2.is.admin-username}")
    private String adminUsername;

    @Value("${wso2.is.admin-password}")
    private String adminPassword;

    @Value("${wso2.is.client-id}")
    private String clientId;

    @Value("${wso2.is.client-secret}")
    private String clientSecret;

    // -------------------------------------------------------------------------

    public ScimUserResponse.ScimUser fetchUser(String rawUsername) {

        String username = sanitizeUsername(rawUsername);
        log.info("Fetching user from WSO2 IS — username: {}", username);

        try {
            // Step 1: Get Bearer token
            String token = getAccessToken();
            if (token == null) {
                log.error("Failed to get access token from WSO2 IS");
                return null;
            }

            // Step 2: Call SCIM2 with Bearer token
            String url = isHost + "/scim2/Users?filter=userName+eq+" + username;
            log.info("SCIM2 URL: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            headers.set("Accept", "application/json");

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            String body = response.getBody();
            log.info("SCIM2 response status : {}", response.getStatusCode());
            log.info("SCIM2 response body   : {}", body);

            if (body == null || body.trim().startsWith("<")) {
                log.error("SCIM2 returned HTML — unexpected");
                return null;
            }

            ScimUserResponse scimResponse =
                    objectMapper.readValue(body, ScimUserResponse.class);

            if (scimResponse.getTotalResults() == 0
                    || scimResponse.getResources() == null
                    || scimResponse.getResources().isEmpty()) {
                log.warn("No user found in WSO2 IS for username: {}", username);
                return null;
            }

            ScimUserResponse.ScimUser user = scimResponse.getResources().get(0);
            log.info("User found — name: {}, email: {}",
                    user.getFullName(), user.getPrimaryEmail());

            return user;

        } catch (Exception e) {
            log.error("Failed to fetch user '{}': {}", username, e.getMessage(), e);
            return null;
        }
    }

    // -------------------------------------------------------------------------

    private String getAccessToken() {
        try {
            String tokenUrl = isHost + "/oauth2/token";

            String credentials = clientId + ":" + clientSecret;
            String basicAuth = "Basic " + Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", basicAuth);
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "password");
            body.add("username", adminUsername);
            body.add("password", adminPassword);
            body.add("scope", "openid");

            HttpEntity<MultiValueMap<String, String>> entity =
                    new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    tokenUrl, HttpMethod.POST, entity, Map.class);

            String token = (String) response.getBody().get("access_token");
            log.info("Access token obtained successfully");
            return token;

        } catch (Exception e) {
            log.error("Failed to get access token: {}", e.getMessage(), e);
            return null;
        }
    }

    // -------------------------------------------------------------------------

    private String sanitizeUsername(String username) {
        if (username != null && username.contains("@")) {
            return username.substring(0, username.indexOf("@"));
        }
        return username;
    }
}
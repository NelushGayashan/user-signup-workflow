package com.example.usersignupworkflow.service;

import com.example.usersignupworkflow.model.ScimUserResponse;
import com.fasterxml.jackson.databind.JsonNode;
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

        // Try Method 1: SCIM2 with Bearer token
        ScimUserResponse.ScimUser user = fetchUserViaScim2(username);
        if (user != null) return user;

        // Try Method 2: SCIM2 with Basic Auth directly
        log.warn("SCIM2 Bearer failed — trying Basic Auth");
        user = fetchUserViaScim2BasicAuth(username);
        if (user != null) return user;

        log.error("All methods failed to fetch user: {}", username);
        return null;
    }

    // -------------------------------------------------------------------------
    // Method 1: SCIM2 with Bearer Token
    // -------------------------------------------------------------------------

    private ScimUserResponse.ScimUser fetchUserViaScim2(String username) {
        try {
            String token = getAccessToken();
            if (token == null) return null;

            String url = isHost + "/scim2/Users?filter=userName+eq+" + username;
            log.info("SCIM2 Bearer URL: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            headers.set("Accept", "application/json");

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            return parseScimResponse(response.getBody(), username);

        } catch (Exception e) {
            log.warn("SCIM2 Bearer method failed: {}", e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Method 2: SCIM2 with Basic Auth
    // -------------------------------------------------------------------------

    private ScimUserResponse.ScimUser fetchUserViaScim2BasicAuth(String username) {
        try {
            String url = isHost + "/scim2/Users?filter=userName+eq+" + username;
            log.info("SCIM2 BasicAuth URL: {}", url);

            String credentials = adminUsername + ":" + adminPassword;
            String basicAuth = "Basic " + Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", basicAuth);
            headers.set("Accept", "application/json");

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            String body = response.getBody();
            log.info("SCIM2 BasicAuth response: {}", body);

            if (body != null && body.trim().startsWith("{") && !body.contains("\"status\":401")) {
                return parseScimResponse(body, username);
            }

            return null;

        } catch (Exception e) {
            log.warn("SCIM2 BasicAuth method failed: {}", e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Token fetcher
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
            body.add("scope", "internal_user_mgt_view internal_user_mgt_list openid");

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    tokenUrl, HttpMethod.POST, entity, JsonNode.class);

            String token = response.getBody().get("access_token").asText();
            log.info("Access token obtained successfully");
            return token;

        } catch (Exception e) {
            log.error("Failed to get access token: {}", e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Parser
    // -------------------------------------------------------------------------

    private ScimUserResponse.ScimUser parseScimResponse(String body, String username) {
        try {
            if (body == null || body.trim().startsWith("<")) {
                log.error("Response is HTML not JSON");
                return null;
            }

            ScimUserResponse scimResponse = objectMapper.readValue(body, ScimUserResponse.class);

            if (scimResponse.getTotalResults() == 0
                    || scimResponse.getResources() == null
                    || scimResponse.getResources().isEmpty()) {
                log.warn("No user found for username: {}", username);
                return null;
            }

            ScimUserResponse.ScimUser user = scimResponse.getResources().get(0);
            log.info("User found — name: {}, email: {}",
                    user.getFullName(), user.getPrimaryEmail());
            return user;

        } catch (Exception e) {
            log.warn("Failed to parse SCIM response: {}", e.getMessage());
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
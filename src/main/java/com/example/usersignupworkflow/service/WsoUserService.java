package com.example.usersignupworkflow.service;

import com.example.usersignupworkflow.model.ScimUserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WsoUserService {

    private final RestTemplate restTemplate;

    @Value("${wso2.is.host}")
    private String isHost;

    @Value("${wso2.is.admin-username}")
    private String adminUsername;

    @Value("${wso2.is.admin-password}")
    private String adminPassword;

    // -------------------------------------------------------------------------
    // Fetch any user by username via SOAP
    // -------------------------------------------------------------------------

    public ScimUserResponse.ScimUser fetchUser(String rawUsername) {

        String username = sanitizeUsername(rawUsername);
        log.info("Fetching user via SOAP — username: {}", username);

        try {
            String soapUrl = isHost + "/services/RemoteUserStoreManagerService";

            String soapBody = """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                                  xmlns:ser="http://service.ws.um.carbon.wso2.org">
                   <soapenv:Header/>
                   <soapenv:Body>
                      <ser:getUserClaimValues>
                         <ser:userName>%s</ser:userName>
                         <ser:profileName>default</ser:profileName>
                      </ser:getUserClaimValues>
                   </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(username);

            String credentials = adminUsername + ":" + adminPassword;
            String basicAuth = "Basic " + Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", basicAuth);
            headers.set("SOAPAction", "urn:getUserClaimValues");
            headers.setContentType(MediaType.TEXT_XML);

            HttpEntity<String> entity = new HttpEntity<>(soapBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    soapUrl, HttpMethod.POST, entity, String.class);

            String responseBody = response.getBody();
            log.debug("SOAP response: {}", responseBody);

            if (responseBody == null) {
                log.error("SOAP response body is null for user: {}", username);
                return null;
            }

            String email     = extractClaim(responseBody,
                    "http://wso2.org/claims/emailaddress");
            String firstName = extractClaim(responseBody,
                    "http://wso2.org/claims/givenname");
            String lastName  = extractClaim(responseBody,
                    "http://wso2.org/claims/lastname");

            log.info("SOAP claims — email: {}, firstName: {}, lastName: {}",
                    email, firstName, lastName);

            if (email == null) {
                log.warn("No email claim found for user: {}", username);
                return null;
            }

            return buildScimUser(username, email, firstName, lastName);

        } catch (Exception e) {
            log.error("SOAP fetch failed for user '{}': {}",
                    username, e.getMessage(), e);
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Fetch admin email from WSO2 IS
    // -------------------------------------------------------------------------

    public String fetchAdminEmail() {
        log.info("Fetching admin email from WSO2 IS via SOAP");
        try {
            ScimUserResponse.ScimUser adminUser = fetchUser(adminUsername);
            if (adminUser != null && adminUser.getPrimaryEmail() != null) {
                log.info("Admin email resolved: {}", adminUser.getPrimaryEmail());
                return adminUser.getPrimaryEmail();
            }
            log.warn("Admin email not found in WSO2 IS — will use fallback");
            return null;
        } catch (Exception e) {
            log.error("Failed to fetch admin email: {}", e.getMessage(), e);
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Parse claim value from SOAP XML response
    // -------------------------------------------------------------------------

    private String extractClaim(String xml, String claimUri) {
        try {
            String uriTag = "<ax2719:claimUri>" + claimUri + "</ax2719:claimUri>";
            int uriIndex = xml.indexOf(uriTag);
            if (uriIndex == -1) {
                log.debug("Claim URI not found: {}", claimUri);
                return null;
            }

            String valueOpenTag  = "<ax2719:value>";
            String valueCloseTag = "</ax2719:value>";

            int valueStart = xml.indexOf(valueOpenTag, uriIndex);
            if (valueStart == -1) return null;

            valueStart += valueOpenTag.length();
            int valueEnd = xml.indexOf(valueCloseTag, valueStart);
            if (valueEnd == -1) return null;

            return xml.substring(valueStart, valueEnd).trim();

        } catch (Exception e) {
            log.warn("Failed to extract claim {}: {}", claimUri, e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Build ScimUser object from extracted claims
    // -------------------------------------------------------------------------

    private ScimUserResponse.ScimUser buildScimUser(String username,
                                                    String email,
                                                    String firstName,
                                                    String lastName) {
        ScimUserResponse.ScimUser user = new ScimUserResponse.ScimUser();
        user.setUserName(username);

        ScimUserResponse.EmailEntry emailEntry = new ScimUserResponse.EmailEntry();
        emailEntry.setValue(email);
        emailEntry.setPrimary(true);
        user.setEmails(List.of(emailEntry));

        ScimUserResponse.Name name = new ScimUserResponse.Name();
        name.setGivenName(firstName != null ? firstName : "");
        name.setFamilyName(lastName != null ? lastName : "");
        user.setName(name);

        return user;
    }

    // -------------------------------------------------------------------------

    private String sanitizeUsername(String username) {
        if (username != null && username.contains("@")) {
            return username.substring(0, username.indexOf("@"));
        }
        return username;
    }
}
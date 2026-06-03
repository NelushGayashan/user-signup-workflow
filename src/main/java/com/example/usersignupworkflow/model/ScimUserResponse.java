package com.example.usersignupworkflow.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScimUserResponse {

    @JsonProperty("totalResults")
    private int totalResults;

    @JsonProperty("Resources")
    private List<ScimUser> resources;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScimUser {

        private String id;
        private String userName;
        private List<EmailEntry> emails;
        private Name name;

        public String getPrimaryEmail() {
            if (emails == null || emails.isEmpty()) return null;
            return emails.stream()
                    .filter(e -> Boolean.TRUE.equals(e.getPrimary()))
                    .map(EmailEntry::getValue)
                    .findFirst()
                    .orElse(emails.get(0).getValue());
        }

        public String getFullName() {
            if (name == null) return userName;
            String first = name.getGivenName()  != null ? name.getGivenName()  : "";
            String last  = name.getFamilyName() != null ? name.getFamilyName() : "";
            String full  = (first + " " + last).trim();
            return full.isEmpty() ? userName : full;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EmailEntry {
        private String value;
        private Boolean primary;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Name {
        private String givenName;
        private String familyName;
    }
}
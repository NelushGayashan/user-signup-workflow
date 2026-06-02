package com.example.usersignupworkflow.service;

import com.example.usersignupworkflow.model.ScimUserResponse;
import com.example.usersignupworkflow.model.WorkflowRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserSignupWorkflowService {

    private final WsoUserService       wsoUserService;
    private final EmailService         emailService;
    private final ApimCallbackService  apimCallbackService;

    @Async("workflowExecutor")
    public void processAsync(WorkflowRequest request) {

        String workflowReference = request.getWorkflowReference();
        String rawUsername       = request.getUsername();
        String tenantDomain      = request.getTenantDomain();

        log.info("=====================================================");
        log.info("Processing signup workflow");
        log.info("  ref          : {}", workflowReference);
        log.info("  username     : {}", rawUsername);
        log.info("  tenantDomain : {}", tenantDomain);
        log.info("=====================================================");

        // ── Step 1: Fetch user from WSO2 IS ──────────────────────────────
        String userEmail = null;
        String fullName  = rawUsername;

        try {
            ScimUserResponse.ScimUser user =
                    wsoUserService.fetchUser(rawUsername);

            if (user != null) {
                userEmail = user.getPrimaryEmail();
                fullName  = user.getFullName();
                log.info("User resolved — name: {}, email: {}",
                        fullName, userEmail);
            } else {
                log.warn("User '{}' not found in WSO2 IS. " +
                        "Emails will be skipped.", rawUsername);
            }
        } catch (Exception e) {
            log.error("Error fetching user '{}' from IS: {}",
                    rawUsername, e.getMessage(), e);
        }

        // ── Step 2: Send signup success email to new user ─────────────────
        if (userEmail != null) {
            try {
                emailService.sendUserSignupSuccessEmail(
                        userEmail, fullName, rawUsername, tenantDomain);
            } catch (Exception e) {
                log.warn("User email failed for '{}': {}. Continuing.",
                        userEmail, e.getMessage());
            }
        } else {
            log.warn("Skipping user email — no address for '{}'", rawUsername);
        }

        // ── Step 3: Send admin alert email ────────────────────────────────
        try {
            emailService.sendAdminNewUserAlertEmail(
                    fullName,
                    rawUsername,
                    userEmail != null ? userEmail : "N/A",
                    tenantDomain
            );
        } catch (Exception e) {
            log.warn("Admin email failed: {}. Continuing.", e.getMessage());
        }

        // ── Step 4: Approve signup in APIM ────────────────────────────────
        String description = userEmail != null
                ? "User signup approved. Email notifications sent."
                : "User signup approved. Email skipped — no address in IS.";

        try {
            apimCallbackService.approveSignup(workflowReference, description);
            log.info("Signup approved in APIM — ref: {}", workflowReference);
        } catch (Exception e) {
            log.error("CRITICAL — APIM approval failed for ref: {}. " +
                            "User stays PENDING. Error: {}",
                    workflowReference, e.getMessage(), e);
        }

        log.info("=====================================================");
        log.info("Workflow complete for: {}", rawUsername);
        log.info("=====================================================");
    }
}
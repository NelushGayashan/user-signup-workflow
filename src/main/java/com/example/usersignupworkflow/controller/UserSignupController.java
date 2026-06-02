package com.example.usersignupworkflow.controller;

import com.example.usersignupworkflow.model.WorkflowRequest;
import com.example.usersignupworkflow.service.UserSignupWorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/workflow")
@RequiredArgsConstructor
@Slf4j
public class UserSignupController {

    private final UserSignupWorkflowService workflowService;

    @PostMapping("/user-signup")
    public ResponseEntity<String> handleUserSignup(
            @RequestBody WorkflowRequest request) {

        log.info("==> Received user signup workflow request");
        log.info("    workflowReference : {}", request.getWorkflowReference());
        log.info("    username          : {}", request.getUsername());
        log.info("    tenantDomain      : {}", request.getTenantDomain());
        log.info("    callBackURL       : {}", request.getCallBackURL());

        if (request.getWorkflowReference() == null) {
            log.warn("Missing workflowReference");
            return ResponseEntity.badRequest().body("Missing workflowExternalRef");
        }

        if (request.getUsername() == null) {
            log.warn("Missing username");
            return ResponseEntity.badRequest().body("Missing userName");
        }

        workflowService.processAsync(request);

        return ResponseEntity.ok("Workflow received. Processing in background.");
    }
}
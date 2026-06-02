package com.example.usersignupworkflow.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.admin-email}")
    private String adminEmail;

    @Value("${app.portal-url}")
    private String portalUrl;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // -------------------------------------------------------------------------

    public void sendUserSignupSuccessEmail(String toEmail,
                                           String fullName,
                                           String username,
                                           String tenantDomain) {
        log.info("Sending signup success email → {}", toEmail);
        try {
            Context context = new Context();
            context.setVariable("fullName",     fullName);
            context.setVariable("username",     username);
            context.setVariable("tenantDomain", tenantDomain);
            context.setVariable("portalUrl",    portalUrl);
            context.setVariable("signupDate",
                    LocalDateTime.now().format(FORMATTER));

            String html = templateEngine.process("user-signup-success", context);

            sendHtmlEmail(
                    toEmail,
                    "🎉 Welcome! Your Developer Portal Account is Ready",
                    html
            );

            log.info("Signup success email sent → {}", toEmail);

        } catch (Exception e) {
            log.error("Failed to send signup success email to {}: {}",
                    toEmail, e.getMessage(), e);
            throw new RuntimeException("Failed to send user email", e);
        }
    }

    // -------------------------------------------------------------------------

    public void sendAdminNewUserAlertEmail(String fullName,
                                           String username,
                                           String userEmail,
                                           String tenantDomain) {
        log.info("Sending admin alert email → {}", adminEmail);
        try {
            Context context = new Context();
            context.setVariable("fullName",     fullName);
            context.setVariable("username",     username);
            context.setVariable("userEmail",    userEmail);
            context.setVariable("tenantDomain", tenantDomain);
            context.setVariable("signupDate",
                    LocalDateTime.now().format(FORMATTER));

            String html = templateEngine.process("admin-new-user-alert", context);

            sendHtmlEmail(
                    adminEmail,
                    "🔔 New User Registration — " + username,
                    html
            );

            log.info("Admin alert email sent → {}", adminEmail);

        } catch (Exception e) {
            log.error("Failed to send admin alert email: {}",
                    e.getMessage(), e);
            throw new RuntimeException("Failed to send admin email", e);
        }
    }

    // -------------------------------------------------------------------------

    private void sendHtmlEmail(String to, String subject, String htmlContent)
            throws MessagingException {

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(
                message,
                MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                "UTF-8"
        );

        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);
        helper.setFrom("noreply@apiplatform.com");

        mailSender.send(message);
    }
}
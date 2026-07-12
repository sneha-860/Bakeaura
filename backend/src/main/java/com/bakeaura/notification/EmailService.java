package com.bakeaura.notification;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j

public class EmailService {

    private final JavaMailSender mailSender;

    @Value ("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Async
    public void sendVerificationEmail(String toEmail, String token) {
        String subject = "Verify your Bakeaura account";
        String verificationLink = baseUrl + "/api/auth/verify-email?token=" + token;
        String body = "<!DOCTYPE html><html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
            + "<body style='margin:0;padding:0;background:#FAF6F1;font-family:Helvetica,Arial,sans-serif;'>"
            + "<div style='max-width:560px;margin:40px auto;background:#FFFDF9;border-radius:16px;overflow:hidden;border:1px solid #E8D9C8;'>"
            + "<div style='background:#2C1810;padding:24px 32px;'>"
            + "<span style='display:inline-block;width:36px;height:36px;border-radius:50%;background:#C0603A;color:#fff;font-family:Georgia,serif;font-size:1.1rem;font-weight:bold;line-height:36px;text-align:center;'>B</span>"
            + "<span style='color:#FAF6F1;font-size:1.15rem;font-weight:700;margin-left:10px;vertical-align:middle;letter-spacing:0.01em;'>Bakeaura</span>"
            + "</div>"
            + "<div style='padding:40px 32px;'>"
            + "<h1 style='margin:0 0 12px;font-size:1.4rem;color:#2C1810;font-family:Georgia,serif;font-weight:700;'>Verify your email address</h1>"
            + "<p style='margin:0 0 28px;color:#6f625c;font-size:0.95rem;line-height:1.7;'>Welcome to Bakeaura! Click the button below to confirm your email address and activate your account. This link expires in <strong style='color:#2C1810;'>24 hours</strong>.</p>"
            + "<a href='" + verificationLink + "' style='display:inline-block;background:#C0603A;color:#ffffff;text-decoration:none;padding:14px 30px;border-radius:8px;font-weight:600;font-size:0.95rem;letter-spacing:0.02em;'>Verify my email</a>"
            + "<p style='margin:28px 0 0;color:#6f625c;font-size:0.8rem;line-height:1.6;'>Or copy and paste this link into your browser:<br><a href='" + verificationLink + "' style='color:#C0603A;word-break:break-all;'>" + verificationLink + "</a></p>"
            + "<hr style='margin:28px 0;border:none;border-top:1px solid #E8D9C8;'>"
            + "<p style='margin:0;color:#6f625c;font-size:0.8rem;line-height:1.5;'>If you didn't create a Bakeaura account you can safely ignore this email.</p>"
            + "</div>"
            + "</div>"
            + "</body></html>";
        sendEmail(toEmail, subject, body);
    }

    @Async
    public void sendOrderConfirmationEmail(String toEmail, String orderId, String sellerName){
        String subject = " Order confirmed -Bakeaura ";
        String body= "<h2>Your order is confirmed!</h2>"
                + "<p>Order ID: <strong>#" + orderId + "</strong></p>"
                + "<p>Seller: <strong>" + sellerName + "</strong></p>"
                + "<p>You can track your order in the Bakeaura app.</p>";
        sendEmail(toEmail,subject, body);
    }

    @Async
    public void sendOrderDeliveredEmail (String toEmail,String orderId){
        String subject = "Order Delivered - Bakeaura";
        String body = "<h2>Your order has been delivered!</h2>" +
                "<p>Order ID: <strong>" + orderId + "</strong></p>" +
                "<p>Enjoyed your treats? Leave a review for the baker!</p>";
        sendEmail(toEmail, subject, body);
    }

    @Async
    public void sendEmailChangeVerification(String toEmail, String token) {
        String subject = "Confirm your new email - Bakeaura";
        String verificationLink = baseUrl + "/api/auth/verify-email-change?token=" + token;
        String body = "<h2>Confirm your new email address</h2>"
                + "<p>Click the link below to confirm this email address for your Bakeaura account.</p>"
                + "<a href='" + verificationLink + "'>Confirm Email Change</a>"
                + "<p>This link expires in 24 hours.</p>"
                + "<p>If you did not request this change, please ignore this email.</p>";
        sendEmail(toEmail, subject, body);
    }

    private void sendEmail(String toEmail, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, "Bakeaura");
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(body, true);
            mailSender.send(message);
            log.info("Email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send email to {} — subject '{}': {}", toEmail, subject, e.getMessage());
        }
    }
}

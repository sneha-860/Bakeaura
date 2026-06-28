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

    @Async
    public void sendVerificationEmail(String toEmail,String token ){
        String subject = "Verify your Bakeaura Account ";
        String verificationLink = baseUrl+ "/api/auth/verify-email?token=" + token;
        String body = "<h2> Welcome to Bakeaura! </h2>" +"<p>Click the link below to verify you email Address. </p>"+
                "<a href='" + verificationLink + "'>Verify Email</a>"+
                "<p> This link Expires in 24 hours. </p>";
        sendEmail(toEmail,subject,body);
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

    private void sendEmail(String toEmail,String subject , String body){
        try {
            MimeMessage message =mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message , true , "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(body, true);
            mailSender.send(message);
            log.info("Email sent to {} ",toEmail);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", toEmail,e.getMessage());
        }
    }
}

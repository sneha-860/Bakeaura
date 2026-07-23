package com.bakeaura.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmsService {

    private final RestTemplate restTemplate;

    @Value("${fast2sms.api-key:disabled}")
    private String apiKey;

    @Async
    public void sendOrderConfirmedSms(String phone, String orderId) {
        if (phone == null || phone.isBlank()) {
            log.warn("SMS skipped for order {} — customer has no phone number", orderId);
            return;
        }
        String message = "Your Bakeaura order #" + orderId +
                " has been confirmed! Track it in the app.";
        sendSms(phone, message);
    }

    @Async
    public void sendOutForDeliverySms(String phone, String orderId) {
        if (phone == null || phone.isBlank()) {
            log.warn("SMS skipped for order {} — customer has no phone number", orderId);
            return;
        }
        String message = "Your Bakeaura order #" + orderId +
                " is out for delivery! Your treats are on the way.";
        sendSms(phone, message);
    }

    private void sendSms(String phone, String message) {
        try {
            HttpHeaders headers = new HttpHeaders();
            // API key goes in the header — never in the URL where it would appear in access logs
            headers.set("authorization", apiKey);
            headers.set("cache-control", "no-cache");
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Phone number and message go in the POST body — never in the URL
            String body = String.format(
                    "{\"numbers\":\"%s\",\"route\":\"q\",\"message\":\"%s\"}",
                    phone,
                    message.replace("\"", "\\\"")
            );

            HttpEntity<String> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    "https://www.fast2sms.com/dev/bulkV2",
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            // Log only the HTTP status — never log phone numbers
            log.info("SMS dispatched — status: {}", response.getStatusCode());

        } catch (Exception e) {
            // Don't log the exception message if it might echo the phone number back
            log.error("Failed to send SMS — check Fast2SMS credentials and phone format");
        }
    }
}

package com.bakeaura.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

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
            String url = "https://www.fast2sms.com/dev/bulkV2" +
                    "?authorization=" + apiKey +
                    "&numbers=" + phone +
                    "&route=q" +
                    "&message=" + message;

            HttpHeaders headers = new HttpHeaders();
            headers.set("cache-control", "no-cache");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            log.info("SMS sent to {} — response: {}", phone, response.getBody());

        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", phone, e.getMessage());
        }
    }
}

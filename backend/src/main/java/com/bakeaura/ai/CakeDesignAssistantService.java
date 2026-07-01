package com.bakeaura.ai;

import com.bakeaura.cloudinary.CloudinaryService;
import com.bakeaura.customorder.CustomOrderRequest;
import com.bakeaura.customorder.CustomOrderRequestService;
import com.bakeaura.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CakeDesignAssistantService {

    private final GeminiAiService geminiAiService;
    private final CloudinaryService cloudinaryService;
    private final CustomOrderRequestService customOrderRequestService;
    private final NotificationService notificationService;

    public CustomOrderRequest createCustomOrderFromDescription(
            Long customerId,
            Long sellerId,
            String customerDescription,
            String occasion,
            Integer serves,
            BigDecimal budgetMin,
            BigDecimal budgetMax) {

        String designBrief = geminiAiService.generateDesignBrief(customerDescription);
        byte[] imageBytes = geminiAiService.generateCakeImage(designBrief);
        String imageUrl = uploadGeneratedImage(imageBytes);

        CustomOrderRequest result = customOrderRequestService.submitAiGeneratedRequest(
                customerId, sellerId, designBrief, imageUrl,
                occasion, serves, budgetMin, budgetMax);

        notificationService.notifyUser(
                sellerId,
                "CUSTOM_ORDER_REQUEST",
                "New AI-generated custom cake request waiting for your response.",
                result.getId()
        );

        return result;
    }

    private String uploadGeneratedImage(byte[] imageBytes) {
        try {
            Map<String, Object> uploadResult =
                    cloudinaryService.uploadImageBytes(imageBytes, "custom-order-cakes");
            return (String) uploadResult.get("secure_url");
        } catch (IOException e) {
            log.error("Failed to upload AI-generated cake image to Cloudinary", e);
            throw new RuntimeException("Could not save the generated cake image. Please try again.");
        }
    }
}
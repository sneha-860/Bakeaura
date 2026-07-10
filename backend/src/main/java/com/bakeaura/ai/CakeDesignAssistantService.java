package com.bakeaura.ai;

import com.bakeaura.cloudinary.CloudinaryService;
import com.bakeaura.customorder.CustomOrderRequest;
import com.bakeaura.customorder.CustomOrderRequestService;
import com.bakeaura.exception.ServiceUnavailableException;
import com.bakeaura.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CakeDesignAssistantService {

    private final GeminiAiService geminiAiService;
    private final CloudinaryService cloudinaryService;
    private final CustomOrderRequestService customOrderRequestService;
    private final NotificationService notificationService;

    public CakeDesignPreviewResponseDto generatePreview(
            String description, String occasion, String referenceImageBase64) {

        String prompt = "Occasion: " + occasion + ". Customer request: " + description;

        String designBrief;
        if (referenceImageBase64 != null && !referenceImageBase64.isBlank()) {
            byte[] referenceImageBytes = Base64.getDecoder().decode(referenceImageBase64);
            String imagePrompt = prompt + " The customer has attached a reference photo showing "
                    + "the style they like. Write a design brief for a baker to recreate a similar "
                    + "style — describe it as inspired by the reference, not an exact copy.";
            designBrief = geminiAiService.generateDesignBriefFromImage(imagePrompt, referenceImageBytes);
        } else {
            designBrief = geminiAiService.generateDesignBrief(prompt);
        }

        CakeImageResult imageResult = geminiAiService.generateCakeImage(designBrief);
        String imageBase64 = imageResult.data() != null
                ? Base64.getEncoder().encodeToString(imageResult.data())
                : null;

        return new CakeDesignPreviewResponseDto(designBrief, imageBase64, imageResult.mimeType());
    }

    public CustomOrderRequest confirmAndSubmit(Long customerId, ConfirmCustomOrderDto dto) {
        String imageUrl = null;
        if (dto.getImageBase64() != null && !dto.getImageBase64().isBlank()) {
            byte[] imageBytes = Base64.getDecoder().decode(dto.getImageBase64());
            imageUrl = uploadGeneratedImage(imageBytes);
        }

        CustomOrderRequest result = customOrderRequestService.submitAiGeneratedRequest(
                customerId,
                dto.getSellerId(),
                dto.getDesignBrief(),
                imageUrl,
                dto.getOccasion(),
                dto.getServes(),
                dto.getBudgetMin(),
                dto.getBudgetMax());

        notificationService.notifyUser(
                dto.getSellerId(),
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
            throw new ServiceUnavailableException("Could not save the generated cake image. Please try again.");
        }
    }
}
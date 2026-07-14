package com.bakeaura.ai;

import com.bakeaura.cloudinary.CloudinaryService;
import com.bakeaura.customorder.CustomOrderRequestService;
import com.bakeaura.customorder.CustomOrderResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    public CakeDesignPreviewResponseDto generatePreview(
            String description, String occasion, Integer serves,
            java.math.BigDecimal budgetMin, java.math.BigDecimal budgetMax,
            String referenceImageBase64) {

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Occasion: ").append(occasion).append(". ");
        if (serves != null) promptBuilder.append("Serves: ").append(serves).append(". ");
        if (budgetMin != null && budgetMax != null) {
            promptBuilder.append("Budget: ₹").append(budgetMin).append("–₹").append(budgetMax).append(". ");
        }
        promptBuilder.append("Customer request: ").append(description);
        String prompt = promptBuilder.toString();

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

    public CustomOrderResponseDto confirmAndSubmit(Long customerId, ConfirmCustomOrderDto dto) {
        String imageUrl = null;
        if (dto.getImageBase64() != null && !dto.getImageBase64().isBlank()) {
            byte[] imageBytes = Base64.getDecoder().decode(dto.getImageBase64());
            imageUrl = uploadGeneratedImage(imageBytes);
        }

        // submitAiGeneratedRequest now sends the seller notification internally
        return customOrderRequestService.submitAiGeneratedRequest(
                customerId,
                dto.getSellerId(),
                dto.getDesignBrief(),
                imageUrl,
                dto.getOccasion(),
                dto.getServes(),
                dto.getBudgetMin(),
                dto.getBudgetMax());
    }

    private String uploadGeneratedImage(byte[] imageBytes) {
        try {
            Map<String, Object> uploadResult =
                    cloudinaryService.uploadImageBytes(imageBytes, "custom-order-cakes");
            return (String) uploadResult.get("secure_url");
        } catch (Exception e) {
            log.warn("Could not upload AI-generated image to Cloudinary — proceeding without image. Cause: {}", e.getMessage());
            return null;
        }
    }
}
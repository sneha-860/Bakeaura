package com.bakeaura.ai;

import com.bakeaura.exception.ServiceUnavailableException;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiAiService {

    private final ChatClient cakeDesignChatClient;
    private final Client googleGenAiRawClient;

    @CircuitBreaker(name = "gemini", fallbackMethod = "generateDesignBriefFallback")
    public String generateDesignBrief(String customerDescription) {
        return cakeDesignChatClient.prompt()
                .user(customerDescription)
                .call()
                .content();
    }

    public String generateDesignBriefFallback(String customerDescription, Throwable t) {
        log.error("Gemini circuit breaker triggered for design brief generation. Cause: {}", t.getMessage());
        throw new ServiceUnavailableException("Our AI design assistant is temporarily unavailable. Please try again in a moment.");
    }

    @CircuitBreaker(name = "gemini", fallbackMethod = "generateDesignBriefFromImageFallback")
    public String generateDesignBriefFromImage(String textPrompt, byte[] referenceImageBytes) {
        return cakeDesignChatClient.prompt()
                .user(u -> u.text(textPrompt)
                        .media(MimeTypeUtils.IMAGE_JPEG, new ByteArrayResource(referenceImageBytes)))
                .call()
                .content();
    }

    public String generateDesignBriefFromImageFallback(String textPrompt, byte[] referenceImageBytes, Throwable t) {
        log.error("Gemini circuit breaker triggered for image-based design brief generation. Cause: {}", t.getMessage());
        throw new ServiceUnavailableException("Our AI design assistant is temporarily unavailable. Please try again in a moment.");
    }

    @CircuitBreaker(name = "gemini", fallbackMethod = "generateCakeImageFallback")
    public CakeImageResult generateCakeImage(String designBrief) {
        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseModalities("TEXT", "IMAGE")
                .build();

        GenerateContentResponse response = googleGenAiRawClient.models.generateContent(
                "gemini-2.5-flash-image",
                "A professional, appetizing photo of this custom cake design: " + designBrief,
                config
        );

        for (Part part : response.parts()) {
            if (part.inlineData().isPresent() && part.inlineData().get().data().isPresent()) {
                String mime = part.inlineData().get().mimeType().orElse("image/png");
                return new CakeImageResult(part.inlineData().get().data().get(), mime);
            }
        }

        throw new ServiceUnavailableException("Gemini did not return an image for this cake design. Please try again.");
    }

    public CakeImageResult generateCakeImageFallback(String designBrief, Throwable t) {
        log.error("Gemini circuit breaker triggered for image generation. Cause: {}", t.getMessage());
        throw new ServiceUnavailableException("Our AI design assistant is temporarily unavailable. Please try again in a moment.");
    }
}

package com.bakeaura.ai;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

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
        throw new RuntimeException("Our AI design assistant is temporarily unavailable. Please try again in a moment.");
    }

    @CircuitBreaker(name = "gemini", fallbackMethod = "generateCakeImageFallback")
    public byte[] generateCakeImage(String designBrief) {
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
                return part.inlineData().get().data().get();
            }
        }

        throw new RuntimeException("Gemini did not return an image for this cake design.");
    }

    public byte[] generateCakeImageFallback(String designBrief, Throwable t) {
        log.error("Gemini circuit breaker triggered for image generation. Cause: {}", t.getMessage());
        throw new RuntimeException("Our AI design assistant is temporarily unavailable. Please try again in a moment.");
    }
}

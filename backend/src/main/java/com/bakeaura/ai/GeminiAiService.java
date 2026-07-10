package com.bakeaura.ai;

import com.bakeaura.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiAiService {

    private final ChatClient cakeDesignChatClient;

    // Static so Lombok @RequiredArgsConstructor ignores it — initialized once, shared across all requests
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

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

    // Uses Pollinations AI — free, no API key needed, generates real AI cake images via the Flux model.
    // Gracefully returns null data if Pollinations is unreachable; frontend handles null by showing a text-only state.
    public CakeImageResult generateCakeImage(String designBrief) {
        try {
            // Keep the prompt concise — very long URLs can cause issues; 300 chars is enough for good image generation
            String truncated = designBrief.length() > 300 ? designBrief.substring(0, 300) : designBrief;
            String prompt = "Professional bakery photography, custom cake: " + truncated;
            String encodedPrompt = URLEncoder.encode(prompt, StandardCharsets.UTF_8);
            String url = "https://image.pollinations.ai/prompt/" + encodedPrompt
                    + "?width=512&height=512&nologo=true&model=flux";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 200 && response.body() != null && response.body().length > 0) {
                log.info("Pollinations AI image generated successfully ({} bytes)", response.body().length);
                return new CakeImageResult(response.body(), "image/jpeg");
            }

            log.warn("Pollinations AI returned status {} — falling back to text-only preview", response.statusCode());
        } catch (Exception e) {
            log.warn("Image generation via Pollinations AI failed: {} — returning text-only preview", e.getMessage());
        }
        return new CakeImageResult(null, null);
    }
}

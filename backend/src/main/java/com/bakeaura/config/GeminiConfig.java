package com.bakeaura.config;

import com.google.genai.Client;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeminiConfig {

    @Value("${spring.ai.google.genai.api-key}")
    private String geminiApiKey;

    @Bean
    public ChatClient cakeDesignChatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder
                .defaultSystem("""
                        You are a professional cake design assistant for BakeAura, a hyperlocal bakery marketplace.
                        A customer describes a cake they want in natural language.
                        Turn their description into a clear, structured design brief a home baker can read
                        and immediately understand what to make.
                        Ask a clarifying question only if occasion, servings, flavor, or budget is missing.
                        Keep the brief concise and written for the baker, not the customer.
                        """)
                .build();
    }

    @Bean
    public Client googleGenAiRawClient() {
        return Client.builder()
                .apiKey(geminiApiKey)
                .build();
    }
}

package com.bakeaura.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeminiConfig {

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
}

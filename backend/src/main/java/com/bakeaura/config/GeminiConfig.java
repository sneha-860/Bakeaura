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
                        You will be given a customer's cake request along with their occasion, number of servings, and budget range.
                        Turn this into a clear, structured design brief that a home baker can read and immediately act on.
                        DO NOT ask any clarifying questions — all required information has already been collected.
                        Structure the brief with these sections: Overview, Design & Decoration, Servings & Budget.
                        If the customer mentions flavors, add a Flavor Notes section. Keep it concise and written for the baker.
                        """)
                .build();
    }
}

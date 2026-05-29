package com.bakeaura.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync  // This tells Spring: "some methods run in background threads"
public class AsyncConfig {
}

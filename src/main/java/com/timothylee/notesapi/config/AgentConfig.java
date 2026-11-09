package com.timothylee.notesapi.config;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

    @Value("${anthropic.api-key}")
    private String apiKey;

    @Bean
    public com.anthropic.client.AnthropicClient anthropicSdkClient() {
        return AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
    }
}

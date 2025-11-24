package com.example.notion_to_github.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean("notionWebClient")
    public WebClient notionWebClient(
            @Value("${notion.base-url}") String baseUrl,
            @Value("${notion.api-key}") String apiKey,
            @Value("${notion.version}") String notionVersion
    ) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Notion-Version", notionVersion)
                .build();
    }

    @Bean("githubClient")
    public WebClient githubClient(
            @Value("${github.token}") String token
    ) {
        return WebClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader("Authorization", "Bearer " + token)
                .defaultHeader("Accept", "application/vnd.github+json")
                .build();
    }
}

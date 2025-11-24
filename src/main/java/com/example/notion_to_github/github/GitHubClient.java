package com.example.notion_to_github.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.Base64;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GitHubClient {

    @Autowired
    private WebClient githubClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${github.owner}")
    private String owner;

    @Value("${github.repo}")
    private String repo;

    @Value("${github.branch}")
    private String branch;

    @Value("${github.file-path}")
    private String filePath;

    /**
     * Create or update a file in the repo with the given markdown content.
     */
    public void upsertMarkdownFile(String markdownContent) {
        String path = "/repos/" + owner + "/" + repo + "/contents/" + filePath;

        // 1. Try to get existing file to know if we need 'sha' (update)
        String sha = null;
        try {
            JsonNode existing = githubClient.get()
                    .uri(path + "?ref=" + branch)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (existing != null && existing.has("sha")) {
                sha = existing.get("sha").asText();
            }
        } catch (Exception ex) {
            // 404 or any error -> treat as new file, so sha remains null
        }

        // 2. Prepare request body
        Map<String, Object> body = new HashMap<>();
        body.put("message", "Sync Notion page at " + Instant.now());
        body.put("content", Base64.getEncoder().encodeToString(markdownContent.getBytes(StandardCharsets.UTF_8)));
        body.put("branch", branch);

        if (sha != null) {
            body.put("sha", sha);
        }

        githubClient.put()
                .uri(path)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(); // simple blocking
    }
}


package com.example.notion_to_github.github;

import com.example.notion_to_github.notion.NotionDocument;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GitHubClient {

    @Autowired
    private WebClient githubClient;

    @Value("${github.owner}")
    private String owner;

    @Value("${github.repo}")
    private String repo;

    @Value("${github.branch}")
    private String branch;

    public void upsertMarkdownFiles(List<NotionDocument> documents) {
        for (NotionDocument document : documents) {
            upsertSingleDocument(document);
        }
    }

    private void upsertSingleDocument(NotionDocument document) {
        String path = "/repos/" + owner + "/" + repo + "/contents/" + document.path();

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
        } catch (Exception ignored) {
            // No-op: the file does not exist yet.
        }

        Map<String, Object> body = new HashMap<>();
        body.put("message", "Sync Notion page at " + Instant.now() + " -> " + document.path());
        body.put("content", Base64.getEncoder().encodeToString(document.markdownContent().getBytes(StandardCharsets.UTF_8)));
        body.put("branch", branch);

        if (sha != null) {
            body.put("sha", sha);
        }

        githubClient.put()
                .uri(path)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }
}


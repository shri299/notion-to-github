package com.example.notion_to_github.notion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotionClient {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    @Qualifier("notionWebClient")
    private WebClient notionClient;

    // IMPORTANT: This is a DATABASE ID, not a page ID.
    @Value("${notion.page-id}")
    private String databaseId;

    /**
     * Entry point:
     * Fetch ALL pages inside the database and convert each page to markdown.
     */
    public String fetchPageAsMarkdown() {

        List<String> pageIds = fetchPageIdsInDatabase(databaseId);
        System.out.println("The number pages fetched: {}" + pageIds.size());
        StringBuilder md = new StringBuilder();
        int i=0;
        for (String pid : pageIds) {
            System.out.println("Processing fro page ID: {}" + pid);
            md.append("# Page\n\n");

            List<String> lines = new ArrayList<>();
            fetchBlocksRecursively(pid, lines, 0);

            md.append(String.join("\n", lines));
            md.append("\n\n---\n\n"); // separator between pages
            i++;
            if (i==6) break; // For testing, limit to first 3 pages
        }

        return md.toString();
    }

    /**
     * STEP 1:
     * Query database → return child page IDs inside the DB.
     */
    private List<String> fetchPageIdsInDatabase(String dbId) {

        JsonNode response = notionClient.post()
                .uri("/databases/" + dbId + "/query")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        List<String> pageIds = new ArrayList<>();

        if (response != null && response.has("results")) {
            for (JsonNode item : response.get("results")) {
                String id = item.get("id").asText();
                pageIds.add(id);
            }
        }

        return pageIds;
    }

    /**
     * STEP 2:
     * For a given page, recursively fetch content blocks.
     */
    private void fetchBlocksRecursively(String blockId, List<String> lines, int depth) {

        String url = "/blocks/" + blockId + "/children?page_size=100";

        JsonNode response = notionClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (response == null || !response.has("results")) {
            return;
        }

        for (JsonNode block : response.get("results")) {

            String type = block.get("type").asText();
            String id = block.get("id").asText();

            List<String> blockLines = convertBlockToMarkdown(block, type, depth);
            lines.addAll(blockLines);

            if (block.path("has_children").asBoolean(false)) {
                fetchBlocksRecursively(id, lines, depth + 1);
            }
        }
    }

    /**
     * Block → Markdown converter
     */
    private List<String> convertBlockToMarkdown(JsonNode block, String type, int depth) {

        List<String> result = new ArrayList<>();
        String indent = "  ".repeat(Math.max(0, depth));

        switch (type) {
            case "paragraph" -> {
                String text = richTextToPlain(block.path("paragraph").path("rich_text"));
                result.add(indent + (text.isEmpty() ? "" : text));
            }
            case "heading_1" -> result.add("# " + richTextToPlain(block.path("heading_1").path("rich_text")));
            case "heading_2" -> result.add("## " + richTextToPlain(block.path("heading_2").path("rich_text")));
            case "heading_3" -> result.add("### " + richTextToPlain(block.path("heading_3").path("rich_text")));

            case "bulleted_list_item" -> {
                String text = richTextToPlain(block.path("bulleted_list_item").path("rich_text"));
                result.add(indent + "- " + text);
            }

            case "numbered_list_item" -> {
                String text = richTextToPlain(block.path("numbered_list_item").path("rich_text"));
                result.add(indent + "1. " + text);
            }

            case "to_do" -> {
                boolean checked = block.path("to_do").path("checked").asBoolean(false);
                String text = richTextToPlain(block.path("to_do").path("rich_text"));
                String checkbox = checked ? "[x]" : "[ ]";
                result.add(indent + "- " + checkbox + " " + text);
            }

            case "code" -> {
                String lang = block.path("code").path("language").asText("plaintext");
                String text = richTextToPlain(block.path("code").path("rich_text"));
                result.add("```" + lang);
                result.add(text);
                result.add("```");
            }

            default -> {
                String text = richTextToPlain(block.path(type).path("rich_text"));
                if (!text.isEmpty()) result.add(indent + text);
            }
        }

        return result;
    }

    /**
     * Convert rich_text → plain string.
     */
    private String richTextToPlain(JsonNode richTextArray) {
        if (richTextArray == null || !richTextArray.isArray()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode rt : richTextArray) {
            sb.append(rt.path("plain_text").asText(""));
        }
        return sb.toString().trim();
    }
}

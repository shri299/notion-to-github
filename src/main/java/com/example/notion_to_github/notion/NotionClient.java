package com.example.notion_to_github.notion;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class NotionClient {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    @Qualifier("notionWebClient")
    private WebClient notionClient;

    @Value("${notion.page-id}")
    private String pageId;

    /**
     * Public method: fetch full page as markdown string
     */
    public String fetchPageAsMarkdown() {
        // For page content, we call children API on the page ID
        String rootBlockId = pageId;
        List<String> lines = new ArrayList<>();
        fetchBlocksRecursively(rootBlockId, lines, 0);
        return String.join("\n", lines);
    }

    /**
     * Recursive function to traverse blocks and append markdown lines
     */
    private void fetchBlocksRecursively(String blockId, List<String> lines, int depth) {
        String url = "/blocks/" + blockId + "/children?page_size=100";

        JsonNode response = notionClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(); // for simplicity, using block() here

        if (response == null || !response.has("results")) {
            return;
        }

        for (JsonNode block : response.get("results")) {
            String type = block.get("type").asText();
            String id = block.get("id").asText();

            // Convert this block to markdown line(s)
            List<String> blockLines = convertBlockToMarkdown(block, type, depth);
            lines.addAll(blockLines);

            // If block has children, recurse
            boolean hasChildren = block.path("has_children").asBoolean(false);
            if (hasChildren) {
                fetchBlocksRecursively(id, lines, depth + 1);
            }
        }
    }

    /**
     * Minimal markdown conversion for common block types.
     * You can expand this as needed.
     */
    private List<String> convertBlockToMarkdown(JsonNode block, String type, int depth) {
        List<String> result = new ArrayList<>();
        String indent = "  ".repeat(Math.max(0, depth));

        switch (type) {
            case "paragraph" -> {
                String text = richTextToPlain(block.path("paragraph").path("rich_text"));
                if (!text.isEmpty()) {
                    result.add(indent + text);
                } else {
                    result.add(""); // blank line
                }
            }
            case "heading_1" -> {
                String text = richTextToPlain(block.path("heading_1").path("rich_text"));
                result.add("# " + text);
            }
            case "heading_2" -> {
                String text = richTextToPlain(block.path("heading_2").path("rich_text"));
                result.add("## " + text);
            }
            case "heading_3" -> {
                String text = richTextToPlain(block.path("heading_3").path("rich_text"));
                result.add("### " + text);
            }
            case "bulleted_list_item" -> {
                String text = richTextToPlain(block.path("bulleted_list_item").path("rich_text"));
                result.add(indent + "- " + text);
            }
            case "numbered_list_item" -> {
                String text = richTextToPlain(block.path("numbered_list_item").path("rich_text"));
                // markdown needs the actual number; we just use "1." for simplicity
                result.add(indent + "1. " + text);
            }
            case "to_do" -> {
                boolean checked = block.path("to_do").path("checked").asBoolean(false);
                String text = richTextToPlain(block.path("to_do").path("rich_text"));
                String checkbox = checked ? "[x]" : "[ ]";
                result.add(indent + "- " + checkbox + " " + text);
            }
            case "code" -> {
                String lang = block.path("code").path("language").asText("plain text");
                String text = richTextToPlain(block.path("code").path("rich_text"));
                result.add("```" + lang);
                result.add(text);
                result.add("```");
            }
            default -> {
                // Fallback: just print the type and some text if available
                String text = richTextToPlain(block.path(type).path("rich_text"));
                if (!text.isEmpty()) {
                    result.add(indent + text);
                }
            }
        }

        return result;
    }

    /**
     * Convert Notion rich_text array to plain string
     */
    private String richTextToPlain(JsonNode richTextArray) {
        if (richTextArray == null || !richTextArray.isArray()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode rt : richTextArray) {
            String plain = rt.path("plain_text").asText("");
            sb.append(plain);
        }
        return sb.toString().trim();
    }
}


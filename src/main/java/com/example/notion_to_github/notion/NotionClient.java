package com.example.notion_to_github.notion;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class NotionClient {

    @Autowired
    @Qualifier("notionWebClient")
    private WebClient notionClient;

    /**
     * This can be either a database ID or a page ID and serves as the root for exporting.
     */
    @Value("${notion.page-id}")
    private String rootId;

    /**
     * Entry point: fetch a tree of pages beneath the provided root and convert them to Markdown
     * with paths that mirror the Notion hierarchy.
     */
    public List<NotionDocument> fetchDocuments() {
        NotionObjectType objectType = resolveRootType();

        if (objectType == NotionObjectType.DATABASE) {
            return fetchDatabaseDocuments(rootId);
        }

        return fetchPageAndChildren(rootId, "");
    }

    /**
     * STEP 1:
     * Query database → return child page IDs inside the DB.
     */
    private List<NotionDocument> fetchDatabaseDocuments(String databaseId) {
        JsonNode db = notionClient.get()
                .uri("/databases/" + databaseId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        String dbName = extractTitle(db == null ? null : db.path("title"), "database");
        String basePath = sanitizeFileName(dbName);

        JsonNode response = notionClient.post()
                .uri("/databases/" + databaseId + "/query")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        List<NotionDocument> documents = new ArrayList<>();

        if (response != null && response.has("results")) {
            for (JsonNode item : response.get("results")) {
                String id = item.path("id").asText();
                String pagePrefix = basePath.isEmpty() ? "" : basePath;
                documents.addAll(fetchPageAndChildren(id, pagePrefix));
            }
        }

        return documents;
    }

    private List<NotionDocument> fetchPageAndChildren(String pageId, String parentDirectory) {
        String title = fetchPageTitle(pageId);
        String sanitizedTitle = sanitizeFileName(title);

        List<String> lines = new ArrayList<>();
        List<PageReference> childPages = new ArrayList<>();
        fetchBlocksRecursively(pageId, lines, childPages, 0);

        String currentDirectory = combinePaths(parentDirectory, sanitizedTitle);

        List<NotionDocument> documents = new ArrayList<>();
        documents.add(new NotionDocument(combinePaths(currentDirectory, sanitizedTitle + ".md"), String.join("\n", lines)));

        for (PageReference child : childPages) {
            documents.addAll(fetchPageAndChildren(child.id(), currentDirectory));
        }

        return documents;
    }

    /**
     * STEP 2:
     * For a given page, recursively fetch content blocks while tracking child pages.
     */
    private void fetchBlocksRecursively(String blockId, List<String> lines, List<PageReference> childPages, int depth) {

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

            if ("child_page".equals(type)) {
                String title = block.path("child_page").path("title").asText("Untitled");
                childPages.add(new PageReference(id, title));
                lines.add("  ".repeat(Math.max(0, depth)) + "- " + title + " (sub-page)");
                continue;
            }

            List<String> blockLines = convertBlockToMarkdown(block, type, depth);
            lines.addAll(blockLines);

            if (block.path("has_children").asBoolean(false)) {
                fetchBlocksRecursively(id, lines, childPages, depth + 1);
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

    private NotionObjectType resolveRootType() {
        try {
            JsonNode db = notionClient.get()
                    .uri("/databases/" + rootId)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (db != null && "database".equalsIgnoreCase(db.path("object").asText())) {
                return NotionObjectType.DATABASE;
            }
        } catch (Exception ignored) {
            // If database lookup fails, fall back to page lookup.
        }

        return NotionObjectType.PAGE;
    }

    private String fetchPageTitle(String pageId) {
        JsonNode page = notionClient.get()
                .uri("/pages/" + pageId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        return extractTitle(page == null ? null : page.path("properties"), "page");
    }

    private String extractTitle(JsonNode node, String fallback) {
        if (node == null) {
            return fallback;
        }

        if (node.isArray()) {
            for (JsonNode titleNode : node) {
                String text = richTextToPlain(titleNode.path("title"));
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }

        Iterator<String> fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            String field = fieldNames.next();
            JsonNode property = node.path(field);
            if ("title".equals(property.path("type").asText())) {
                String text = richTextToPlain(property.path("title"));
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }

        return fallback;
    }

    private String sanitizeFileName(String input) {
        return input == null ? "" : input.trim().replaceAll("\\s+", "_").replaceAll("[^A-Za-z0-9_.-]", "-");
    }

    private String combinePaths(String base, String leaf) {
        if (base == null || base.isEmpty()) {
            return leaf;
        }
        if (leaf == null || leaf.isEmpty()) {
            return base;
        }
        return base + "/" + leaf;
    }

    private enum NotionObjectType {
        PAGE,
        DATABASE
    }

    private record PageReference(String id, String title) { }
}

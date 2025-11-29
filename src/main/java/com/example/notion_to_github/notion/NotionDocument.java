package com.example.notion_to_github.notion;

/**
 * Simple representation of a Notion page/database export ready to be written to GitHub.
 */
public record NotionDocument(String path, String markdownContent) {
}


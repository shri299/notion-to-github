package com.example.notion_to_github.service;
import com.example.notion_to_github.github.*;
import com.example.notion_to_github.notion.NotionClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SyncService {

    @Autowired
    private NotionClient notionClient;
    @Autowired
    private GitHubClient githubClient;

    public String syncNotionPageToGithub() {
        // 1. Fetch page content from Notion as markdown
        String markdown = notionClient.fetchPageAsMarkdown();

        // 2. Push markdown to GitHub
        githubClient.upsertMarkdownFile(markdown);

        return "Synced successfully";
    }
}


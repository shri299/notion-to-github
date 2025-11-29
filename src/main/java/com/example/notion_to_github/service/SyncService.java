package com.example.notion_to_github.service;
import com.example.notion_to_github.github.*;
import com.example.notion_to_github.notion.NotionClient;
import com.example.notion_to_github.notion.NotionDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SyncService {

    @Autowired
    private NotionClient notionClient;
    @Autowired
    private GitHubClient githubClient;

    public String syncNotionPageToGithub() {
        List<NotionDocument> documents = notionClient.fetchDocuments();
        githubClient.upsertMarkdownFiles(documents);

        return "Synced " + documents.size() + " file(s)";
    }
}


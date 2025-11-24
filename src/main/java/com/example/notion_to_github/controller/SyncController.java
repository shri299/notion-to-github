package com.example.notion_to_github.controller;

import com.example.notion_to_github.service.SyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
public class SyncController {

    @Autowired
    private SyncService syncService;

    /**
     * Simple trigger:
     * POST http://localhost:8080/api/sync/notion-to-github
     */
    @PostMapping("/notion-to-github")
    public String syncNotionToGithub() {
        return syncService.syncNotionPageToGithub();
    }

    /**
     * Optional health check:
     */
    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}


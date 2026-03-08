package com.agentrunner.api;

import java.util.List;

public record MarkdownWorkflowRegisterRequest(
        String markdownPath,
        String cli,
        String projectPath,
        List<String> mcpProfiles
) {
    public String safeMarkdownPath() {
        return markdownPath == null ? "" : markdownPath.trim();
    }

    public List<String> safeMcpProfiles() {
        return mcpProfiles == null ? List.of() : mcpProfiles;
    }
}


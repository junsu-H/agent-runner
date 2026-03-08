package com.agentrunner.api;

import java.util.List;

public record McpTerminalOpenRequest(
        String cli,
        String projectPath,
        List<String> profiles,
        String mdFilePath
) {
    public List<String> safeProfiles() {
        return profiles == null ? List.of() : profiles;
    }

    public String safeMdFilePath() {
        return mdFilePath == null ? "" : mdFilePath.trim();
    }
}


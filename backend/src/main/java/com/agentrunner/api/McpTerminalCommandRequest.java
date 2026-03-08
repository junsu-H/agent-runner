package com.agentrunner.api;

import java.util.List;

public record McpTerminalCommandRequest(
        String cli,
        String projectPath,
        List<String> profiles,
        String command
) {
    public String safeCommand() {
        return command == null ? "" : command.trim();
    }

    public List<String> safeProfiles() {
        return profiles == null ? List.of() : profiles;
    }
}


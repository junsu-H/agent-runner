package com.agentrunner.api;

import java.util.List;

public record MarkdownWorkflowResponse(
        String workflowId,
        String name,
        String sourcePath,
        String selectedCli,
        String projectPath,
        List<String> mcpProfiles,
        List<String> steps,
        String registeredAt
) {
}


package com.agentrunner.api;

import java.util.List;

public record MarkdownWorkflowTerminalLaunchResponse(
        String workflowId,
        String runId,
        String selectedCli,
        String projectPath,
        String scriptPath,
        String summary,
        List<String> outputLines
) {
}


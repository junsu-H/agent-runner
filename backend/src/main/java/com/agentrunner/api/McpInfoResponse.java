package com.agentrunner.api;

import java.util.List;

public record McpInfoResponse(
        String cli,
        String checkCommand,
        boolean connected,
        String summary,
        List<String> outputLines
) {
}


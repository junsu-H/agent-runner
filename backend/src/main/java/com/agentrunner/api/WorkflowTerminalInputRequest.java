package com.agentrunner.api;

public record WorkflowTerminalInputRequest(
        String input,
        Boolean appendNewline
) {
    public String safeInput() {
        return input == null ? "" : input;
    }

    public boolean shouldAppendNewline() {
        return appendNewline == null || appendNewline;
    }
}


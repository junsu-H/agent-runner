package com.agentrunner.workflow;

public record StepExecutionResult(
        String step,
        String command,
        int exitCode,
        boolean success,
        String logFile,
        String message
) {
}

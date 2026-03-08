package com.agentrunner.workflow;

import java.util.List;

public record WorkflowRunResult(
        boolean success,
        String selectedCli,
        String runDirectory,
        List<StepExecutionResult> steps
) {
}

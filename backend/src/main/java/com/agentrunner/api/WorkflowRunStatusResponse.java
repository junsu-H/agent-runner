package com.agentrunner.api;

import com.agentrunner.workflow.StepExecutionResult;

import java.util.List;

public record WorkflowRunStatusResponse(
        String runId,
        String status,
        String selectedCli,
        String runDirectory,
        Integer totalSteps,
        Integer completedSteps,
        Integer progressPercent,
        String currentStep,
        String currentCommand,
        Boolean cancelRequested,
        String message,
        String lastOutputLine,
        List<String> currentStepOutputTail,
        List<StepExecutionResult> steps
) {
}

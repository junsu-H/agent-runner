package com.agentrunner.workflow;

import java.io.BufferedWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

final class ActiveRun {

    enum RunStatus {
        QUEUED, RUNNING, SUCCESS, FAILED, CANCELLED
    }

    final String runId, selectedCli, runDirectory;
    final int totalSteps;
    final List<StepExecutionResult> steps = new ArrayList<>();
    final List<String> currentStepOutputTail = new ArrayList<>();
    RunStatus status = RunStatus.QUEUED;
    int completedSteps;
    String currentStep, currentCommand, message = "queued", lastOutputLine;
    boolean cancelRequested;
    LocalDateTime finishedAt;
    Process currentProcess;
    BufferedWriter currentProcessStdin;

    ActiveRun(String runId, String selectedCli, String runDirectory, int totalSteps) {
        this.runId = runId;
        this.selectedCli = selectedCli;
        this.runDirectory = runDirectory;
        this.totalSteps = totalSteps;
    }

    boolean isTerminal() {
        return status == RunStatus.SUCCESS || status == RunStatus.FAILED || status == RunStatus.CANCELLED;
    }

    static void markCancelled(ActiveRun run, String message) {
        run.status = RunStatus.CANCELLED;
        run.message = message;
        run.currentStep = null;
        run.currentCommand = null;
        run.finishedAt = LocalDateTime.now();
    }

    static void destroyProcessTree(Process process) {
        process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }
}

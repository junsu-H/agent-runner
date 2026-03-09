package com.agentrunner.workflow;

import com.agentrunner.api.RunWorkflowRequest;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static com.agentrunner.workflow.WorkflowUtils.abbreviate;
import static com.agentrunner.workflow.WorkflowUtils.shellSingleQuote;

@Component
class WorkflowRunOrchestrator {

    private static final int OUTPUT_TAIL_LIMIT = 40;

    private final WorkflowStepExecutor stepExecutor;
    private final WorkflowEventBroadcaster broadcaster;
    private final McpProfileService mcpProfileService;
    private final TerminalLaunchService terminalLaunchService;

    WorkflowRunOrchestrator(
            WorkflowStepExecutor stepExecutor,
            WorkflowEventBroadcaster broadcaster,
            McpProfileService mcpProfileService,
            TerminalLaunchService terminalLaunchService
    ) {
        this.stepExecutor = stepExecutor;
        this.broadcaster = broadcaster;
        this.mcpProfileService = mcpProfileService;
        this.terminalLaunchService = terminalLaunchService;
    }

    void execute(ActiveRun run, WorkflowStepNode head, RunWorkflowRequest request, Path runDir) {
        synchronized (run) {
            if (run.status == ActiveRun.RunStatus.CANCELLED) return;
            run.status = ActiveRun.RunStatus.RUNNING;
            run.message = "running";
        }
        broadcastStatus(run);

        List<String> mcpProfiles = request.safeMcpProfiles();
        if (!mcpProfiles.isEmpty() && !applyMcpProfilesPhase(run, request, mcpProfiles)) {
            return;
        }

        if (!executeStepsPhase(run, head, request, runDir)) {
            return;
        }

        if (request.shouldOpenTerminalAfter()) {
            openTerminalPhase(run, request);
        }

        synchronized (run) {
            if (run.cancelRequested) {
                markCancelled(run, "cancelled after last step");
            } else {
                run.status = ActiveRun.RunStatus.SUCCESS;
                run.message = "completed";
                run.finishedAt = LocalDateTime.now();
            }
        }
        broadcastStatus(run);
    }

    private boolean applyMcpProfilesPhase(ActiveRun run, RunWorkflowRequest request, List<String> profiles) {
        synchronized (run) {
            if (run.cancelRequested) {
                markCancelled(run, "cancelled before MCP setup");
                broadcastStatus(run);
                return false;
            }
            run.currentStep = "mcp-setup";
            run.currentCommand = "apply MCP profiles: " + String.join(", ", profiles);
            run.currentStepOutputTail.clear();
            run.lastOutputLine = null;
            run.message = "applying MCP profiles";
        }
        broadcastStatus(run);

        try {
            mcpProfileService.applyMcpProfiles(request.normalizedCli(), request.projectPath(), profiles, request.mcpProfilePath());
            appendOutputLine(run, "MCP profiles applied: " + String.join(", ", profiles));
            appendOutputLine(run, "cli=" + request.normalizedCli() + " projectPath=" + request.projectPath());
        } catch (Exception e) {
            synchronized (run) {
                run.status = ActiveRun.RunStatus.FAILED;
                run.message = "MCP setup failed: " + e.getMessage();
                run.currentStep = null;
                run.currentCommand = null;
                run.finishedAt = LocalDateTime.now();
            }
            broadcastStatus(run);
            return false;
        }

        synchronized (run) {
            run.currentStep = null;
            run.currentCommand = null;
        }
        broadcastStatus(run);
        return true;
    }

    private boolean executeStepsPhase(ActiveRun run, WorkflowStepNode head, RunWorkflowRequest request, Path runDir) {
        WorkflowStepNode current = head;
        while (current != null) {
            synchronized (run) {
                if (run.cancelRequested) {
                    markCancelled(run, "cancelled before next step");
                    broadcastStatus(run);
                    return false;
                }
                run.currentStep = current.getId();
                run.currentCommand = String.join(" ", current.getCommand());
                run.currentStepOutputTail.clear();
                run.lastOutputLine = null;
                run.message = "running " + current.getId();
            }
            broadcastStatus(run);

            StepExecutionResult result;
            try {
                result = stepExecutor.execute(current, request, runDir, createListener(run));
            } catch (Exception e) {
                synchronized (run) {
                    run.status = ActiveRun.RunStatus.FAILED;
                    run.message = e.getMessage();
                    run.currentStep = null;
                    run.currentCommand = null;
                    run.currentProcess = null;
                    run.currentProcessStdin = null;
                    run.finishedAt = LocalDateTime.now();
                }
                broadcastStatus(run);
                return false;
            }

            synchronized (run) {
                run.steps.add(result);
                run.completedSteps = run.steps.size();
                run.currentStep = null;
                run.currentCommand = null;
                if (!result.success() || run.cancelRequested) {
                    if (run.cancelRequested) {
                        markCancelled(run, "cancelled during " + current.getId());
                    } else {
                        run.status = ActiveRun.RunStatus.FAILED;
                        run.message = "step failed: " + current.getId();
                        run.finishedAt = LocalDateTime.now();
                    }
                    broadcastStatus(run);
                    return false;
                }
            }
            broadcastStatus(run);
            current = current.getNext();
        }
        return true;
    }

    private void openTerminalPhase(ActiveRun run, RunWorkflowRequest request) {
        synchronized (run) {
            if (run.cancelRequested) return;
            run.currentStep = "open-terminal";
            run.currentCommand = "opening system terminal";
            run.message = "opening terminal for interactive session";
        }
        broadcastStatus(run);

        try {
            String cli = request.normalizedCli();
            String projectPath = request.projectPath();
            String launchCommand = buildInteractiveLaunchCommand(cli, projectPath);
            TerminalLaunchService.TerminalLaunchResult result = terminalLaunchService.launchInSystemTerminal(projectPath, launchCommand);
            appendOutputLine(run, "terminal opened: " + result.appName());
        } catch (Exception e) {
            appendOutputLine(run, "terminal open failed (non-fatal): " + e.getMessage());
        }

        synchronized (run) {
            run.currentStep = null;
            run.currentCommand = null;
        }
        broadcastStatus(run);
    }

    private WorkflowStepExecutor.StepOutputListener createListener(ActiveRun run) {
        return new WorkflowStepExecutor.StepOutputListener() {
            @Override
            public void onProcessStarted(Process process, BufferedWriter stdin) {
                synchronized (run) {
                    run.currentProcess = process;
                    run.currentProcessStdin = stdin;
                }
                broadcastStatus(run);
            }

            @Override
            public void onOutputLine(String line) {
                appendOutputLine(run, line);
            }

            @Override
            public void onProcessFinished() {
                synchronized (run) {
                    run.currentProcess = null;
                    run.currentProcessStdin = null;
                }
                broadcastStatus(run);
            }
        };
    }

    private void appendOutputLine(ActiveRun run, String line) {
        if (line == null) return;
        String stripped = line.strip();
        if (stripped.isEmpty()) return;

        String statusName, message, currentStep, currentCommand;
        synchronized (run) {
            run.lastOutputLine = stripped;
            run.currentStepOutputTail.add(stripped);
            if (run.currentStepOutputTail.size() > OUTPUT_TAIL_LIMIT) {
                run.currentStepOutputTail.remove(0);
            }
            if (run.currentStep != null) {
                run.message = "running " + run.currentStep + " | " + abbreviate(stripped, 140);
            }
            statusName = run.status.name();
            message = run.message;
            currentStep = run.currentStep;
            currentCommand = run.currentCommand;
        }

        broadcaster.broadcastEvent(run.runId, new WorkflowEventBroadcaster.TerminalWsEvent(
                "stdout", run.runId, statusName, message, currentStep, currentCommand, stripped, null));
    }

    void broadcastStatus(ActiveRun run) {
        broadcaster.broadcastEvent(run.runId, snapshotEvent(run));
    }

    WorkflowEventBroadcaster.TerminalWsEvent snapshotEvent(ActiveRun run) {
        synchronized (run) {
            return new WorkflowEventBroadcaster.TerminalWsEvent(
                    "status", run.runId, run.status.name(), run.message,
                    run.currentStep, run.currentCommand, null, List.copyOf(run.currentStepOutputTail));
        }
    }

    private static void markCancelled(ActiveRun run, String message) {
        run.status = ActiveRun.RunStatus.CANCELLED;
        run.message = message;
        run.currentStep = null;
        run.currentCommand = null;
        run.finishedAt = LocalDateTime.now();
    }

    private static String buildInteractiveLaunchCommand(String cli, String projectPath) {
        StringBuilder sb = new StringBuilder();
        sb.append("cd ").append(shellSingleQuote(projectPath)).append("; ");
        sb.append("clear; ");
        sb.append("echo 'agent-runner workflow completed – terminal attached'; ");
        if ("claude".equals(cli)) {
            Path mcpConfig = Path.of(projectPath).resolve(".mcp.json");
            if (Files.isRegularFile(mcpConfig)) {
                sb.append("exec claude --continue --mcp-config ").append(shellSingleQuote(mcpConfig.toString()));
            } else {
                sb.append("exec claude --continue");
            }
        } else if ("copilot".equals(cli)) {
            sb.append("exec copilot");
        } else if ("gemini".equals(cli)) {
            sb.append("exec gemini");
        } else {
            sb.append("exec codex");
        }
        return sb.toString();
    }
}

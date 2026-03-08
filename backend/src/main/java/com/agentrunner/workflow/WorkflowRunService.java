package com.agentrunner.workflow;

import com.agentrunner.api.LinkedListDefinitionResponse;
import com.agentrunner.api.LinkedListNodeResponse;
import com.agentrunner.api.MarkdownWorkflowTerminalLaunchResponse;
import com.agentrunner.api.RunWorkflowRequest;
import com.agentrunner.api.WorkflowRunStatusResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.agentrunner.workflow.WorkflowUtils.RUN_ID_FORMAT;
import static com.agentrunner.workflow.WorkflowUtils.isWindows;
import static com.agentrunner.workflow.WorkflowUtils.shellSingleQuote;
import static com.agentrunner.workflow.WorkflowUtils.tailLines;

@Service
public class WorkflowRunService {

    private final LinkedListWorkflowFactory workflowFactory;
    private final WorkflowStepExecutor stepExecutor;
    private final WorkflowRunOrchestrator orchestrator;
    private final WorkflowEventBroadcaster broadcaster;
    private final McpProfileService mcpProfileService;
    private final TerminalLaunchService terminalLaunchService;
    private final WorkflowScriptService workflowScriptService;
    private final ExecutorService runExecutor = Executors.newCachedThreadPool();
    private final ConcurrentMap<String, ActiveRun> activeRuns = new ConcurrentHashMap<>();

    public WorkflowRunService(
            LinkedListWorkflowFactory workflowFactory,
            WorkflowStepExecutor stepExecutor,
            WorkflowRunOrchestrator orchestrator,
            WorkflowEventBroadcaster broadcaster,
            McpProfileService mcpProfileService,
            TerminalLaunchService terminalLaunchService,
            WorkflowScriptService workflowScriptService
    ) {
        this.workflowFactory = workflowFactory;
        this.stepExecutor = stepExecutor;
        this.orchestrator = orchestrator;
        this.broadcaster = broadcaster;
        this.mcpProfileService = mcpProfileService;
        this.terminalLaunchService = terminalLaunchService;
        this.workflowScriptService = workflowScriptService;
    }

    public LinkedListDefinitionResponse plan(RunWorkflowRequest request) {
        WorkflowStepNode head = workflowFactory.build(request);
        List<LinkedListNodeResponse> nodes = new ArrayList<>();

        WorkflowStepNode current = head;
        while (current != null) {
            String commandPreview = String.join(" ", current.getCommand());
            String nextId = current.getNext() == null ? null : current.getNext().getId();
            nodes.add(new LinkedListNodeResponse(current.getId(), current.getLabel(), current.getSkillPath(), nextId, commandPreview));
            current = current.getNext();
        }

        return new LinkedListDefinitionResponse(
                "Agent Runner Dynamic Workflow", "linked-list", request.normalizedCli(),
                head.getId(), request.normalizedSelectedSkills(), nodes
        );
    }

    public WorkflowRunResult run(RunWorkflowRequest request) throws IOException, InterruptedException {
        WorkflowStepNode head = workflowFactory.build(request);
        Path runDir = createRunDirectory();

        List<StepExecutionResult> results = new ArrayList<>();
        WorkflowStepNode current = head;
        boolean allSuccess = true;

        while (current != null) {
            StepExecutionResult result = stepExecutor.execute(current, request, runDir, null);
            results.add(result);
            if (!result.success()) {
                allSuccess = false;
                break;
            }
            current = current.getNext();
        }

        return new WorkflowRunResult(allSuccess, request.normalizedCli(), runDir.toAbsolutePath().toString(), results);
    }

    public WorkflowRunStatusResponse startAsync(RunWorkflowRequest request) throws IOException {
        WorkflowStepNode head = workflowFactory.build(request);
        int totalSteps = countSteps(head);

        String runId = RUN_ID_FORMAT.format(LocalDateTime.now()) + "-" + UUID.randomUUID().toString().substring(0, 8);
        Path runDir = Path.of("runs", runId);
        Files.createDirectories(runDir);

        ActiveRun activeRun = new ActiveRun(runId, request.normalizedCli(), runDir.toAbsolutePath().toString(), totalSteps);
        activeRuns.put(runId, activeRun);
        orchestrator.broadcastStatus(activeRun);
        runExecutor.submit(() -> orchestrator.execute(activeRun, head, request, runDir));

        return toStatusResponse(activeRun);
    }

    public WorkflowRunStatusResponse getRunStatus(String runId) {
        return toStatusResponse(requireRun(runId));
    }

    public WorkflowRunStatusResponse cancelRun(String runId) {
        ActiveRun run = requireRun(runId);
        Process processToStop;
        synchronized (run) {
            if (run.isTerminal()) {
                return toStatusResponse(run);
            }
            run.cancelRequested = true;
            run.message = "cancel requested";
            processToStop = run.currentProcess;

            if (run.status == ActiveRun.RunStatus.QUEUED) {
                ActiveRun.markCancelled(run, "cancel requested");
                orchestrator.broadcastStatus(run);
                return toStatusResponse(run);
            }
        }

        if (processToStop != null) {
            ActiveRun.destroyProcessTree(processToStop);
        }
        orchestrator.broadcastStatus(run);
        return toStatusResponse(run);
    }

    public void registerTerminalSession(String runId, WebSocketSession session) {
        ActiveRun run = requireRun(runId);
        broadcaster.registerSession(runId, session);
        broadcaster.sendInitialSnapshot(session, orchestrator.snapshotEvent(run));
    }

    public void unregisterTerminalSession(String runId, WebSocketSession session) {
        broadcaster.unregisterSession(runId, session);
    }

    public void sendTerminalInput(String runId, String input, boolean appendNewline) {
        ActiveRun run = requireRun(runId);
        String normalized = input == null ? "" : input;
        if (normalized.isEmpty() && !appendNewline) {
            return;
        }

        synchronized (run) {
            if (run.isTerminal()) {
                throw new IllegalStateException("run already finished");
            }
            if (run.currentProcessStdin == null) {
                throw new IllegalStateException("no active process that accepts stdin");
            }
            try {
                run.currentProcessStdin.write(normalized);
                if (appendNewline) {
                    run.currentProcessStdin.newLine();
                }
                run.currentProcessStdin.flush();
            } catch (IOException e) {
                throw new IllegalStateException("failed to write stdin: " + e.getMessage(), e);
            }
        }

        broadcaster.broadcastEvent(run.runId, new WorkflowEventBroadcaster.TerminalWsEvent(
                "stdin", run.runId, null, null, null, null, normalized, null));
    }

    public record GenerateWorkflowResult(String fileName, String filePath, int skillCount, List<String> skills) {}

    public GenerateWorkflowResult generateWorkflowFile(RunWorkflowRequest request) throws IOException {
        String projectPath = request.projectPath();
        String baseName = request.safeWorkflowName();

        LinkedListWorkflowFactory.UnifiedWorkflowSpec spec = workflowFactory.buildUnified(request);
        Path workflowDir = Path.of(projectPath, ".workflow");
        Files.createDirectories(workflowDir);

        String fileName = baseName + ".md";
        Path workflowMd = workflowDir.resolve(fileName);
        int seq = 1;
        while (Files.exists(workflowMd)) {
            fileName = baseName + "-" + seq + ".md";
            workflowMd = workflowDir.resolve(fileName);
            seq++;
        }
        Files.writeString(workflowMd, spec.prompt(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);

        return new GenerateWorkflowResult(
                fileName,
                workflowMd.toAbsolutePath().toString(),
                spec.skillCount(),
                request.normalizedSelectedSkills()
        );
    }

    public MarkdownWorkflowTerminalLaunchResponse launchInTerminal(RunWorkflowRequest request) throws IOException {
        String normalizedCli = request.normalizedCli();
        String projectPath = request.projectPath();
        List<String> profiles = request.safeMcpProfiles();
        String fileName = request.safeWorkflowName() + ".md";

        // 1. Verify workflow file exists
        Path workflowMd = Path.of(projectPath, ".workflow", fileName);
        if (!Files.isRegularFile(workflowMd)) {
            throw new IllegalArgumentException(".workflow/" + fileName + " 파일이 없습니다. 먼저 Md 파일을 생성하세요.");
        }

        // 2. Apply MCP profiles
        if (!profiles.isEmpty()) {
            mcpProfileService.applyMcpProfiles(normalizedCli, projectPath, profiles);
        }

        // 3. Write launch script + open terminal with CLI pre-fill
        String runId = RUN_ID_FORMAT.format(LocalDateTime.now()) + "-term-" + UUID.randomUUID().toString().substring(0, 8);
        Path runDir = Path.of("runs", runId);
        Files.createDirectories(runDir);

        Path scriptPath = workflowScriptService.writeWorkflowScript(runDir, normalizedCli, projectPath, workflowMd, fileName);

        TerminalLaunchService.TerminalLaunchResult launchResult;
        if (isWindows()) {
            launchResult = terminalLaunchService.launchScriptInSystemTerminal(projectPath, scriptPath);
        } else {
            String launchCommand = "cd " + shellSingleQuote(projectPath)
                    + "; clear; echo " + shellSingleQuote("[agent-runner] .workflow/" + fileName + " → " + normalizedCli)
                    + "; /bin/zsh " + shellSingleQuote(scriptPath.toAbsolutePath().toString());
            launchResult = terminalLaunchService.launchInSystemTerminal(projectPath, launchCommand);
        }

        List<String> output = new ArrayList<>();
        output.add("cli=" + normalizedCli);
        output.add("mcp=" + (profiles.isEmpty() ? "none" : String.join(", ", profiles)));
        output.add("workflow=" + workflowMd.toAbsolutePath());
        output.add("launcher=" + launchResult.appName());
        output.addAll(launchResult.outputLines());

        return new MarkdownWorkflowTerminalLaunchResponse(
                null, runId, normalizedCli, projectPath,
                scriptPath.toAbsolutePath().toString(), launchResult.appName() + " opened",
                tailLines(output, 120)
        );
    }

    private WorkflowRunStatusResponse toStatusResponse(ActiveRun run) {
        synchronized (run) {
            int pct = run.totalSteps == 0 ? 0 : (int) Math.round((run.completedSteps * 100.0) / run.totalSteps);
            return new WorkflowRunStatusResponse(
                    run.runId, run.status.name(), run.selectedCli, run.runDirectory,
                    run.totalSteps, run.completedSteps, pct,
                    run.currentStep, run.currentCommand, run.cancelRequested,
                    run.message, run.lastOutputLine, List.copyOf(run.currentStepOutputTail), List.copyOf(run.steps));
        }
    }

    private ActiveRun requireRun(String runId) {
        ActiveRun run = activeRuns.get(runId);
        if (run == null) throw new IllegalArgumentException("run not found: " + runId);
        return run;
    }

    private Path createRunDirectory() throws IOException {
        Path runDir = Path.of("runs", RUN_ID_FORMAT.format(LocalDateTime.now()));
        Files.createDirectories(runDir);
        return runDir;
    }

    private int countSteps(WorkflowStepNode head) {
        int count = 0;
        for (WorkflowStepNode c = head; c != null; c = c.getNext()) count++;
        return count;
    }

}

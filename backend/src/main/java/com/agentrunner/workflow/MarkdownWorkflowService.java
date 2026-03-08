package com.agentrunner.workflow;

import com.agentrunner.api.MarkdownWorkflowResponse;
import com.agentrunner.api.MarkdownWorkflowTerminalLaunchResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.agentrunner.workflow.WorkflowUtils.*;

@Service
public class MarkdownWorkflowService {

    record RegisteredMarkdownWorkflow(
            String workflowId,
            String name,
            String sourcePath,
            String selectedCli,
            String projectPath,
            List<String> mcpProfiles,
            List<String> steps,
            LocalDateTime registeredAt
    ) {
    }

    private final McpProfileService mcpProfileService;
    private final TerminalLaunchService terminalLaunchService;
    private final ConcurrentMap<String, RegisteredMarkdownWorkflow> markdownWorkflows = new ConcurrentHashMap<>();

    public MarkdownWorkflowService(McpProfileService mcpProfileService, TerminalLaunchService terminalLaunchService) {
        this.mcpProfileService = mcpProfileService;
        this.terminalLaunchService = terminalLaunchService;
    }

    public MarkdownWorkflowResponse registerMarkdownWorkflow(
            String markdownPath, String cli, String projectPath, List<String> mcpProfiles
    ) {
        String safeMarkdownPath = markdownPath == null ? "" : markdownPath.trim();
        if (safeMarkdownPath.isBlank()) {
            throw new IllegalArgumentException("markdownPath is required");
        }

        Path markdownFile = Path.of(safeMarkdownPath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(markdownFile)) {
            throw new IllegalArgumentException("workflow markdown file not found: " + markdownFile);
        }

        MarkdownWorkflowParser.ParsedMarkdownWorkflow parsed = MarkdownWorkflowParser.parseMarkdownWorkflow(markdownFile);

        String workflowId = "wf-" + UUID.randomUUID().toString().substring(0, 8);
        String selectedCli = normalizeCli(cli);
        String selectedProjectPath = resolveProjectPathForMarkdown(projectPath, markdownFile);
        List<String> selectedProfiles = MarkdownWorkflowParser.normalizeProfilesWithoutDefaults(mcpProfiles);

        RegisteredMarkdownWorkflow workflow = new RegisteredMarkdownWorkflow(
                workflowId, parsed.name(), markdownFile.toString(), selectedCli,
                selectedProjectPath, selectedProfiles, parsed.steps(), LocalDateTime.now()
        );

        markdownWorkflows.put(workflowId, workflow);
        return toResponse(workflow);
    }

    public List<MarkdownWorkflowResponse> listMarkdownWorkflows() {
        return markdownWorkflows.values().stream()
                .sorted(Comparator.comparing(RegisteredMarkdownWorkflow::registeredAt).reversed())
                .map(this::toResponse)
                .toList();
    }

    public MarkdownWorkflowTerminalLaunchResponse openRegisteredMarkdownWorkflowInTerminal(String workflowId) throws IOException {
        RegisteredMarkdownWorkflow workflow = markdownWorkflows.get(workflowId);
        if (workflow == null) {
            throw new IllegalArgumentException("workflow not found: " + workflowId);
        }

        if (!workflow.mcpProfiles().isEmpty()) {
            mcpProfileService.applyMcpProfiles(workflow.selectedCli(), workflow.projectPath(), workflow.mcpProfiles());
        }

        String runId = RUN_ID_FORMAT.format(LocalDateTime.now()) + "-md-" + UUID.randomUUID().toString().substring(0, 8);
        Path runDir = Path.of("runs", runId);
        Files.createDirectories(runDir);

        List<String> executableSteps = MarkdownWorkflowParser.normalizeMarkdownWorkflowCommands(
                workflow.selectedCli(), workflow.workflowId(), workflow.projectPath(), workflow.steps()
        );
        Path scriptPath = writeMarkdownWorkflowScript(runDir, workflow, executableSteps);

        TerminalLaunchService.TerminalLaunchResult launchResult;
        if (isWindows()) {
            launchResult = terminalLaunchService.launchScriptInSystemTerminal(workflow.projectPath(), scriptPath);
        } else {
            String launchCommand = "cd " + shellSingleQuote(workflow.projectPath())
                    + "; clear; echo " + shellSingleQuote("agent-runner markdown workflow: " + workflow.name())
                    + "; /bin/zsh " + shellSingleQuote(scriptPath.toAbsolutePath().toString());
            launchResult = terminalLaunchService.launchInSystemTerminal(workflow.projectPath(), launchCommand);
        }

        List<String> output = new ArrayList<>();
        output.add("workflowId=" + workflow.workflowId());
        output.add("name=" + workflow.name());
        output.add("source=" + workflow.sourcePath());
        output.add("steps=" + executableSteps.size());
        output.add("script=" + scriptPath.toAbsolutePath());
        output.add("launcher=" + launchResult.appName());
        output.add("runner=" + launchResult.commandText());
        output.addAll(launchResult.outputLines());

        return new MarkdownWorkflowTerminalLaunchResponse(
                workflow.workflowId(), runId, workflow.selectedCli(), workflow.projectPath(),
                scriptPath.toAbsolutePath().toString(), launchResult.appName() + " opened",
                tailLines(output, 120)
        );
    }

    private Path writeMarkdownWorkflowScript(
            Path runDir, RegisteredMarkdownWorkflow workflow, List<String> commands
    ) throws IOException {
        if (isWindows()) {
            return writeMarkdownWorkflowScriptWindows(runDir, workflow, commands);
        }
        Path scriptPath = runDir.resolve("workflow-launch.sh");
        StringBuilder sb = new StringBuilder();
        sb.append("#!/bin/zsh").append(System.lineSeparator());
        sb.append("set -u").append(System.lineSeparator());
        sb.append("cd ").append(shellSingleQuote(workflow.projectPath())).append(" || exit 1").append(System.lineSeparator());
        sb.append("echo ").append(shellSingleQuote("workflow: " + workflow.name())).append(System.lineSeparator());
        sb.append("echo ").append(shellSingleQuote("source: " + workflow.sourcePath())).append(System.lineSeparator());
        sb.append("echo").append(System.lineSeparator());
        sb.append("workflow_failed=0").append(System.lineSeparator());
        sb.append("step_status=0").append(System.lineSeparator());
        sb.append("total_steps=").append(commands.size()).append(System.lineSeparator());
        sb.append(System.lineSeparator());

        for (int i = 0; i < commands.size(); i++) {
            int index = i + 1;
            String command = commands.get(i);
            sb.append("if [ \"$workflow_failed\" -eq 0 ]; then").append(System.lineSeparator());
            sb.append("  echo ").append(shellSingleQuote("[step " + index + "/" + commands.size() + "] running")).append(System.lineSeparator());
            sb.append("  ").append(command).append(System.lineSeparator());
            sb.append("  step_status=$?").append(System.lineSeparator());
            sb.append("  if [ \"$step_status\" -ne 0 ]; then").append(System.lineSeparator());
            sb.append("    echo ").append(shellSingleQuote("[failed] step " + index + " exit=$step_status")).append(System.lineSeparator());
            sb.append("    workflow_failed=1").append(System.lineSeparator());
            sb.append("  fi").append(System.lineSeparator());
            sb.append("fi").append(System.lineSeparator());
            sb.append(System.lineSeparator());
        }

        sb.append("if [ \"$workflow_failed\" -eq 0 ]; then").append(System.lineSeparator());
        sb.append("  echo ").append(shellSingleQuote("[done] workflow completed")).append(System.lineSeparator());
        sb.append("else").append(System.lineSeparator());
        sb.append("  echo ").append(shellSingleQuote("[stopped] workflow aborted due to step failure")).append(System.lineSeparator());
        sb.append("fi").append(System.lineSeparator());
        sb.append(System.lineSeparator());
        sb.append("exec ${SHELL:-/bin/zsh} -l").append(System.lineSeparator());

        Files.writeString(scriptPath, sb.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        scriptPath.toFile().setExecutable(true, true);
        return scriptPath;
    }

    private Path writeMarkdownWorkflowScriptWindows(
            Path runDir, RegisteredMarkdownWorkflow workflow, List<String> commands
    ) throws IOException {
        Path scriptPath = runDir.resolve("workflow-launch.ps1");
        String nl = "\r\n";
        StringBuilder sb = new StringBuilder();
        sb.append("Set-Location -Path '").append(workflow.projectPath().replace("'", "''")).append("'").append(nl);
        sb.append("Write-Host 'workflow: ").append(workflow.name().replace("'", "''")).append("'").append(nl);
        sb.append("Write-Host 'source: ").append(workflow.sourcePath().replace("'", "''")).append("'").append(nl);
        sb.append("Write-Host ''").append(nl);
        sb.append("$workflow_failed = 0").append(nl);
        sb.append("$total_steps = ").append(commands.size()).append(nl);
        sb.append(nl);

        for (int i = 0; i < commands.size(); i++) {
            int index = i + 1;
            String command = commands.get(i);
            sb.append("if ($workflow_failed -eq 0) {").append(nl);
            sb.append("  Write-Host '[step ").append(index).append("/").append(commands.size()).append("] running'").append(nl);
            sb.append("  ").append(command).append(nl);
            sb.append("  if ($LASTEXITCODE -ne 0) {").append(nl);
            sb.append("    Write-Host '[failed] step ").append(index).append(" exit=$LASTEXITCODE'").append(nl);
            sb.append("    $workflow_failed = 1").append(nl);
            sb.append("  }").append(nl);
            sb.append("}").append(nl);
            sb.append(nl);
        }

        sb.append("if ($workflow_failed -eq 0) {").append(nl);
        sb.append("  Write-Host '[done] workflow completed'").append(nl);
        sb.append("} else {").append(nl);
        sb.append("  Write-Host '[stopped] workflow aborted due to step failure'").append(nl);
        sb.append("}").append(nl);

        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] content = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[bom.length + content.length];
        System.arraycopy(bom, 0, result, 0, bom.length);
        System.arraycopy(content, 0, result, bom.length, content.length);
        Files.write(scriptPath, result, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return scriptPath;
    }

    private String resolveProjectPathForMarkdown(String projectPath, Path markdownFile) {
        if (projectPath != null && !projectPath.isBlank()) {
            return normalizeProjectPath(projectPath);
        }
        Path parent = markdownFile.getParent();
        if (parent != null) {
            return parent.toAbsolutePath().normalize().toString();
        }
        return normalizeProjectPath(null);
    }

    private MarkdownWorkflowResponse toResponse(RegisteredMarkdownWorkflow workflow) {
        return new MarkdownWorkflowResponse(
                workflow.workflowId(), workflow.name(), workflow.sourcePath(),
                workflow.selectedCli(), workflow.projectPath(), workflow.mcpProfiles(),
                workflow.steps(), workflow.registeredAt().toString()
        );
    }
}

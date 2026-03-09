package com.agentrunner.workflow;

import com.agentrunner.api.McpInfoResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.agentrunner.workflow.WorkflowUtils.*;

@Service
public class McpConnectionService {

    private static final int MCP_CHECK_TIMEOUT_SECONDS = 45;

    private final McpProfileService mcpProfileService;
    private final TerminalLaunchService terminalLaunchService;

    public McpConnectionService(McpProfileService mcpProfileService, TerminalLaunchService terminalLaunchService) {
        this.mcpProfileService = mcpProfileService;
        this.terminalLaunchService = terminalLaunchService;
    }

    public McpInfoResponse checkMcpInfo(String cli, String projectPath) {
        return checkMcpInfoInternal(normalizeCli(cli), normalizeProjectPath(projectPath));
    }

    public McpInfoResponse precheckMcp(String cli, String projectPath, List<String> profiles, boolean applyConfig) {
        String normalizedCli = normalizeCli(cli);
        String normalizedProjectPath = normalizeProjectPath(projectPath);

        if (applyConfig) {
            mcpProfileService.applyMcpProfiles(normalizedCli, normalizedProjectPath, profiles, null);
        }

        return checkMcpInfoInternal(normalizedCli, normalizedProjectPath);
    }

    public McpInfoResponse connectMcp(String cli, String projectPath, List<String> profiles) {
        String normalizedCli = normalizeCli(cli);
        String normalizedProjectPath = normalizeProjectPath(projectPath);
        List<String> normalizedProfiles = mcpProfileService.normalizeProfiles(profiles);

        mcpProfileService.applyMcpProfiles(normalizedCli, normalizedProjectPath, normalizedProfiles, null);

        List<String> output = new ArrayList<>();
        output.add("MCP profiles applied");
        output.add("cli=" + normalizedCli);
        output.add("projectPath=" + normalizedProjectPath);
        output.add("profiles=" + String.join(", ", normalizedProfiles));
        Path root = Path.of(normalizedProjectPath);
        output.add("generated=" + root.resolve(".mcp.json"));
        output.add("generated=" + root.resolve(".vscode/mcp.json"));
        output.add("generated=" + root.resolve(".gemini/settings.json"));
        output.add("generated=" + root.resolve("mcp-servers.toml"));
        output.add("linked=" + root.resolve(".codex/config.toml"));

        return new McpInfoResponse(normalizedCli, "mcp connect", true, "MCP profiles applied", output);
    }

    public McpInfoResponse runTerminalCommand(String cli, String projectPath, List<String> profiles, String rawCommand) {
        String normalizedCli = normalizeCli(cli);
        String normalizedProjectPath = normalizeProjectPath(projectPath);
        String command = rawCommand == null ? "" : rawCommand.trim();
        if (command.isBlank()) {
            throw new IllegalArgumentException("command is required");
        }

        if (!profiles.isEmpty()) {
            mcpProfileService.applyMcpProfiles(normalizedCli, normalizedProjectPath, profiles, null);
        }

        List<String> processCommand = buildTerminalCommand(normalizedCli, normalizedProjectPath, command);
        TerminalLaunchService.McpCommandResult result = terminalLaunchService.runCommand(processCommand, normalizedProjectPath, 90);

        List<String> lines = new ArrayList<>();
        lines.add("$ " + command);
        lines.addAll(result.outputLines());
        if (result.timedOut()) {
            lines.add("(timeout)");
        } else if (result.exitCode() != 0) {
            lines.add("(exit=" + result.exitCode() + ")");
        }

        boolean success = !result.timedOut() && result.exitCode() == 0;
        String summary = success ? "terminal command finished" : "terminal command failed";

        return new McpInfoResponse(normalizedCli, result.commandText(), success, summary, tailLines(lines, 300));
    }

    public McpInfoResponse openSystemTerminal(String cli, String projectPath, List<String> profiles, String mdFilePath) {
        String normalizedCli = normalizeCli(cli);
        String normalizedProjectPath = normalizeProjectPath(projectPath);

        if (!profiles.isEmpty()) {
            mcpProfileService.applyMcpProfiles(normalizedCli, normalizedProjectPath, profiles, null);
        }

        try {
            Path scriptPath = McpLaunchScriptWriter.write(normalizedCli, normalizedProjectPath, mdFilePath);

            TerminalLaunchService.TerminalLaunchResult launchResult;
            if (isWindows()) {
                launchResult = terminalLaunchService.launchScriptInSystemTerminal(normalizedProjectPath, scriptPath);
            } else {
                String launchCommand = "cd " + shellSingleQuote(normalizedProjectPath)
                        + "; clear; echo " + shellSingleQuote("agent-runner MCP terminal attached")
                        + "; /bin/zsh " + shellSingleQuote(scriptPath.toAbsolutePath().toString());
                launchResult = terminalLaunchService.launchInSystemTerminal(normalizedProjectPath, launchCommand);
            }

            List<String> lines = new ArrayList<>();
            lines.add(launchResult.appName() + " opened");
            lines.add("script=" + scriptPath.toAbsolutePath());
            lines.add("runner=" + launchResult.commandText());
            lines.addAll(launchResult.outputLines());

            return new McpInfoResponse(normalizedCli, "open-system-terminal", true, launchResult.appName() + " opened", lines);
        } catch (IOException e) {
            throw new IllegalStateException("failed to write mcp launch script: " + e.getMessage(), e);
        }
    }

    private McpInfoResponse checkMcpInfoInternal(String cli, String projectPath) {
        List<List<String>> candidates = mcpCheckCandidates(cli, projectPath);
        List<String> mergedOutput = new ArrayList<>();
        TerminalLaunchService.McpCommandResult lastResult = null;

        for (List<String> command : candidates) {
            TerminalLaunchService.McpCommandResult result = terminalLaunchService.runCommand(command, projectPath, MCP_CHECK_TIMEOUT_SECONDS);
            lastResult = result;

            mergedOutput.add("$ " + result.commandText());
            mergedOutput.addAll(result.outputLines());

            if (result.timedOut()) {
                mergedOutput.add("(timeout)");
                continue;
            }

            if (result.exitCode() == 0) {
                boolean connected = inferConnectedFromOutput(result.outputLines());
                String summary = connected
                        ? "MCP connection verified"
                        : "MCP command responded, but connection looks disconnected";

                return new McpInfoResponse(cli, result.commandText(), connected, summary, tailLines(mergedOutput, 60));
            }

            mergedOutput.add("(exit=" + result.exitCode() + ")");
        }

        String fallbackCommand = lastResult == null ? cli + " mcp list" : lastResult.commandText();
        return new McpInfoResponse(cli, fallbackCommand, false, "Unable to verify MCP connection via CLI command", tailLines(mergedOutput, 60));
    }

    private static List<List<String>> mcpCheckCandidates(String cli, String projectPath) {
        List<List<String>> candidates = new ArrayList<>();

        switch (cli) {
            case "claude" -> {
                Path claudeMcpConfig = Path.of(projectPath).resolve(".mcp.json");
                if (Files.isRegularFile(claudeMcpConfig)) {
                    candidates.add(List.of("claude", "--mcp-config", claudeMcpConfig.toString(), "-p", "/mcp"));
                    candidates.add(List.of("claude", "--mcp-config", claudeMcpConfig.toString(), "mcp", "list"));
                } else {
                    candidates.add(List.of("claude", "-p", "/mcp"));
                }
                candidates.add(List.of("claude", "mcp", "list"));
            }
            case "copilot" -> candidates.add(List.of("copilot", "-p", "/mcp"));
            case "gemini" -> candidates.add(List.of("gemini", "-p", "/mcp"));
            case "codex" -> {
                candidates.add(List.of("codex", "mcp", "list", "--json"));
                candidates.add(List.of("codex", "mcp", "list"));
            }
            default -> throw new IllegalArgumentException("unsupported CLI: " + cli);
        }

        return candidates;
    }

    private List<String> buildTerminalCommand(String cli, String projectPath, String command) {
        String trimmed = command.trim();
        if (trimmed.startsWith("/")) {
            if ("claude".equals(cli)) {
                List<String> built = new ArrayList<>();
                Path claudeMcpConfig = Path.of(projectPath).resolve(".mcp.json");
                built.add("claude");
                if (Files.isRegularFile(claudeMcpConfig)) {
                    built.add("--mcp-config");
                    built.add(claudeMcpConfig.toString());
                }
                built.add("-p");
                built.add(trimmed);
                return built;
            }

            if ("copilot".equals(cli)) {
                return List.of("copilot", "-p", trimmed);
            }

            if ("gemini".equals(cli)) {
                return List.of("gemini", "-p", trimmed);
            }

            if ("/mcp".equals(trimmed) || trimmed.startsWith("/mcp ")) {
                return List.of("codex", "mcp", "list");
            }
            return List.of("codex", "exec", trimmed, "--sandbox", "read-only", "--skip-git-repo-check");
        }

        if (isWindows()) {
            return List.of("powershell", "-NoProfile", "-Command", trimmed);
        }
        return List.of("/bin/zsh", "-lc", trimmed);
    }

    private static boolean inferConnectedFromOutput(List<String> outputLines) {
        if (outputLines == null || outputLines.isEmpty()) {
            return true;
        }
        String joined = String.join("\n", outputLines).toLowerCase(Locale.ROOT);
        if (joined.contains("disconnected")
                || joined.contains("not connected")
                || joined.contains("0 connected")
                || joined.contains("no mcp servers configured")) {
            return false;
        }
        return true;
    }
}

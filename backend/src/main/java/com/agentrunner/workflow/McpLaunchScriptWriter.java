package com.agentrunner.workflow;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import static com.agentrunner.workflow.WorkflowUtils.isWindows;
import static com.agentrunner.workflow.WorkflowUtils.shellSingleQuote;

final class McpLaunchScriptWriter {

    private McpLaunchScriptWriter() {}

    static Path write(String cli, String projectPath, String mdFilePath) throws IOException {
        if (isWindows()) {
            return writeWindows(cli, projectPath, mdFilePath);
        }
        return writeMac(cli, projectPath, mdFilePath);
    }

    private static Path writeMac(String cli, String projectPath, String mdFilePath) throws IOException {
        Path runDir = Files.createTempDirectory("agent-runner-mcp-");
        Path scriptPath = runDir.resolve("mcp-launch.sh");

        String cliCommand;
        if ("claude".equals(cli)) {
            Path mcpConfig = Path.of(projectPath).resolve(".mcp.json");
            StringBuilder sb = new StringBuilder("claude");
            if (Files.isRegularFile(mcpConfig)) {
                sb.append(" --mcp-config ").append(shellSingleQuote(mcpConfig.toString()));
            }
            if (mdFilePath != null && !mdFilePath.isBlank()) {
                List<String> paths = new ArrayList<>();
                for (String p : mdFilePath.split("\\R")) {
                    String trimmed = p.trim();
                    if (!trimmed.isEmpty()) paths.add(trimmed);
                }
                if (!paths.isEmpty()) {
                    StringBuilder prompt = new StringBuilder();
                    for (String p : paths) {
                        prompt.append("@").append(p).append(" ");
                    }
                    prompt.append("\uc0dd\uc131\ub41c \uc815\ubcf4\ub97c \ud50c\ub79c\ubaa8\ub4dc\ub85c \uc2e4\ud589\ud574 \uc918");
                    sb.append(" -p ").append(shellSingleQuote(prompt.toString()));
                }
            }
            cliCommand = sb.toString();
        } else if ("copilot".equals(cli)) {
            Path copilotMcpConfig = Path.of(projectPath).resolve(".vscode").resolve("mcp.json");
            if (Files.isRegularFile(copilotMcpConfig)) {
                cliCommand = "copilot --additional-mcp-config " + shellSingleQuote(copilotMcpConfig.toString());
            } else {
                cliCommand = "copilot";
            }
        } else if ("gemini".equals(cli)) {
            // gemini reads .gemini/settings.json automatically from CWD
            cliCommand = "gemini";
        } else {
            // codex reads .codex/config.toml automatically from CWD
            cliCommand = "codex";
        }

        String script = """
                #!/bin/zsh
                set +e
                echo 'cli=%s'
                echo
                %s
                echo
                echo '[agent-runner] CLI exited. Keeping terminal alive.'
                exec ${SHELL:-/bin/zsh} -l
                """.formatted(cli, cliCommand);

        Files.writeString(scriptPath, script, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        scriptPath.toFile().setExecutable(true, true);
        return scriptPath;
    }

    private static Path writeWindows(String cli, String projectPath, String mdFilePath) throws IOException {
        Path runDir = Files.createTempDirectory("agent-runner-mcp-");
        Path scriptPath = runDir.resolve("mcp-launch.ps1");

        String cliCommand;
        if ("claude".equals(cli)) {
            Path mcpConfig = Path.of(projectPath).resolve(".mcp.json");
            StringBuilder sb = new StringBuilder("& claude");
            if (Files.isRegularFile(mcpConfig)) {
                sb.append(" --mcp-config '").append(mcpConfig.toString().replace("'", "''")).append("'");
            }
            if (mdFilePath != null && !mdFilePath.isBlank()) {
                List<String> paths = new ArrayList<>();
                for (String p : mdFilePath.split("\\R")) {
                    String trimmed = p.trim();
                    if (!trimmed.isEmpty()) paths.add(trimmed);
                }
                if (!paths.isEmpty()) {
                    StringBuilder prompt = new StringBuilder();
                    for (String p : paths) {
                        prompt.append("@").append(p).append(" ");
                    }
                    prompt.append("\uc0dd\uc131\ub41c \uc815\ubcf4\ub97c \ud50c\ub79c\ubaa8\ub4dc\ub85c \uc2e4\ud589\ud574 \uc918");
                    sb.append(" -p '").append(prompt.toString().replace("'", "''")).append("'");
                }
            }
            cliCommand = sb.toString();
        } else if ("copilot".equals(cli)) {
            Path copilotMcpConfig = Path.of(projectPath).resolve(".vscode").resolve("mcp.json");
            if (Files.isRegularFile(copilotMcpConfig)) {
                cliCommand = "& copilot --additional-mcp-config '" + copilotMcpConfig.toString().replace("'", "''") + "'";
            } else {
                cliCommand = "& copilot";
            }
        } else if ("gemini".equals(cli)) {
            // gemini reads .gemini/settings.json automatically from CWD
            cliCommand = "& gemini";
        } else {
            // codex reads .codex/config.toml automatically from CWD
            cliCommand = "& codex";
        }

        String nl = "\r\n";
        StringBuilder sb = new StringBuilder();
        sb.append("Set-Location -Path '").append(projectPath.replace("'", "''")).append("'").append(nl);
        sb.append("Write-Host 'cli=").append(cli).append("'").append(nl);
        sb.append("Write-Host ''").append(nl);
        sb.append(cliCommand).append(nl);
        sb.append("Write-Host ''").append(nl);
        sb.append("Write-Host '[agent-runner] CLI exited. Keeping terminal alive.'").append(nl);

        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] content = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[bom.length + content.length];
        System.arraycopy(bom, 0, result, 0, bom.length);
        System.arraycopy(content, 0, result, bom.length, content.length);
        Files.write(scriptPath, result, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return scriptPath;
    }
}

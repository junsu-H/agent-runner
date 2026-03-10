package com.agentrunner.workflow;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static com.agentrunner.workflow.WorkflowUtils.isWindows;
import static com.agentrunner.workflow.WorkflowUtils.shellSingleQuote;

@Component
class WorkflowScriptService {

    Path writeWorkflowScript(Path runDir, String cli, String projectPath, Path workflowMd, String fileName) throws IOException {
        if (isWindows()) {
            return writeWorkflowScriptWindows(runDir, cli, projectPath, fileName);
        }
        return writeWorkflowScriptMac(runDir, cli, projectPath, fileName);
    }

    private Path writeWorkflowScriptMac(Path runDir, String cli, String projectPath, String fileName) throws IOException {
        Path scriptPath = runDir.resolve("workflow-launch.sh");
        String nl = "\n";
        String preFillText = getCliPreFillText(cli, projectPath, fileName);
        String cliCommand = buildInteractiveCliCommand(cli, projectPath);

        StringBuilder sb = new StringBuilder();
        sb.append("#!/bin/zsh").append(nl);
        sb.append("set +e").append(nl);
        sb.append("cd ").append(shellSingleQuote(projectPath)).append(" || exit 1").append(nl);
        sb.append(nl);

        // Copy pre-fill text to clipboard
        sb.append("echo -n ").append(shellSingleQuote(preFillText)).append(" | pbcopy").append(nl);
        sb.append("echo '[agent-runner] Cmd+V 붙여넣기 후 Enter'").append(nl);
        sb.append("echo").append(nl);
        sb.append(nl);

        // Unset CLAUDECODE to avoid "nested session" error
        sb.append("unset CLAUDECODE").append(nl);
        sb.append(nl);

        // Launch CLI interactively (foreground)
        sb.append(cliCommand).append(nl);
        sb.append(nl);
        sb.append("exec ${SHELL:-/bin/zsh} -l").append(nl);

        Files.writeString(scriptPath, sb.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        scriptPath.toFile().setExecutable(true, true);
        return scriptPath;
    }

    private Path writeWorkflowScriptWindows(Path runDir, String cli, String projectPath, String fileName) throws IOException {
        Path scriptPath = runDir.resolve("workflow-launch.ps1");
        String nl = "\r\n";
        String preFillText = getCliPreFillText(cli, projectPath, fileName);
        String cliCommand = buildInteractiveCliCommandWindows(cli, projectPath);

        StringBuilder sb = new StringBuilder();
        sb.append("Set-Location -Path '").append(projectPath.replace("'", "''")).append("'").append(nl);
        sb.append(nl);

        // Copy pre-fill text to clipboard
        sb.append("Set-Clipboard -Value '").append(preFillText.replace("'", "''")).append("'").append(nl);
        sb.append("Write-Host \"[agent-runner] Ctrl+V -> Enter\"").append(nl);
        sb.append("Write-Host ''").append(nl);
        sb.append(nl);

        // Unset CLAUDECODE to avoid "nested session" error
        sb.append("Remove-Item Env:CLAUDECODE -ErrorAction SilentlyContinue").append(nl);
        sb.append(nl);

        // Launch CLI interactively (foreground)
        sb.append(cliCommand).append(nl);

        // Write with UTF-8 BOM so PowerShell correctly detects encoding
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] content = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[bom.length + content.length];
        System.arraycopy(bom, 0, result, 0, bom.length);
        System.arraycopy(content, 0, result, bom.length, content.length);
        Files.write(scriptPath, result,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return scriptPath;
    }

    String buildInteractiveCliCommand(String cli, String projectPath) {
        if ("claude".equals(cli)) {
            Path mcpConfig = Path.of(projectPath).resolve(".mcp.json");
            if (Files.isRegularFile(mcpConfig)) {
                return "claude --mcp-config " + shellSingleQuote(mcpConfig.toString());
            }
            return "claude";
        } else if ("copilot".equals(cli)) {
            return "copilot";
        } else if ("gemini".equals(cli)) {
            return "gemini";
        } else {
            return "codex";
        }
    }

    String buildInteractiveCliCommandWindows(String cli, String projectPath) {
        if ("claude".equals(cli)) {
            Path mcpConfig = Path.of(projectPath).resolve(".mcp.json");
            if (Files.isRegularFile(mcpConfig)) {
                return "& claude --mcp-config '" + mcpConfig.toString().replace("'", "''") + "'";
            }
            return "& claude";
        } else if ("copilot".equals(cli)) {
            return "& copilot";
        } else if ("gemini".equals(cli)) {
            return "& gemini";
        } else {
            return "& codex";
        }
    }

    private static final String DEFAULT_FINAL_PROMPT = "@workflow/{{WORKFLOW_FILE}} 이 워크플로우 plan mode로 실행해 줘.";

    String getCliPreFillText(String cli, String projectPath, String fileName) {
        String template = loadFinalPromptTemplate(projectPath);
        return template.replace("{{WORKFLOW_FILE}}", fileName);
    }

    private String loadFinalPromptTemplate(String projectPath) {
        try {
            Path file = Path.of(projectPath, "FINAL_PROMPT.md");
            if (Files.isRegularFile(file)) {
                return Files.readString(file, StandardCharsets.UTF_8).trim();
            }
        } catch (IOException ignored) {
        }
        return DEFAULT_FINAL_PROMPT;
    }
}

package com.agentrunner.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.agentrunner.workflow.WorkflowUtils.configureProcessEnvironment;
import static com.agentrunner.workflow.WorkflowUtils.isWindows;

@RestController
@RequestMapping("/api/prerequisites")
public class PrerequisiteController {

    record PrerequisiteItem(String id, String name, String command, String installCmd, boolean optional) {
    }

    record CheckResult(String id, String name, boolean installed, String version, String installCmd, boolean optional) {
    }

    record InstallResult(String id, boolean success, String output) {
    }

    private static final List<PrerequisiteItem> ITEMS = List.of(
            new PrerequisiteItem("codex", "Codex CLI", "codex", "npm i -g @openai/codex", false),
            new PrerequisiteItem("claude", "Claude CLI", "claude", "npm i -g @anthropic-ai/claude-code", false),
            new PrerequisiteItem("copilot", "Copilot CLI", "gh", "gh extension install github/gh-copilot", false),
            new PrerequisiteItem("gemini", "Gemini CLI", "gemini", "npm i -g @google/gemini-cli", false),
            new PrerequisiteItem("ghostty", "Ghostty", "ghostty", "", true)
    );

    @GetMapping("/check")
    public List<CheckResult> checkAll() {
        List<CheckResult> results = new ArrayList<>();
        for (PrerequisiteItem item : ITEMS) {
            boolean installed = isCommandAvailable(item.command());
            String version = installed ? getCommandVersion(item.command()) : "";
            results.add(new CheckResult(item.id(), item.name(), installed, version, item.installCmd(), item.optional()));
        }
        return results;
    }

    @PostMapping("/{id}/install")
    public InstallResult install(@PathVariable String id) {
        PrerequisiteItem item = ITEMS.stream()
                .filter(i -> i.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown prerequisite: " + id));

        if (item.installCmd().isBlank()) {
            return new InstallResult(id, false, "이 항목은 수동 설치가 필요합니다.");
        }

        try {
            List<String> command;
            if (isWindows()) {
                command = List.of("powershell", "-NoProfile", "-Command", item.installCmd());
            } else {
                command = List.of("/bin/zsh", "-lc", item.installCmd());
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            configureProcessEnvironment(pb);

            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new InstallResult(id, false, "설치 시간 초과");
            }

            boolean success = process.exitValue() == 0;
            return new InstallResult(id, success, output.toString().trim());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new InstallResult(id, false, "설치 실패: " + e.getMessage());
        }
    }

    private boolean isCommandAvailable(String command) {
        try {
            List<String> cmd;
            if (isWindows()) {
                cmd = List.of("powershell", "-NoProfile", "-Command", "Get-Command " + command + " -ErrorAction SilentlyContinue");
            } else {
                cmd = List.of("which", command);
            }
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        }
    }

    private String getCommandVersion(String command) {
        try {
            List<String> cmd;
            if (isWindows()) {
                cmd = List.of("powershell", "-NoProfile", "-Command", command + " --version");
            } else {
                cmd = List.of("/bin/zsh", "-lc", command + " --version");
            }
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                    break; // first line only
                }
            }

            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "";
            }
            return process.exitValue() == 0 ? output.toString().trim() : "";
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return "";
        }
    }
}

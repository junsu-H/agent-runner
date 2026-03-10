package com.agentrunner.workflow;

import com.agentrunner.api.RunWorkflowRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.agentrunner.workflow.WorkflowUtils.normalizePath;

class WorkflowPromptBuilder {

    record PlannedStepSpec(
            String skillName,
            String skillPath,
            String skillDirectoryPath,
            String stepRequest,
            boolean commandStep,
            int requestItemOrder,
            int requestItemCount
    ) {
    }

    static String buildPrompt(
            PlannedStepSpec spec,
            RunWorkflowRequest request,
            int stepOrder,
            int totalSteps
    ) {
        String outputDir = normalizePath(request.projectPath()) + "/workflow";

        StringBuilder sb = new StringBuilder();
        sb.append("Execute agent-runner workflow step.\n\n")
                .append("Step order: ").append(stepOrder).append(" / ").append(totalSteps).append("\n")
                .append("Skill name: ").append(spec.skillName()).append("\n")
                .append("Skill folder: ").append(normalizePath(spec.skillDirectoryPath())).append("\n")
                .append("Project path: ").append(normalizePath(request.projectPath())).append("\n")
                .append("Output directory: ").append(outputDir).append("\n")
                .append("Global request: ").append(request.safeRequestText()).append("\n");

        String outputFile = outputDir + "/" + slugify(spec.skillName()) + ".md";

        if (spec.commandStep()) {
            sb.append("Execution granularity: command-step\n")
                    .append("Skill request item: ").append(spec.stepRequest()).append("\n")
                    .append("Skill item order: ").append(spec.requestItemOrder()).append(" / ").append(spec.requestItemCount()).append("\n")
                    .append("Output file: ").append(outputFile).append("\n\n")
                    .append("Rules:\n")
                    .append("1. Read ALL files in the skill folder (SKILL.md and any other .md files).\n")
                    .append("2. Execute the request based on the full context.\n")
                    .append("3. Save result to ").append(outputFile).append(" (mkdir -p if needed).\n");
            return sb.toString();
        }

        sb.append("Execution granularity: skill-step\n")
                .append("Skill request (full): ").append(spec.stepRequest()).append("\n")
                .append("Output file: ").append(outputFile).append("\n\n")
                .append("Rules:\n")
                .append("1. Read ALL files in the skill folder (SKILL.md and any other .md files).\n")
                .append("2. Execute the request based on the full context.\n")
                .append("3. Save result to ").append(outputFile).append(" (mkdir -p if needed).\n");
        return sb.toString();
    }

    static List<String> buildCommand(String cli, String projectPath, String prompt, String skillDirectoryPath, boolean continueSession) {
        if ("codex".equals(cli)) {
            // codex reads .codex/config.toml automatically from CWD
            return List.of("codex", "exec", prompt);
        }

        if ("copilot".equals(cli)) {
            List<String> command = new ArrayList<>();
            command.add("copilot");
            command.add("-p");
            command.add(prompt);
            Path copilotMcpConfig = Path.of(projectPath).resolve(".vscode").resolve("mcp.json");
            if (Files.isRegularFile(copilotMcpConfig)) {
                command.add("--additional-mcp-config");
                command.add(copilotMcpConfig.toString());
            }
            return command;
        }

        if ("gemini".equals(cli)) {
            // gemini reads .gemini/settings.json automatically from CWD
            return List.of("gemini", "-p", prompt);
        }

        String claudeMcpConfigPath = Path.of(projectPath).resolve(".mcp.json").toString();
        List<String> command = new ArrayList<>();
        command.add("claude");
        command.add("-p");
        command.add(prompt);
        command.add("--max-turns");
        command.add("15");
        if (Files.isRegularFile(Path.of(claudeMcpConfigPath))) {
            command.add("--mcp-config");
            command.add(claudeMcpConfigPath);
        }
        command.add("--add-dir");
        command.add(projectPath);
        command.add("--add-dir");
        command.add(skillDirectoryPath);
        return command;
    }

    static List<String> splitRequestItems(String rawStepPrompt) {
        List<String> items = new ArrayList<>();
        for (String line : rawStepPrompt.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                items.add(trimmed);
            }
        }
        return items;
    }

    static String normalizeSkillName(String skillName) {
        return skillName == null ? "" : skillName.trim().toLowerCase(Locale.ROOT);
    }

    static String humanizeSkillName(String skillName) {
        String normalized = skillName == null ? "" : skillName.trim().replace('_', '-');
        String[] pieces = normalized.split("-");
        StringBuilder sb = new StringBuilder();

        for (String piece : pieces) {
            if (piece.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(piece.charAt(0)));
            if (piece.length() > 1) {
                sb.append(piece.substring(1));
            }
        }

        return sb.length() == 0 ? "Skill" : sb.toString();
    }

    static String slugify(String value) {
        String slug = (value == null ? "" : value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        return slug.isBlank() ? "skill" : slug;
    }
}

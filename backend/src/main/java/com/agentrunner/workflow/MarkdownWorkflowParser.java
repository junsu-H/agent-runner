package com.agentrunner.workflow;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.agentrunner.workflow.WorkflowUtils.shellSingleQuote;

class MarkdownWorkflowParser {

    static final Pattern MD_TITLE_PATTERN = Pattern.compile("^\\s*#\\s+(.+?)\\s*$");
    static final Pattern MD_LIST_ITEM_PATTERN = Pattern.compile("^\\s*(?:[-*]|\\d+\\.)\\s+(.+?)\\s*$");

    record ParsedMarkdownWorkflow(String name, List<String> steps) {
    }

    static ParsedMarkdownWorkflow parseMarkdownWorkflow(Path markdownFile) {
        List<String> lines;
        try {
            lines = Files.readAllLines(markdownFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read workflow markdown: " + markdownFile, e);
        }

        String fileName = markdownFile.getFileName() == null ? "workflow" : markdownFile.getFileName().toString();
        String defaultName = fileName.replaceFirst("\\.md$", "");
        String workflowName = defaultName;

        List<String> stepsFromCodeBlock = new ArrayList<>();
        boolean inCodeBlock = false;
        boolean captureCodeBlock = false;

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine;
            String trimmed = line.trim();

            if (!inCodeBlock) {
                Matcher titleMatcher = MD_TITLE_PATTERN.matcher(line);
                if (titleMatcher.matches() && workflowName.equals(defaultName)) {
                    workflowName = titleMatcher.group(1).trim();
                }
            }

            if (trimmed.startsWith("```")) {
                if (!inCodeBlock) {
                    inCodeBlock = true;
                    String language = trimmed.substring(3).trim().toLowerCase(Locale.ROOT);
                    captureCodeBlock = language.isEmpty()
                            || "bash".equals(language) || "sh".equals(language)
                            || "zsh".equals(language) || "shell".equals(language);
                } else {
                    inCodeBlock = false;
                    captureCodeBlock = false;
                }
                continue;
            }

            if (inCodeBlock && captureCodeBlock) {
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                stepsFromCodeBlock.add(trimmed);
            }
        }

        List<String> steps = new ArrayList<>(stepsFromCodeBlock);
        if (steps.isEmpty()) {
            for (String rawLine : lines) {
                String line = rawLine == null ? "" : rawLine;
                Matcher listItemMatcher = MD_LIST_ITEM_PATTERN.matcher(line);
                if (!listItemMatcher.matches()) {
                    continue;
                }
                String candidate = listItemMatcher.group(1).trim();
                if (candidate.startsWith("`") && candidate.endsWith("`") && candidate.length() > 1) {
                    candidate = candidate.substring(1, candidate.length() - 1).trim();
                }
                if (!candidate.isEmpty() && !candidate.startsWith("#")) {
                    steps.add(candidate);
                }
            }
        }

        if (steps.isEmpty()) {
            throw new IllegalArgumentException("workflow markdown must contain executable steps in fenced code block or markdown list");
        }

        return new ParsedMarkdownWorkflow(workflowName, List.copyOf(steps));
    }

    static List<String> normalizeMarkdownWorkflowCommands(
            String selectedCli, String workflowId, String projectPath, List<String> rawSteps
    ) {
        List<String> commands = new ArrayList<>();
        int stepIndex = 0;
        for (String rawStep : rawSteps) {
            if (rawStep == null) {
                continue;
            }
            String step = rawStep.trim();
            if (step.isEmpty()) {
                continue;
            }
            boolean continueSession = "claude".equals(selectedCli) && stepIndex > 0;
            String normalized = normalizeTerminalStepCommand(selectedCli, projectPath, step, continueSession);
            if (!normalized.isBlank()) {
                commands.add(normalized);
                stepIndex++;
            }
        }

        if (commands.isEmpty()) {
            throw new IllegalArgumentException("workflow has no executable commands: " + workflowId);
        }
        return commands;
    }

    static String normalizeTerminalStepCommand(String cli, String projectPath, String step, boolean continueSession) {
        String trimmed = step.trim();
        if (trimmed.isBlank()) {
            return "";
        }

        Path claudeMcpConfig = Path.of(projectPath).resolve(".mcp.json");
        String continueFlag = continueSession ? " --continue" : "";

        if (trimmed.startsWith("/")) {
            if ("claude".equals(cli)) {
                String base = Files.isRegularFile(claudeMcpConfig)
                        ? "claude" + continueFlag + " --mcp-config " + shellSingleQuote(claudeMcpConfig.toString()) + " -p "
                        : "claude" + continueFlag + " -p ";
                return base + shellSingleQuote(trimmed);
            }

            if ("copilot".equals(cli)) {
                return "copilot -p " + shellSingleQuote(trimmed);
            }

            if ("gemini".equals(cli)) {
                return "gemini -p " + shellSingleQuote(trimmed);
            }

            if ("/mcp".equals(trimmed) || trimmed.startsWith("/mcp ")) {
                return "codex mcp list";
            }
            return "codex exec " + shellSingleQuote(trimmed) + " --sandbox read-only --skip-git-repo-check";
        }

        if ("claude".equals(cli)
                && Files.isRegularFile(claudeMcpConfig)
                && (trimmed.equals("claude") || trimmed.startsWith("claude "))
                && !trimmed.contains("--mcp-config")) {
            if (trimmed.equals("claude")) {
                return "claude" + continueFlag + " --mcp-config " + shellSingleQuote(claudeMcpConfig.toString());
            }
            String tail = trimmed.substring("claude".length()).trim();
            return "claude" + continueFlag + " --mcp-config " + shellSingleQuote(claudeMcpConfig.toString()) + " " + tail;
        }

        return trimmed;
    }

    static List<String> normalizeProfilesWithoutDefaults(List<String> profiles) {
        Set<String> dedup = new LinkedHashSet<>();
        if (profiles != null) {
            for (String profile : profiles) {
                if (profile == null) {
                    continue;
                }
                String normalized = profile.trim().toLowerCase(Locale.ROOT);
                if (!normalized.isEmpty()) {
                    dedup.add(normalized);
                }
            }
        }
        return List.copyOf(dedup);
    }
}

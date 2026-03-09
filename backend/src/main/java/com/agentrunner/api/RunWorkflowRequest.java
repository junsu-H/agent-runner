package com.agentrunner.api;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record RunWorkflowRequest(
        String projectPath,
        String issueKey,
        String requestText,
        String cli,
        Boolean dryRun,
        List<String> selectedSkills,
        Map<String, String> stepPrompts,
        List<String> commandStepSkills,
        List<String> mcpProfiles,
        String mcpProfilePath,
        Boolean openTerminalAfter,
        String workflowName
) {

    public String normalizedCli() {
        String value = (cli == null || cli.isBlank()) ? "codex" : cli;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public String safeIssueKey() {
        return (issueKey == null || issueKey.isBlank()) ? "NO-ISSUE" : issueKey.trim();
    }

    public String safeRequestText() {
        return requestText == null ? "" : requestText.trim();
    }

    public boolean isDryRun() {
        return dryRun != null && dryRun;
    }

    public List<String> normalizedSelectedSkills() {
        return normalizeList(selectedSkills);
    }

    public List<String> normalizedCommandStepSkills() {
        return normalizeList(commandStepSkills);
    }

    public List<String> safeMcpProfiles() {
        return normalizeList(mcpProfiles);
    }

    public boolean shouldOpenTerminalAfter() {
        return openTerminalAfter != null && openTerminalAfter;
    }

    public String safeWorkflowName() {
        if (workflowName == null || workflowName.isBlank()) return "workflow";
        String name = workflowName.trim();
        if (name.toLowerCase(Locale.ROOT).endsWith(".md")) {
            name = name.substring(0, name.length() - 3);
        }
        name = name.replaceAll("[^a-zA-Z0-9가-힣\\-_.]", "");
        return name.isEmpty() ? "workflow" : name;
    }

    public Map<String, String> normalizedStepPrompts() {
        if (stepPrompts == null || stepPrompts.isEmpty()) {
            return Map.of();
        }

        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : stepPrompts.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key = entry.getKey().trim().toLowerCase(Locale.ROOT);
            if (key.isEmpty()) {
                continue;
            }
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            if (!value.isEmpty()) {
                normalized.put(key, value);
            }
        }

        return Map.copyOf(normalized);
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        Set<String> deduped = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                deduped.add(trimmed);
            }
        }

        return List.copyOf(deduped);
    }
}

package com.agentrunner.workflow;

import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class WorkflowUtils {

    static final DateTimeFormatter RUN_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    static final Set<String> SUPPORTED_CLI = Set.of("codex", "claude", "copilot", "gemini");

    private WorkflowUtils() {
    }

    static String shellSingleQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    static String normalizeCli(String cli) {
        if (cli == null || cli.isBlank()) {
            return "codex";
        }
        return cli.trim().toLowerCase(Locale.ROOT);
    }

    static String normalizeProjectPath(String projectPath) {
        if (projectPath != null && !projectPath.isBlank()) {
            return projectPath;
        }
        return Path.of("").toAbsolutePath().normalize().toString();
    }

    static List<String> tailLines(List<String> lines, int maxLines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        int size = lines.size();
        if (size <= maxLines) {
            return List.copyOf(lines);
        }
        return List.copyOf(lines.subList(size - maxLines, size));
    }

    static String normalizePath(String path) {
        return path == null ? "" : path.replace('\\', '/');
    }

    static String abbreviate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    static String toAppleScriptStringLiteral(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                + "\"";
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    static String resolveTerminalAppName(List<String> command) {
        if (!command.isEmpty()) {
            String first = command.get(0);
            if ("ghostty".equals(first) || "open".equals(first)) {
                return "Ghostty";
            }
            if ("wt".equals(first) || "wt.exe".equals(first)) {
                return "Windows Terminal";
            }
            if ("powershell".equals(first) || "powershell.exe".equals(first)) {
                return "PowerShell";
            }
            if ("cmd".equals(first) || "cmd.exe".equals(first)) {
                return "cmd";
            }
        }
        return isWindows() ? "cmd" : "Terminal.app";
    }

    public static void configureProcessEnvironment(ProcessBuilder pb) {
        Map<String, String> env = pb.environment();
        if (env.get("HOME") == null || env.get("HOME").isBlank()) {
            env.put("HOME", System.getProperty("user.home"));
        }
    }
}

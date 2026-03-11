package com.agentrunner.workflow;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.agentrunner.workflow.WorkflowUtils.*;

@Service
public class TerminalLaunchService {

    record McpCommandResult(List<String> outputLines, int exitCode, boolean timedOut, String commandText) {
    }

    record TerminalLaunchResult(String appName, String commandText, List<String> outputLines) {
    }

    TerminalLaunchResult launchScriptInSystemTerminal(String projectPath, Path scriptPath) {
        if (isWindows()) {
            return launchScriptInWindowsTerminal(projectPath, scriptPath);
        }
        return launchScriptInMacTerminal(projectPath, scriptPath);
    }

    private TerminalLaunchResult launchScriptInMacTerminal(String projectPath, Path scriptPath) {
        String absScript = scriptPath.toAbsolutePath().toString();
        List<String> errors = new ArrayList<>();

        // Wrap in a UUID-named temp script so Ghostty's window restoration
        // does not re-use a stale fixed-path script.
        try {
            String wrapperName = "agent-runner-launch-" + UUID.randomUUID().toString().substring(0, 8) + ".sh";
            Path wrapperScript = Path.of(System.getProperty("java.io.tmpdir"), wrapperName);
            // Self-replace: on first run, overwrite this script to a plain shell launcher
            // so Ghostty Cmd+D split panes just open zsh instead of re-running the command.
            Files.writeString(wrapperScript, "#!/bin/zsh\n"
                    + "printf '#!/bin/zsh\\nexec zsh -l\\n' > \"$0\"\n"
                    + "exec " + "'" + absScript.replace("'", "'\\''") + "'" + "\n");
            Files.setPosixFilePermissions(wrapperScript, PosixFilePermissions.fromString("rwxr-xr-x"));

            List<String> ghosttyCmd = List.of("open", "-na", "Ghostty", "--args",
                    "--command=" + wrapperScript.toAbsolutePath());

            ProcessBuilder pb = new ProcessBuilder(ghosttyCmd);
            pb.directory(Path.of(projectPath).toFile());
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            configureProcessEnvironment(pb);
            Process process = pb.start();

            boolean exited = process.waitFor(3, TimeUnit.SECONDS);
            if (!exited || process.exitValue() == 0) {
                return new TerminalLaunchResult("Ghostty", String.join(" ", ghosttyCmd), List.of());
            }
            errors.add("Ghostty: exit=" + process.exitValue());
        } catch (IOException e) {
            errors.add("Ghostty: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            errors.add("Ghostty: interrupted");
        }

        // Fallback: Terminal.app via AppleScript
        List<List<String>> candidates = new ArrayList<>();
        candidates.add(List.of("/usr/bin/osascript", "-e", "tell application \"Terminal\"\n"
                + "activate\n"
                + "do script " + toAppleScriptStringLiteral(absScript) + "\n"
                + "end tell"));

        for (List<String> candidate : candidates) {
            McpCommandResult result = runCommand(candidate, projectPath, 20);
            if (!result.timedOut() && result.exitCode() == 0) {
                return new TerminalLaunchResult(resolveTerminalAppName(candidate), result.commandText(), result.outputLines());
            }

            String detail = result.timedOut()
                    ? "timeout"
                    : ("exit=" + result.exitCode() + ", output=" + String.join(" | ", tailLines(result.outputLines(), 6)));
            errors.add(resolveTerminalAppName(candidate) + ": " + detail);
        }

        throw new IllegalStateException("failed to open terminal app. tried: " + String.join(" / ", errors));
    }

    private TerminalLaunchResult launchScriptInWindowsTerminal(String projectPath, Path scriptPath) {
        String absScript = scriptPath.toAbsolutePath().toString();
        boolean isPowerShellScript = absScript.endsWith(".ps1");

        List<String> errors = new ArrayList<>();

        // 1) Try Windows Terminal (wt) with PowerShell
        if (isPowerShellScript) {
            List<String> wtCmd = List.of("wt", "-d", projectPath,
                    "powershell", "-NoExit", "-ExecutionPolicy", "Bypass", "-File", absScript);
            try {
                ProcessBuilder pb = new ProcessBuilder(wtCmd);
                pb.directory(Path.of(projectPath).toFile());
                pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                pb.redirectError(ProcessBuilder.Redirect.DISCARD);
                configureProcessEnvironment(pb);
                Process process = pb.start();

                boolean exited = process.waitFor(2, TimeUnit.SECONDS);
                if (!exited || process.exitValue() == 0) {
                    return new TerminalLaunchResult("Windows Terminal", String.join(" ", wtCmd), List.of());
                }
                errors.add("Windows Terminal: exit=" + process.exitValue());
            } catch (IOException e) {
                errors.add("Windows Terminal: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                errors.add("Windows Terminal: interrupted");
            }
        }

        // 2) Fallback candidates
        List<List<String>> candidates = new ArrayList<>();
        if (isPowerShellScript) {
            candidates.add(List.of("powershell", "-NoExit", "-ExecutionPolicy", "Bypass", "-File", absScript));
        } else {
            candidates.add(List.of("wt", "-d", projectPath, "cmd", "/c", absScript));
            candidates.add(List.of("cmd", "/c", "start", "cmd", "/k", absScript));
        }

        for (List<String> candidate : candidates) {
            McpCommandResult result = runCommand(candidate, projectPath, 20);
            if (!result.timedOut() && result.exitCode() == 0) {
                return new TerminalLaunchResult(resolveTerminalAppName(candidate), result.commandText(), result.outputLines());
            }

            String detail = result.timedOut()
                    ? "timeout"
                    : ("exit=" + result.exitCode() + ", output=" + String.join(" | ", tailLines(result.outputLines(), 6)));
            errors.add(resolveTerminalAppName(candidate) + ": " + detail);
        }

        throw new IllegalStateException("failed to open terminal app. tried: " + String.join(" / ", errors));
    }

    TerminalLaunchResult launchInSystemTerminal(String projectPath, String launchCommand) {
        if (isWindows()) {
            return launchInWindowsTerminal(projectPath, launchCommand);
        }
        return launchInMacTerminal(projectPath, launchCommand);
    }

    private TerminalLaunchResult launchInMacTerminal(String projectPath, String launchCommand) {
        List<String> errors = new ArrayList<>();

        // 1) Write command to temp script, then use --command config option
        //    to avoid "Allow Ghostty to execute /bin/zsh?" security prompt
        try {
            String scriptName = "agent-runner-launch-" + UUID.randomUUID().toString().substring(0, 8) + ".sh";
            Path tempScript = Path.of(System.getProperty("java.io.tmpdir"), scriptName);
            String escapedPath = "'" + projectPath.replace("'", "'\\''") + "'";
            // Self-replace: on first run, overwrite this script to a plain shell launcher
            // so Ghostty Cmd+D split panes just open zsh instead of re-running the command.
            Files.writeString(tempScript, "#!/bin/zsh\n"
                    + "printf '#!/bin/zsh\\nexec zsh -l\\n' > \"$0\"\n"
                    + "cd " + escapedPath + "\n" + launchCommand + "\nexec zsh -l\n");
            Files.setPosixFilePermissions(tempScript, PosixFilePermissions.fromString("rwxr-xr-x"));

            List<String> ghosttyCmd = List.of("open", "-na", "Ghostty", "--args",
                    "--command=" + tempScript.toAbsolutePath());

            ProcessBuilder pb = new ProcessBuilder(ghosttyCmd);
            pb.directory(Path.of(projectPath).toFile());
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            configureProcessEnvironment(pb);
            Process process = pb.start();

            boolean exited = process.waitFor(3, TimeUnit.SECONDS);
            if (!exited || process.exitValue() == 0) {
                return new TerminalLaunchResult("Ghostty", String.join(" ", ghosttyCmd), List.of());
            }
            errors.add("Ghostty: exit=" + process.exitValue());
        } catch (IOException e) {
            errors.add("Ghostty: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            errors.add("Ghostty: interrupted");
        }

        // 2) Fallback candidates
        List<List<String>> candidates = new ArrayList<>();
        candidates.add(List.of("/usr/bin/osascript", "-e", "tell application \"Terminal\"\n"
                + "activate\n"
                + "do script " + toAppleScriptStringLiteral(launchCommand) + "\n"
                + "end tell"));

        for (List<String> candidate : candidates) {
            McpCommandResult result = runCommand(candidate, projectPath, 20);
            if (!result.timedOut() && result.exitCode() == 0) {
                return new TerminalLaunchResult(resolveTerminalAppName(candidate), result.commandText(), result.outputLines());
            }

            String detail = result.timedOut()
                    ? "timeout"
                    : ("exit=" + result.exitCode() + ", output=" + String.join(" | ", tailLines(result.outputLines(), 6)));
            errors.add(resolveTerminalAppName(candidate) + ": " + detail);
        }

        throw new IllegalStateException("failed to open terminal app. tried: " + String.join(" / ", errors));
    }

    private TerminalLaunchResult launchInWindowsTerminal(String projectPath, String launchCommand) {
        List<String> errors = new ArrayList<>();

        // 1) Try Windows Terminal (wt)
        List<String> wtCmd = List.of("wt", "-d", projectPath, "cmd", "/k", launchCommand);
        try {
            ProcessBuilder pb = new ProcessBuilder(wtCmd);
            pb.directory(Path.of(projectPath).toFile());
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            configureProcessEnvironment(pb);
            Process process = pb.start();

            boolean exited = process.waitFor(2, TimeUnit.SECONDS);
            if (!exited || process.exitValue() == 0) {
                return new TerminalLaunchResult("Windows Terminal", String.join(" ", wtCmd), List.of());
            }
            errors.add("Windows Terminal: exit=" + process.exitValue());
        } catch (IOException e) {
            errors.add("Windows Terminal: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            errors.add("Windows Terminal: interrupted");
        }

        // 2) Fallback: PowerShell new window
        List<List<String>> candidates = new ArrayList<>();
        candidates.add(List.of("powershell", "-Command",
                "Start-Process powershell -ArgumentList '-NoExit','-Command',\"Set-Location ''" + projectPath + "''; " + launchCommand.replace("'", "''") + "\""));
        // 3) Fallback: cmd start
        candidates.add(List.of("cmd", "/c", "start", "cmd", "/k", "cd /d \"" + projectPath + "\" && " + launchCommand));

        for (List<String> candidate : candidates) {
            McpCommandResult result = runCommand(candidate, projectPath, 20);
            if (!result.timedOut() && result.exitCode() == 0) {
                return new TerminalLaunchResult(resolveTerminalAppName(candidate), result.commandText(), result.outputLines());
            }

            String detail = result.timedOut()
                    ? "timeout"
                    : ("exit=" + result.exitCode() + ", output=" + String.join(" | ", tailLines(result.outputLines(), 6)));
            errors.add(resolveTerminalAppName(candidate) + ": " + detail);
        }

        throw new IllegalStateException("failed to open terminal app. tried: " + String.join(" / ", errors));
    }

    McpCommandResult runCommand(List<String> command, String projectPath, int timeoutSeconds) {
        String commandText = String.join(" ", command);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(Path.of(projectPath).toFile());
        pb.redirectErrorStream(true);
        configureProcessEnvironment(pb);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            return new McpCommandResult(List.of("failed to start: " + e.getMessage()), 127, false, commandText);
        }

        boolean finished;
        try {
            finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new McpCommandResult(List.of("interrupted while waiting for command"), 130, false, commandText);
        }

        if (!finished) {
            process.destroy();
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }

        List<String> output = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String normalized = line.strip();
                if (!normalized.isEmpty()) {
                    output.add(normalized);
                }
            }
        } catch (IOException e) {
            output.add("failed to read output: " + e.getMessage());
        }

        int exitCode;
        if (finished) {
            try {
                exitCode = process.exitValue();
            } catch (IllegalThreadStateException e) {
                exitCode = 124;
            }
        } else {
            exitCode = 124;
        }

        return new McpCommandResult(output, exitCode, !finished, commandText);
    }
}

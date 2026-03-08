package com.agentrunner.workflow;

import com.agentrunner.api.RunWorkflowRequest;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.TimeUnit;

import static com.agentrunner.workflow.WorkflowUtils.configureProcessEnvironment;

@Component
class WorkflowStepExecutor {

    private static final int STEP_TIMEOUT_MINUTES = 10;

    interface StepOutputListener {
        void onProcessStarted(Process process, BufferedWriter stdin);
        void onOutputLine(String line);
        void onProcessFinished();
    }

    private static final StepOutputListener NOOP_LISTENER = new StepOutputListener() {
        @Override public void onProcessStarted(Process process, BufferedWriter stdin) { }
        @Override public void onOutputLine(String line) { }
        @Override public void onProcessFinished() { }
    };

    StepExecutionResult execute(
            WorkflowStepNode step, RunWorkflowRequest request, Path runDir, StepOutputListener listener
    ) throws IOException, InterruptedException {
        if (listener == null) {
            listener = NOOP_LISTENER;
        }

        Path logFile = runDir.resolve(step.getId() + ".log");
        String cmdText = String.join(" ", step.getCommand());

        if (request.isDryRun()) {
            Files.writeString(logFile,
                    "[DRY-RUN]\nstep=" + step.getId() + "\ncommand=" + cmdText + "\n\nprompt=\n" + step.getPrompt() + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            listener.onOutputLine("[DRY-RUN] " + step.getId());
            return new StepExecutionResult(step.getId(), cmdText, 0, true, logFile.toAbsolutePath().toString(), "dry-run");
        }

        if (!isExecutableAvailable(step.getCommand().get(0))) {
            String msg = "Executable not found in PATH: " + step.getCommand().get(0);
            Files.writeString(logFile, msg, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            listener.onOutputLine(msg);
            return new StepExecutionResult(step.getId(), cmdText, 127, false, logFile.toAbsolutePath().toString(), msg);
        }

        ProcessBuilder pb = new ProcessBuilder(step.getCommand());
        pb.directory(Path.of(request.projectPath()).toFile());
        pb.redirectErrorStream(true);
        configureProcessEnvironment(pb);

        Process process = pb.start();
        BufferedWriter processStdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        listener.onProcessStarted(process, processStdin);

        int exitCode;
        boolean timedOut = false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.newLine();
                writer.flush();
                listener.onOutputLine(line);
            }
            boolean finished = process.waitFor(STEP_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                timedOut = true;
                process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                listener.onOutputLine("[timeout] step exceeded " + STEP_TIMEOUT_MINUTES + " minutes");
            }
            exitCode = finished ? process.exitValue() : 124;
        } finally {
            try { processStdin.close(); } catch (IOException ignored) { }
            listener.onProcessFinished();
        }

        boolean success = !timedOut && exitCode == 0;
        String message = timedOut ? "timeout after " + STEP_TIMEOUT_MINUTES + "min" : (success ? "ok" : "step failed");
        return new StepExecutionResult(step.getId(), cmdText, exitCode, success,
                logFile.toAbsolutePath().toString(), message);
    }

    private boolean isExecutableAvailable(String commandName) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isBlank()) return false;
        String[] extensions = WorkflowUtils.isWindows() ? new String[]{"", ".exe", ".cmd", ".bat"} : new String[]{""};
        for (String entry : pathEnv.split(File.pathSeparator)) {
            for (String ext : extensions) {
                if (Files.isExecutable(Path.of(entry, commandName + ext))) return true;
            }
        }
        return false;
    }
}

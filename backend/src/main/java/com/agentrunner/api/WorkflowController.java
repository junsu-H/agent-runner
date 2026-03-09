package com.agentrunner.api;

import com.agentrunner.workflow.MarkdownWorkflowService;
import com.agentrunner.workflow.McpConnectionService;
import com.agentrunner.workflow.WorkflowRunResult;
import com.agentrunner.workflow.WorkflowRunService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowRunService workflowRunService;
    private final McpConnectionService mcpConnectionService;
    private final MarkdownWorkflowService markdownWorkflowService;

    public WorkflowController(
            WorkflowRunService workflowRunService,
            McpConnectionService mcpConnectionService,
            MarkdownWorkflowService markdownWorkflowService
    ) {
        this.workflowRunService = workflowRunService;
        this.mcpConnectionService = mcpConnectionService;
        this.markdownWorkflowService = markdownWorkflowService;
    }

    @GetMapping("/linked-list/definition")
    public ResponseEntity<?> defaultDefinition(
            @RequestParam(value = "projectPath", required = false) String projectPath,
            @RequestParam(value = "issueKey", required = false) String issueKey,
            @RequestParam(value = "requestText", required = false) String requestText,
            @RequestParam(value = "cli", required = false) String cli,
            @RequestParam(value = "selectedSkills", required = false) List<String> selectedSkills,
            @RequestParam(value = "commandStepSkills", required = false) List<String> commandStepSkills
    ) {
        RunWorkflowRequest request = new RunWorkflowRequest(
                projectPath == null || projectPath.isBlank() ? System.getProperty("user.home") + "/workspace/agent-runner" : projectPath,
                issueKey, requestText, cli, true,
                selectedSkills == null ? List.of() : selectedSkills,
                Map.of(),
                commandStepSkills == null ? List.of() : commandStepSkills,
                List.of(), null, false, null
        );

        try {
            return ResponseEntity.ok(workflowRunService.plan(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/mcp/info")
    public ResponseEntity<?> mcpInfo(
            @RequestParam(value = "cli", required = false) String cli,
            @RequestParam(value = "projectPath", required = false) String projectPath
    ) {
        try {
            return ResponseEntity.ok(mcpConnectionService.checkMcpInfo(cli, projectPath));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/linked-list/plan")
    public ResponseEntity<?> plan(@RequestBody RunWorkflowRequest request) {
        if (request == null || request.projectPath() == null || request.projectPath().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "projectPath is required"));
        }

        try {
            return ResponseEntity.ok(workflowRunService.plan(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/linked-list/run")
    public ResponseEntity<?> run(@RequestBody RunWorkflowRequest request) {
        if (request == null || request.projectPath() == null || request.projectPath().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "projectPath is required"));
        }

        try {
            WorkflowRunResult result = workflowRunService.run(request);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/linked-list/generate")
    public ResponseEntity<?> generateWorkflow(@RequestBody RunWorkflowRequest request) {
        if (request == null || request.projectPath() == null || request.projectPath().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "projectPath is required"));
        }

        try {
            return ResponseEntity.ok(workflowRunService.generateWorkflowFile(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    public record WorkflowFileEntry(String name, String path) {}

    @GetMapping("/file/list")
    public ResponseEntity<?> listWorkflowFiles(@RequestParam("projectPath") String projectPath) {
        try {
            Path workflowDir = Path.of(projectPath, "workflow");
            if (!Files.isDirectory(workflowDir)) {
                return ResponseEntity.ok(List.of());
            }
            List<WorkflowFileEntry> entries;
            try (var stream = Files.list(workflowDir)) {
                entries = stream
                        .filter(p -> !Files.isDirectory(p))
                        .filter(p -> p.getFileName().toString().endsWith(".md"))
                        .sorted()
                        .map(p -> {
                            String fileName = p.getFileName().toString();
                            String name = fileName.substring(0, fileName.length() - 3);
                            return new WorkflowFileEntry(name, p.toAbsolutePath().normalize().toString());
                        })
                        .toList();
            }
            return ResponseEntity.ok(entries);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/file/unique-name")
    public ResponseEntity<?> uniqueName(
            @RequestParam("projectPath") String projectPath,
            @RequestParam("name") String name) {
        String baseName = (name == null || name.isBlank()) ? "workflow" : name.trim();
        Path workflowDir = Path.of(projectPath, "workflow");
        String fileName = baseName + ".md";
        if (!Files.exists(workflowDir.resolve(fileName))) {
            return ResponseEntity.ok(Map.of("uniqueName", baseName, "exists", false));
        }
        int seq = 1;
        while (Files.exists(workflowDir.resolve(baseName + "-" + seq + ".md"))) {
            seq++;
        }
        return ResponseEntity.ok(Map.of("uniqueName", baseName + "-" + seq, "exists", true));
    }

    @GetMapping("/file/read")
    public ResponseEntity<?> readWorkflowFile(@RequestParam("path") String filePath) {
        try {
            Path path = Path.of(filePath).toAbsolutePath().normalize();
            if (!Files.exists(path)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "파일을 찾을 수 없습니다."));
            }
            String content = Files.readString(path);
            return ResponseEntity.ok(Map.of("content", content, "path", path.toString()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/linked-list/run/terminal")
    public ResponseEntity<?> runInTerminal(@RequestBody RunWorkflowRequest request) {
        if (request == null || request.projectPath() == null || request.projectPath().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "projectPath is required"));
        }

        try {
            return ResponseEntity.ok(workflowRunService.launchInTerminal(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/linked-list/run/async")
    public ResponseEntity<?> runAsync(@RequestBody RunWorkflowRequest request) {
        if (request == null || request.projectPath() == null || request.projectPath().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "projectPath is required"));
        }

        try {
            return ResponseEntity.ok(workflowRunService.startAsync(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/markdown/register")
    public ResponseEntity<?> registerMarkdownWorkflow(@RequestBody MarkdownWorkflowRegisterRequest request) {
        if (request == null || request.safeMarkdownPath().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "markdownPath is required"));
        }

        try {
            return ResponseEntity.ok(markdownWorkflowService.registerMarkdownWorkflow(
                    request.safeMarkdownPath(), request.cli(), request.projectPath(), request.safeMcpProfiles()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/markdown")
    public ResponseEntity<?> listMarkdownWorkflows() {
        try {
            return ResponseEntity.ok(markdownWorkflowService.listMarkdownWorkflows());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/markdown/{workflowId}/open-terminal")
    public ResponseEntity<?> openMarkdownWorkflowTerminal(
            @PathVariable("workflowId") String workflowId
    ) {
        try {
            return ResponseEntity.ok(markdownWorkflowService.openRegisteredMarkdownWorkflowInTerminal(workflowId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<?> runStatus(@PathVariable("runId") String runId) {
        try {
            return ResponseEntity.ok(workflowRunService.getRunStatus(runId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/runs/{runId}/cancel")
    public ResponseEntity<?> cancelRun(@PathVariable("runId") String runId) {
        try {
            return ResponseEntity.ok(workflowRunService.cancelRun(runId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/runs/{runId}/stdin")
    public ResponseEntity<?> sendTerminalInput(
            @PathVariable("runId") String runId,
            @RequestBody WorkflowTerminalInputRequest request
    ) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "request body is required"));
        }

        try {
            workflowRunService.sendTerminalInput(runId, request.safeInput(), request.shouldAppendNewline());
            return ResponseEntity.ok(workflowRunService.getRunStatus(runId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }
}

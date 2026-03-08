package com.agentrunner.api;

import com.agentrunner.workflow.McpConnectionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mcp")
public class McpController {

    private final McpConnectionService mcpConnectionService;

    public McpController(McpConnectionService mcpConnectionService) {
        this.mcpConnectionService = mcpConnectionService;
    }

    @GetMapping("/info")
    public ResponseEntity<?> info(
            @RequestParam(value = "cli", required = false) String cli,
            @RequestParam(value = "projectPath", required = false) String projectPath,
            @RequestParam(value = "profiles", required = false) List<String> profiles,
            @RequestParam(value = "apply", required = false) Boolean apply
    ) {
        try {
            boolean applyConfig = apply != null && apply;
            return ResponseEntity.ok(mcpConnectionService.precheckMcp(cli, projectPath, profiles, applyConfig));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/connect")
    public ResponseEntity<?> connect(
            @RequestParam(value = "cli", required = false) String cli,
            @RequestParam(value = "projectPath", required = false) String projectPath,
            @RequestParam(value = "profiles", required = false) List<String> profiles
    ) {
        try {
            return ResponseEntity.ok(mcpConnectionService.connectMcp(cli, projectPath, profiles));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/terminal")
    public ResponseEntity<?> runTerminalCommand(
            @RequestBody McpTerminalCommandRequest request
    ) {
        if (request == null || request.safeCommand().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "command is required"));
        }

        try {
            return ResponseEntity.ok(mcpConnectionService.runTerminalCommand(
                    request.cli(),
                    request.projectPath(),
                    request.safeProfiles(),
                    request.safeCommand()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/terminal/open")
    public ResponseEntity<?> openSystemTerminal(
            @RequestBody McpTerminalOpenRequest request
    ) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "request body is required"));
        }

        try {
            return ResponseEntity.ok(mcpConnectionService.openSystemTerminal(
                    request.cli(),
                    request.projectPath(),
                    request.safeProfiles(),
                    request.safeMdFilePath()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}

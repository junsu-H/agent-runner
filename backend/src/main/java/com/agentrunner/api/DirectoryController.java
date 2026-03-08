package com.agentrunner.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/directories")
public class DirectoryController {

    public record DirectoryEntry(String name, String path) {}

    public record DirectoryListResponse(
            String current,
            String parent,
            List<DirectoryEntry> directories
    ) {}

    @GetMapping
    public ResponseEntity<?> listDirectories(
            @RequestParam(value = "path", required = false) String pathParam
    ) {
        try {
            Path dir;
            if (pathParam == null || pathParam.isBlank()) {
                dir = Path.of(System.getProperty("user.home"));
            } else {
                String expanded = pathParam.startsWith("~")
                        ? System.getProperty("user.home") + pathParam.substring(1)
                        : pathParam;
                dir = Path.of(expanded).toAbsolutePath().normalize();
            }

            if (!Files.isDirectory(dir)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Not a directory: " + dir));
            }

            Path parent = dir.getParent();
            List<DirectoryEntry> entries;
            try (Stream<Path> stream = Files.list(dir)) {
                entries = stream
                        .filter(Files::isDirectory)
                        .filter(p -> {
                            try { return !Files.isHidden(p); } catch (IOException e) { return false; }
                        })
                        .sorted()
                        .map(p -> new DirectoryEntry(p.getFileName().toString(), p.toString()))
                        .toList();
            }

            return ResponseEntity.ok(new DirectoryListResponse(
                    dir.toString(),
                    parent != null ? parent.toString() : null,
                    entries
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}

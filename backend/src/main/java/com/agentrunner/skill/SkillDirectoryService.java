package com.agentrunner.skill;

import com.agentrunner.api.SkillDirectoryResponse;
import com.agentrunner.api.SkillListResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
public class SkillDirectoryService {

    public SkillListResponse listSkills(String overrideRoot) throws IOException {
        Path agentRunnerRoot = resolveRoot(overrideRoot);
        Path skillsRoot = resolveSkillsRoot(agentRunnerRoot);

        if (!Files.isDirectory(skillsRoot)) {
            throw new IllegalArgumentException("skills directory not found: " + skillsRoot);
        }

        List<SkillDirectoryResponse> skills;
        try (Stream<Path> stream = Files.list(skillsRoot)) {
            skills = stream
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(this::toResponse)
                    .toList();
        }

        return new SkillListResponse(
                agentRunnerRoot.toString(),
                skillsRoot.toString(),
                skills.size(),
                skills
        );
    }

    private SkillDirectoryResponse toResponse(Path directory) {
        Path skillMd = directory.resolve("SKILL.md");
        boolean hasSkillMd = Files.isRegularFile(skillMd);
        return new SkillDirectoryResponse(
                directory.getFileName().toString(),
                directory.toAbsolutePath().normalize().toString(),
                hasSkillMd,
                hasSkillMd ? skillMd.toAbsolutePath().normalize().toString() : null
        );
    }

    /**
     * If the given path itself is named "skills" and contains subdirectories, treat it as the skills root directly.
     * Otherwise fall back to {root}/skills/.
     */
    private Path resolveSkillsRoot(Path root) {
        if ("skills".equals(root.getFileName().toString()) && Files.isDirectory(root)) {
            return root;
        }
        return root.resolve("skills");
    }

    private Path resolveRoot(String overrideRoot) {
        if (overrideRoot != null && !overrideRoot.isBlank()) {
            return Path.of(overrideRoot).toAbsolutePath().normalize();
        }

        String envRoot = System.getenv("AGENT_RUNNER_ROOT");
        if (envRoot != null && !envRoot.isBlank()) {
            return Path.of(envRoot).toAbsolutePath().normalize();
        }

        // Walk up from cwd to find directory containing "skills/"
        Path cwd = Path.of("").toAbsolutePath().normalize();
        for (Path p = cwd; p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("skills"))) {
                return p;
            }
        }
        return cwd;
    }
}

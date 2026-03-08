package com.agentrunner.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.agentrunner.workflow.WorkflowUtils.SUPPORTED_CLI;
import static com.agentrunner.workflow.WorkflowUtils.normalizeProjectPath;

@Service
public class McpProfileService {

    private static final String MCP_SETUP_SKILL_NAME = "agent-runner-mcp-setup";

    private final ObjectMapper objectMapper;
    private final McpConfigWriter mcpConfigWriter;

    public McpProfileService(ObjectMapper objectMapper, McpConfigWriter mcpConfigWriter) {
        this.objectMapper = objectMapper;
        this.mcpConfigWriter = mcpConfigWriter;
    }

    public void applyMcpProfiles(String cli, String projectPath, List<String> profiles) {
        if (!SUPPORTED_CLI.contains(cli)) {
            return;
        }

        List<String> normalizedProfiles = normalizeProfiles(profiles);
        if (normalizedProfiles.isEmpty()) {
            return;
        }

        Path agentRunnerRoot = resolveAgentRunnerRoot(projectPath);
        ObjectNode mergedConfig = mergeMcpProfiles(agentRunnerRoot, normalizedProfiles);
        JsonNode serversNode = mergedConfig.path("mcpServers");

        if (!serversNode.isObject()) {
            throw new IllegalStateException("merged MCP profile has no mcpServers object");
        }

        Path projectRoot = Path.of(normalizeProjectPath(projectPath)).toAbsolutePath().normalize();
        mcpConfigWriter.writeClaudeMcpConfig(projectRoot, mergedConfig);
        mcpConfigWriter.writeCopilotMcpConfig(projectRoot, mergedConfig);
        mcpConfigWriter.writeGeminiMcpConfig(projectRoot, mergedConfig);
        Path codexServers = mcpConfigWriter.writeCodexMcpServersToml(projectRoot, serversNode);
        mcpConfigWriter.linkProjectCodexConfig(projectRoot, codexServers);
    }

    public List<String> normalizeProfiles(List<String> profiles) {
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

        if (dedup.isEmpty()) {
            dedup.add("sequential-thinking");
            dedup.add("serena");
        }

        return List.copyOf(dedup);
    }

    private Path resolveAgentRunnerRoot(String projectPath) {
        if (projectPath != null && !projectPath.isBlank()) {
            Path asPath = Path.of(projectPath).toAbsolutePath().normalize();
            if (Files.isDirectory(asPath.resolve("skills"))) {
                return asPath;
            }
        }

        String envRoot = System.getenv("AGENT_RUNNER_ROOT");
        if (envRoot != null && !envRoot.isBlank()) {
            Path asPath = Path.of(envRoot).toAbsolutePath().normalize();
            if (Files.isDirectory(asPath.resolve("skills"))) {
                return asPath;
            }
        }

        Path cwd = Path.of("").toAbsolutePath().normalize();
        for (Path p = cwd; p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("skills"))) {
                return p;
            }
        }
        return cwd;
    }

    private ObjectNode mergeMcpProfiles(Path agentRunnerRoot, List<String> profiles) {
        ObjectNode merged = objectMapper.createObjectNode();
        int loadedProfileCount = 0;

        for (String profile : profiles) {
            Path profileFile = resolveProfileFile(agentRunnerRoot, profile);
            if (profileFile == null) {
                continue;
            }

            try {
                JsonNode node = objectMapper.readTree(Files.readString(profileFile, StandardCharsets.UTF_8));
                if (node != null && node.isObject()) {
                    mergeObjectNodes(merged, node);
                    loadedProfileCount++;
                }
            } catch (IOException e) {
                throw new IllegalStateException("failed to read MCP profile: " + profileFile, e);
            }
        }

        if (loadedProfileCount == 0) {
            throw new IllegalStateException("no MCP profile file was found for selected profiles: " + String.join(", ", profiles));
        }

        return merged;
    }

    private static void mergeObjectNodes(ObjectNode target, JsonNode source) {
        source.fields().forEachRemaining(entry -> {
            String field = entry.getKey();
            JsonNode sourceValue = entry.getValue();
            JsonNode targetValue = target.get(field);

            if (targetValue != null && targetValue.isObject() && sourceValue.isObject()) {
                mergeObjectNodes((ObjectNode) targetValue, sourceValue);
            } else {
                target.set(field, sourceValue.deepCopy());
            }
        });
    }

    private Path resolveProfileFile(Path agentRunnerRoot, String profile) {
        Path mcpSetupProfile = agentRunnerRoot.resolve("skills")
                .resolve(MCP_SETUP_SKILL_NAME)
                .resolve("profiles")
                .resolve(profile + ".json");
        if (Files.isRegularFile(mcpSetupProfile)) {
            return mcpSetupProfile;
        }

        Path domainAnalysisProfile = agentRunnerRoot.resolve("skills")
                .resolve("domain-analysis")
                .resolve("profiles")
                .resolve(profile + ".json");
        if (Files.isRegularFile(domainAnalysisProfile)) {
            return domainAnalysisProfile;
        }

        return null;
    }

}

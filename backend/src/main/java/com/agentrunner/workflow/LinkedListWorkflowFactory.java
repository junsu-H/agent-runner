package com.agentrunner.workflow;

import com.agentrunner.api.SkillDirectoryResponse;
import com.agentrunner.api.SkillListResponse;
import com.agentrunner.api.RunWorkflowRequest;
import com.agentrunner.skill.SkillDirectoryService;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.agentrunner.workflow.WorkflowUtils.SUPPORTED_CLI;
import static com.agentrunner.workflow.WorkflowUtils.normalizePath;

@Component
public class LinkedListWorkflowFactory {

    public record UnifiedWorkflowSpec(
            String prompt,
            int skillCount,
            List<String> skillDirectoryPaths) {
    }

    private final SkillDirectoryService skillDirectoryService;

    public LinkedListWorkflowFactory(SkillDirectoryService skillDirectoryService) {
        this.skillDirectoryService = skillDirectoryService;
    }

    public WorkflowStepNode build(RunWorkflowRequest request) {
        String cli = request.normalizedCli();
        if (!SUPPORTED_CLI.contains(cli)) {
            throw new IllegalArgumentException("cli must be one of: codex, claude, copilot, gemini");
        }

        List<String> selectedSkills = request.normalizedSelectedSkills();
        if (selectedSkills.isEmpty()) {
            throw new IllegalArgumentException("selectedSkills is required. Choose at least one skill.");
        }

        Set<String> commandStepSkillSet = new LinkedHashSet<>();
        for (String skillName : request.normalizedCommandStepSkills()) {
            commandStepSkillSet.add(WorkflowPromptBuilder.normalizeSkillName(skillName));
        }

        Map<String, String> stepPrompts = request.normalizedStepPrompts();
        SkillListResponse skillCatalog = loadSkillCatalog(request.projectPath());
        Map<String, SkillDirectoryResponse> byName = indexByName(skillCatalog.skills());

        List<WorkflowPromptBuilder.PlannedStepSpec> plannedSteps = new ArrayList<>();

        for (String selectedSkill : selectedSkills) {
            SkillDirectoryResponse skill = byName.get(WorkflowPromptBuilder.normalizeSkillName(selectedSkill));
            if (skill == null) {
                throw new IllegalArgumentException("Unknown skill: " + selectedSkill);
            }
            if (!skill.hasSkillMd() || skill.skillMdPath() == null || skill.skillMdPath().isBlank()) {
                throw new IllegalArgumentException("SKILL.md is required for selected skill: " + skill.name());
            }

            String rawStepPrompt = stepPrompts.get(WorkflowPromptBuilder.normalizeSkillName(skill.name()));
            if (rawStepPrompt == null || rawStepPrompt.isBlank()) {
                throw new IllegalArgumentException("Step prompt is required for skill: " + skill.name());
            }

            boolean commandStep = commandStepSkillSet.contains(WorkflowPromptBuilder.normalizeSkillName(skill.name()));
            if (!commandStep) {
                plannedSteps.add(new WorkflowPromptBuilder.PlannedStepSpec(
                        skill.name(),
                        skill.skillMdPath(),
                        skill.path(),
                        rawStepPrompt.trim(),
                        false,
                        1,
                        1
                ));
                continue;
            }

            List<String> requestItems = WorkflowPromptBuilder.splitRequestItems(rawStepPrompt);
            if (requestItems.isEmpty()) {
                throw new IllegalArgumentException(
                        "Step prompt for command-step skill must include at least one non-empty line: " + skill.name()
                );
            }

            for (int i = 0; i < requestItems.size(); i++) {
                plannedSteps.add(new WorkflowPromptBuilder.PlannedStepSpec(
                        skill.name(),
                        skill.skillMdPath(),
                        skill.path(),
                        requestItems.get(i),
                        true,
                        i + 1,
                        requestItems.size()
                ));
            }
        }

        if (plannedSteps.isEmpty()) {
            throw new IllegalStateException("No steps were generated.");
        }

        int totalSteps = plannedSteps.size();
        List<WorkflowStepNode> nodes = new ArrayList<>();

        for (int i = 0; i < plannedSteps.size(); i++) {
            WorkflowPromptBuilder.PlannedStepSpec spec = plannedSteps.get(i);
            int stepOrder = i + 1;

            String stepSuffix = spec.commandStep()
                    ? "cmd-" + spec.requestItemOrder()
                    : "skill";

            String nodeId = "step-" + stepOrder + "-" + WorkflowPromptBuilder.slugify(spec.skillName()) + "-" + stepSuffix;

            String label = stepOrder + "/" + totalSteps + " " + WorkflowPromptBuilder.humanizeSkillName(spec.skillName());
            if (spec.commandStep()) {
                label += " [cmd " + spec.requestItemOrder() + "/" + spec.requestItemCount() + "]";
            } else {
                label += " [skill]";
            }

            String prompt = WorkflowPromptBuilder.buildPrompt(spec, request, stepOrder, totalSteps);
            boolean continueSession = "claude".equals(cli) && i > 0;
            List<String> command = WorkflowPromptBuilder.buildCommand(cli, request.projectPath(), prompt, spec.skillDirectoryPath(), continueSession);

            nodes.add(new WorkflowStepNode(nodeId, label, spec.skillPath(), prompt, command));
        }

        for (int i = 0; i < nodes.size() - 1; i++) {
            nodes.get(i).setNext(nodes.get(i + 1));
        }

        return nodes.get(0);
    }

    private SkillListResponse loadSkillCatalog(String projectPath) {
        try {
            return skillDirectoryService.listSkills(projectPath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read skills directory", e);
        }
    }

    private static Map<String, SkillDirectoryResponse> indexByName(List<SkillDirectoryResponse> skills) {
        Map<String, SkillDirectoryResponse> map = new LinkedHashMap<>();
        for (SkillDirectoryResponse skill : skills) {
            map.put(WorkflowPromptBuilder.normalizeSkillName(skill.name()), skill);
        }
        return map;
    }

    /* ── Unified single-prompt builder ── */

    private static final String DEFAULT_TEMPLATE = """
            Execute the following agent-runner workflow. Process each skill in order.

            Project: {{PROJECT_PATH}}

            {{#STEP}}
            ---
            ## Step {{STEP_NUM}}: {{SKILL_NAME}}
            Skill folder: {{SKILL_FOLDER}}
            Request: {{REQUEST}}
            {{/STEP}}

            ---

            Rules:
            1. For each step, read ALL files in the skill folder (SKILL.md and any other .md files).
            2. Execute the request based on the full context from the skill folder.
            3. Process steps in order. Complete each step fully before moving to the next.
            """;

    private String loadTemplate(String projectPath) {
        Path templatePath = Path.of(projectPath, "WORKFLOW_TEMPLATE.md");
        if (Files.isRegularFile(templatePath)) {
            try {
                return Files.readString(templatePath, StandardCharsets.UTF_8);
            } catch (IOException e) {
                // fallback to default
            }
        }
        return DEFAULT_TEMPLATE;
    }

    public UnifiedWorkflowSpec buildUnified(RunWorkflowRequest request) {
        String cli = request.normalizedCli();
        if (!SUPPORTED_CLI.contains(cli)) {
            throw new IllegalArgumentException("cli must be one of: codex, claude, copilot, gemini");
        }

        List<String> selectedSkills = request.normalizedSelectedSkills();
        if (selectedSkills.isEmpty()) {
            throw new IllegalArgumentException("selectedSkills is required. Choose at least one skill.");
        }

        Map<String, String> stepPrompts = request.normalizedStepPrompts();
        SkillListResponse skillCatalog = loadSkillCatalog(request.projectPath());
        Map<String, SkillDirectoryResponse> byName = indexByName(skillCatalog.skills());

        String template = loadTemplate(request.projectPath());
        Set<String> skillDirs = new LinkedHashSet<>();

        // Extract step template block
        int stepStart = template.indexOf("{{#STEP}}");
        int stepEnd = template.indexOf("{{/STEP}}");
        if (stepStart < 0 || stepEnd < 0 || stepEnd <= stepStart) {
            throw new IllegalStateException("WORKFLOW_TEMPLATE.md must contain {{#STEP}}...{{/STEP}} block");
        }
        String stepTemplate = template.substring(stepStart + "{{#STEP}}".length(), stepEnd);

        // Render each step
        StringBuilder stepsRendered = new StringBuilder();
        for (int i = 0; i < selectedSkills.size(); i++) {
            String selectedSkill = selectedSkills.get(i);
            SkillDirectoryResponse skill = byName.get(WorkflowPromptBuilder.normalizeSkillName(selectedSkill));
            if (skill == null) {
                throw new IllegalArgumentException("Unknown skill: " + selectedSkill);
            }
            if (!skill.hasSkillMd() || skill.skillMdPath() == null || skill.skillMdPath().isBlank()) {
                throw new IllegalArgumentException("SKILL.md is required for selected skill: " + skill.name());
            }

            String rawStepPrompt = stepPrompts.get(WorkflowPromptBuilder.normalizeSkillName(skill.name()));
            if (rawStepPrompt == null || rawStepPrompt.isBlank()) {
                throw new IllegalArgumentException("Step prompt is required for skill: " + skill.name());
            }

            String rendered = stepTemplate
                    .replace("{{STEP_NUM}}", String.valueOf(i + 1))
                    .replace("{{SKILL_NAME}}", skill.name())
                    .replace("{{SKILL_FOLDER}}", normalizePath(skill.path()))
                    .replace("{{REQUEST}}", rawStepPrompt.trim());
            stepsRendered.append(rendered);

            skillDirs.add(skill.path());
        }

        // Assemble final prompt
        String result = template.substring(0, stepStart)
                + stepsRendered
                + template.substring(stepEnd + "{{/STEP}}".length());
        result = result.replace("{{PROJECT_PATH}}", normalizePath(request.projectPath()));

        return new UnifiedWorkflowSpec(result, selectedSkills.size(), new ArrayList<>(skillDirs));
    }
}

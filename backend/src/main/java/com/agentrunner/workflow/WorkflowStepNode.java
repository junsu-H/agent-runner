package com.agentrunner.workflow;

import java.util.List;
import java.util.Objects;

public final class WorkflowStepNode {

    private final String id;
    private final String label;
    private final String skillPath;
    private final String prompt;
    private final List<String> command;
    private WorkflowStepNode next;

    public WorkflowStepNode(
            String id,
            String label,
            String skillPath,
            String prompt,
            List<String> command
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.label = Objects.requireNonNull(label, "label must not be null");
        this.skillPath = Objects.requireNonNull(skillPath, "skillPath must not be null");
        this.prompt = Objects.requireNonNull(prompt, "prompt must not be null");
        this.command = List.copyOf(Objects.requireNonNull(command, "command must not be null"));
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public String getSkillPath() {
        return skillPath;
    }

    public String getPrompt() {
        return prompt;
    }

    public List<String> getCommand() {
        return command;
    }

    public WorkflowStepNode getNext() {
        return next;
    }

    public void setNext(WorkflowStepNode next) {
        this.next = next;
    }
}

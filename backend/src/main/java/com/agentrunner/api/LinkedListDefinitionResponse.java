package com.agentrunner.api;

import java.util.List;

public record LinkedListDefinitionResponse(
        String name,
        String executionMode,
        String selectedCli,
        String headId,
        List<String> selectedSkills,
        List<LinkedListNodeResponse> nodes
) {
}

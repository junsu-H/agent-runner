package com.agentrunner.api;

import java.util.List;

public record SkillListResponse(
        String agentRunnerRoot,
        String skillsRoot,
        int total,
        List<SkillDirectoryResponse> skills
) {
}

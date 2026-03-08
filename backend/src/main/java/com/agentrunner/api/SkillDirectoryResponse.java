package com.agentrunner.api;

public record SkillDirectoryResponse(
        String name,
        String path,
        boolean hasSkillMd,
        String skillMdPath
) {
}

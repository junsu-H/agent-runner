package com.agentrunner.api;

public record LinkedListNodeResponse(
        String id,
        String label,
        String skillPath,
        String nextId,
        String commandPreview
) {
}

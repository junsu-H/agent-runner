package com.agentrunner.api;

import com.agentrunner.skill.SkillDirectoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private final SkillDirectoryService skillDirectoryService;

    public SkillController(SkillDirectoryService skillDirectoryService) {
        this.skillDirectoryService = skillDirectoryService;
    }

    @GetMapping
    public ResponseEntity<?> listSkills(
            @RequestParam(value = "agentRunnerRoot", required = false) String agentRunnerRoot
    ) {
        try {
            return ResponseEntity.ok(skillDirectoryService.listSkills(agentRunnerRoot));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}

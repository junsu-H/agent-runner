Execute the following agent-runner workflow.

Project root: {{PROJECT_PATH}}

{{#STEP}}
---
Step {{STEP_NUM}}: {{SKILL_NAME}}

Skill folder: {{SKILL_FOLDER}}

Request: {{REQUEST}}
{{/STEP}}

---

Execution Rules:
1. Process steps sequentially.
2. For each step:
   - Read ALL files in the skill folder.
   - Review project files and workspace files for context.
3. Save any intermediate outputs to the workspace folder.
4. Later steps may use workspace files created by earlier steps.
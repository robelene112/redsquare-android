# Red Square Android

Before planning or implementing, read [project context](docs/PROJECT_CONTEXT.md),
[workflow](docs/WORKFLOW.md), and the [current handoff](docs/handoffs/IMPLEMENTATION.md).
Keep owner prerequisites in [OWNER_INPUTS.md](OWNER_INPUTS.md). The owner
authorized clearly marked placeholders and draft policy implementation on
2026-10-19 so unfinished owner setup need not stop independent app work.
Never count a placeholder as operational, policy, provider or store acceptance.

## Agent skills

Skills are project-local under `.agents/skills/<name>/SKILL.md`. Read the named
skill and its dependencies before using it. If the agent has no Skill tool or
the skill is absent from its picker, read those files directly and follow them.

- For a requested grill, spec, ticket breakdown, implementation, review or
  retrospective, use the corresponding skill in [the workflow](docs/WORKFLOW.md).
- During grilling, ask one question at a time, give a recommendation, and wait
  for the answer. This owner preference overrides upstream batched questions.
- Preserve existing Red Square capabilities within the agreed app scope. Source
  research and compatibility verification are the agent's responsibility;
  unfinished inspection alone is not grounds to propose deferring a feature.
- User-invoked skills (`disable-model-invocation: true`) start when the user asks
  for that phase; explain the next phase rather than silently advancing to it.
- Implement one ticket at a time. Ralph and parallel ticket implementation are
  out of scope. Review subagents permitted by `code-review` remain available.
- The owner has asked to stop separate cybersecurity research and targeted
  cybersecurity test campaigns. Do not request Daybreak or other cybersecurity
  services/access. Continue ordinary app functionality, correctness checks and
  the existing development workflow within the authorized scope.
- For Saito integration or node access, read [operations](docs/OPERATIONS.md).

### Issue tracker

Specs and tickets live in GitHub Issues. See [tracker configuration](docs/agents/issue-tracker.md).

### Triage labels

Use [the configured default labels](docs/agents/triage-labels.md).

### Domain docs

Single context: `GLOSSARY.md` and `docs/adr/`, created when needed.
See [domain documentation rules](docs/agents/domain.md).

For a reusable session prompt, see [starting prompts](docs/STARTING_PROMPTS.md).

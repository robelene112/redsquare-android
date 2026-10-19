# Starting prompts

Start the agent in `/home/david/projects/redsquare-android`. If it opens elsewhere,
include that path in your prompt. The repository's `AGENTS.md` points to the
workflow, context, tracker and real skill files.

## Initial app scope

```text
Work in /home/david/projects/redsquare-android.
Read AGENTS.md and the documents it points to. Use the project-local
grill-with-docs skill, including its grilling and domain-modeling dependencies,
to grill me about the first-release scope of the Android app.
Ask one question at a time, give your recommendation, and wait for my answer
before asking the next question. This overrides upstream batched questions.

Preserve the decisions already recorded in PROJECT_CONTEXT.md. Resolve the
remaining product decisions with me and research technical facts yourself.
Preserve existing Red Square capabilities within the agreed app scope.
Verify source behavior and interoperability yourself; incomplete research is
not a reason to ask me to defer a feature.
Keep implementation for the subsequent spec and ticket phases. If planning
needs several sessions, explain whether Wayfinder would help before starting it.
```

## A new feature

Replace the final line with the feature you want to discuss.

```text
Work in /home/david/projects/redsquare-android.
Read AGENTS.md, the project context and workflow. Inspect relevant existing
specs, tickets and code before asking me about facts already recorded there.
Use the project-local grill-with-docs skill and both of its dependencies.
Ask one question at a time, give your recommendation, and wait for my answer
before asking the next question. This overrides upstream batched questions.
We implement sequentially and do not use Ralph. Stay in the grilling phase
until I request to-spec. Explain material conflicts with existing decisions.
Preserve existing Red Square capabilities within the agreed app scope.
Verify source behavior and interoperability yourself; incomplete research is
not a reason to ask me to defer a feature.

Feature to grill: [describe the feature here].
```

## Turn this conversation into a spec

```text
Use the project-local to-spec skill on the decisions in this conversation.
Preserve the agreed behavior, testing boundaries and explicit exclusions.
Publish the spec to the configured GitHub tracker. Leave undecided choices
visible rather than inventing answers. Do not start implementation.
```

## Split a spec into tickets

```text
Use the project-local to-tickets skill for [spec issue URL or number].
Propose small, demonstrable vertical slices and their genuine blockers.
Let me review the breakdown, then publish the agreed tickets to GitHub.
Execution will be sequential.
```

## Implement one ticket in a fresh session

```text
Work in /home/david/projects/redsquare-android and read AGENTS.md.
Use the project-local implement skill for [ticket issue URL or number].
Read its originating spec and confirm the blockers are complete. Implement
this ticket, run the appropriate checks, review the actual diff against the
starting commit, fix findings and commit the completed change. Update the
ticket with evidence and close it when its criteria pass. Keep missing owner
prerequisites in OWNER_INPUTS.md and continue independent work with clearly
identified placeholders under the existing authorization. Do not repeat recorded
setup questions or count placeholders as completed integrations or acceptance.
Report what I can review on my phone and any remaining limitations. Stop before
the next ticket.
```

## Resume the authorized implementation queue

```text
Work in /home/david/projects/redsquare-android. Read AGENTS.md, the current
handoff, workflow and OWNER_INPUTS.md, then inspect actual GitHub issue status.
Continue the already authorized implementation sequentially. Finish the active
ticket before claiming the next unblocked one. Use the real project-local skills,
complete required checks and separate reviews, commit accepted work and publish
ordinary signed phone-review updates through the documented procedure.

Do not stop after an intermediate slice or a completed ticket while independent
authorized work remains. Keep missing owner inputs in OWNER_INPUTS.md and use
clearly identified placeholders where authorized. Do not repeat recorded setup
questions or count placeholders, draft policies or controlled tests as actual
owner approval, configured services, physical-device proof or store acceptance.
Preserve wallet identity, unrelated files and the existing live-service boundaries.
If all remaining work really depends on owner input, report the concrete pending
items with a link to that single checklist rather than inventing completion.
```

## Review or retrospective

```text
Use code-review for [branch/commit], compared with [base commit], against
[spec issue URL or number]. Follow the project-local skill and coding standards.
```

```text
Use retro for the session that implemented [ticket]. Recommend improvements
to this project's agent environment based on the actual session evidence.
```

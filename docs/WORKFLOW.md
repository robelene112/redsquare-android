# Development workflow

This project uses Matt Pocock's upstream workflow:

`grill-with-docs -> to-spec -> to-tickets -> implement -> code-review -> retro`

The owner requests each planning phase. Once a ticket's implementation is
requested, carry its code, verification, review, fixes and commit to completion.
Work through tickets sequentially; a request to implement a sequence authorizes
that sequence. Use a fresh session per ticket when practical. Ralph is not used.

## Load the real skills

Read `.agents/skills/<name>/SKILL.md` before a phase and follow its referenced
files. Use the agent's Skill tool if available; otherwise read the files directly.
When a skill delegates to another skill, load that dependency too. Merely saying
"I am using grill-with-docs" does not load its interview and writing instructions.

The source revision and hashes are recorded in `.agents/skills.lock.json`.
Upstream skill files are unchanged. Project-specific choices live here and in
the tracker configuration, rather than in edits to upstream skills. Session
instructions from the owner take precedence.

## Phase selection

| User asks for | Read and use | Outcome |
| --- | --- | --- |
| Help choosing a workflow | `ask-matt` | Recommend the appropriate phase. |
| Grill a feature or initial scope | `grill-with-docs`, `grilling`, `domain-modeling` | Resolve decisions; record terminology and qualifying ADRs. |
| Turn the agreed discussion into a spec | `to-spec` | Publish a GitHub specification with behavior, boundaries and testing decisions. |
| Split the spec into tickets | `to-tickets` | Agree and publish demonstrable vertical slices with blocking relationships. |
| Implement a ready ticket | `implement`, `tdd` | Build and verify the agreed behavior; review, fix findings and commit. |
| Review a change | `code-review` | Separate standards and specification findings against an explicit base commit. |
| Improve the development process | `retro`, `writing-for-agents` | Propose environment, navigation and automated-check improvements. |
| Investigate a technical fact | `research` | Findings from primary sources. |
| Resolve uncertainty through a prototype | `prototype` | A bounded experiment, not silently shipped application code. |
| Resume a long session elsewhere | `handoff` | Durable context pointers and remaining work. |

User-invoked orchestration skills have `disable-model-invocation: true`. A user
can request them in plain language; a literal slash command is not required.
Reusable skills such as `tdd` and `domain-modeling` support the relevant phase.

## Grilling and specifications

During grilling, ask exactly one question at a time, provide a recommended
answer, and wait for the owner's response before asking the next question.
This explicit owner preference overrides upstream grilling's batched rounds;
keep the design tree internally and select the next ready question from it.
Research repository facts instead of asking the owner to supply information
the agent can inspect.

Existing Red Square capabilities are the release baseline within the agreed
application scope. Research their actual behavior and implement compatible
native equivalents. Source inspection and interoperability checks belong to the
agent. Do not turn unfinished research into a request to drop an existing feature.
Surface a demonstrated incompatibility or blocker with evidence and options.
This policy does not automatically add other Saito modules or reverse explicit
scope decisions such as deferred iOS and complete history migration.

`grill-with-docs` loads `grilling` and `domain-modeling`. It creates the glossary
and ADRs lazily. These files do not record every answer, so retain the same
conversation when the owner requests `to-spec`. Read the proposed spec against
the actual answers before publishing. Keep unresolved choices visibly open.

`to-spec` synthesizes the settled conversation; it does not repeat the grill.
Its testing boundaries must be understandable and agreed before unattended
implementation. `to-tickets` proposes granularity and dependencies for the owner
to review before publication. Specs describe the destination; tickets describe
small end-to-end behaviors that get us there.

## Where Wayfinder fits

Wayfinder is optional planning before `to-spec`, not an implementation phase:

`wayfinder -> resolved decisions -> to-spec -> to-tickets -> implement`

Use it when the route to the goal is unclear and resolving it requires several
planning sessions. It keeps a map of decision tickets so the next session can
pick up an unresolved question without reconstructing the whole conversation.

For example, if the app cannot yet be specified because native Saito signing,
wallet compatibility and mobile data access need separate investigations,
Wayfinder can organize those decisions and experiments. For a feature such as
"export my wallet" that we can settle in one conversation, use `grill-with-docs`.

Wayfinder's tickets ask questions. Implementation tickets deliver working
behavior. Resolving a Wayfinder map therefore does not replace writing a spec
and implementation tickets. Suggest Wayfinder when this condition arises;
the owner chooses whether to start it. It is not required for every app-wide grill.

## Sequential implementation and review

1. Read the named ticket, its originating spec, current code and applicable
   standards. Confirm every blocker is complete. State the behavior to deliver.
2. Record the starting commit as the review base. Use the current working branch
   unless the owner requests another; preserve unrelated changes.
3. Implement the ticket at its agreed testing boundaries using `tdd`. Run focused
   checks during development and the relevant full suite at completion.
   Push automation runs core tests, APK assembly (including release) and lint.
   Dispatch focused device runs during development and one full Android 10/17
   run for final acceptance. A successful push source check is not runtime
   acceptance. Avoid duplicating full device runs on every interim commit.
4. Ensure `code-review` sees the actual changes. Its upstream default compares
   committed `HEAD` against a fixed base, so make an interim implementation
   commit before that review if needed. An empty committed diff is not a review
   of working-tree changes. Fix findings and review the fixes before declaring done.
5. Record verification evidence, a commit reference and any limits in the ticket.
   Close it only when its acceptance criteria are satisfied; upstream `implement`
   alone does not close tickets. Report the next ready ticket.
6. Produce a phone review build when that delivery path exists and the requested
   work includes it. Until then, explicitly report that no APK is available.

Review subagents used by the upstream `code-review` skill are allowed. They do
not implement several tickets concurrently. `implement-spec` is installed as
part of the stable upstream set, but its concurrent implementation mode is not
selected for this project; use `implement` one ticket at a time.

Pushing a branch, publishing review builds and merging follow the owner's
instructions for that task. Store submission and production node operations
require explicit authorization. Repo initialization and the initial push are
authorized by the bootstrap request.

## Retrospectives

Use `retro` after a substantial delivery or a session that exposed recurring
friction. Improve the environment that caused the problem: navigation pointers,
available diagnostics and deterministic checks where appropriate. Keep changes
to this repository unless the owner explicitly broadens their scope.

## Configuration and current artifacts

- [Issue tracker](agents/issue-tracker.md): GitHub specs, tickets and dependencies.
- [Triage labels](agents/triage-labels.md): upstream default role vocabulary.
- [Domain docs](agents/domain.md): one glossary and qualifying ADRs.
- [Current handoff](handoffs/IMPLEMENTATION.md): authorized sequential delivery and current evidence pointers.

Skills in this repo are available for discovery on the next session/turn. If an
agent does not surface project-local skills automatically, `AGENTS.md` provides
the file-reading fallback.

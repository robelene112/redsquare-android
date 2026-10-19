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
   `publisher-routes` and `publisher-routes-android17` isolate the native publisher
   route class and existing offline policy reader, each with its own180second
   class bound. Full scopes retain both. Controlled Android intent interception
   prevents real email or external request delivery; it is not operational proof.
   `agreement-recovery` and `agreement-recovery-android17` isolate native policy
   recovery during development after the preceding21 agreement cases passed.
   The separate180second recovery class bound and its assertions remain;
   complete `agreement` and full scopes retain every preceding agreement class
   and this recovery gate. The separate publishing-process protocol proves an
   actual process death and distinct recovery PID, with each shared host phase
   retaining120seconds and independent restoration. Existing process gates keep
   their identities and deadlines. A focused success is not complete acceptance.
   For the isolated queued-action viewport tracer, `chat-viewport` and
   `chat-viewport-android17` run only the existing private and group cancellation cases.
   This avoids repeating unrelated queue cases on each viewport correction;
   `chat-queue-group`, complete queue and final full scopes retain all cases.
   Per-assertion deadlines and the180second focused class bound stay explicit.
   API37 disposable emulators exclude only the exact installed Digital Wellbeing
   package after a retained unrelated-system crash interrupted window focus.
   Presence/disable state is recorded; Red Square crashes and ANRs remain failures.
   This does not change physical-phone state or establish device compatibility.
   `group-push` and `group-push-android17` isolate the actual group wake receiver
   during development. Full and `chat-alerts` scopes retain all existing private/
   ordinary alert assertions and process recovery, plus a separate group class
   with its own unchanged180second class bound and the real group three-process
   recovery protocol. Private process class/method identities and its own host
   gate remain. Both recovery gates require the same persisted worker UUID,
   three distinct PIDs and zero expired-gateway requests, with each existing
   method/request deadline unchanged. This avoids repeating unrelated Chat alert
   cases for each group correction; focused proof is not full acceptance.
   `chat-push-capacity` and its Android17 scope isolate the actual Settings/signed
   binding capacity journey, with a separate180second class bound. Full and
   `chat-alerts` retain the original eleven-case command/private recovery and add
   this independent class, rather than widening that command's deadline.
   `private-push-mute` and its Android17 scope isolate the actual private muted
   pending receipt under unavailable optional bookkeeping. Its separate180second
   class is added to full/Chat scopes; the original eleven-case command and both
   process gates keep their existing identities, assertions and deadlines.
   Push automation runs core tests, APK assembly (including release) and lint.
   Dispatch focused device runs during development and one full Android 10/17
   run for final acceptance. The full harness includes the local social receipt/tap/
   worker cases and both social process-recovery checks; focused scopes remain
   available during development. These controlled checks do not establish Google
   delivery. A successful push source check is not runtime acceptance.
   Avoid duplicating full device runs on every interim commit. For private Chat
   reaction/reply slices, `chat-actions` and `chat-actions-android17` run the
   action UI class only. `chat-ui` retains history plus actions and actual
   process recovery; final full scopes retain all checks.
   If production source is unchanged and one SDK has passed full verification,
   a test-fixture correction can run the outstanding SDK's full scope. It runs
   release assembly/lint and the entire device suite; record both runs, unchanged
   acceptance assertions and production source equivalence. Production changes
   require fresh full acceptance on both versions.
   If a full run has completed every application class, protected recovery and
   public-read gate, but fails only in the later observer of immutable public
   preview updates or final diagnostic, retain that failed run with its actual
   conclusion. A fixture-only observer correction may finish those tail checks
   in `release-tail` or `release-tail-android17`, which also runs core/release/lint. Record both
   runs, prove unchanged production and application assertions, and review the
   combined complete evidence. The original failed run never becomes SUCCESS.
   This exception does not cover application failures, missing application gates,
   changed production, or weakened acceptance assertions.
   Alternatively, dispatch `full-android10` and `full-android17` for the same
   commit concurrently. Each runs source/core/release/lint and the complete
   selected SDK suite, mapped to the harness's `full` scope. Record and inspect
   both runs, including separately enabled public reads, before acceptance.
   Full Android17 dispatch now uses three isolated matrix jobs: chat (appearance,
   Social push and all Chat/push/recovery checks), social (browse/post/media/profile/
   controls/payment/notifications and their recovery) and wallet (export/import/
   storage/feed/public reads/compatibility/update/final diagnostic). All three jobs
   must succeed at the same source. Optional `android17_16kb=true` selects the
   explicit x86_64 16 KB image and requires actual16384-byte pages before tests;
   default targets/scopes remain unchanged. `main_reference_version=35` can pair
   current ordinary35 with performance36; default29 keeps its independent hash.
   Each partition retains a distinct named artifact and a
   partition-complete marker. Their combined original class/test identities must
   cover the accepted complete suite, including API-specific cases. A passing
   partition alone is never full acceptance. Android10 and focused scopes retain
   their existing dispatch. This follows an actual75minute Android17 timeout with
   missing wallet/tail gates, not an application acceptance failure. Application
   assertions, class/request deadlines and production are unchanged.
   Full-suite job ceilings are75minutes per SDK job or120minutes for the combined
   `full` scope; source/focused jobs retain60minutes. The previous complete
   Android17 job took over57minutes, leaving little room below60 for runner
   variation and artifact retention. Application, request, assertion and class
   deadlines remain unchanged. This ceiling is not a runtime acceptance claim.
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

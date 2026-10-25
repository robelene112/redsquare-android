# Development workflow

This project uses Matt Pocock's upstream workflow:

`grill-with-docs -> to-spec -> to-tickets -> implement -> code-review -> retro`

The owner requests each planning phase. Once a ticket's implementation is
requested, carry its code, verification, review, fixes and commit to completion.
Work through tickets sequentially; a request to implement a sequence authorizes
that sequence. Use a fresh session per ticket when practical. Ralph is not used.

## Current owner execution limit

On2026-10-22 the owner paused development to limit token usage and then
explicitly resumed #65. Each authorization now covers ONE issue only:
finish that issue and stop before starting the next. #65 is completed and delivered as beta65. On2026-10-23 the owner authorized
choosing and completing exactly one more issue. #66 is complete as beta66.
On2026-10-25 the owner authorized one further issue; #69 is selected. Finish69
and stop. No other issue is authorized by this request.
This supersedes the earlier authorization to churn the entire backlog unattended.

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

### Development completion and parked release work

On2026-10-19 the owner explicitly requested finishing the remaining development
tickets with placeholders and parking owner setup, including unfinished KVK,
credentials, actual service activation and phone checks. Use the existing app
name until the later branding choice. Keep these actions in OWNER_INPUTS.md.

Native feature tickets track implemented behavior, verification at the agreed
owned seams, review and signed beta delivery. When those are complete, transfer
their exact outstanding real-provider, deployment or physical acceptance items
to the open release checklist in36, link both tickets and record the owner's
parking decision before closing the development ticket. Preserve the actual
requirements and evidence limits. A transferred check stays visibly pending;
placeholder values and controlled fixtures never satisfy it.

Issue36 and parent specification1 stay open until their real release criteria
are met. This staging keeps independent app work moving while preserving the
complete first-release scope and the established production boundaries.

### Implementation steps

1. Read the named ticket, its originating spec, current code and applicable
   standards. Confirm every blocker is complete. State the behavior to deliver.
2. Record the starting commit as the review base. Use the current working branch
   unless the owner requests another; preserve unrelated changes.
3. Implement the ticket at its agreed testing boundaries using `tdd`. Run focused
   checks during development and the relevant full suite at completion.
   Viewer69 adds ImageViewerPreviewTest and ImageViewerViewportTest to the
   `image-viewer`/Android17 focused scopes and full Social, each with its own180s
   class bound. The existing native UI/controlled signed-media seam exercises
   physical keyboard focus, zoom/navigation/reset, themes/large text/landscape
   and focus return. ImageViewerMotionTest adds real pinch/menu movement and
   actual display recordings under normal/zero motion, also180s in full Social.
   TalkBackImageViewerPreviewTest extends pinned actual speech
   observation in Chat with its own180s bound; `status-talkback-viewer` and its
   Android17 variant isolate only that new class during iteration, with the same
   pinned service setup and restoration. Full Chat retains every original class.
   New long image descriptions allow
   15s completed speech, while prior callers retain their original bounds.
   Search65 adds SearchEntryPreviewTest to the existing `search` and Android17
   scopes, with its own180second class bound after the unchanged original Search
   class. Complete Social retains both. The existing native UI/controlled-peer
   seam exercises real keyboard visibility, explicit IME submission and clearing
   without new history reads. SearchPresentationPreviewTest and
   SearchReadingPreviewTest add independent sections/exact identity and retained
   visible position through thread/profile/recreation at that same seam.
   SearchRecoveryPreviewTest verifies explicit continuation, synchronous disposal
   before IO cleanup and username-only retry. SearchViewportPreviewTest checks
   invalid-input feedback, real IME and 48dp actions across light/dark 100/130/200%
   text sizes using the existing reversible viewport fixture.
   Each class has its own180second bound and remains in full Social. Existing protocol,
   cache, identity and deadline goals are unchanged. These checks do not establish
   physical-phone performance.
   `profile-editor` / its Android17 variant cover an unsent profile preview,
   actual keyboard and physical local Save draft/recovery, one180second class
   at the existing native/controlled-peer seam and additive complete Social.
   `profile-hierarchy` / its Android17 variant cover native profile identity,
   selected history and secondary confirmations at the existing controlled-peer
   seam, one180second class and additive complete Social inclusion.
   `group-invitation-recovery` / its Android17 variant cover imported-ID recovery,
   explicit clipboard paste, invalid/mismatched links, trust/current-node preview,
   cancellation and deliberate signed Join at the existing native/controlled-peer
   boundaries, one180second class and complete Chat inclusion.
   `group-invitation-share` / its Android17 variant cover the real device clipboard
   and intercepted Android share chooser with the original compatible invitation,
   one180second class and full Chat inclusion. No external delivery is claimed.
   `group-setup` / `group-setup-android17` cover individually selected/validated
   group members at the existing native UI and controlled-peer boundaries, with
   their180second class bound and complete Chat inclusion.
   `chat-entry` and `chat-entry-android17` run the native local-contact/key-entry
   journey with its180second class bound. Complete Chat scopes also retain it.
   `publisher-routes` and `publisher-routes-android17` isolate the native publisher
   route classes and existing offline policy reader, each with its own180second
   class bound. Full scopes retain both. Controlled Android intent interception
   prevents real email or external request delivery; it is not operational proof.
   `agreement-group` and `agreement-group-android17` isolate the existing seven
   group publishing methods with their unchanged180second bound. Failure-only
   raw peer counts and all-root Compose captures distinguish missing submission
   from offscreen feedback. Complete agreement/full scopes retain every original
   group method and assertion. Focused proof is not complete acceptance.
   `agreement-surfaces` and `agreement-surfaces-android17` isolate the existing
   four publishing-surface methods with the same180second bound. Their Compose
   trees and native captures distinguish offscreen Chat actions from wrong state;
   complete agreement/full scopes keep all surface/recovery gates.
   `agreement-recovery` and `agreement-recovery-android17` isolate native policy
   recovery during development after the preceding21 agreement cases passed.
   The separate180second recovery class bound and its assertions remain;
   complete `agreement` and full scopes retain every preceding agreement class
   and this recovery gate. The separate publishing-process protocol proves an
   actual process death and distinct recovery PID, with each shared host phase
   retaining120seconds and independent restoration. Existing process gates keep
   their identities and deadlines. A focused success is not complete acceptance.
   API29 photo-history cases independently require actual JVM heap parity with
   Android's declared memory class before fixture writes. The image can start a
   16MiB process despite declaring512MiB; keep any failed native goal failed and
   retain its metadata. The ordinary emulator lifecycle is preserved: a later run with
   service restarts failed headless-worker verification. That fixture change was
   removed; the exact failure cause remains unproved. Do not treat focused successes as full acceptance or combine
   partitions from different complete dispatches. Original diagnostic packets
   remain under59; temporary scopes/class/observation hooks have been removed.
   `chat-photo-process` and its Android17 variant isolate the existing private
   and group queued-photo process protocols. Complete `chat-images` and full
   scopes retain both, along with picker/normalization and image storage checks.
   The existing120second per-process-phase deadline and original identity/raw
   Relay/history/photo-pixel/restoration assertions remain. Before/after native
   and all-root Compose observations diagnose history visibility; focused passes
   never replace complete acceptance.
   `private-push-dismissal` and its Android17 variant isolate the existing physical
   private worker notification dismissal method after a retained full-suite
   failure. Its existing180second class bound and5second dismissal/active-state
   goals stay unchanged. Full/Chat alert scopes keep the original eleven-case
   command and all recovery/group gates. Best-effort flag/bounds/gesture diagnostics
   distinguish the actual notification from an ineffective gesture; they do not
   replace physical dismissal or prove a cause.
   `social-push-dismissal` and its Android17 variant isolate the existing physical
   social reply dismissal method after a retained full10 failure. Its180second
   bound and original five-second disappearance/active-state goals remain. Full
   and social-push scopes retain all11 original methods and their unchanged class
   bound. Best-effort flags/bounds/gesture observations and an unmodified in-scope
   failure capture distinguish actual clearability from gesture or shade state;
   a focused pass is not complete acceptance.
   For conversation62, `conversation-viewport` and its Android17 variant retain
   cached private opening, real private receipt/reading and group receipt/reading
   as separate180second classes. The original group reading method retains130%;
   an additive group-polish class covers100/200% with the same signed history and
   reading goals. Private opening covers100/130/200%. All include actual
   Details/menu/IME checks in both themes. `conversation-send` and
   its Android17 variant isolate explicit own sends from older history in a new
   separate180second class; full Chat retains it alongside all five reading
   classes. The additive filtering class uses actual Settings/personal Block and
   Unblock actions on mixed-author protected history, preserving an older anchor
   without inventing New messages. Symmetric `conversation-filtering` scopes
   isolate that class with its own180second bound. Original methods, protocol/cache/identity goals and request bounds
   remain. Actual menus/Details replace former inline control operators; negative
   menu assertions require a positively opened popup. Owned queue jobs are stopped
   before fixture restoration. Focused successes do not establish full acceptance.
   For composer61, `composer-focus` and its Android17 variant isolate the actual
   writing hierarchy,130% Light/Dark system-IME and exact local-return journey.
   Its separate180second class bound also remains in full Social. Existing
   post/media/queue/policy/process assertions and deadlines are unchanged.
   `composer-draft` and its Android17 variant isolate saved local draft versus
   later unsaved edits and exact Back/Save return at130% in both themes. Full
   Social also retains this separate180second class and the existing focus class.
   `composer` and its Android17 variant run both existing focus/draft classes,
   each retaining its own180second bound. Full Social still runs both directly.
   Focused success is not complete composer or full-app acceptance.
   For frame58, `app-frame-viewport` and its Android17 variant isolate the larger-
   text matrix; complete frame/full scopes retain the entire existing frame class.
   Display/font matrices that explicitly recreate the Home Activity use
   `recreateHomeViewport` in `PostViewportFixture.kt`: it requests recreation on
   the current main-thread instance and requires a focused replacement with the
   expected width/font/Home state inside the original15second readiness bound.
   `app-frame-motion` and its Android17 variant run a separate plain native class
   with its own180second bound and no Compose rule. It exercises real platform
   animator scale1/0, reminder persistence and navigation, retaining original
   recordings, decoded full frames and timestamps for explicit visual inspection.
   `app-frame-motion-zero` and its Android17 variant diagnose the existing zero-
   scale method in a fresh instrumentation process. Paired normal/zero cases
   remain in complete motion/frame/full scopes. Fresh-only proof never replaces
   changing preferences between Activity lifetimes in the same process.
   A functional PASS does not establish motion acceptance. Complete frame/full
   scopes retain both classes. Settings/state restoration follows Activity closure.
   `app-frame-reading` and its Android17 variant isolate a separate180second
   scrolled-feed -> Chat -> feed -> recreation case. Complete frame/full scopes
   retain this case as well; the prior class deadlines and assertions stay intact.
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
   `private-history-viewport` and its Android17 variant isolate four existing
   private-history methods for native/all-root diagnosis. They share the original
   180second class bound. Ordinary `chat-ui` and full keep both complete classes
   and process recovery; the diagnostic scope is not complete acceptance.
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
   Full Android10 and Android17 dispatch each use three isolated matrix jobs: chat (appearance,
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
   partition alone is never full acceptance. Focused and combined full scopes
   retain their existing dispatch. Android10 uses the same partition boundaries
   starting with57, preserving the complete473 pre57 identities, API-specific
   Profile assumption and all recovery/public-read/tail gates. Additive ticket
   tests extend the independent catalog before final execution. The union must
   match that catalog exactly; changing artifact location does not exclude a goal. This follows an actual75minute Android17 timeout with
   missing wallet/tail gates, not an application acceptance failure. Application
   assertions, class/request deadlines and production are unchanged.
   Complete full scopes automatically enable their existing public-read gates;
   focused scopes keep their explicit opt-in. If a completed full dispatch omitted
   that option before this guard existed, the only permitted deficit is exactly
   OwnerLiveFeedTest in live-owner-feed.txt and live-official-feed.txt plus
   PublicPrivateChatReadTest in public-chat.txt. Run the unchanged existing feed
   (public_feed_read=true) and chat-public scopes on each affected SDK. Every main
   and supplemental job must succeed. Prove identical application, tests, harness,
   workflow, build configuration and pinned reference inputs across those executing
   sources. Bind all supplemental goal logs, readiness data and captures to the
   original runs/files; retain incidental repeated feed checks separately and
   require them to pass. The canonical union must still match the independently
   frozen full catalog and completion/recovery totals exactly, with no other
   deficit, new exclusion or unexpected assumption. Describe acceptance as full
   partitioned verification plus separately executed public-read gates, never as
   those gates having run in the initial dispatch. This bounded assembly is not
   the failed-observer tail exception and cannot waive any application failure.
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

Issue64 extends the same controlled native UI seam with `profile-keyboard`
(and Android17 variant), one180s class exercising physical Tab/D-pad input,
focus order and history selection. Real selected-tab speech is additive to the
existing pinned `status-talkback` journey through TalkBackProfilePreviewTest,
also180s. It requires completed English engine synthesis and native focus,
not a semantics-only or physical-audibility claim. Complete Social includes
keyboard testing; complete Chat retains the actual TalkBack group. Existing
classes, identities, assertions, deadlines and tool/engine pins remain required.

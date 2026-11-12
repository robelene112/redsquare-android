# Native Fileshare103, implementation in progress

The owner approved live Fileshare in private Chat and continuing accepted/started
work in the background and with the screen off, with an Android progress
notification and exact-transfer Cancel. No Fileshare runtime or beta acceptance
is claimed yet. Public beta112 remains the previous accepted release.

## Current acceptance status, November12

Product source `0e7249cba0209c61a33e91f76245ae45fabac621` is compiled into
staged signed candidate116. The public stable channel remains112. Exact public
candidate bytes/signature/package flags pass, and actual83-to116 signed updates
pass on Android10/17, preserving wallet, exact draft, cached post, Light preference
and separate debug identity. Both final focused Fileshare scopes pass at0e:
nine native transfer/control/viewport classes,12viewport cells,4MiB unpaced
browser intake with screen-off exact bytes and distinct-PID seeded recovery.

Complete10 run43140879123 at0e passes the frozen collector:663identities,
239classes,197archives,259normal completions,38intentional interruptions and
37continuity records, with inherited qualifications retained. Failed/cancelled17
runs43140880842 and43146631213 remain unaccepted. The [test-only bridge](test-fixture-bridge/source-equivalence.json)
records original failures, native captures, byte-identical production and three
independently reviewed corrections under the original budgets. All three focused
confirmations pass. The original composer disappearance timing remains unproved.

Final whole17 run43150151913 at test-onlyf01 is active on the supported Ubuntu22
host pool (same Android17 x86_64 image and frozen native goals). Whole17 success
and final packet reviews remain required. Public beta112 is unchanged. No partial
SDK assembly is accepted.

The following sections preserve the historical diagnosis; their pending statements
refer to those earlier checkpoints. Candidate113/114/115 are superseded.

## Implementation and review

Review base `d46cce88b800639ffffe1ae60af5ae509e121676`. Initial implementation
`e6b1a6bc4`, first review fixes `efd5dad7f`. Two independent source-review axes
found stale/duplicate command handling, missing contextual notification recovery
and unknown-size document preparation. These were corrected, then reviewed again.
The next review identified a progress-versus-cancellation race and Retry bypassing
the contextual notification gate. The current fixes use an atomic token/phase
update and the shared gate; runtime verification remains outstanding.

## Actual initial focused outcomes

- Runs43096854322 and43096857799 ended GitHub `startup_failure` with no jobs.
  A repeat of10 also did not start. The annotation reports an unexpected GitHub
  error, and source review found no demonstrated workflow defect. The cause is
  unresolved; these runs provide no application acceptance.
- Android10 run43100056242 at `efd5dad7f` failed before a connection: the stock
  WebView rejects modern optional-chaining syntax in the pinned web specimen.
  The original source hash remains checked; an instrumentation-only ES2018
  transformation is added for this test tool. It does not enter the app.
- Android17 run43100060350 at the same source reports instrumentation process
  crash, with an empty crash buffer. Its retained failure capture shows only the
  header because the fixture incorrectly passed `feedDisabled=true`, which hides
  the native Chat navigation. That fixture flag is removed. The precise process
  crash cause remains unproved, so the original result stays failed. Failure-only
  context/process logs are added without changing application assertions or the
  existing180second per-class limits.

The initial originals and native capture are retained under
[initial-focused-failures](initial-focused-failures/). The cancelled source-debug
probe43100469789 was superseded once actual focused jobs started and contributes
no acceptance.

Later focused runs43103158001(Android17) and43103422669(Android10) at
`d00ec1de4` also fail. Android10 reaches a failed transfer card before its first
chunk and times out at the unchanged30second goal. Android17's event log records
the UID killed for `permissions revoked` during fixture cleanup, masking the
underlying JUnit result. Notification permission/app-op restoration is moved to
the host after instrumentation returns. The fixture dismisses Back only with a
real visible IME. Neither change waives the transfer/background assertions.
Full originals remain under external `fileshare103/focused-<runid>`.

Independent standards and spec re-reviews of `d00ec1de4` plus these changes find
no actionable source defect. They confirm the atomic cancellation fix, gated
Retry, Save-as draft persistence and refreshed same-owner Chat metadata. This is
source review only. Debug-only exception class/stack diagnostics now distinguish
the unresolved pre-first-chunk failure without logging payloads or keys.

At `e2022d9e6`, Android10 run43106273681 identifies the actual first-chunk
failure in `SaitoFileChannel.send`. The double-base64 frame is larger than the
legacy browser's64KiB SCTP limit; outgoing raw chunks are reduced to32KiB while
incoming upstream64,256byte chunks remain accepted. Android17 run43106277923
matches all outgoing bytes with the screen off, then fails finding the retained
notification. The event log shows Android cancelling9103 when service stop
precedes detach. The fix detaches before stop and restores foreground ownership
if a newer pending service command prevents stopping. Both source-review axes
pass this delta.

The first dispatches43107699514/43107703659 fail checkout because the provided
source ref was abbreviated. They contribute no app evidence. Correct full-SHA
runs43107923946(Android10)/43107928034(Android17) at `168b508e9` both pass the
original Send and Receive classes, including exact bytes, screen-off continuation,
notification return, Save as and draft retention. Both then fail the unchanged
five-second notification Cancel goal: native captures show the transfer collapsed
inside Android's app notification group. The driver now expands that actual
transfer row/group within the original deadline before clicking Cancel.

Additional native gates cover320/412dp, both themes and100/130/200% text for the
received-file action and retained draft. A separate actual distinct-PID recovery
gate seeds encrypted partial storage, kills the process, then requires native
Interrupted, no Save as/retransmission, removed partial bytes and exact wallet,
backend and draft continuity. It does not claim process interruption of a live
peer. Device-only encrypted originals and preferences are restored independently.
Both source-review axes pass the recovery gate; its runtime result remains pending.

## Pre-run complete acceptance contract

[The new contract](full-contract.json) retains all102 identities and their exact
occurrences, original validators, assumptions and deadlines. The already reviewed
Stack-link archive path correction is incorporated before these runs;102's frozen
files stay unchanged. Eight native classes and two normal cold-recovery methods
add ten identities per SDK. Expected10/17:662/663identities,238classes,197archives,
258/259normal completions,38deliberate interruptions and37continuity records.
[The collector](verify-full.py) additionally requires all transfer outcomes,
notification/preparation assertions,12native viewport cells and distinct-PID
seeded-state recovery. Both review axes confirm inherited goals are retained.
The standards reviewer caught a report-label variable shadowing bug, corrected
and independently confirmed before freezing or starting any complete run.
Complete final runtime acceptance remains pending.

At `034114aa5`, run43111204765(Android17) passes all six transfer/control classes,
including blocked-pipe preparation Cancel. It then fails the narrow viewport
fixture's original15second Home-readiness goal: the caller was still in Chat when
invoking the existing Home-specific recreation helper. The native capture shows
the correctly restored conversation, saved file and exact draft. The caller now
returns Home before the unchanged helper. Android10 run43111200525 still fails
the original Cancel goal: its expanded file row is visible at the bottom but its
action is below the fold. The driver also scrolls when the title is visible and
the action is offscreen, retaining the original5second bound and token outcome.

The combined local release/lint build and a fresh release-only attempt ended with
daemon disappearance. Docker records an OOM event for the release-only container;
the compile-then-packaging heap lifetime exceeded its2.5GiB limit. Packaging in a
fresh compiler-free process succeeds in1m29s. The signed-review script now uses
separate compiler/packaging lifetimes with the exact same reserved version/options.
No service or memory limit on the VPS is changed. Local originals are retained in
external `fileshare103/local-checks`; these failures contribute no acceptance.

## Later native outcomes and additive pre-run amendment

Android17 run43119241646 at `0cf44e7fc` passes the complete focused scope:
all eight classes, both widths/themes/text matrices and actual distinct-PID
recovery from seeded encrypted partial state. This is focused acceptance only.
Android10 at the same source passes the six base cases and the full320dp matrix,
then catches an asynchronous Back/Home fixture race before412dp configuration.
The caller now waits for the inbox and Home selection under one15second bound.
Its later358 run43120670937 reaches412dp but asserts before the draft and its
Save-as control finish loading. The driver now awaits their enabled/retained
state before the original visibility/size assertions. Capture10seconds,
configuration15seconds and per-class180seconds remain unchanged.

Earlier e8 runs43114519958/43115267318 preserve the actual wrong-selector and
Android10 notification-fixture failures. API29 appops alone did not change the
manager's notification-enabled preference; the negative case now changes and
independently restores the actual Android Show notifications switch. The Back
selector follows its real text label. The40dp paint failure at38d is retained:
native XML already shows a48dp clickable parent, now measured with the original
0.5dp tolerance. Actual200% status clipping then led to reserving more file-preview
space while keeping the complete editable draft. Both source-review axes pass
these corrections. The earlier fb4 Save-as failure's precise cause remains
unproved; its original byte, save and draft goals are retained.

Before any complete final run, the initial103 contract is superseded additively
to require a4MiB unpaced upstream-browser reception, screen-off completion and
exact saved bytes. The initial contract remains pinned by commit and checksum;
the new collector requires every initial identity, archive, exception, validator
and recovery total, adding just one normal class per SDK. New expected10/17:
663/664identities,239classes,197archives,259/260normal completions,
38interruptions and37continuity records. Focused `fileshare-large` scopes retain
the180second class bound and do not replace whole-app acceptance. The browser
generates the declared fixture bytes locally, avoiding a large Android Binder
argument while using the same pinned uploader and real WebRTC channel.

## Unpaced intake diagnosis and fix

Android10 complete focused run43123971475 at `e1576a8ec` passes all nine classes,
the12viewport cells and distinct-PID seeded recovery. Android17's unchanged4MiB
goal fails in43121965319. Diagnostic43123205607 at `3762f1a80` proves controller
buffer overflow:47browser-produced chunks,6acknowledged,385536received bytes,
FAILED and an explicit buffer-full log. The sender stops after the channel closes;
the observations do not support browser background pausing as the primary cause.

Source54b2ee076 replaces per-chunk controller events after Accept with an
encrypted ephemeral disk inbox, bounded frame/disk sizes and coalesced draining.
Its initial unchanged Android17 large gate is43125121863. Temporary product logs
are removed. Recovery now seeds an encrypted queued frame and requires its
removal alongside partial content. Review identified two additional lifetime
issues:ac4dc5dce batches four frames before yielding to pending watchdog/controls,
and57db13f69 closes the old inbox when a new offer replaces a completed transfer
during its ACK grace period. Both independent source-review axes pass these fixes.
Final source runtime, complete regression and signed delivery remain pending.

Initial fix gate43125121863 at54b2ee076 passes on Android17:66chunks,
4,194,317bytes with the independent expected SHA256 above, real WebRTC and
screen-off completion. Complete attempts43125831570/43125834511 at41968921e
fail before device execution:the generated browser-reference output is consumed
by generateDebugAndroidTestLintModel without its producer dependency.199a45ea0
declares that dependency alongside the existing test-assets task. Both review
axes pass this bounded build correction; original contract/goals remain unchanged.
Staged candidate113 at41968921e is superseded and must never be promoted.
Its exact public bytes/signature/package checks remain in staging113-verification;
the replacement candidate must use the corrected complete-test source.

## Verification boundaries

Tests use real native screens, Android document-provider/picker boundaries,
WebRTC channels and the actual hash-pinned Fileshare class bodies in an isolated
browser peer. Browser fixture signing uses the published unsafe native test
identity; independent native/WASM wallet vectors remain a separate core gate.
The fixture is not a fully initialized live Saito web app. It never targets
community recipients. Foreground/screen-off/notification and final byte results
must pass on Android10/17 before acceptance; whole-app verification and ordinary
signed-update/delivery checks remain required. No node/provider/Play operation,
owner-phone or OEM acceptance is implied.

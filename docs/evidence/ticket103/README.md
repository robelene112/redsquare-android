# Native Fileshare103, implementation in progress

The owner approved live Fileshare in private Chat and continuing accepted/started
work in the background and with the screen off, with an Android progress
notification and exact-transfer Cancel. No Fileshare runtime or beta acceptance
is claimed yet. Public beta112 remains the previous accepted release.

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

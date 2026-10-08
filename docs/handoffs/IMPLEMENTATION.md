# Sequential implementation handoff

Started 2026-10-08. The owner authorized implementing all 35 proposed tickets,
committing after each completed ticket, and reviewing the app at the end.
Continue without routine permission requests or a grill per ticket.

- Authoritative release spec: https://github.com/robelene112/redsquare-android/issues/1
- Tickets: native sub-issues #2 through #36 with native dependencies.
- #2 wallet/signature compatibility and #3 persistent wallet/signed updates are
  verified on Android 10 and Android 17. Next: #4 live feed and cached launch. Inspect
  GitHub for the current authoritative ticket status.
- Use the project-local implement, tdd and two-axis code-review skills.
- Production mutation and store account/publication boundaries remain those
  stated in the specification and operations guide; never infer working
  interoperability or physical Pixel measurements from an unverified mock.

Native implementation and local build exist: Kotlin/JVM core, Compose scaffold,
wallet creation/import/export/signatures, independent reference tests and
debug-only diagnostic. Docker supplies Java 17/Gradle/SDK on this VPS. Current
Bouncy Castle pin is 1.86; nine core tests, debug/test/release assembly and lint
passed locally. Prior 1.81 device evidence is superseded for acceptance.

Review base for #2: d0e9f19. Implementation/security/harness fixes are committed
and pushed through 6869690. Standards and Spec reviews pass after fixes.
Hosted KVM run 37834551583 passed Android 10 and Android 17 independent tests
and visible diagnostics, alongside core tests, APK assembly and lint.
Earlier failed run logs are retained in `.scratch/`; SDK
download/path/runtime-library problems were setup failures, not passed device
checks. See `docs/research/NATIVE_PROOF_VERIFICATION.md` for durable evidence
and limitations. No final phone review APK exists.

Research notes for feed, wallet sync and notifications are committed in 34c3704
as completed read-only findings, not implemented features. Future notifications
need Firebase access and a gateway deployment. An optional one-question prompt
asked whether to use a dedicated or existing Firebase project; no answer yet.
Continue independent work. Never invent credentials or upload user wallet keys.

Owner requested app review at the end. Physical Pixel 7 checks are outstanding;
emulator results never establish physical performance/update/notification
acceptance. Retain these limits in release verification.

Ticket #3 review base is 34c3704. AndroidX Test runner 1.7.0, JUnit extension
1.3.0 and Espresso 3.7.0 fix the documented Android 17 InputManager reflection
failure; actual wallet UI tests now pass on Android 10 and 17. Regression run
37840610980 exposed replacement identity after missing ciphertext; cb065b5
preserves it and shows recovery guidance. File/directory sync and reopening
verification address AtomicFile's swallowed persistence failures. Run 37845030873
passed Android 10 wallet, cold-process and notice checks. Android 17 wallet UI
passed, but the later observer failed: retained XML/screenshot proves the exact
System UI ANR overlay. The observer now records and dismisses only that dialog,
never an app ANR. Do not claim a retry explains earlier uncaptured failures.

Signing initialization and verified release artifacts work. The private signing
state is outside Git under the operator's local share directory, with restricted
permissions. Public certificate SHA-256 is recorded in the update-check script.
Versions 3 and 4 contain the initial wallet implementation and have NOT been
published. Signed versions 5 and 6 are published at
https://davidengelen.nl/redsquare/ with truthful wallet-foundation build notes.
Public HTTPS manifests and downloaded APK checksums match verified local
artifacts. A phone-sized Chromium check verifies the visible version and link.
Version 6 (d0d3a2f) adds the update notice; neither version is the live-feed/import
milestone. Ordinary debug tests use a loopback manifest and stub the browser
boundary. Public signed-update integration is explicitly enabled on hosted
runners, not the network-disabled local emulator. Run 37846484687 (38d576d)
passed actual signed 5-to-6 update continuity on Android 10. Android 17 later
timed out waiting for the reopened wallet; its missing/corrupt-storage cases
passed. No screenshot was captured for that timeout, so its cause remains
unknown. Future waits retain a public-screen failure screenshot. KVM images
now receive two cores/2 GiB; this is a test resource adjustment, not an
established explanation of the timeout.

Run 37846931132 (add4096, temporary verification/wallet-first-save) reproduced
failed initial saving followed by permanently failing Retry after storage was
restored. Fixes through 7e24a03 use a durable nonsecret initialization marker
before generating an unused key, and remove/sync it before exposing identity.
Valid pending ciphertext retains its wallet; invalid partial first writes may
retry only while unpublished. I/O/provider failures preserve pending data.
Saved-file and directory sync prevent Retry bypassing the publication barrier.
New controlled application checks cover partial writes, valid interrupted saves,
temporary unreadability and failed commit sync. The fixtures model crash states;
they do not reproduce arbitrary physical power cuts. Existing missing/corrupt
wallets without the initialization marker remain fail closed.

Standards and Spec reviews report zero findings through 7e24a03. Local build/lint
passed. Signed build 8 (7e24a03) is published and HTTPS metadata/APK checksum
verified; build 7 is unpublished. Run 37849279965 (35dff32) passed the full
Android 10/17 recovery and signed 5-to-8 update checks. Downloaded evidence was
inspected: eleven instrumentation tests on each SDK, cold-process and actual
signed update continuity. Public wallet screenshots/continuity summaries are
retained in docs/evidence/ticket02. See
[wallet delivery verification](../research/WALLET_PREVIEW_VERIFICATION.md).
Final Standards and Spec reviews report zero findings through 1b50e2f. Ticket #3
is complete; its final evidence/status commit changes only documentation.

App state: 2 of 35 tickets complete. Next ready: #4. No final app review build yet.

Ticket #4 is active, review base a1973c2. Native authenticated owner WSS,
independent verified feed envelopes, public-cache cold starts and outage UI
are implemented through 8e316b1. The two-axis review's cache-commit and
connectivity races were fixed; a later refresh-position test synchronization
finding is fixed in c17e0b5. Additional cache-failure and unreadable-refresh UI
checks are compiled but pending runtime. Full run 37857285246 at 4fd87bd passed Android 10 and 17 including separate
fresh-owner public reads; actual artifacts were inspected and durable public
feed evidence retained under docs/evidence/ticket03/first-full. Focused run 37857995735 at 8e316b1 covers recovery and
position. Inspect actual artifacts and current GitHub status before claiming
acceptance. An unreadable refresh with existing cached posts is expected to
expose a missing warning in the current implementation; obtain its functional
red before correcting that rendering condition. See NATIVE_FEED_VERIFICATION.
No feed APK published yet; stable signed review build remains version 8.

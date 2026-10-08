# Sequential implementation handoff

Started 2026-10-08. The owner authorized implementing all 35 proposed tickets,
committing after each completed ticket, and reviewing the app at the end.
Continue without routine permission requests or a grill per ticket.

- Authoritative release spec: https://github.com/robelene112/redsquare-android/issues/1
- Tickets: native sub-issues #2 through #36 with native dependencies.
- #2 wallet/signature compatibility is verified on Android 10 and Android 17.
  Active: #3 persistent local wallet and signed preview/update delivery. Inspect
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

Ticket #3 review base is 34c3704. The first persisted-wallet UI test passed on
Android 10 in run 37838876059. Android 17 passed crypto but its UI test failed
inside Espresso 3.6 before executing the test body: InputManager.getInstance
was removed. AndroidX Test runner 1.7.0, JUnit extension 1.3.0 and Espresso 3.7.0
contain the documented getSystemService fix and are being verified. The subsequent
missing-ciphertext recovery test is deliberately red until it has run on a
device. Interim review found (1) missing ciphertext can create another identity,
and (2) AtomicFile.finishWrite alone does not prove successful persistence.
Directory/file sync and reopening/verification address the second finding;
the first awaits its regression failure before implementation.

Signing initialization and verified release artifacts work. The private signing
state is outside Git under the operator's local share directory, with restricted
permissions. Public certificate SHA-256 is recorded in the update-check script.
Versions 3 and 4 contain the initial wallet implementation and have NOT been
published. The publisher, cold-process UI check and signed-update checker are
committed. The update notice, remaining recovery checks, real signed update
proof, final reviews and publication still need completion. The stable website
path responds normally but currently has no published app assets.

App state: 1 of 35 tickets complete, #3 in progress. No final app review build yet.

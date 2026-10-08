# Sequential implementation handoff

Started 2026-10-08. The owner authorized implementing all 35 proposed tickets,
committing after each completed ticket, and reviewing the app at the end.
Continue without routine permission requests or a grill per ticket.

- Authoritative release spec: https://github.com/robelene112/redsquare-android/issues/1
- Tickets: native sub-issues #2 through #36 with native dependencies.
- #2 wallet/signature compatibility is verified on Android 10 and Android 17.
  Next: #3 persistent local wallet and signed preview/update delivery. Inspect
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

Untracked research notes for feed, wallet sync and notifications are completed
read-only findings, not implemented features. Preserve them and commit as
research separately from unrelated application changes. Future notifications
need Firebase access and a gateway deployment. An optional one-question prompt
asked whether to use a dedicated or existing Firebase project; no answer yet.
Continue independent work. Never invent credentials or upload user wallet keys.

Owner requested app review at the end. Physical Pixel 7 checks are outstanding;
emulator results never establish physical performance/update/notification
acceptance. Retain these limits in release verification.

App state: implementation in progress. No final app review build yet.

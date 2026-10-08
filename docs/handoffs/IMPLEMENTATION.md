# Sequential implementation handoff

Started 2026-10-08. The owner authorized implementing all 35 proposed tickets,
committing after each completed ticket, and reviewing the app at the end.
Continue without routine permission requests or a grill per ticket.

- Authoritative release spec: https://github.com/robelene112/redsquare-android/issues/1
- Tickets: native sub-issues #2 through #36 with native dependencies.
- Starting ticket: #2, wallet/signature compatibility on Android.
- Use the project-local implement, tdd and two-axis code-review skills.
- Production mutation and store account/publication boundaries remain those
  stated in the specification and operations guide; never infer working
  interoperability or physical Pixel measurements from an unverified mock.

Build tooling is being established in Docker because this VPS has no system
JDK, Gradle or Android SDK. Inspect current scripts, GitHub evidence and Git
history before reporting an APK or completed feature. Synthetic protocol
reference keys belong in tests only, never a real user's identity.

App state: implementation in progress. No final app review build yet.

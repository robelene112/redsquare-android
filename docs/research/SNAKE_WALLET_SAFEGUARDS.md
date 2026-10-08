# Snake wallet safeguards baseline

Read-only inspection of `/home/david/snake-stickerbook` on 2026-10-08 following
its maintained `AGENTS.md`. Only source and tests were inspected. No saved wallet
material was read, no tests were executed and no source or runtime was changed.

- File selection is preview-only. Unencrypted JSON backups up to 5 MiB are
  validated for structure, bounded key fields, version type, public-key validity
  and matching private/public keys before mutation (`snake.js:382` and `:612`,
  `test/wallet-import.node.js:210`). Cross-version payloads are preserved.
- The UI distinguishes same-identity restoration from different-identity
  switching, showing shortened public keys (`web/main.js:684`,
  `web/index.html:1958`). Same-wallet restoration warns about replacing settings
  and skips switching-only balance and pending-work assessment.
- Different-identity switching requires a fresh outgoing-wallet export when
  balance is positive or unknown. Verified zero balance permits skipping a
  strongly recommended backup. Assessment includes a fresh native balance
  snapshot and activated other-crypto balances (`snake.js:562` and `:995`,
  `test/wallet-import.node.js:449`). This is not a general social-history gate.
- Export proof binds outgoing identity, incoming identity and selected file
  fingerprint; selecting another file invalidates it (`snake.js:689`). Export
  clears peer endpoints for portability (`snake.js:450`). Browser success means
  download initiation, not proof that the user retained the file
  (`web/main.js:793`). Define successful export appropriately for Android.
- Registered names and pending or unverifiable work require acknowledgement
  (`web/main.js:592`, `web/index.html:1971`, `web/wallet-loader.js:579`). Unsigned
  work must not be signed by the incoming wallet; already signed work may relay
  without re-signing (`test/wallet-bridge.node.js:947` and `:1007`). Snake receipts
  and Daily work are project-specific examples, not Android app features.
- Activation rechecks conditions, writes and verifies a local recovery record,
  imports, verifies active and persisted identities/settings, and removes the
  recovery record (`snake.js:962` and `:829`). Failures attempt rollback. Failed
  rollback retains recovery state and blocks wallet operations; startup recovers
  interrupted imports (`snake.js:901`, `test/wallet-import.node.js:536`). This
  protects exported wallet/options state, not every separate browser database.
- Successful import reloads the browser after verification (`web/main.js:843`).
  Android needs an equivalent safe state transition, not a literal page reload.
- Browser PIN/biometric authentication and encrypted backup support were not
  found in these paths. Android device authentication for export is an already
  accepted enhancement, not a claim about Snake's current implementation.

The user requested these safeguards as the Android baseline. File-format limits,
Android export behavior and identity-scoped application data must be specified
and verified during implementation rather than copied mechanically.

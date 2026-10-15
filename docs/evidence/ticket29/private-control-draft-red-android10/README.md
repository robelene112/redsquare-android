# Exact reopened draft RED after bounded wait, Android10

Run38691358098 at exact4f9cb3a1a24caf6e3846a4914f084c06fa623156,
1failure25.604s. After the mute action, compatible incoming message, positive
Relay acknowledgement and exact live draft, recreation retains mute/history
but the enabled composer remains empty through the unchanged exact-text20s
observation. Failure PNG/XML show no Archive loading warning at that point.
Unmute/no-wire tail unexecuted; no notification filtering accepted.

This establishes failure of the required draft recovery, without proving
which autosave/lifecycle ordering caused it. Source shows composition-only
text with300ms debounce and no recreation flush. The minimum correction reuses
the existing post composer pattern: per-wallet/backend/conversation ViewModel
text survives recreation, while durable loading remains authoritative for frozen
work and ordinary persistence stays encrypted. No plaintext SavedState/Bundle.

Root original failure PNG inspected; two XML parsed. Public unsafe fixture and
controlled peer only; original wallet/backend/scoped state restored in finally.

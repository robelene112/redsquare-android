# Native late Registry callback RED, Android10

Run38782610994 at611232a382a8e9af03d48cbb42cc01ab2f56819c remains FAILED.
UsernamePreviewTest16cases:15PASS/1FAIL in112.39s. The sole new late Settings
case fails at128 waiting20s for the registered-name UI after its positive server
checkpoint: one same-index fallback/proof pair queued, one namecheck, zero
transactions. Existing fifteen cases pass. Core114/debug/test assembly pass.

Root viewed the original failure PNG and parsed its separate XML: Settings
shows no registered name, with the unavailable name-check message and editable
choice. The unchanged verifier accepts the independent proof in the ordinary
case; SaitoPeer consumes the earlier fallback and discards the later same-index
array. The queued-send checkpoint is not an independent packet-capture record.
It does positively place both controlled sends before the failed UI wait.

Later successful-name/no-register/Wallet assertions in this case and the
username process tail are unexecuted. Scoped finally restores the original
wallet. No production fix, public readiness or live registration follows.
Public22 remains current; no production node/config/service/wallet/chain write.

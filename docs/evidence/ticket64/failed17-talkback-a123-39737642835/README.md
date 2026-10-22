# Rejected TalkBack startup, Android17

Run39737642835 at exacta1234f841bc1d13b7ac6bca2f439e06a6fa550e6 failed13.65s/180
in the original status class, before either post or new profile speech checks.
The existing five-second observation did not see Wallet accessibility focus.
The original frame shows the feed with no visible focus ring. TalkBack logs
show attempted Wallet focus returning false. This does not establish whether
window transition, service focus timing or observation caused the failure.

Add only in-scope failure diagnostics for focused text and current windows,
before Activity teardown. The original five-second condition, service/engine
pins, focus assertion, speech and status behavior remain unchanged. A bounded
rerun will distinguish transient startup from a persistent condition; this
failed execution cannot establish profile TalkBack acceptance.

Verified all33 SHA256SUMS entries. This later README is excluded.
Original archive members and their bindings are retained without edits.
Public63 remains current; complete64 and signed delivery are pending.

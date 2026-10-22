# Rejected initial keyboard observation, Android10

Run39735804256 at exacta6c6d40b1f80189bfac6031e32c0d87ba6ab6b16 is FAILURE,
5.385s/180. The initial semantics RequestFocus action for Back returnsfalse,
before any physical Tab/D-pad input or focus-order goal executes. Root viewed
the exact original: Profile and history tabs are visible, with loading still
active and Refresh disabled. No physical keyboard behavior is proved here.

The corrected fixture waits for Posts and enabled Refresh in the original15s
readiness budget, enters native keyboard mode with a physical Tab, observes
that the actual Activity window has left touch mode, then establishes the
same declared Back start. The original physical Tab order, D-pad distinction,
visible assertions, identities and180s bound remain. Touch-mode initialization
is a hypothesis for the rejection until the corrected execution is observed;
this packet does not prove a product focus defect or subsequent success.

This packet retains2 original archive members and13 hashes. SHA256SUMS
excludes this later README. Raw artifacts remain external at
`/mnt/storage/redsquare-android-artifacts/profile64-keyboard-29-a6-39735804256`.
Public63 remains unchanged. Actual native keyboard/speech and complete64
acceptance remain pending.

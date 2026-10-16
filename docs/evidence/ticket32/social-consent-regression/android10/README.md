# Existing notification flow regression, Android 10

Run [38962218678](https://github.com/robelene112/redsquare-android/actions/runs/38962218678)
at `495fb72cd066aef4fd0f154bcb52779d79a906c9` succeeds. The existing read UI
passes all 32 cases in 157.814 seconds. 1 permission case(s) also pass.
Android 10 opens actual app-specific notification Settings; it has no runtime notification permission.
In-app reading and refreshing remain available after each applicable choice
and Activity recreation. These permission cases do not inject a social receipt
after denial, so they do not independently prove receiver suppression.

The existing history recovery check visibly records activity and read status,
deliberately terminates PID 6386, and recovers in fresh PID 6473. The recovered
phase passes in 5.603 seconds with the same public wallet identity/backend,
No unread activity, retained actor/event and profile navigation despite failed
controlled-server reads. The read error is visible as intended. No outgoing
transactions/reports are recorded. This is actual read-history process recovery,
not a social push wake-up or complete wallet-metadata comparison.

Root viewed all permission originals and each cold-recovery original, eight
images across both SDKs, and parsed all 96 XML files. 106 hashes retain 47
PNG/XML pairs, public request counters where present, raw UI/permission/process
logs, PID checkpoints, continuity result, metadata and core report. Offer flags
are explicitly handled in the read-history process fixture, keeping first-use
behavior outside that recovery case. Normal fixtures preserve new owner offer
state; the permission fixture restores its affected encrypted social files.

Core 114/debug/test pass. Focused scopes omit release/lint/full acceptance; the
separate same-source push check passes release/lint. No real external transport,
physical Pixel timing or whole #32 completion is established. Beta 24 unchanged.

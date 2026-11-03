# Issue77: controlled Like-history requests

Implementation `a7d9fcff6`, reviewed host-test correction `e59f512b0`.
Exact receipt requests retain their signature-only contract. Author/recipient
history validates its scope, limit, cursor and applicable flagged filter before
reaching the same response callback. The fixture can now serve populated,
independently signed Activity and profile Like rows.

The authenticated binary contract test first reproduced the original missing
signature failure (two tests, one failed), then passed. The corrected final
host suite passed117tests, zero failures. Malformed-query checks require a
positively authenticated socket and actual peer validation rejection, rather
than accepting a connection/no-response assertion as success.

Both focused native runs pass five classes: the new populated recipient/author
history journey, existing individual unread/read/recreation/navigation,
viewport, persistence/recovery and revised-post Activity checks.

- [Android10](https://github.com/robelene112/redsquare-android/actions/runs/41725547926)
- [Android17](https://github.com/robelene112/redsquare-android/actions/runs/41725552462)
- [Actual Android10 profile Likes](author-like-history-android10.png)

The native runs use `a7d9fcff6`. The only subsequent code change is the host
contract test correction; Android main/test sources, core main, peer fixture,
build inputs and emulator harness are identical. The final full117-test host
run uses `e59f512b0`. The distinct exact-receipt query remains covered by the
binary contract check; the original production receipt logic and assertions
were not altered.

## Standards

One negative-test observation finding was fixed and re-reviewed. No unresolved
standards findings.

## Spec

No findings. Both requested native OS runs and the signed contract check pass.

## Limits

This repairs the controlled test peer, not a public-node incident. Focused
success is not complete app acceptance or physical Pixel performance. The owner
authorized one integrated next beta after the entire86batch; this ticket has
no separate beta publication. Final combined verification/delivery belongs to94.

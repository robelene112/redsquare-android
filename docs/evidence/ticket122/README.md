# Read deadline recovery, #122

Parent: [#117](https://github.com/robelene112/redsquare-android/issues/117).
Review base: `492c61b646cb3697b791c05148c068836c5cc119`.
Final native source: `a653ebb6034e759154194739141b6183df4ec46a`.
The intervening #121 navigation follow-up is separately accepted in
[its own packet](../ticket121/navigation/README.md).

Feed foreground polling, profile history, Stack discovery and conversation reads
now distinguish their own request deadline from cancellation by their caller.
A local deadline produces recoverable failure while retaining available reading;
a cancelled caller still cancels. The existing 12-second peer request timeout,
5/30-second feed retry/poll intervals, class budgets and response bounds are
unchanged. Search, profile metadata and notifications are unchanged.

The conversation continues its independent verified-parent lookup after a failed
thread read. The native fixture opens a signed reply through real Search and
recovers its parent from the independent original-post cache. Controlled Archive
replies are withheld after authentication; no production node is involved.

## Complete relevant native checks

| SDK | Complete successful parent | Normal methods | ZIP entries | TAR members |
| --- | --- | ---: | ---: | ---: |
| Android 10 | [43776843142](https://github.com/robelene112/redsquare-android/actions/runs/43776843142) | 7 | 96 | 26 |
| Android 17 | [43776845884](https://github.com/robelene112/redsquare-android/actions/runs/43776845884) | 7 | 85 | 26 |

Both parents use the exact same final source. Each completes five classes with
seven normal method completions, no assumptions/skips, zero capture exits and
no publication to the owned peer. The seven methods exercise cold/cached feed
polling recovery without navigation, lifecycle restart or Retry; retained profile
history and actual Refresh; cached Stack reading and new writing after Retry;
independent conversation-parent recovery; caller cancellation; and enclosing
caller-deadline cancellation.

Actual observed feed failure occurs around the unchanged request deadline:
12,013/12,145ms after withheld receipt on Android10 and 11,854/12,361ms on Android17.
The request timeout starts before authentication/Archive receipt, so these receipt
intervals need not all reach 12,000ms. Feed recovery follows without user action.
Explicit caller cancellation completes in 2ms on both SDKs. Enclosing caller
cancellation completes at its separate 3-second test budget. Each cancellation
replays exactly one old callback, including its original request id, on a retained
real authenticated socket; the cancelled reader retains zero articles and the
new reader loads one. The unchanged peer request deadline is not shortened.

Both independent Standards and Spec reviews accept the final source and both
native packets. Standards found zero documented violations and one low-priority
possible duplicated-fixture smell, explicitly deferred to keep the fix small.
Each SDK directory contains 33 original byte-exact copied files: five native logs,
verification and assessment receipts, and all 26 capture members. External
originals retain the run/artifact API responses, complete logs, original server-
digest-verified ZIPs, every extracted ZIP/TAR byte, and the collectors/assessor:
`/mnt/storage/redsquare-android-artifacts/read-deadlines122`.

## Source checks

Source/core/lint verification separately succeeds in
[43777535705](https://github.com/robelene112/redsquare-android/actions/runs/43777535705),
at the same final source, with 55 original ZIP entries verified byte-exact. Lint
completes with zero errors, 94 warnings and three hints. Focused native parents run core/gateway tasks and debug
app/test assembly; they do not establish lint, release or performance assembly.
The core native-parent report has 224 tests, zero failures/ignored, restored
FROM-CACHE. The gateway test task also completes FROM-CACHE. Local full debug
AndroidTest Kotlin compilation succeeds; its original log is retained externally.
Whole-app qualification and signed beta delivery remain #131.

## Earlier observations and failures

- Cold-feed red [43769541018](https://github.com/robelene112/redsquare-android/actions/runs/43769541018),
  source `5b718d7b3fde12083e05feb8607ca499a8060ce0`, fails the visible error-feedback
  wait after the controlled peer withholds the actual request. First cold-feed
  green43773403174 at e47b4f139 succeeds; it is superseded by complete checks above.
- Profile43773757594 and Stack43774334870 fail the initial feedback wait. Their
  original fixtures did not scroll lazy error rows into view, so these failures
  alone cannot distinguish missing product feedback from an offscreen row.
- Conversation43774946068 fails compilation because text entry returns Unit;
  the test now invokes the IME action separately. It is not a runtime red.
- Combined43775445410/43775447899 pass both feed methods, then fail the same
  offscreen Profile feedback observation. The actual LazyColumn now scrolls to
  feedback before asserting visibility, preserving the 20-second wait.
- Combined43776251406 passes feed/profile/Stack, then fails a duplicate matcher:
  the search editor and result had identical text. The query is now shorter than
  the signed reply body. Its SDK17 counterpart43776254278 was replaced/cancelled.
- Replaced/cancelled43775049986,43775141921,43775145213,43775361430 and43775364599
  retain their own original artifacts/status and are excluded from acceptance.

Every earlier failed/cancelled parent remains a failure/cancellation; successful
prefixes do not establish whole-parent acceptance. New focused checks preserve
existing assertion goals and deadlines. These owned-emulator results do not
qualify physical/OEM behavior, live providers/nodes, funding or Play prerequisites.
The public beta remains 0.1.131 until the final combined release.

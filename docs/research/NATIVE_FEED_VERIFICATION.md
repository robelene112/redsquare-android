# Native feed verification

Ticket #4 is in progress. Review base: a1973c2. This file distinguishes
independent protocol evidence, controlled Android flows, live read checks and
physical-device acceptance.

The initial protocol decoder test failed before implementation
(`.scratch/feed-decoder-red.txt`) and passed with the independently signed
WASM/Elliptic archive fixture. A subsequent independently signed null-field
fixture exposed rejected compatible fields (`feed-null-red.txt`); the corrected
interpretation passed. Altered/malformed-row isolation failed before Archive
reading was implemented (`feed-rows-red.txt`) and then passed, including an
older cursor from the rejected row. Unsigned Archive request serialization
failed before implementation (`feed-wire-red.txt`) and passed against the exact
WASM network transaction and independently source-defined API prefix.

[Hosted run 37852075248](https://github.com/robelene112/redsquare-android/actions/runs/37852075248)
at 7456359 compiled the Android fixture and reached its test body on Android 10.
It failed waiting for the signed reference post on the native feed, because no
feed connection/rendering existed yet. The downloaded instrumentation output
was inspected: one test, one ComposeTimeoutException at the visible-post wait.
This is the functional red checkpoint, not a build/server setup failure.

The first implementation renders verified original post text and sender key,
uses fixed-owner WSS with ordinary TLS verification, answers nonce challenges,
and sends the exact unsigned Archive request only after handshake completion.
Private keys are never in the request. No transactions were broadcast. Reading
public Archive records does not prove chain inclusion or funded validity.

Run 37853290748 at 7837753 passed source assembly/lint but again timed out at
the controlled visible-post wait on Android 10. No screenshot or peer trace was
captured, so the exact failure cause remains unestablished. The fixture now
binds and names 127.0.0.1 explicitly; default server host names need not meet the
app's deliberately strict loopback allowlist. Rejected debug fixture URLs fail
closed at an unavailable loopback endpoint, never fall back to production.
Future failures retain a public-screen screenshot and any peer assertion.

Focused dispatch (`scope=feed`) runs core tests and the actual Android 10 feed
flow without claiming Android 17, release lint or wallet/update acceptance.
Default pushes and full dispatch preserve the full two-SDK verification.

Focused run 37853995069 at b196502 passed all thirteen core reference tests,
debug/test assembly and the authenticated native feed flow on Android 10. The
actual downloaded instrumentation output was inspected: OK (1 test). The peer
requires a valid signed counter-challenge answer before accepting the Archive
request. Android 17 and full wallet/update checks were intentionally not run.

Focused run 37854587674 at 06728c0 reached the authenticated feed and then
failed the newly added reopening check: the visible cached-post wait timed out
at line 109 after peer shutdown and activity recreation. Its captured public
screen was inspected and showed an empty feed with the refresh error. This is
the functional red for persistent cache/outage display. The next implementation
stores up to 100 recent original public envelopes with a 16 MiB cache budget,
re-verifies them on load before opening a peer, preserves keyed list position,
and distinguishes observed internet loss from an unavailable selected backend.
Write failures retain live reading and show an offline-cache warning. These
limits are native cache policies, not universal Saito text/image limits.

Focused run 37855624594 at c4d4291 passed thirteen core tests and the native
Android 10 flow: authenticated feed, peer shutdown, activity recreation, cached
post still visible and Server unavailable. Its actual downloaded output was
inspected: OK (1 test). A separate emulator-only observer now force-stops and
cold-launches with an unavailable loopback peer; it records only public UI and
framework timing lines, never private storage. This additional cold-process
check has not yet run.

Live-owner verification is a separate `public_feed_read=true` dispatch input.
It clears only the isolated emulator's .dev installation, launches the real
owner endpoint, and waits for visible post author identities. It cannot pass
from a seeded cache. Transport failure diagnostics contain only exception
class/status, never nonce, key, payload or exception message. Public read,
Android 17, full update acceptance and recovery timing are still pending. HTTP 200 from both public Red Square
pages on 2026-10-08 establishes web readiness only, not native peer success.
The first feed implementation has not been published as a signed review build.

Physical timing uses [the measurement protocol](FEED_PERFORMANCE_PROTOCOL.md).
Pixel 7 runs remain part of final device acceptance (#35); no emulator timing
establishes the accepted 2,000/5,000 ms physical targets.


Two-axis review of the committed implementation through 4fd87bd found cache
commit verification and connectivity callback races. Commit 8e316b1 syncs the
cache directory and reopens the exact committed envelopes before reporting a
successful save; connectivity state now follows callback-supplied capabilities
and clears only the tracked lost network. Failure screenshots share a helper
that preserves the original assertion if evidence capture fails.

The controlled peer now serves independent 20/21-post WASM fixtures for
position preservation across an inserted newest post and activity recreation.
It also restarts on the same endpoint to test the green recovery message and
its disappearance. Follow-up review caught a synchronization gap: observing
the socket's sent response was not proof that the app merged it. The test now
waits for the app's Refresh action to become enabled before checking position.
Additional device cases cover unavailable cache storage and unreadable refresh
responses while retaining good posts. These tests compile locally; their actual
runtime outcomes are not yet recorded here.

Focused run 37857995735 at 8e316b1 passed the reconnection UI test: cached
posts remained visible, the restarted authenticated peer produced Back online,
and the message disappeared. Its second test failed only at the final
newest-post wait after recreation and scrolling to index zero. The retained
screenshot shows Reference post 20 at the top, without an outage/error; the
exact race cause is not established. The previously identified refresh-wait
fix is present in c17e0b5. Do not claim the failed run proved insertion-position
acceptance. Run 37858491264 verifies that correction plus cache failure and
unreadable refresh behavior.

The independently signed refreshed array was also checked directly through
the native decoder seam: all 21 posts verify, no rows are ignored, the first
post is New reference post at 1700000000021, and the last is Reference post 01.
The focused local RedSquareFeedTest run passed in 25 seconds. This rules out
failure to decode the fixture's new post in isolation, but not an Android
request/merge/list-position race.

# Native feed verification

Ticket #4 is in progress. Review base: a1973c2. This file distinguishes
independent protocol evidence, controlled Android flows, live read checks and
physical-device acceptance.

Accepted runtime candidate: **c5953de**. Full run **37863843807** passed on
2026-10-09: fourteen core tests, debug/test/release assembly and lint, the
complete Android 10/17 runtime suite, separate fresh owner reads, cached cold
launches and actual signed 5-to-8 updates. Selected public logs and screenshots
were downloaded, inspected and retained under `docs/evidence/ticket03/final-full`.
Both review axes' final code findings are resolved. No feed APK is published;
Pixel acceptance remains required and unverified in #35. Earlier failed runs
below are historical checkpoints, not the current acceptance status.

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
check had not run at that checkpoint; its later full result is below.

Live-owner verification is a separate `public_feed_read=true` dispatch input.
It clears only the isolated emulator's .dev installation, launches the real
owner endpoint, and waits for visible post author identities. It cannot pass
from a seeded cache. Transport failure diagnostics contain only exception
class/status, never nonce, key, payload or exception message. At that checkpoint public reading, Android 17, full update acceptance and
recovery timing remained pending; later recorded results follow. HTTP 200 from both public Red Square
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


Full run 37857285246 at 4fd87bd passed source assembly, release lint, thirteen
core tests and both Android 10/17 runtime stages. Downloaded artifacts were
inspected: fresh owner reads each found three visible post authors; screenshots
show actual current owner-feed content. Native read instrumentation completed
in 5.314/24.608 seconds respectively; these are entire test durations, not
physical app startup benchmarks. Controlled feed reopening, cold-process cache
recovery, wallet recovery and actual signed 5-to-8 update continuity passed on
both SDKs. Durable public feed evidence is under
[the first full evidence](../evidence/ticket03/first-full/provenance.txt).
Emulator framework logs include approximately 2–3 seconds on API29 and 8.7
seconds on API37; multiple API29 lines are retained without selecting one as
an accepted measurement. These logs do not establish either Pixel target.
This passing baseline predates the later review fixes and expanded UI tests;
a final reviewed-source run is still required.


Focused run 37858491264 at c17e0b5 passed reconnection and the corrected
multi-post refresh/recreation position test. The unreadable-response test
failed waiting for the explanatory warning while a good cached post remained.
The rendering condition incorrectly required an empty feed; that condition is
now removed. The cache-failure case displayed its new live post but no warning:
its obstruction used `.new`, which is not the write path used by
[Android 10 AtomicFile](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android10-release/core/java/android/util/AtomicFile.java).
The primary source was retrieved and its startWrite body inspected.
This was a fixture error, not evidence of a cache-write failure being ignored.
The corrected fixture preserves the existing public cache elsewhere, obstructs
its actual base path, and restores it in finally. It leaves wallet storage
untouched. Future multiple failures retain separate test-named screenshots as
well as the existing last-failure screenshot.


Signed build 9 was prepared locally from f71c506 with the established package,
certificate and monotonic version. Its app/core/scripts/build-support content
is identical to final runtime candidate 1bcd832 (git diff checked). It remains
unpublished. Stable preview remains 8; the first complete live-feed/selection/
import milestone is delivered after #7. No physical review is requested now.


Run 37859209011 at 1bcd832 passed all fourteen core tests, source assembly/lint,
the complete Android 10 runtime, and all four controlled Android 17 feed cases
(46.308 seconds total). The later fresh live-owner test crashed the process with
IllegalArgumentException: performMeasureAndLayout called during measure layout,
in AndroidComposeView.dispatchDraw. Its screenshot shows Loading posts; the
transport diagnostic includes SocketException without a payload or key. This
trace establishes the layout crash, not its underlying cause or a server fault.
[Failure evidence](../evidence/ticket03/failed-final/android17-live-crash.txt)
and the screenshot are retained. Subsequent Android 17 wallet/update stages did
not run, so the full candidate is not accepted. Build 9 remains unpublished.
Primary-source dependency research verifies BOM 2026.06.01/Compose 1.11.4 fit
the existing SDK 36/AGP 8.10.1/Kotlin 2.1.20. The newer 2026.09.00 stack requires
SDK 37/AGP 9.1.0 and is not selected. A focused Android 17/public-read scope
checks the compatible update before another full run. No specific crash fix
is inferred from a version number.

The resolved debug graph uses Lifecycle 2.9.4 and Kotlin stdlib 2.2.21; the
direct Lifecycle declaration is aligned with its existing resolved version.
Seven instrumentation factories migrate to the supported Compose 1.11.4 v2
empty rule, retaining their assertions and bounded waits. Local
`scripts/android-build :app:assembleDebugAndroidTest` passes in 42 seconds
with these changes. This proves compilation, not device runtime acceptance.
Focused BOM-only run 37861050271 still uses the legacy scheduler, allowing
its result to be distinguished from the later v2 candidate.

Run **37861050271** at **1374b1c** passed the focused Android 17 check with
Compose 1.11.4 and the legacy test scheduler: all four controlled UI cases
(52.17 seconds total), unavailable-peer cached cold launch and separate fresh
owner read (three visible author identities, 30.825 seconds test duration).
Downloaded results and both screenshots were inspected; durable evidence is
under `docs/evidence/ticket03/compose-focused-17`. Test duration is not first
interactive-feed timing. This passing experiment does not establish the prior
crash cause or final wallet/update acceptance. Full run **37861712249** at
**dec035d** checks the reviewed v2 scheduler candidate on both SDKs and remains
pending. No physical timing is claimed.

Full run 37861712249 at dec035d passed assembly/lint, all fourteen core tests,
the full Android 10 stage, all four Android 17 controlled feed cases (44.179
seconds), fresh live-owner read (three authors, 18.123 seconds test duration),
wallet recovery and cold identity continuity. It later failed the update-notice
UI test after ten seconds, before Android 17 signed-update/diagnostic stages.
This was not the earlier layout crash. The retained trace supplies no request
count or screen to establish its cause. The one-response HTTP fixture is being
replaced by a realistic multi-request server, preserving the existing UI/link
assertions and adding request-count/public-screen evidence. No application
behavior is changed by that diagnostic candidate; a focused Android 17 check
will distinguish its result. Final full acceptance remains pending.

Focused run **37863180844** at **3310534** passed the Android 17 notice/link
assertions with the multi-request fixture (16.743 seconds test duration). The
server recorded **three** manifest requests; the inspected screenshot shows
Preview update available and View update. This demonstrates multiple requests
in that passing flow and the fixture's ability to serve them, supporting the
one-shot mismatch hypothesis without retrospectively proving the failed run's
request sequence. App code was unchanged. Evidence is retained under
`docs/evidence/ticket03/update-focused-17`. Shared harness helper 47ce6df
keeps full and focused commands identical. Final full run **37863843807** at
**c5953de** now checks both SDKs, including explicit fresh owner reads; pending.

## Accepted runtime candidate

Full run **37863843807** at **c5953de** passed on **2026-10-09**: all fourteen
core tests, debug/test/release assembly and lint, sixteen instrumentation cases
per SDK (four feed, one separate fresh-owner read and eleven wallet/update
cases), unavailable-peer cached cold launch, wallet cold continuity and actual
signed **5-to-8** updates. Public logs and live/cached screenshots for both
SDKs were downloaded and inspected; durable selected evidence is under
`docs/evidence/ticket03/final-full`. Both fresh reads show three author identities.
Controlled feed tests took 13.61 seconds on Android 10 and 42.995 on Android 17;
fresh live tests took 6.921 and 21.994 seconds respectively. These are test
durations, not physical launch measurements.

The repeated-request fixture observed three manifest requests on both SDKs;
the unchanged notice/link assertions passed. No layout crash occurred in this
accepted run. The historical failures remain recorded and their original causes
are not retroactively established. App/core/scripts/workflow content is unchanged
by subsequent evidence-only commits, checked with git diff. Both independent
reviews report zero remaining actionable findings through the reviewed changes.

No production writes occurred. Pixel launch/update acceptance remains required
and unverified in #35. Build 9 remains unpublished; the next public milestone
follows #7, including both backends and safeguarded import/export.

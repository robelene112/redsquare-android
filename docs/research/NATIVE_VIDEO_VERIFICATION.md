# Native YouTube verification

Active #17, fixed review base711e42e. Parent approved seams1–4 apply: observable
native feed/thread flows against controlled Saito and provider boundaries;
verified signed content, failure/lifecycle recovery and pinned web behavior.
Read docs/research/NATIVE_GIF_VIDEO_CONTRACT.md for source/API provenance.

Completed #15 has full Android10/17 acceptance. #16 remains open with an external
Android-authorized Giphy-key/URL-retention dependency; no credentials were reused.
#17 is independently ready (native prerequisite#8 closed), assigned sequentially.
No node, wallet configuration, production transaction or store write.

First core tracer RED: compatible first YouTube link API absent, compile failed
in24s. GREEN: exact M7lc1UVf-VE canonical target passes in42s. UI tracer launches
the native feed with a controlled signed post and provider boundary. It requires
zero provider requests before a visible tap, player readiness after tap, close
back to the retained post and no emitted transaction. Provider code deliberately
has no playVideo implementation, so automatic playback cannot pass readiness.
An initial local test-compile attempt used a nonexistent transaction timestamp
property; corrected to the fixture's explicit public timestamp. No runtime
result is claimed until the hosted tracer completes.


Explicit signed-link tracer RED23s (property absent), GREEN46s. Verified archive
content now exposes only bounded string links; malformed link metadata does not
replace authenticated text. Real host/path boundaries and 11-character IDs are
required before constructing a fixed HTTPS playback target. Primary YouTube
IFrame/client-identity/security documentation rechecked2026-10-10, including
installed package name as base URL and error153. Source note has direct links.


Android17 RED38078560019 at64bab9c reaches the controlled signed post then fails
because Watch on YouTube is absent (9.924s). Original screenshot shows its link,
with leading text clipped in the small development viewport; retained with that
limit. First player/core/test compilation PASS; before runtime, API33-only
InputStream.readNBytes was replaced with bounded Okio buffering for Android10.
Provider failure releases the WebView; renderer-crash cleanup avoids querying a
dead renderer. The unchanged tap tracer additionally requires the actual API
request Referer to identify the installed .dev package and autoplay0/correct ID.

First player runtime at18027d6 did not pass: Android10 run38079399700 and
Android17 run38079401831 both timed out waiting for provider readiness
(22.570s/31.930s test totals). Individually inspected original failure images
show the YouTube dialog with the load-failure explanation, Retry, Close video
and Open in YouTube. They establish the visible failure state, not the cause of
loading failure or successful playback. No provider request count or scoped
console capture was retained in those runs. Logs/images are retained under
ticket16/first-player-failure, with these limits.

Interim two-axis source review at18027d6 found an unenforced minimum player
viewport in both axes and a primary-error masking fixture cleanup in Standards.
The next candidate measures the actual remaining viewport before creating a
WebView, offers external playback in windows below200dp on either axis, and
uses primary-preserving fixture cleanup. Controlled-provider-only diagnostics
record request count, console severity/line and network error codes, never user
content or wallet information. Its runtime checks remain pending.

Read-only support traced MockWebServer3.5.4.0 URL construction to
InetAddress.getHostName, which can turn a numeric loopback bind into localhost.
The debug provider deliberately accepts only literal127.0.0.1 and otherwise
fails closed to port1. The fixture now constructs its allowed numeric URL
explicitly, retaining that restriction and the Referer/tap assertions. This is
a source-supported hypothesis; the earlier runs do not prove their actual
supplied host or the failure cause. Viewport/core/test compile passes1m40s;
four video-core cases pass, including independent WASM/Elliptic signed posts
and quote commentary/original IDs. No runtime success is claimed here.

Bounded source-fix reviews of18027d6...21f6b97: Standards0 residual findings;
Spec confirms the viewport guard fixes its finding. The prior18027d6 review
remains interim and neither review establishes runtime acceptance. Focused17
run38079974731 checks the unchanged tap flow at21f6b97.

Hosted source-only run38079974479 at21f6b97 PASS: independently inspected report
43 core tests/0 failures/0 ignored, debug/test/release assembly and lint. Both
runtime steps were skipped by design; this does not establish device playback.
Focused Android10 companion38080279091 checks the same unchanged tap flow.

First tap-flow GREEN: Android10 run38080279091 at21f6b97, one case4.842s.
Actual provider request count and installed-app Referer assertions pass, along
with no contact before tap, provider readiness, close to the retained post and
no emitted transaction. The individually inspected original PNG shows ready
instructions above a black controlled-player surface, with Close and external
navigation. Its fake provider text is not visible against that background; no
actual YouTube media playback is shown or claimed. Scoped log has one non-main
frame network error-2; the verified API response/readiness still passed. The
next fixture gives controlled text visible contrast. Prior failure's actual
hostname/cause remains unproved. Retained tap-green-android10 is intermediate
acceptance only; landscape/recovery/thread/full checks remain pending.

Focused17 run38079974731 was superseded by05f89ef's two-case size checks,
not accepted. Its retained local job log shows slow Android Emulator download
progress from10% at19:30:56 to15% at19:38:14, then cancellation19:39:09.
It never reached the source/device checks. No app-loading failure is inferred.
Focused size checks38080627218/38080629357 at05f89ef are pending.

A separate public read of YouTube's oEmbed endpoint returned metadata for the
documented exampleM7lc1UVf-VE, titled YouTube Developers Live: Embedded Web
Player Customization, authored by Google for Developers. This confirms a
public metadata response, not native playback or attestation. The ordinary
controlled tests do not contact that live endpoint.

Size checks38080627218/38080629357 at05f89ef did not complete their first case
within the180s harness limit. Both logs contain a start record and no completed
case, failure stack or OK result; no size assertion is accepted. Available
captures do not locate the stalled operation. A collapsed landscape feed is a
source-supported possibility, not an established cause. Preserve the incomplete
logs under ticket16/size-incomplete. The fixture now records stages, and the
harness retains an independent last-window screenshot even on timeout.

Supplemental Spec review found an actual target-identity gap: cached text-link
and explicit-link posts share the generic Watch button and video ID. Both tests
now bind the tap to the expected signed post's own card, preserving the cache.
That adjustment compiles56s. The next rotation tracer starts with an opened
portrait video, then requires selected-video recovery with no additional
provider contact in an undersized landscape window, visible close/external
controls and a fresh portrait tap with actual minimum bounds. This avoids
scrolling a cramped landscape feed; rotation recovery remains to be proved.

Rotation run38081332636 at911e87f completes two cases26.176s: the first tap
passes, the rotation case fails atline95 before tapping with0 provider requests.
Its stage log contains only launch. The individually inspected original failure
PNG shows the previously cached text-link post and backup reminder in portrait;
the explicit-link post was not visible. No rotation behavior is established.
Artifacts include hidden .scratch directories, which must be inspected directly.
Atff12bd6, the setup waits for both archive responses and loading completion,
then the existing scoped helper scrolls to the signed explicit-link card. The
portrait return likewise waits for a scrollable feed before locating that card.
Spec review of911e87f...ff12bd6 has0 actionable findings. FocusedRED38081746532
is pending; the accepted rotation assertions and production code are unchanged.

Rotation RED38081746532 atff12bd6 reaches portrait readiness, with exactly one
provider request, then landscape display. It fails line105 after10s waiting for
the selected-video fallback; two cases18.652s total, first tap passes. Retained
stages and individually inspected original PNG show the landscape app without
the video dialog. Evidence: ticket16/rotation-red. The minimal production fix
moves validated public selection into a saveable root playback host outside
lazy rows and backend-specific navigation. Activity-bound browsers are recreated,
never retained; actual wallet/backend changes close the selection. A restored
selection belonging to a real owner stays hidden while that owner/backend
loads. A new deliberate tap on public cached content without a loaded wallet
can open an anonymous selection, preserving public reading without setup; it
closes when a real owner arrives. This does not restore a previous owner's
selection or provide any signing capability to the player.
Only public owner/backend/video identifiers are saved; no wallet capability is
passed to provider content. Runtime GREEN remains pending.

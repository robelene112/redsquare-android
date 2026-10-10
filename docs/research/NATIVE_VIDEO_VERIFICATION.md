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

Root-host source compiles1m41s. Bounded d8e9869...f8f22d4 Spec review0;
Standards flagged an overly broad description of null-session loading, resolved
by6c2186e's distinction above, with0 residual findings. No wallet gate was added.

Focused10 run38082111356 atf8f22d4 reaches landscape fallback, unchanged request
count and visible close/external controls, then fresh portrait readiness with2
requests. It fails the final widget lookup atline118: Chromium's accessibility
WebView nodes have no content description, despite the native property. Original
failure PNG is a black player surface with ready/close/external text; separately
captured XML exposes two WebView nodes at[12,132][288,580] and the controlled
provider text. These are different frames, not real media playback. Small-window
XML has no WebView. At6fd854c, both positive viewport measurement and negative
undersized-window assertion use the actual android.webkit.WebView class. This
strengthens the absence check and preserves the200dp bounds requirement.
Focused10 run38082402358 is pending; production source remainsf8f22d4.

The companion17 run38082113563 also reaches landscape fallback and fresh
portrait readiness with2 requests, then fails line120's repeated label lookup
after5s; two cases40.405s, first tap passes. Its individually inspected original
PNG visibly shows white controlled-provider text on black, ready/close/external
controls. No real media is depicted. The corrected class selector addresses
actual WebView nodes rather than a native label that Chromium can replace.

Corrected rotation GREEN38082402358 at6fd854c: two Android10 cases10.825s,
including actual WebView absence in the undersized window and visible portrait
bounds at least200dp. Individually inspected original fallback and portrait PNGs
show the external message and a black controlled-player surface respectively,
with visible close/external/ready copy. No real media is depicted.

Thread/provider recovery GREEN38082750948 atbc35794: three Android10 cases
16.916s. Its independent signed text row is positively opened as a conversation;
the controlled archive validates the selected field5 thread ID. HTTP503, Retry
to provider153, canonical external ACTION_VIEW stub, Retry to ready, Close to
conversation and Back to feed all pass with exact1/2/3 API requests and no
transaction. The individually inspected error PNG displays the expected153
explanation and escape/retry controls. Retained recovery-green-android10 is
intermediate only. Bounded fixture Spec review6c2186e...bc35794 has0 findings.

The next lifecycle tracer ata3647ad uses trusted public WebView button taps and
documented YouTube state events, rather than invoking private browser methods.
It requires native observed playing, pause after backgrounding with no automatic
resume, and cue-only reconstruction after Activity recreation. Primary IFrame
API onStateChange documentation was rechecked2026-10-10; state1 is playing and
state2 paused. The native playing observation is not implemented yet.
FocusedRED38083125042 is pending. A separate opt-in live provider read remains
planned so controlled readiness is not confused with actual YouTube playback.

Lifecycle RED38083125042 ata3647ad: four cases29.266s, first three pass.
The new tracer taps its trusted public provider button then fails line272's
native playing observation after10s, with one API request. Individually
inspected original PNG and separate XML expose Playing controlled video while
the native label remains ready. Evidence: ticket16/lifecycle-red. The minimal
fix subscribes to documented onStateChange events:1 reports playing;0/2/5
restore ready without starting playback. Native polling maps the new state to
plain product copy. No playVideo invocation or wallet bridge is introduced.
[Primary state-event reference](https://developers.google.com/youtube/iframe_api_reference#Events).
Background/recreation GREEN remains pending.

Lifecycle GREEN38083557172 at16b97ba: four Android10 cases24.422s, including
trusted provider tap, native playing, background pause without a new request,
and cue-only recreation with exactly two requests. Original paused/recreated
PNGs were inspected separately: paused-player shows a black player surface
with native ready copy; recreated-player shows a black app window with the system
status bar. Its cause is unestablished. Later XML captures expose Paused
controlled video and Play controlled video respectively; recreation readiness
is established by the successful assertions and later XML, not its bitmap. The assertions establish the controlled lifecycle behavior;
the bitmaps do not depict real YouTube media. Evidence: lifecycle-green-android10.
Bounded93d582a...16b97ba Standards review0; Spec identified a provider error
that subsequent state/ready events could erase before the500ms native poll.
The existing recovery tracer now emits153, then state5 and ready in one callback
tick, requiring the same error/Retry UI. Production is unchanged for this RED.

Sticky-error RED38083896709 at688aefa: four Android10 cases41.672s, three
pass. Thread recovery fails line205 after20s waiting for153, with two provider
requests and one thread read. Original failure PNG is mostly black with an
external-link control; separately captured XML reports native ready copy, no
error/Retry. Retained sticky-error-red. The minimal source fix guards state
updates after error and guards ready both before and after cueVideoById, preserving
a synchronous cue error too. Explicit Retry creates a fresh browser attempt.
Actual GREEN is pending; no automatic play or private bridge was added.

Sticky-error GREEN38084273614 at2a99f86: four Android10 cases23.724s. The
original error PNG clearly shows the153 explanation and Retry/external/Close
controls after error, state5 and ready in the same callback tick; unchanged
external/retry/navigation assertions also pass. Retained sticky-error-green-android10.
Both bounded review axes0 residual hard findings. Review cleanup shares only
the repeated primary-preserving diagnostic capture, keeping scenario-specific
checks and request diagnostics local. Full acceptance and actual media remain open.
Full Spec review separately confirmed optional-scheme/last-text-URL semantics
in both pinned Browser helpers; the native first-URL assumption needs correction.

Text candidate compatibility RED28s: five focused core cases, two failures.
The new independently WASM/Elliptic-signed text row without an explicit scheme
expects M7lc1UVf-VE but receives null; the corrected trailing-YouTube expectation
also receives null. Retained text-candidate-red XML/log. The independent offline
proof now invokes unchanged Browser extractFirstValidURL/urlRegexp/numberFilter
and Tweet extractYoutubeId from both pins, with four literal text cases: omitted
scheme; generic URL then YouTube; reverse order; two different YouTube IDs.
Results are M7, M7, null and bHQqvYy5KYo, respectively, at both pins. Original
signed text/link/quote/malformed reference rows remain byte-equivalent as parsed
objects; four new public unsafe scalar1 references are appended. Read-only
network-disabled pure crypto used the named pinned dependency entry points only;
no initialized Saito app, private options or node state was accessed. Source
provenance now corrects the prior first-link assumption. Native candidate
selection mirrors the helper, retaining strict URI host/port/ID validation and
canonical HTTPS targets. Focused GREEN/core/debug/test compilation is pending.

Text candidate GREEN: full44 core cases0 failures/0 errors/0 skipped; all five
video-core cases pass. Native debug/test assembly succeeds2m21s. This includes
unchanged provider hardening and explicit-link/verified-quote tests, plus the
four independently signed candidate rows. It is not device or live-media proof.

Bounded text-selection fix reviewsbb491bb...32e28c4 Spec0 and
2a99f86...32e28c4 Standards0 (including shared diagnostic helper). Both verify
source hashes and unchanged old signed references. Separate PublicYouTubeReadTest
is now explicitly enabled only through video-public/video-public-android17
scopes and publicVideoRead=1. Normal/full controlled checks cannot select it.
Its Saito archive stays controlled; the player loads the real API/iframe without
a provider fixture, requires visible ready before a trusted provider Play tap,
then requires native playing from the real documented state event. It captures
ready/playing/failure PNGs and separate XML, plus actual WebView provider metadata.
No private browser method, account login, Saito write or credential is used.
Test assemblyPASS; actual provider playback is pending, not inferred from compile.

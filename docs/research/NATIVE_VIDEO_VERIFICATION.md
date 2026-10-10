# Native YouTube verification

FullAndroid10 companion38087989539 at383caac also fails the same pre-scroll
first video case with0 provider requests:3/4 video cases pass40.199s. Browse
8/8 passes15.281s. The original in-scope failure PNG, individually inspected,
again shows cached queued content without a visible target video post. Retained
full-video-red/android10. No old full candidate is accepted.

Corrected focusedAndroid17 run38088217096 at534330e is GREEN: all4 video cases
66.007s, including exact-post scroll/tap, installed-app Referer and close, rotation,
provider recovery and trusted controlled playback/background/recreation. The
individually inspected original tapped-player PNG visibly shows the controlled
provider text and native ready instructions; it is not real YouTube media.
Raw log and that PNG retained scroll-green/android17. Local test assemblyPASS
1m1s; both bounded review axes0 through534330e. FullAndroid17/10 runs
38088542044/38088569769 at534330e are pending with public Saito reads enabled.
Production remainsc6113d2. Actual public playback remains blocked by the observed
provider refusal; underlying cause unestablished. Keep #17 open while continuing
independently ready work sequentially. No further speculative provider changes
are planned; immutable regression results must still be inspected and recorded.

FullAndroid17 run38087176435 at e8fafd9 FAILS in controlled videos before the
later foundation/public-read groups. Three of4 video cases pass,44.025s total;
the first case times out atline50 before tap with0 provider requests. The
individually inspected original in-scope PNG shows the feed's cached queued
post and backup reminder, with no visible target video post. The pre-scroll
wait required the older selected post to be composed already; a LazyColumn
need not compose offscreen posts. Other cases instead wait both archive reads
and loading completion, then scroll the exact signed post into view. The first
fixture now uses that same setup and dismisses the existing Later reminder
before the unchanged scoped scroll/tap. All positive provider readiness,
installed-app Referer, no-autoplay/no-write and close assertions remain.
This is a source-supported setup correction; its new runtime result is pending.
Retained full-video-red/android17 has the raw video result and original PNG/XML.
FullAndroid10 companion38087989539 remains pending at383caac. Neither full
candidate is accepted; the actual public provider refusal remains separate.

Focused browse recovery run38087621645 atf6c016e is GREEN: all8 cases15.376s,
including cached profile Posts/Replies/Likes after the controlled server stops.
The individually inspected original cached-Likes PNG shows the retained liked
post, missing-history explanation and refresh failure warning. Acceptance log
and that PNG are retained in ticket16/browse-readiness-green/android10. The pass
does not establish the cause of the earlier failed navigation. Cleanup383caac
compiles and has0 residual findings on both review axes. FullAndroid10 companion
38087989539 at383caac is pending; fullAndroid17 38087176435 remains pending at
e8fafd9. Production source is identical between both full candidates andc6113d2.

An additional read-only source/provider review found no evidence-supported app
fix for the observed public refusals. The documented installed-package identity,
relative origin/referrer policy and corrected minimum viewport are present.
The native refusal maps provider101/150, separately from missing identity153;
the actual code and underlying provider reason are not distinguished by the
retained probes. Automatic Media Integrity documentation does not establish
that these failures need credentials or manual configuration. Actual playback
acceptance remains unresolved, and #17 stays open.

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

Full source reviews711e42e...5454618: Spec0 actionable source/evidence findings;
Standards0 hard breaches/0 actionable smells. This does not establish runtime.
Public checks at5454618 do not pass: Android10 run38084979254 one case30.203s;
Android17 run38084966811 one case45.588s. Both reach native ready then fail
line61 because the public Play control is absent. Individually inspected original
failure PNGs show a mostly black app/external control on10 and a visible native
ready header/black player on17. Separate XMLs expose no provider controls. Actual
WebViews are74.0.3729.185 and145.0.7632.218 respectively. No cause is established,
and the current17 provider prevents attributing both failures to10's older
WebView. Evidence: public-no-control/android10 andandroid17. No real playback
claim, no full acceptance or ticket closure.
A bounded read-only DevTools diagnostic is added only to opt-in public scopes:
it inspects numeric DOM geometry and public controls of the .dev wrapper and
documented M7 example, not private application globals/cookies/storage/headers.
It makes no playback call or acceptance assertion. Recent WebViews automatically
enable debugging for debuggable builds; no production source or debugging flag
was changed. [Android primary reference](https://developer.android.com/reference/android/webkit/WebView#setWebContentsDebuggingEnabled(boolean)).
Syntax checks pass; actual captured browser geometry remains pending. Existing
UI/playing acceptance gates are unchanged.

Diagnostic reviews5454618...ec6d437: Spec0; Standards noted SIGTERM could
bypass normal finally cleanup of an owned ADB forward. The tool now handles
cancellation, closes its active socket, removes its owned forward and stops
polling. This changes only the optional diagnostic; application/test acceptance
assertions remain unchanged. SyntaxPASS; the current immutableec6d437 diagnostic
run38085634358 still uses the earlier tool, and is not proof of the cleanup fix.

Standards re-reviewec6d437...d1c70a7 reports0 residual cleanup findings.
Diagnostic Android17 run38085634358 at ec6d437 fails the same public Play
assertion after38.23s. Its three diagnostic records are generic read failures;
no frame geometry or browser screenshot was captured. This does not establish
the player dimensions or the provider failure cause. Discovery now records
only stage names and page/frame counts, and connects to a page in the isolated
.dev process before filtering each frame by the existing allowed origins.
DOM reads are filtered per allowed frame. Review found that the optional page
screenshot could include other frames after broadened discovery, so it has been
removed entirely. Only the existing native UI test captures PNGs. The changed tool
remains a diagnostic, separate from the unchanged native acceptance assertions.
Immutable5771d9d run38086007749 predates that removal; it must not be cited as
runtime proof of the screenshot boundary fix.

Diagnostic run38086007749 at5771d9d fails the same unchanged public Play
assertion, one case39.469s. Allowed-frame DOM records now establish a layout
defect: completed .dev wrapper viewport296×448, but body and player/iframe
height0; the YouTube child viewport is296×0. Its public Play video button exists
at y=-36 with72px height, clipped by the zero-height frame. The video remains
paused with readyState0. This is concrete zero-height rendering evidence,
not successful playback. Retained public-zero-height/android17 JSONL, test log
and WebView145 metadata. The old diagnostic also attempted page screenshots;
those PNGs are not retained or used as proof, and current23eea43 omits that call.
Both bounded review axes through23eea43 report0 residual findings.
Minimal production fix uses viewport-relative100vh instead of a percentage
height chain, and displays the iframe as a block to remove inline baseline
spacing. The existing actual-provider test is the acceptance RED; its trusted
Play/control and native Playing assertions remain unchanged. Build and actual
provider GREEN are pending. No autoplay or provider/wallet capability was added.

CSS-only8e46329 is not GREEN. Android10 run38086337225 fails native Playing
after a trusted control click, one case51.545s; Android17 run38086335271 fails
the missing Play assertion, one43.16s. Both captured completed wrapper296×448
with body/iframe0 and YouTube viewport296×0. The CSS viewport-height attempt
therefore did not correct the underlying sizing mode. Android10's original
failure PNG remains mostly black/external control; separate XML exposes a tiny
Play control and Unable to play media. No media success claim. Evidence retained
public-content-height/android10 andandroid17. Native/debug-test buildPASS1m6s;
both bounded CSS source review axes0, but runtime acceptance fails.
Android's primary WebView documentation explains that content-height layout
fixes the HTML body height and can break relative sizing. The next minimal
correction explicitly sets MATCH_PARENT native layout parameters before HTML
loading and restores relative100% CSS height, keeping block iframe display.
[Primary layout-size contract](https://android.googlesource.com/platform/frameworks/base/+/9f52b4aa3b0cf720b67f9f7c7ccaafba4eac4367/core/java/android/webkit/WebView.java#289).
The existing live acceptance RED/Play/Playing assertions remain unchanged;
this explanation is a source-supported diagnosis to verify with fresh geometry
and trusted playback, not a passed result.

Native sizing is verified atc6113d2: Android10 run38086674099 and Android17
run38086671894 show completed296×448 wrapper/body/iframe and YouTube child
viewports. The zero-height defect is corrected. Both trusted Play/control gates
are reached, but native Playing still fails after30s, one51.91/56.76s respectively.
Individually inspected original failure PNGs both show the native embed-disabled
explanation, Retry and Open in YouTube. This maps to101/150; the exact numeric
provider code was not captured. Retained public-provider-refusal/android10/17.
The documented M7 example is therefore refused in these runs; no real-media
success, general provider outage or attestation/network cause is established.
Local debug/test buildPASS1m44s; both native-layout source review axes0.
An explicit public_video_reference option selects the existing independently
signed last-text candidate bHQqvYy5KYo for a second public probe, without changing
the original M7 default or its Play/Playing assertions. YouTube's public oEmbed
read returned its Google for Developers title, "Google I/O 2011: YouTube's iframe
Player: The Future of Embedding" and typevideo on2026-10-10. This is metadata,
not embedding/playback proof. No signed fixture is altered. CDP allows only the
two selected public embed paths and retains the same frame-only DOM boundary.
The second probe is pending and is not a provider-refusal-as-success test.

Alternate bHQ probee8fafd9 also fails positive Playing after trusted Play on both
versions: Android10 run38087140673 one51.691s; Android17 run38087138529 one51.313s.
Both original PNGs individually inspected show the same native embed-disabled
copy/Retry/external escape. Retained public-alternate-refusal/android10/17.
Separately, unauthenticated public watch metadata from this VPS for both IDs
returned LOGIN_REQUIRED with a bot-check reason. That read used a generic
Mozilla/5.0 user agent, no login/cookies or media extraction; it is a different
host/client from the emulator and does not establish the emulator refusal cause.
Public YouTube acceptance remains unresolved. Do not label either refusal as
successful media playback or invent an attestation credential requirement.

Full Android10 run38087174575 at e8fafd9 stops in the existing eight-case browse
group: seven pass, one31.662s overall failure atcached-profile line296 waiting
for Posts after a fresh activity click. The only failure PNG is captured after
ActivityScenario disposal and shows a blank app/status bar; it does not establish
the active failure screen or cause. Retained full-browse-red/android10. Feed
navigation memory changes from the temporary null-wallet key to the loaded
identity key; cached rows can arrive before that load. The corrected fixture
waits for existing public New post enabled readiness before its author tap,
matching the accepted cached-conversation recovery precondition, and captures
inside the fresh scenario on failure. All cached posts/replies/likes/outage
assertions and15s waits remain. This is a bounded startup-session hypothesis,
not a proven cause. Production is unchangedc6113d2. Full17 companion38087176435
at e8fafd9 remains pending; focused browse10 and outstanding full10 are required.

Reviewe8fafd9...d22c2a2 confirmed the readiness/assertion boundary and corrected
handoff, but Standards noted the touched case's enclosing diagnostic capture
and manual server cleanup could still replace the primary error. The case now
uses AutoCloseable.use for primary-preserving cleanup and shares a small scoped
primary-preserving capture helper for its inner/outer diagnostics. This is a
source-observed cleanup risk, not an observed masking incident. No production
or cached recovery acceptance assertions changed; runtime proof remains pending.

Final immutable regression candidate534330e: Android10 run38088569769 and
Android17 run38088542044 both PASS. Retained raw results count127 completed
instrumented cases in36 records each, including four controlled video cases
23.302/28.207s, eight browse cases, all eleven actual process interruptions,
both public Saito reads, cached cold launches and signed5-to8 update continuity.
Both core reports contain44 cases,0failures,0ignored; build/release/lint passed.
Evidence retained ticket16/final-full/android10 andandroid17. Native controlled
player originals were individually inspected:17 shows the correctly sized
controlled wrapper;10 shows controlled text on a black viewport and external
control, with header fields absent despite passing semantic assertions. Both
owner-feed originals show public content. Both official-feed originals lack an
author despite passing the separate semantics/read gate. Those incomplete
bitmaps are excluded from visual acceptance; their cause is unestablished.
This evidence proves the534330e regression candidate, not subsequent #18 edits,
actual YouTube media playback, physical Pixel7 performance or production writes.
Public provider playback remains blocked as recorded on #17; keep the issue open.

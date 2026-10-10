# Native image verification

Active #15, fixed base3d47253. Parent seams1–4. No live transactions or node writes.
Source contract NATIVE_IMAGES_CONTRACT.md. Ticket14 is complete.

## Current acceptance

Android10 full38068713412 atad4a8c0 PASS123 completed cases/35 records/11 actual
process deaths; core39/build/release/lint and both public reads/cold/update
continuity pass. Retained logs, independently recounted two-axis reviews, inspected
selected PNGs and limits: ../evidence/ticket14/full-android10/REVIEW.md.
Actual frames from this final10 run independently pass pinned WASM/Elliptic
verification, A/B unchanged templates and offline Chromium image-order/raster
checks; retained ../evidence/ticket14/interop. Full17 companion38069923347
at4dd0d3d passes media but fails an older unreadable-refresh feed fixture.
The corrected readiness guard compiles at8ffe1c4; focused feed17 and another
full17 companion remain required. Production/build/scripts/workflow unchanged.
Do not close #15 until that remaining full result is inspected and accepted.
The checkpoints below preserve chronological red/green evidence and historical
pending statuses rather than claiming each old candidate is final.

## Protocol checkpoint

Independent public unsafe scalar1/2 references generated with pinned WASM0.3.2,
Elliptic6.6.1 and verified WASM hash, network-none/read-only/cap-drop/env-i512MiB
Node24.20.0. Named dependency directory and generator/helper files only on the
repeat run; output byte-identical. First generator attempt failed on a guessed
Node path; command discovery found /usr/bin/node. No initialized Saito app,
wallet options or database reads. Three valid12x8 solid-color PNGs distinguish
own and original media. These are synthetic reference payloads, never funded.

Protocol tracers, all logs retained protocol-checkpoint/:
- Ordered image property API red24s, green46s.
- Unsafe/non-string descriptors red28s, green44s. Authenticated text retained;
  safe HTTP(S), backend-relative and non-SVG raster data URIs exposed, maxfour.
  URL filtering affects display only; signed envelopes remain exact.
- Conflicting dual-original image order red29s, green44s. Compare raw embedded
  arrays to the independently verified original, preserving outer commentary.
- Outgoing quote composition API red24s, green44s. Actual native signature equals
  independent reference, ordered own images and exact original media preserved.

Source/debug/test APK build PASS1m53s, core39 zero failures. First Android UI
tracer checks displayed image pixels/order and full-screen viewing against the
controlled peer. Expected RED run38060039219 confirmed the post text displayed
before the image-descriptor wait failed after15seconds (ImagePreviewTest:45).
Retained display/android10-red.txt. No cause inference is needed: the gallery
was absent from this source.
Images/images-android17 scopes mapped correctly among all30 declared SDK choices;
shell/whitespace checks pass. No Android media runtime or feature completion claim.

## Display checkpoint

Native feed/thread/profile and verified-original galleries now share bounded
asynchronous loading: two concurrent jobs,2MiB compressed image bodies,
16,384-axis/100M-pixel source guards,1024-axis sampled software bitmaps and16MiB
public bitmap cache. Remote reads have12second whole-call timeout; relative
paths resolve to the selected approved backend. No authentication/wallet state
is attached to image requests. Release cleartext policy remains unchanged.
Loading/unavailable states preserve text; manual retry and fullscreen viewer
provide close/previous/next/zoom. These are implementation limits, awaiting
runtime acceptance. Incoming GIF currently decodes a static first frame;
animated remote/Giphy rendering belongs next#16.

Focused Android10 run38060625794 at1d6dbb8 PASS: ordered distinct red/blue raster
pixels, opening/closing fullscreen viewer and zero submitted transactions.
Source/core/debug/test/release/lint checks also pass. The interim standards
review found abandoned synchronous downloads could hold slots until timeout;
the correction cancels OkHttp calls when the image coroutine is cancelled and
checks activity before decode/cache. It awaits verification. Spec review0.
Next tracer selects a synthetic image through a controlled picker-result
boundary and verifies saved/recreated draft text/media without publication.
This first tracer does not claim actual system picker UI or process-death proof.
The first retained image-viewer.png captures the underlying feed root, not the
dialog, despite successful viewer assertions. It is excluded from viewer visual
acceptance. Corrected capture uses the actual Android window screenshot; this
does not change the runtime assertions or establish a different failure cause.

## Remaining acceptance

Actual picker, normalization/orientation/metadata/resource bounds, durable image
work across save/restart/queue/import/backend changes, remote failure placeholders,
quote/media author edit/deletion propagation, real process interruption and
native-frame/current-client image presentation proof. Focused checks then full
Android10/17 acceptance, two-axis review, commit/push. Pixel timing remains#35.

## Selection/draft checkpoint in progress

Expected RED38060848742 at07cbfde confirmed the selected-image tracer fails
on the absent Add images UI; the signed-image display case remains PASS.
Retained selection/android10-red.txt. Controlled picker results use an owned
synthetic MediaStore PNG, not a real account/photo or broad storage permission.

Composer now uses AndroidX image-only single/multiple visual selection and
enforces four results including fallback providers. Selected content is read
with32MiB byte cap,15second provider deadline, bounded read/cleanup pools and
cancellation/descriptor closure off the UI thread. Decode rejects unsupported
input MIME/axis/pixel bounds and targets1920x1024 software pixels. JPEG encoding
flattens alpha to white, emits no source metadata, and has finite quality/resize
attempts under380KiB JPEG/512KiB URI and remaining whole-message budget. Actual
format/orientation/metadata/failure fixtures remain pending.

The previous text/media draft is verified in encrypted storage before provider
reads. A prepared batch appears only after encrypted save/reopen verification;
failed preparation/save retains previous media and composed text. Saved records
append default-empty images for legacy drafts and compare exact raw own signed
data.images to stored images. Memory, queue cancellation, queue display and
exact-candidate persistence preserve them. Composition uses the independently
verified core builder; image-only Send is enabled. The scrollable composer keeps
media accessible in constrained viewports. These changes await focused runtime
and broader recovery acceptance; the ticket remains open.

Focused Android10 GREEN38061594037 at312f58c PASS both display and controlled
selection/draft cases in7.016seconds, core39/source/debug/test/release/lint.
Text matches after saving/reopening and activity recreation; rendered normalized
JPEG pixels match the independent red fixture, zero transactions. This is not
process-death/disk-only recovery proof. Actual-window viewer screenshot inspected
and shows the red image, index/close/previous/next controls. The saved-draft
capture shows the image after scrolling; text is above the captured viewport,
so semantic text assertion and visual attachment evidence are distinct.
Both interim source-review axes0 findings at312f58c.

Next tracer opens the real system picker against an owned MediaStore green PNG.
Initial filename selection is a fixture observation pending10/17 hierarchy
inspection; an absent filename on a modern picker does not prove an app defect.
Picker PNG/XML and failure captures are retained to adapt selectors from facts.

First real-picker runs41459dc: Android10 38062146694 and Android17 38062148656
failed the filename-selection fixture. Android10's Recent view was empty;
Android17's actual Photo Picker contained one green photo described by its date,
with no filename node. The legacy listing cause is not established. Android10
display/draft cases passed; Android17 display passed and draft reopen timed out
after the second New post click. Its failure screenshot shows the feed. The
fixture's text-only wait could match the composer header instead of the feed
button; this is an observed selector ambiguity, not a proven source defect or
complete root-cause account. Logs/captures retained system-picker-first/.

Corrected test waits for the clickable feed New post control before recreation
and reopening. Legacy real SAF uses a separate read-only synthetic image
DocumentsProvider root, with positive selected-provider toolbar observation;
wallet documents/provider remain untouched. Modern Photo Picker selection uses
the observed date description and requires exactly one matching photo. Selected
picker hierarchy capture precedes a bounded enabled confirmation-button wait.
Prepared-image pixels, image-only Send enabled and zero-transaction assertions
remain unchanged. Corrected focused10/17 runs are pending, production source is
unchanged from312f58c. No longer file timeouts or acceptance relaxation applied.

Corrected Android17 focused38062884910 at847190c PASS all3 cases in28.679s,
core39/debug/test/release/lint. Retained logs and actual-window captures under
system-picker-corrected/android17. Exact draft screenshot inspected: blank text,
green prepared image, enabled Send. No transaction sent in this picker case.
Android10 companion38062883051 never reached instrumentation: `run-as ... test`
failed with Permission denied. da084d5 checks positive size using Android's shell
builtin instead; focused38063402024 pending. This is a host fixture correction,
not evidence of an application image defect. Production unchanged from312f58c.

Next controlled-socket cases compose and Send an image-only ordered red/blue
post, a green image reply and a green image quote of the authenticated red/blue
original. Assertions inspect actual signed JPEG dimensions/colors/count/order,
whole message size, reply routing/recipients and exact dual original image data.
Actual public test frames are captured for independent WASM/Elliptic verification.
These cases are pending and do not establish compatibility yet.

Android10 da084d5 focused38063402024 reaches instrumentation: display and
saved/recreated draft PASS; real SAF test fails selected-provider toolbar at163.
In-scope screenshot shows roots drawer still open with Recent selected and the
synthetic root visible. End-of-run host XML shows Launcher, so it cannot explain
that in-scope state. Drawer click/navigation cause remains unproved. Fixture now
waits for UI idle, captures the drawer hierarchy, reacquires the title and retries
selection within the original10-second navigation deadline until the same positive
provider-toolbar observation. In-scope failure XML is retained before cleanup.
No file timeout, prepared-pixel or zero-transaction assertion was weakened.

Sending checkpoint d8d6bbf: local core/debug/test/release/lint build PASS4m16s
before the final fixture field/selector adjustments; hosted exact-candidate
checks remain authoritative. Android10 38063744722 image-only signed post and
existing display/draft PASS. Real-picker diagnostic XML failed because its parent
directory did not yet exist; fixed before navigation retry. Quote availability
passed, then text-only feed wait timed out with viewport on the original's second
image. Reply timed out at availability; no reply frame capture exists, indicating
its peer assertion did not finish. Previous cached quote roots can expose the
same original text before current refresh. Fixture now clears only synthetic
feed/conversation/profile/quote-original/postdraft records and waits current peer
requests plus enabled Refresh. Availability wait checks peer failures directly.
Post-refresh waits current requests and explicitly scrolls the lazy list to the
quote text. These observations do not establish a production defect or complete
root cause. Original failed logs/captures remain in GitHub artifacts/scratch.

Recovery tracer uses three real processes: prepare and persist, delete source and
kill; cold restore pixels/text then uncertain Send and kill; cold recover exact
candidate, reconcile verified archive without broadcast, clear only after success.
Local build PASS2m04s; runtime pending. Supplemental spec review0 findings.
Standards review found early process failures could lose in-scope screenshots;
fixed by finally capture for all phases and retaining partial stdout/stderr on
instrumentation errors/timeouts. This is failure observability, not relaxed gates.

1e56a14 Android10 focused38064108916: all5 display/draft/signed image post/reply/
quote cases PASS; only actual SAF selected-root assertion fails. In-scope XML now
proves two title matches: a Recent-view app tile under apps_group behind the
roots drawer, and the intended row under roots_list. Generic findObject(title)
can hit the background tile; the failed screenshot shows Recent remains selected.
Selection is now scoped to the observed roots_list ancestor, retaining the
positive toolbar requirement. This captured ambiguity is more specific than
an unproved transition-timing explanation; no longer timeout applied.

Android17 38064111383 at1e56a14 PASS all6 picker/display/draft/actual sending cases
in45.906s. Recovery first preparation phase times out before an image appears;
in-scope capture shows keyboard and focused text field with Add images outside
viewport. The recovery fixture's Add images click lacked the scroll/display check
already used by the sending fixture. Added explicit scroll and visible assertion.
No process death occurred in this failed phase. Its finally capture works and
retains failure.png; no application data-loss or complete timing cause is claimed.

Preparation checkpoint local build PASS2m13s. New external-picker/socket tests
use hand-written TIFF orientation/GPS tags with known colored quadrants for all8
orientations; normalized JPEG pixels/dimensions and omitted APP1/source marker are
asserted. Transparent PNG→white,4000x2000 WebP→1920x960, one-pixel local GIF and
JPEG are sent together under four-image/per-URI/whole-message bounds. Invalid
batch, missing/deleted source, five fallback selections and32MiB+1 source must
leave previous image/text intact and broadcast nothing until explicit Send.
These fixtures await runtime; input compression uses Android Bitmap but EXIF
bytes and expected orientation mappings are literal, independent of preparation.

9662864 Android10 38064524729 again PASS all5 other cases. Scoped drawer click
now successfully selects the image provider: toolbar positive, file provisioned
and visible. Captured grid XML exposes filename/size/date in a content-description,
with empty text. Changed legacy file selection to that observed description prefix,
requiring exactly one match as before. Provider listing is present; the earlier
filename-text absence is not missing fixture data. Screenshot shows the image tile.

Remote fixtures append independently signed public unsafe1/2 posts at fixed
loopback19125; all earlier vectors are unchanged. Controlled HTTP cases assert
404/2MiB+1/invalid raster placeholders preserve verified text, explicit retry
recovers one valid red image, and leaving two stalled requests via Settings to
select the other approved backend allows blue pixels before obsolete12s HTTP
timeouts. Emulator timing bounds check cancellation capacity only, not Pixel
performance. Production image source is unchanged from312f58c; runtime pending.


6972766 focused38064903068 Android10 and38064905862 Android17 each pass5/6
initial cases. Android10 sole failure waits for OPEN after the file tap; captured
selected/failure XML positively shows the app already resumed. The fixture now
accepts positive app return or an enabled confirmation, then still requires
prepared green pixels, enabled Send and zero transactions. Android17 reply fails
scrolling before cold thread refresh finished; capture shows Loading replies and
disabled Refresh. Fixture waits enabled Refresh before unchanged displayed-reply
assertion. Neither run reached supplemental preparation/loading/process checks.

New wallet replacement/restoration, author edit/deletion, queued cancellation and
original-backend variants include a normalized red image. The shared selection
helper requires source deletion==1 before returning; lifecycle assertions require
retained pixels. Image queue process mode uses an actual kill and cold delivery;
pre-interruption timestamp/identity plus decoded image/pixel assertions are not a
claim of externally captured pre-send byte equality. Queue host captures synthetic
evidence in finally. Focused images also exercises the four lifecycle variants;
full scope runs those through their existing classes. Runtime remains pending.

Review follow-up: both axes report0 residual findings after shared cleanup keeps
primary failures, suppresses cleanup errors and attempts all owned URI deletions,
and stalled-download timing starts before activity launch/any request. Local
core/debug/test/release/lint build PASS2m16s on these Kotlin fixture changes.
Independent WASM0.3.2/Elliptic6.6.1 checks pass all three actual Android10 frames
from697. Unmodified A/B constructor/template graphs render outer green JPEG then
original red/blue PNG sources in exact order; gallery negative controls reject
unsafe sources and cap4 safe entries. Controlled template proof has no browser
DOM, network, node initialization or current served-revision acceptance claim.


c3ff0f2 Android10 focused38065815558:6/6 initial image cases PASS18.184s;
8 EXIF orientations, four-format fitting, remote failure/retry and two-stall
cancellation PASS. Only failed/excess selection test fails. Its count-negative
used the same URI five times; AndroidX1.10.1 GetMultipleContents parser bytecode
uses LinkedHashSet.add for data/ClipData, collapsing those entries. Fixture now
returns five distinct owned URIs and labels each rejection failure. Original
assertions retain prior1 image/text and zero transmission until Send. The earlier
capture does not identify the failing selection conclusively; do not treat this
source-confirmed fixture defect as a proven complete runtime cause.

Android17 c3 focused38065817557 passes5/6 initial cases; image-only posting times
out before Remove image1 appears. In-scope capture shows focused empty text field
and keyboard without prepared image. It does not prove a provider/preparation
failure; no preparation or recovery phases reached. More positive launch/state
observations are needed before claiming a cause.

Supplemental storage-obstruction variants add image pixels to prior text recovery
checks. A mounted media quote variant seeds verified author edit/deletion records
and observes original red/blue media retained through edit and removed after
deletion, with own green media/commentary retained and cached deletion honored.
It complements actual UI author commands and is not an end-to-end quote author
command claim. Independent mediaAuthor commands append to the reference set;
every existing reference field was compared unchanged. Local first supplemental
build PASS2m19s before cleanup/capture/rejection fixes; exact corrected build and
runtime pending. Both axes requested follow-up for corrected cleanup/capture
and non-vacuous browser pin requirements.

Offline Chromium1.63.0 decoded actual quote template DOM images on both pinned
A/B graphs: own green JPEG RGB30/189/70, original red PNG220/30/40 and blue
PNG20/60/220, each12x8 in correct order. Browser blocks external requests; source
hashes and decoded public frames retained in ticket14/interop. This proves a
controlled presentation adapter, not initialized node/current deployed revision,
live publication or a physical performance result. Browser proof requires exactly
the two expected pinned revisions before it can emit PASS.


Corrected selection and failure-observability local build PASS2m15s. Final
supplement skips unnecessary empty-text replacement in image-only posting,
asserts visible Add and actual picker Intent before preparation wait. It adds
cold feed red/blue pixel assertions across the first real process death: cold
peer returns no rows, so cached signed envelopes and a new bitmap cache must
supply both original images. Image queue failures now capture in-scope screens;
host retains partial instrumentation output on timeout/nonzero. These added
assertions await the exact final focused candidate runtime.

Final supplemental core/debug/test/release/lint build PASS2m08s. Review found the
cold-cache image labels/colors could match earlier cached image-only posts;
first phase now explicitly clears only synthetic public feed caches alongside
its synthetic drafts, and both phases require Signed image reference and unique
gallery nodes. This establishes fixture identity before pixel assertions. Final
small fixture guard recompile pending; hosted exact-candidate checks remain
acceptance authority. Both earlier bounded review-fix follow-ups report0.

Cold-cache guard recompile PASS1m06s. Both bounded review axes0 residual actionable
findings. Final focused10/17 candidate dispatch pending; ticket is still open.

354a1c7 focused10/17 stop in initial image-only field assertion: captured semantics
shows EditableText/InputText empty, with merged Text=[Write a post]. The new
assertTextEquals incorrectly included placeholder copy. Fixture now specifically
asserts empty EditableText, still avoids unnecessary keyboard focus and verifies
picker launch and prepared/signed image behavior. This is an assertion correction,
not an established production defect. Independent image groups now continue after
one fails to retain more diagnostic evidence; any group failure still rejects
scope acceptance. Both complete final source-review axes at354 report0 actionable
findings; this subsequent bounded fixture change still needs review/runtime.

Placeholder correction narrow build PASS1m06s. Both bounded review axes0 findings
on editable-value assertion and independent diagnostic groups with strict final
failure aggregation. Complete source-review acceptance remains at354a1c7 plus
this reviewed fixture-only correction; runtime/full acceptance still pending.

fe1be79 focused Android10 38067303565 / Android17 38067305720 each pass6 initial
image cases and5 preparation/loading cases, plus cold queued-image delivery.
Lifecycle passes6/7 each, including source-deleted wallet replacement, author
edit/delete, mounted quote revision/removal, cancelled queue and both failed
storage flows. Two reproducible fixture assertions still fail: disabled text
fields omit SetText semantics, and retained queue history gives two generic
Image1of1 matches. Cold recovery now asserts exact disabled text without an
editing action. Shared list-gallery pixels now scope to the card containing the
exact clickable post text, preserving history and positive pixel assertions.
Failure capture encloses the scoped queue image assertion. Production source
remains unchanged. Narrow compile and bounded review pending, then exact focused
and full10/17 acceptance remain required.

Scoped gallery/read-only field correction narrow build PASS1m14s. Both exact
SDK runs and bounded review pending.

4808761 focused Android10 38068060875 PASS all20 completed cases plus3 actual
process deaths:6 basic,5 preparation/loading,7 lifecycle,1 cold candidate and
1 cold image queue. Both bounded source-review axes report0 findings.
Android17 38068063232 still running. Full-order review found the text/image
backend-queue variants shared one body while producing distinct cached signed
posts. The image variant now uses its own literal body consistently in compose,
frame validation, exact visible post and scoped pixels. Cache history is retained.
Narrow compile PASS1m07s. Full-script author process checks clear prior synthetic
revision/media caches before image viewing; arbitrary manual partial reordering
is not covered by that isolation. Final full10/17 acceptance remains pending.

4808761 Android17 38068063232 passes19/20 completed cases plus3 process deaths.
All7 lifecycle, both cold recoveries and5 preparation/loading pass. Sole failure
is quote Add images visibility immediately after performScrollTo with the IME
open. Actual-window quote-publish-failure.png shows the focused field and keyboard
with the window panned upward; no picker launch occurred. This proves the observed
window/visibility failure, not an established application defect or unique timing
cause. Fixture now uses Espresso's keyboard dismissal before a bounded scroll/
displayed wait, then still requires the visible click, actual external picker
intent, prepared bytes and exact signed quote. Production source unchanged.
Final full10/17 will cover this correction and the distinct queue body together;
no further duplicate focused dispatch is required before that complete gate.

Keyboard visibility correction narrow compile PASS1m09s. Final full device
checks and bounded review pending.

Final candidatead4a8c0: both bounded review axes0 findings. FullAndroid10
38068713412 and fullAndroid17 38068715154 dispatched with public_feed_read=true.
Both source/core/debug/test/release/lint stages PASS; device stages ongoing as
of2026-10-10 16:50UTC. Push sourcecheck38068712976 also PASS. Runtime results
must still be inspected and retained before #15 closure.

Fullad4a8c0 Android10 38068713412 PASS:123 completed cases across35 records,
11 deliberate process-death stages; both public feeds, signed5-to8 continuity
and cached feed/wallet cold launches. Android17 38068715154 passes earlier
browse/follow/posting17/queue9/social8/quotes7/author8 and all signed image
publication, prep/loading and both image cold recoveries. ImagePreview fails2
cases: saved-draft Add visibility with IME open, and system picker confirmation.
The failure XML shows com.google.android.photopicker with selected owned image
and enabled Done; earlier focused XML uses com.google.android.providers.media.module
and Add (1). Cause of system implementation selection unestablished.
Fixture now accepts both observed labels scoped to the launched package and
recognizes both in cleanup. Shared visible picker navigation dismisses IME and
requires bounded scroll/displayed/enabled click. Signed frame/pixel/identity gates
are unchanged. Local narrow compile PASS1m10s. Keep successful10 acceptance and
run outstanding17 full under WORKFLOW's fixture-only equivalence rule; verify
production/build/scripts/workflow unchanged before closure.

Full174dd0d3d/run38069923347 passes all image/picker/preparation/lifecycle and
both image cold recoveries, then fails an older unreadable-refresh fixture.
108 successful individual cases/24 passing records/11 real process deaths,
one failure. Exact logs/inspected PNG/limits retained full-android17-feed-failure.
Warning absence is observed, not a proven application defect or unique cause.
Initial feed text can publish before loading finishes, while that fixture did
not require enabled Refresh. Corrected fixture awaits both initial archive
streams/positive enabled navigation and a subsequent observed request; unchanged
exact warning/cached-post checks remain required. Narrow compile PASS. Production
unchanged; focused feed17 then complete17 companion verification remain needed.

Bounded readiness review8ffe1c4: both axes0 actionable findings and independent
108/24/11/1 recount. Focused feed17 run38071460383 atc73fc13 underway.
Additional picker-return hardening replaces underlying-app presence alone with
positive picker-package absence plus app presence on the automatic-return path.
Confirmation still accepts only the observed launched-package labels, and exact
prepared pixels/Send enabled/zero transactions remain required. This is a source
hardening, not an established cause of any earlier failure. Narrow compile
PASS1m01s. Full17 companion will cover the final helper; production unchanged.

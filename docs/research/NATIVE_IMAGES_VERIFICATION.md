# Native image verification

Active #15, fixed base3d47253. Parent seams1–4. No live transactions or node writes.
Source contract NATIVE_IMAGES_CONTRACT.md. Ticket14 is complete.

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

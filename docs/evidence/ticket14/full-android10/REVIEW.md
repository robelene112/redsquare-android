# Full Android 10 image acceptance

Run [38068713412](https://github.com/robelene112/redsquare-android/actions/runs/38068713412),
source `ad4a8c07a4197fc30e31dedeb5f95067e19c6861`, full-android10 scope,
public_feed_read=true. Source/core/release/debug/test assembly and lint pass;
39 core tests. Device logs independently counted: 123 successful test cases,
35 completion records and 11 deliberate process-death stages. Counts and exact
log paths are in counts.json. Recovery logs and continuity records pass.

Actual provider selection, normalized attachments, signed image-only post,
reply and quote, viewer ordering, preparation/metadata bounds, stalled/invalid
remote loads and Retry all pass. Existing posting/queue/author classes cover
media preservation through wallet replacement, failed storage, backend switching,
cancellation, edits/deletion and original quote revisions. Cold media recovers
without its source document; the saved signed candidate reconciles without a
second transaction; the queued image delivers on startup with its original
identity, backend, text and pixels. This does not promise delivery while the
application is force-stopped.

Selected PNGs were individually inspected. They show prepared green/red media,
the red first viewer image, retained quote-owned green media after original
removal and recovery after interruption. Some capture a pending composer or a
scrolled gallery with adjacent text outside the visible viewport; filenames
such as quote-published do not by themselves prove completed navigation.
Acceptance also requires the passing exact text, pixel, transaction and recovery
assertions in the retained logs.

Both public-feed reads pass. Fresh individual inspection of the retained
actual-window images confirms the owner label saito.davidengelen.nl and official
label saito.io, each with a public post. The initial report misread the official
image; both review axes caught the inaccurate caption, and reinspection confirmed
the correct backend. No live publication was performed.

Signed 5-to-8 update and cached-feed/wallet cold continuity pass. Actual frames
from this run replace the synthetic proof under ../interop: independently pinned
Saito WASM/Elliptic decode/signature/routing checks pass for post/reply/quote;
unmodified pinned A/B Red Square templates and offline Chromium decode three
images in green/red/blue order. This verifies those explicit baselines, not a
current initialized Saito web app, served revision or permanent archive history.

This is one SDK's acceptance. The reviewed fixture-only follow-up at4dd0d3d
keeps production source, build configuration, scripts and workflow unchanged.
Its outstanding full Android17 companion must pass before ticket closure.
Physical Pixel timing, visual polish, owner review and store delivery remain
separate tickets. No production node changes or new APK publication.

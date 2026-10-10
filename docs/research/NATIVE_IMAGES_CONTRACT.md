# Native image attachment contract

Primary-source research for ticket #15, 2026-10-10. Read existing cached public
A/B/H sources and media, bounds, protocol and quote research; fetched missing
public gallery/browser/image files and official Android documentation. No node
access, app initialization, builds, live transactions, wallet/configuration or
database reads. Only this research document was written.

A = [`saito@9a4ca7e`](https://github.com/SaitoTech/saito/commit/9a4ca7e9442eb5e872168ced361ce741d7d139d7),
B = [`saito@214504ec`](https://github.com/SaitoTech/saito/commit/214504ecece76d1cd20a245fe4468eccbfca2951),
H = archived [`saito-lite-rust@7200a988`](https://github.com/SaitoTech/saito-lite-rust/commit/7200a9886f3dd02f66e8d6c146960e2a5402929f).
These source pins do not establish either backend's current served revision.
H is a historical compatibility baseline, not current vanilla saito.io.

## Signed representation

Posts and replies use `module="RedSquare", request="create tweet"`, with
`data.images` an ordered array of **strings**, alongside `data.text`. Replies
add `parent_id` and `thread_id`; images do not change reply identity/routing.
There are no attachment objects, multipart uploads, file IDs, width/height or
separate media upload calls in this composer path.
[A composition:557–574](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/ui/overlays/compose.js#L557),
[B composition:558–575](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/lib/ui/overlays/compose.js#L558).

Local files are read as data URLs, normally normalized to
`data:image/jpeg;base64,<standard-base64-without-line-breaks>`. Accepted browser
input types are PNG/JPG/JPEG/GIF/WebP. Browser `resizeImg` fits the image inside
1920×1024, preserving aspect ratio, then emits JPEG; local GIF animation and
alpha transparency are therefore not retained. Giphy selections bypass this
normalization and retain their returned URL.
[A types:36](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L36),
[A ingestion:459–497](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/ui/overlays/compose.js#L459),
[A resize:2286–2334](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/browser.ts#L2286).

Image strings belong to the **signed message**, not unsigned `opt`. UTF-8 JSON
message bytes enter the signature preimage. Web Archive returns `{t,m,opt}`;
`m` is Base64 of those message bytes, so inline image bytes are Base64 twice on
that wire. Verify original bytes before exposing images; normalization is an
outgoing preparation step, never a rewrite of a received signed transaction.
[JS message packing](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-js/lib/transaction.ts#L159),
[Rust signature preimage](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/transaction.rs#L813),
[web serialization:315–346](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/transaction.ts#L315).

## Quotes and client presentation

| Representation | Own images | Original images and visible behavior |
| --- | --- | --- |
| Ordinary A/B create tweet/reply | `data.images` | Gallery renders safe first four images. |
| H quote create tweet | `data.images` | `data.retweet_tx` contains the original full web-envelope string. H renders outer gallery plus reconstructed original gallery. |
| A/B composer commentary retweet | `data.images` in signed `retweet` | Signed/archive-preserved, but current receiver updates original counters instead of creating a standalone quote. Its quote notification renders the original, so own commentary/images are not visibly established by that notification path. |
| Cross-client native quote strategy from #13 research | `data.images` | One `create tweet` with both exact original `retweet_tx` and `embedded` snapshot. H reads the former; A/B render `embedded.images` (first four), one nesting level. Preserve that strategy when adding outer attachments. |

[H composer:272–318](https://github.com/SaitoTech/saito-lite-rust/blob/7200a9886f3dd02f66e8d6c146960e2a5402929f/mods/redsquare/lib/post.js#L272),
[H nested reconstruction:151–175](https://github.com/SaitoTech/saito-lite-rust/blob/7200a9886f3dd02f66e8d6c146960e2a5402929f/mods/redsquare/lib/tweet.js#L151),
[H rendering:464–469](https://github.com/SaitoTech/saito-lite-rust/blob/7200a9886f3dd02f66e8d6c146960e2a5402929f/mods/redsquare/lib/tweet.js#L464),
[A retweet fields:577–591](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/ui/overlays/compose.js#L577),
[B receiver:1479 onward](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/redsquare.js#L1479),
[B notification:62–71,137–150](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/lib/notification.js#L62),
[A embedded normalization:240–268](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/tweet.js#L240),
[B nested template:120–127](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/lib/tweet.template.js#L120).
See [the complete quote contract](NATIVE_QUOTES_CONTRACT.md) for authentication,
indexing and the source-inferred strategy's runtime verification limits.

A and B galleries have identical source: accept nonblank strings whose trimmed values lack
whitespace/angle brackets, raster `data:image/` except SVG, HTTP/HTTPS and
single-slash root-relative paths. Reject other schemes and protocol-relative
URLs, escape HTML attributes, lazy-load `<img>`, display first four safe entries.
These are URL-safety checks, not MIME/content/byte validation or pixel bounds.
H's older template directly inserts every array element in `<img src>` and opens
an image overlay. Native should not copy H's permissive interpolation.
[A gallery:15–47](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/tweet-gallery.template.js#L15),
[B gallery:15–47](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/lib/tweet-gallery.template.js#L15),
[H template:13–19](https://github.com/SaitoTech/saito-lite-rust/blob/7200a9886f3dd02f66e8d6c146960e2a5402929f/mods/redsquare/lib/image.template.js#L13),
[H overlay:73–84](https://github.com/SaitoTech/saito-lite-rust/blob/7200a9886f3dd02f66e8d6c146960e2a5402929f/mods/redsquare/lib/image.js#L73).

The browser uses the signed string directly: valid remote GIFs can animate in
`<img>`; remote availability is not authenticated by the post signature.
Root-relative sources need resolution against the selected backend origin.
`/<redsquare-slug>/og-image/<encoded-signature>` is a **social preview** route:
it extracts only `data.images[0]` if a matching Base64 data URI, otherwise uses
an identicon; absent lookup redirects to the default social image. Its extraction
regex accepts `png`, `jpg/jpeg`, `gif`, `webp`, `svg+xml`, and `png+xml` MIME
suffixes (the last maps to `image/png`); that is not the gallery safety policy.
The legacy
`?og_img_sig=` redirects there. It is not an all-attachments service or a proxy
for remote GIF URLs, and must not replace the signed gallery content.
[A social URL:2779](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L2779),
[A extraction:2810–2855](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L2810),
[A routes:2890–2919](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L2890).

## Bounds: source facts versus native policy

Four is a composer cap, including H. Browser compression targets **512 KiB of
complete data-URL characters**, not JPEG bytes; it returns above target when
compression stops shrinking. Ordinary A/B constructor/parser copy the complete
array; their receive path has no attachment-specific byte/count rejection.
Archive 50,000-character threshold spills envelopes to files rather than rejects
them. Neither these paths nor gallery filtering establish universal incoming
image/transaction limits.
[H cap:90](https://github.com/SaitoTech/saito-lite-rust/blob/7200a9886f3dd02f66e8d6c146960e2a5402929f/mods/redsquare/lib/post.js#L90),
[A constructor:1122](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1122),
[A parser:203–207](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/tweet.js#L203),
[A receive:1505](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1505),
[A spill:455,531](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/archive/archive.js#L455).

Current native source observed during research has a **1 MiB signed-message**
limit in creation and restoration, **2 MiB encoded transaction** restore limit,
and **5 MiB encrypted draft plaintext** limit. Four web-target attachments alone
approach 2 MiB message text. Quotes additionally duplicate original images in
Base64 `retweet_tx` and `embedded`. Per-image caps therefore cannot establish
whole-message or durable-record safety.
[transaction bounds](../../core/src/main/kotlin/nl/davidengelen/redsquare/saito/SaitoTransaction.kt),
[draft bounds](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/PostDraftStore.kt).

Concrete compatible **implementation policy**, not upstream protocol maxima:

- At most four outgoing images; normalize PNG/JPEG/WebP/local GIF to JPEG,
  aspect-fit inside 1920×1024. A strict 512-KiB complete data-URL cap corresponds
  to at most **393,198 JPEG bytes** (`23 + 4*ceil(bytes/3) <= 524288`). A simpler
  **380-KiB JPEG cap** leaves margin. Do not interpret 512 KiB as raw JPEG bytes.
- Enforce the whole final UTF-8 JSON message against the native limit, including
  text and both quote representations. Compress/downscale own images to the
  remaining budget; measure actual final bytes. Raising the limit requires
  coordinated create/restore/envelope/queue/cache bounds. Never truncate or
  recompress the independently signed original to make a quote fit.
- One concrete ingest guard is **32 MiB source bytes**, maximum **16,384** on
  either source axis and **100 million source pixels** (wide integer arithmetic),
  then sampled intermediate at most **4 million pixels** before final resize.
  These are agent-selected resource limits, not vanilla limits; they admit
  ordinary Pixel 7 photos without full-resolution bitmap allocation.
- Render at most four safe entries; cap each incoming raster compressed body,
  for example **2 MiB**, before decode, and sample against the display target.
  Keep message-envelope caps independent. Unsupported/oversized/unavailable
  media gets a placeholder while verified text and other images remain visible.
  Apply timeouts and byte bounds to remote responses, not just inline data URIs.

## Android picker, processing and offline durability

Use Kotlin `PickMultipleVisualMedia` with `ImageOnly`, requesting remaining
slots; use single-select when only one slot remains. AndroidX falls back to
`ACTION_OPEN_DOCUMENT`, covering Android 10 devices without a picker. The
fallback ignores the requested maximum, so enforce four in the result callback.
The picker grants selected-media access without broad-library permission.
Default grants expire across app stop/reboot; use
`takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)` if ingestion
must outlive that grant. Cloud-provider selection can need connectivity; a URI
alone does not make a draft offline-ready.
[official photo picker](https://developer.android.com/training/data-storage/shared/photo-picker).

On a worker dispatcher, consume a bounded stream rather than trusting provider
MIME/declared length. Inspect actual format/dimensions with
`BitmapFactory.Options.inJustDecodeBounds`; choose `inSampleSize` before pixel
allocation. Reject invalid dimensions and overflow, close streams, and process
one large image at a time. JPEG compression quality is a hint, not a guaranteed
size, so bound output and retry finitely with lower quality/dimensions.
[Android bitmap bounds/sampling](https://developer.android.com/topic/performance/graphics/load-bitmap),
[Bitmap.compress](https://developer.android.com/reference/android/graphics/Bitmap#compress(android.graphics.Bitmap.CompressFormat,int,java.io.OutputStream)).

Account for orientation including EXIF mirroring before final encoding.
`ExifInterface.getRotationDegrees` and `isFlipped` expose this mapping. Encode a
new pixel image without copying original EXIF/GPS metadata; metadata removal is
an implementation requirement to verify, not a runtime result of this research.
Explicitly flatten alpha to the intended background for JPEG.
[ExifInterface orientation](https://developer.android.com/reference/androidx/exifinterface/media/ExifInterface#getRotationDegrees()).

Persist normalized bytes/data URLs with the unsent draft **before** presenting
it as recoverable, rather than storing only picker URI references. Current
`PostDraftStore.Saved` contains text and optional signed transaction, not an
unsigned image selection. Extend that durable representation while preserving
wallet/backend/thread scope. Once signed/queued, retain exact transaction bytes
for retry; replacing images creates a different candidate.
[current draft schema and persistence](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/PostDraftStore.kt).

The existing AES-256-GCM Android Keystore, filename-associated data,
`AtomicFile`, fsync/reopen validation and `noBackupFilesDir` pattern is suitable
for unpublished media. Internal persistent files need no storage permission;
cache files may be evicted and are unsuitable as the only copy of queued media.
Scope encrypted attachments to the same wallet/backend as their drafts and
include them in replacement/cancellation/recovery logic; authenticate and bound
reads before exposing them. Do not replace an unavailable key with a fresh key
while old encrypted attachments exist.
[Android app-specific storage](https://developer.android.com/training/data-storage/app-specific),
[Android Keystore](https://developer.android.com/privacy-and-security/keystore),
[existing authenticated draft storage](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/PostDraftStore.kt).

This establishes source/API compatibility, not successful network publication,
visual cross-client proof, offline process recovery with media, or a measured
performance result. Those require #15 synthetic fixture and emulator verification
under the authorized implementation workflow; this research performed none.

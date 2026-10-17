# Giphy source preparation, 2026-10-17

Bounded research for open[#16](https://github.com/robelene112/redsquare-android/issues/16)
and parent[#1](https://github.com/robelene112/redsquare-android/issues/1), while the
sole implementation owner completes#17. Read the
[October15 requirements](GIPHY_ANDROID_REQUIREMENTS_2026_10_15.md),
[native media contract](NATIVE_GIF_VIDEO_CONTRACT.md) and current composer/image
seams. Only this note was written. No provider searches, keys, accounts, node
state, application edits, builds or CI were used. The owner's existing key
question remains pending.

## Finding

Missing live Android authorization blocks provider acceptance, not all useful
source work. A native GIF entry point, picker state/recovery, exact selected-URL
attachment, animation and controlled-peer transaction verification can be built
without calling Giphy. Keep missing configuration explicit. Do not initialize
the SDK with a borrowed/example key or present controlled results as live Giphy
acceptance. No new approval requirement for ordinary source work is inferred.

## Current SDK facts and source limits

Official main remains`8c690ab2d0651f5a7a9177d519aaf424ce2dcb6c`.
[Latest release](https://github.com/Giphy/giphy-android-sdk/releases/tag/v2.5.3)
and[public Maven metadata](https://repo.maven.apache.org/maven2/com/giphy/sdk/ui/maven-metadata.xml)
still identify`com.giphy.sdk:ui:2.5.3`; metadata lastUpdated`20260909125159`.
This is not a dependency upgrade or compatibility-build result.

The[Android guide](https://github.com/Giphy/giphy-android-sdk/blob/8c690ab2d0651f5a7a9177d519aaf424ce2dcb6c/Docs.md)
requires AndroidX, min19, compile35+, Kotlin2.1+ and an **Android SDK key**, distinct
per platform. It provides a DialogFragment picker, grid and Compose
`GiphyDialogView` prototype. Selection supplies `Media`; dismissal is separate.
The guide warns a programmatically assigned fragment listener is lost during
process recreation unless restored through its supported host arrangement.
It recommends media IDs for messaging and documents ID lookup. Fresco manages
asset caching, with configurable cache settings; recents are another SDK feature.
This does not authorize a separate application search catalogue/media cache.

The[published AAR](https://repo.maven.apache.org/maven2/com/giphy/sdk/ui/2.5.3/ui-2.5.3.aar)
was inspected in memory without loading Android or initializing the SDK:
1,129,437bytes, SHA256
`dad198b9cee35db65df3c77d6cce0acf42f10160077a555bf7c9ca48190fc599`.
Class-file accessors confirm the Kotlin mapping:

| Needed value | Published accessor path |
| --- | --- |
| Selected ID | `media.id` |
| Original GIF URL | `media.images.original.gifUrl` |
| Creator attribution | `media.user`, with `attributionDisplayName`, `username`, `profileUrl` |
| Source/Giphy links | `media.source`, `sourcePostUrl`, `url` |

There is no demonstrated `originalURL` property. These accessor names establish
availability, not non-null/live values. `configure` references `initGiphy`; its
invocation inventory includes Fresco initialization, core configuration, tracking
version setup and recents construction. It is not a harmless pure assignment.
No complete control-flow/network trace or zero-network guarantee was established.
The public repo supplies docs/examples; the inspected sources-JAR URL returned404.

The[published POM](https://repo.maven.apache.org/maven2/com/giphy/sdk/ui/2.5.3/ui-2.5.3.pom)
includes Fresco3.7.0, coroutines1.11.0, Gson2.14.0, AppCompat1.7.1 and
viewbinding8.13.2. Adding it can change dependency resolution beyond a picker.
Do not infer successful compatibility from meeting the documented minimums.

The[pinned Compose example](https://github.com/Giphy/giphy-android-sdk/blob/8c690ab2d0651f5a7a9177d519aaf424ce2dcb6c/app/src/main/java/com/giphy/sdk/uidemo/ComposeActivity.kt)
creates/configures `GiphyDialogView` in `AndroidView` and handles its listener.
Its separate100MiB demo `VideoCache` is not a requirement for GIF integration and
should not be copied. Example verification mode is not proof of offline or
keyless SDK operation. This app's current `MainActivity` is `ComponentActivity`,
so DialogFragment is not a drop-in host; the Compose view avoids that particular
host change but still needs lifecycle/disposal/recreation verification.

## Direct API comparison

The[API guide](https://developers.giphy.com/docs/api/)
requires a key per platform/section, client-side Search/Trending and unchanged
returned URLs/query parameters. It requires visible Powered By GIPHY branding,
preserved result order and provider separation; it prohibits unapproved custom
URL/asset caches and proxies. Beta keys have100calls/hour. A direct native picker
owns loading/error/empty/retry, debouncing, stale-response cancellation and
pagination, while the SDK supplies much of that UI. Neither route removes the
authorized-key prerequisite. A key's actual permitted API/SDK use was not checked.

The[API schema](https://developers.giphy.com/docs/api/schema/)
provides GIF ID, images, user/source metadata and rendition URLs, so direct API
results can map to the same selected-reference contract. The SDK accessor
`gifUrl` corresponds to the original rendition's GIF URL, not the GIF's Giphy
detail page, MP4 or WebP URL. Keep missing creator metadata optional.

Recommendation: prepare the common native attachment/recovery path first. A
small direct-API picker fits current Compose/controlled-HTTP seams without an
Activity conversion or Fresco dependency; SDK UI remains feasible with an
authorized SDK key and separately observed runtime behavior. Choose the concrete
provider adapter deliberately, rather than initialize both or invent a generic
provider framework. The earlier note's uncertainty about immutable selected
message-reference retention remains unchanged; it proves neither permission nor
prohibition and does not require another owner question now.

## Current native seams

- `PostComposerScreen` already shares new-post/reply/quote behavior and identifies
  a session by owner, backend, parent/quote and endpoint. `PostComposerState`
  persists authored work before external image selection and serializes writes.
  Add a separate selected-GIF-URL append operation using that lifecycle. Recheck
  the active session when an asynchronous selection returns.
- `addImages()` is for document URIs and calls `PostImagePreparation.normalize`,
  which converts accepted local GIF input into JPEG. Do not route provider GIFs
  through it. Keep `data.images` as the exact selected original URL, including
  query parameters, within the existing four-attachment and message-size limits.
- `PostDraftStore` and `RedSquarePosts.message` already carry image strings into
  identity/backend-bound drafts and signed work. Selected ID/attribution can be
  local presentation metadata without changing the established signed URL.
  Do not replace the signed URL with an SDK ID-only message format.
- `PostImageLoader` currently decodes a **Bitmap** into a bounded reusable LRU;
  `PostImages` displays static images. GIFs need an animated display path that
  bypasses that generic cache, preserves fallback text/retry, and stops on
  disposal/background. Transient bytes needed for one decode are not a proposed
  reusable media/search cache. SDK-managed behavior must remain distinguishable
  from custom app caching.

## Minimal proposed tracer, not executed

Use the accepted native-UI boundary plus controlled HTTP/Saito peers:

1. Open a quote composer, author text and a local attachment, then open GIF search.
   Controlled provider HTTP first returns an ordinary failure. Observe Retry and
   Cancel while authored work remains. A retry returns an owned synthetic
   animated GIF, original URL with a query, ID and attribution. Drive actual
   picker UI selection, not direct mutation of composer state.
2. Observe attribution and animation through preview/viewer. Background/reopen,
   dismiss/cancel and reconstruct the process through existing recovery helpers;
   confirm authored text/attachments persist and late results cannot attach to
   another wallet/backend/composer session. Start with the smallest complete
   journey, then expand only for uncovered behavior.
3. Send through the controlled selected Saito peer. Independently inspect its
   signed emitted original URL and quote structure, then read the resulting
   controlled feed. Preserve existing reply/new-post and four-attachment rules.
   Reuse `ImageSelectionTestUi`, image recovery and quote fixture conventions.

Controlled provider HTTP is a test boundary for a direct adapter, not evidence
that the SDK offers an undocumented endpoint override. Production remains fixed
to approved Giphy endpoints; test routing belongs only to the established debug
fixture arrangement. Use owned GIF bytes and fake fixture credentials, never a
public SDK example key. No bespoke catalogue or offline GIF store is needed.

This can establish native state, rendering and Saito compatibility. It cannot
establish live authorized search, SDK initialization/network/cache behavior,
provider quotas, actual returned attribution/removal behavior or full#16/#31
acceptance. Those gates remain explicit after source preparation.

## Direct API details for the first implementation

Follow-up documentation check only, with no provider endpoint/query/key call.
Decision recommendation remains **direct API plus the existing Compose host**,
an optional authorized Android/section key, and a dedicated transient animated
renderer. Leave SDK initialization and its dependency/cache lifecycle out of
this first source slice. This is an implementation recommendation, not live
provider acceptance or a change to#16's scope.

| GET path on `https://api.giphy.com` | Exact request details |
| --- | --- |
| `/v1/gifs/search` | Required`api_key`, `q`; query maximum50characters. Send the user's exact terms, encoded once. Default`limit=25`; beta maximum50. Default`offset=0`, maximum4999. Optional`lang` is a two-letter ISO639-1 code. |
| `/v1/gifs/trending` | Required`api_key`; default`limit=25`, default`offset=0`, maximum499. No search query needed. |

Both support explicit `rating=g`, `pg`, **`pg-13`** or`r`. SDK enum`pg13` is not
the direct API spelling. Omitted rating permits all ratings. A24-result page and
400ms debounce are native choices. Do not silently shorten or autocorrect a
long query; show a local limit message. The docs do not define Unicode counting
units. Blank-query Trending is our UI choice. Terms containing`sticker`,
`stickers` or`transparent` can return sticker content even through GIF Search.
[Search/Trending parameter tables](https://developers.giphy.com/docs/api/#search-endpoint).

Successful Search/Trending JSON has `data` array, `pagination` and `meta`.
Read these exact fields:

| JSON path | Role |
| --- | --- |
| `data[].id` | Provider content identity. |
| `data[].images.original.url` | Original GIF URL for the unchanged signed Saito attachment. |
| `data[].images.fixed_width_small.url` | Smaller grid rendition; use other returned renditions as needed without editing URLs. |
| `data[].title`, `alt_text` | Display/accessibility descriptions. |
| `data[].user.display_name`, `user.username`, `user.profile_url` | Available creator label/profile. |
| `data[].username`, `source`, `source_post_url` | Additional available user/source attribution. |
| `data[].url` | Giphy detail page, not the original GIF image URL. |
| `pagination.offset`, `count`, `total_count` | Returned paging position/count; total may be absent. |
| `meta.status`, `msg`, `response_id` | API outcome and response identity. |

Not every rendition/user property is present. Preserve result order rather than
using a successful parse as permission to reorder. Proposed pagination advances
from returned`offset+count`, stops on zero results/known total/endpoint offset cap,
and never repeats an unchanged page indefinitely. This is native paging policy,
not a provider promise of a fixed catalogue or permanent IDs.
[GIF/images/user/pagination/meta schema](https://developers.giphy.com/docs/api/schema/).

Provider response codes are200success;400missing/invalid parameters;
401authentication/key missing or invalid;403unauthorized/key issue;
404missing requested content;414query longer than50characters;429rate limit.
The response-code page describes codes inside`meta`, so do not treat an HTTP200
with non-success metadata as valid results. Distinguish an empty successful page
from an error. Display a recoverable error without discarding composer state;
avoid automatic rapid retries, especially429. No Giphy-specific `Retry-After`
format, reset interval or503retry schedule was established here.
[Response codes](https://developers.giphy.com/docs/api/response-codes/).

**Presentation requirement:** conspicuous Powered By GIPHY words/logo where the
API is used, plus user and/or source attribution whenever available. Merely
retaining those fields in an internal object is insufficient. A creator label
and accessible profile/source action in the picker/selection presentation is a
concrete implementation; the API does not require displaying every JSON field.
`id`, thumbnail and title alone do not replace available creator/source credits.
Use the provider's approved mark rather than an invented logo.
[Branding and attribution, sections4–5](https://support.giphy.com/hc/en-us/articles/360028134111-GIPHY-API-Terms-of-Service).

## Native decoder contract checked for the selection increment

On2026-10-17, current official Android references confirm
[ImageDecoder ByteBuffer sources](https://developer.android.com/reference/android/graphics/ImageDecoder#createSource(java.nio.ByteBuffer))
are available since API28. An animated drawable continues reading that buffer;
keep it immutable. Target sizing belongs in the header callback.
[AnimatedImageDrawable](https://developer.android.com/reference/android/graphics/drawable/AnimatedImageDrawable)
requires explicit start; stop retains the displayed frame and a later start
resets the animation. Framework frame decoding runs on another thread.

The current source increment chooses8MiB encoded bytes,512pixel longest edge,
two concurrent download/decode slots and visible-view lifecycle ownership.
These are app resource policies, not provider limits or proof of Android
runtime behavior. No custom asset/result store is added. A static owned thumbnail
and independently ImageIO-decoded two-frame original provide distinct native
pixel oracles; only the actual device run can establish those frames display.


The actual selection screenshots exposed native CENTER_CROP overdraw. Current
[AOSP ImageView source](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/widget/ImageView.java)
initializes crop-to-padding false and clips the canvas only when enabled;
CENTER_CROP can scale beyond the view. The [documented setter](https://developer.android.com/reference/android/widget/ImageView#setCropToPadding(boolean))
is available since API16. Setting it true with zero padding is the small native
correction. Source explains the observed geometry; the corrected native boundary
run must establish the actual result. No provider URL or scaling policy change.

# Native Giphy and YouTube contract

Read-only research for #16/#17, 2026-10-10. Read existing media/image/quote/
protocol notes and cached public RedSquare sources; fetched pinned Saito Giphy
and network files plus current official Giphy, YouTube and Android docs. No node,
configuration, wallet/database, app initialization, build, real credential
request, live transaction or production write. Only this document was created.

A = official [`saito@9a4ca7e`](https://github.com/SaitoTech/saito/commit/9a4ca7e9442eb5e872168ced361ce741d7d139d7);
B = official [`saito@214504ec`](https://github.com/SaitoTech/saito/commit/214504ecece76d1cd20a245fe4468eccbfca2951);
H = historical [`saito-lite-rust@7200a988`](https://github.com/SaitoTech/saito-lite-rust/commit/7200a9886f3dd02f66e8d6c146960e2a5402929f).
These source baselines do not prove current service/key availability at either
`saito.davidengelen.nl` or `saito.io`.

## Existing Saito GIF path

1. RedSquare discovers the first module responding to `giphy`, invokes
   `renderInto(container, callback)`, and adds the returned string directly to
   its images array. The shared cap is four local images/GIFs combined.
   [A composer:367–407](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/ui/overlays/compose.js#L367).
2. A/B Giphy advertises peer service `giphy` on a server, **without checking**
   whether a key exists. Browser service discovery sends
   `{request:"get giphy auth",data:{}}` to that peer. The handler returns
   `process.env.GIPHY_KEY` through the callback. The browser instantiates
   `GiphyFetch(auth)` and calls Giphy directly; Saito is not a search/media proxy.
   [A discovery:96–123](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/giphy/giphy.js#L96),
   [B key handler:239–256](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/giphy/giphy.js#L239).
3. Despite its name, `sendRequestAsTransaction` delegates to the peer-request
   wrapper, not chain broadcast. The wrapper packs request JSON in a zero-value
   transaction and signs it only when explicitly requested; Giphy does not ask
   for that signature. The authenticated peer handshake is separate. Native
   transport can use existing application request type12/request-index framing;
   callback type13 contains JSON, here a **JSON string scalar**, not archive rows.
   Missing/undefined server keys may fail callback serialization or time out.
   [network delegation:67–80](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/network.ts#L67),
   [request wrapper:430–463](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-js/saito.ts#L430),
   [callback JSON serialization:313–326](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-js/shared_methods.browser.ts#L313).
4. Both A/B initially search **`inception`**, not Trending. Typing triggers a
   search on every input, with `limit:24,offset:0`; there is no debounce or
   pagination in this implementation. Thumbnails choose `fixed_width_small.url`,
   then `fixed_width.url`, then `original.url`. Selection returns exactly
   `gif.images.original.url`; it is not fetched/converted to JPEG. Errors and
   empty results have distinct labels. H instead used `renderGrid` with search
   offsets, so pagination exists in that historical implementation.
   [B search/selection:171–236](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/giphy/giphy.js#L171),
   [H offset searches:183–195](https://github.com/SaitoTech/saito-lite-rust/blob/7200a9886f3dd02f66e8d6c146960e2a5402929f/mods/giphy/giphy.js#L183).

The resulting URL is signed in `data.images` for posts/replies and quote
commentary; preserve [the quote strategy](NATIVE_QUOTES_CONTRACT.md). Source
has an unused `toDataURL` helper; its presence does not mean selection embeds
GIF bytes. A/B's Giphy template contains no provider attribution element and its
search handler logs typed terms; those are observations, not practices to copy.
[A helper:142–157](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/giphy/giphy.js#L142),
[A template:1–12](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/giphy/lib/giphy.template.js#L1),
[B input:221–224](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/giphy/giphy.js#L221).

## Provider contract and credentials

Giphy requires separate platform/section keys; beta keys allow 100 API calls per
hour, with production upgrade review/pricing. Both endpoints require `api_key`:

| Direct HTTPS GET | Parameters and response |
| --- | --- |
| `https://api.giphy.com/v1/gifs/search` | Exact user query `q` (maximum50 characters), `limit`, `offset` (maximum4999); optional `rating`/`lang`. |
| `https://api.giphy.com/v1/gifs/trending` | `limit`, `offset` (maximum499); optional `rating`. Empty-query Trending is a native recommendation, not A/B behavior. |

Responses contain `data`, `pagination`, `meta`. Omitted rating permits all
ratings. Make API/media calls directly from the client; preserve returned URLs
and query parameters. Standard integrations forbid custom media/URL caching,
proxying, result reordering/filtering or mixed-provider grids without appropriate
approval. Analytics calls are recommended, not described here as universally
mandatory. [official API contract](https://developers.giphy.com/docs/api/).

Display the approved Powered By GIPHY mark/logo and available creator/source
attribution; retain GIF ID and attribution from the response rather than only
its thumbnail. [official API attribution terms](https://support.giphy.com/hc/en-us/articles/360028134111-GIPHY-API-Terms-of-Service).
Direct searches/media views expose network/request data to Giphy; its policy
lists IP, device identifiers, queries and cookies. Search terms need not go to
Saito or onto chain. Do not send wallet identity as `customer_id`, upload wallet
keys, or log query/key-bearing URLs. These are implementation recommendations.
[provider privacy policy](https://support.giphy.com/hc/en-us/articles/360032872931-GIPHY-Privacy-Policy).

The official Android SDK offers picker/grid/Compose integration and animated
media views. Current docs specify SDK2.5.3, min19, compile35+, Kotlin2.1+, and
an **Android SDK key**; current app min29/compile36/Kotlin2.1.20 meets these
listed prerequisites. Its docs recommend media IDs for messaging and describe
Fresco caching, while current API docs prohibit unapproved custom caching.
Do not infer our generic image disk cache is approved from that SDK behavior.
[official SDK docs](https://github.com/Giphy/giphy-android-sdk/blob/main/Docs.md),
[app build](../../app/build.gradle.kts), [Kotlin version](../../build.gradle.kts).

Recommended native integration: an explicit Giphy picker, direct API plus
bounded Android GIF rendering (or official SDK with its own appropriate key),
400ms debounced exact queries, cancellation of stale results, 24-result pages,
visible attribution and Retry. Validate HTTPS media host boundaries and bytes
without rewriting accepted URLs; e.g. allow `giphy.com`/its real subdomains,
reject credentials/nondefault ports and misleading suffix hosts. Preserve API
ordering. Resource caps are native policy, not guarantees of GIF size.
`ImageDecoder.decodeDrawable` supports animated GIF/WebP; use API28-compatible
ByteBuffer source, bounded download, sampled target and stop/start on lifecycle.
Do not use API31-only byte-array overloads on Android10.
[Android animated decoding](https://developer.android.com/reference/android/graphics/ImageDecoder).

For both backends, use the same signed URL format. An app-owned authorized
Android key avoids dependence on either peer's web key and needs no backend
change. Alternatively use selected-peer discovery/auth **only after** the key
holder confirms Android use is authorized. Type9 service records are
`service|domain|name` separated by semicolons; current native peer requests
services but does not expose those records. Implementing that reader is agent
work, not a user configuration question.
[service encoding](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/network/service.rs#L42),
[current native peer](../../app/src/main/kotlin/nl/davidengelen/redsquare/saito/SaitoPeer.kt).

**External dependency:** a valid Android-authorized Giphy key/key holder and,
for volume beyond beta allowance, production approval. No real key or either
backend's key status was inspected. Advertised service alone cannot prove it.
Implement unavailable/loading/empty/rate-limit/error states without claiming a
working live search. Keep selected GIF references in encrypted drafts/queued
signed candidates, but do not promise offline GIF assets or add a durable custom
Giphy search/media cache. Vanilla signed URL retention versus current caching
terms needs the authorized integration holder's clarification; no exception is
established by this research. Never switch to the other backend silently to
obtain credentials.

## Existing YouTube link behavior

A/B prefer signed `data.link`. Otherwise their Browser helper selects the last
recognized non-numeric URL in post text, permits omitted HTTP(S) schemes and
normalizes the selected URL before Tweet extraction. They recognize watch
`v=`, youtu.be, `/shorts/` and `/live/`, strip query/fragment from the ID and
render `https://www.youtube.com/embed/<id>` in an iframe. The extractor uses
substring host checks and does not enforce ID length; native should parse real
host boundaries and require `[A-Za-z0-9_-]{11}`. Generic Open Graph video links
are not this playback path; direct video-file upload is not established.
[A Browser helper:2001](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/browser.ts#L2001),
[B Browser helper:2022](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/lib/saito/browser.ts#L2022),
[A extraction:306–357](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/tweet.js#L306),
[B iframe:109–113](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/lib/tweet.template.js#L109).

## Android embedded playback

Use the official IFrame Player API inside a narrowly scoped Android WebView.
Google's Android guide endorses this; the old Android Player API was deprecated
in2023 and is no longer available. Ordinary documented embedding needs no
YouTube Data API lookup/key or owner/backend credentials.
[official Android guide](https://developers.google.com/youtube/android),
[deprecation history](https://developers.google.com/youtube/terms/revision-history),
[embed parameters](https://developers.google.com/youtube/player_parameters).

Current requirements demand `HTTP Referer` identifying the **actual installed
app ID**, including sideloaded apps. For app-owned local HTML use
`loadDataWithBaseURL("https://" + context.packageName, ...)`; set the same origin
in IFrame API parameters. Release identity is
`https://nl.davidengelen.redsquare`, debug includes `.dev`. This synthetic
base origin does not require hosting a website there. Direct embed loading can
instead set Referer through `loadUrl(url, additionalHttpHeaders)`. Do not use a
file/data/null origin or disguise the app as saito.io. Preserve controls/branding,
minimum200×200 viewport, and no overlays obscuring the active player. Very
high-volume clients may require additional credentials.
[required identity and playback rules](https://developers.google.com/youtube/terms/required-minimum-functionality#embedded-player-api-client-identity),
[Android base URL behavior](https://developer.android.com/reference/android/webkit/WebView#loadDataWithBaseURL(java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String)).

**Tap-only recommendation:** show a native link/video card first; create the
WebView/player only after an explicit tap. Use `autoplay=0`, keep
`mediaPlaybackRequiresUserGesture=true`, cue rather than load-and-play in
`onReady`, and let the user press the player's controls. Never call `playVideo`
on feed entry/refresh/restore. Pause/destroy when leaving playback/background.
This may take a card tap plus player tap, but makes no autoplay promise dependent
on browser gesture propagation. JavaScript is required for the player, not for
wallet/UI logic; retain native feed and compose screens.
[player controls](https://developers.google.com/youtube/iframe_api_reference),
[autoplay/origin parameters](https://developers.google.com/youtube/player_parameters),
[WebView gesture gate](https://developer.android.com/reference/android/webkit/WebSettings#setMediaPlaybackRequiresUserGesture(boolean)).

Current IFrame documentation also describes automatic Android WebView Media
Integrity integration: the provider can receive app identity/version/signing
certificate metadata and a Google Play services attestation token. This does
not involve Saito wallet keys. Record provider data handling in release privacy
work; a controlled replacement API cannot prove real provider attestation or
media playback. Rechecked2026-10-10 against the
[official integration section](https://developers.google.com/youtube/iframe_api_reference#android-webview-media-integrity-api-integration).

Validate schemes/hosts before deriving IDs; allow genuine youtube.com,
www.youtube.com, m.youtube.com, music.youtube.com and youtu.be links, then build
a fixed HTTPS embed URL yourself. Never inject arbitrary post HTML/URL into a
JS-enabled WebView. Disable file/content access and mixed content; validate
main-frame navigation, sending unrelated user-opened links to the external
browser. Permit legitimate HTTPS player subresources; an indiscriminate small
CDN allowlist can itself break playback. If forwarding player error events,
use origin-scoped, main-frame-only messages with a tiny typed schema and expose
no wallet/files/network capabilities.
[Android bridge/navigation guidance](https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges),
[WebView integration/lifecycle hooks](https://developer.android.com/develop/ui/views/layout/webapps/webview).

Handle player error2 invalid ID,5 HTML5 failure,100 unavailable/private,
101/150 owner-disallowed embed,153 missing client identity, and network timeout
separately from invalid post content. Keep text visible, offer Retry and a
canonical Open in YouTube link; do not report every failure as offline.
[official errors](https://developers.google.com/youtube/iframe_api_reference#onError).
Privacy-enhanced `youtube-nocookie.com` is documented for WebView embeds, but
is not zero-data playback and does not waive Referer requirements. Android
WebView media integrity can automatically provide package/version/certificate
and Play-services attestation; AndroidX exposes feature-gated configuration and
opt-out. Do not assume sideloaded/emulator/Play-services-free devices behave
identically without runtime verification.
[privacy-enhanced embedding](https://support.google.com/youtube/answer/171780),
[YouTube integrity behavior](https://developers.google.com/youtube/iframe_api_reference#android-webview-media-integrity-api-integration),
[AndroidX integrity configuration](https://developer.android.com/reference/androidx/webkit/WebViewMediaIntegrityApiStatusConfig).

No Saito backend adapter or block production is indicated for either media
format. Live authorized GIF search, provider/key status, YouTube playback,
physical-device compatibility and tap/lifecycle behavior remain runtime
verification work; this document establishes none of those results.

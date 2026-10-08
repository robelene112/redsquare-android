# Red Square content bounds

Read-only source research, 2026-10-08, for native feed decoding and cache memory.
Inspected `/opt/saito/node` on netcup through a network-disabled, read-only
runner. The twelve relevant RedSquare, browser, Archive and transaction files
matched repository root `/opt/saito` commit
[`9a4ca7e`](https://github.com/SaitoTech/saito/commit/9a4ca7e9442eb5e872168ced361ce741d7d139d7)
byte for byte. Compared selected public static files from inspected official
`prod` commit
[`214504ec`](https://github.com/SaitoTech/saito/commit/214504ecece76d1cd20a245fe4468eccbfca2951).
These identify source revisions, not a newly verified served bundle or runtime.
See [version provenance](SAITO_VERSION_PROVENANCE.md). No application
initialization, node state, wallet, database, configuration or service changes.

## Sender limits are not universal receive limits

| Content | Source-defined behavior | Enforcement and qualification |
| --- | --- | --- |
| Post/reply/edit/retweet commentary text | Default composer threshold **500 JavaScript string units**. Counter uses untrimmed text; submission checks trimmed text. | Above 500, submission is blocked only when `Number(getPendingBalance()) == 0`. Funded accounts can submit longer text; this branch has no further maximum. JavaScript `.length` counts UTF-16 units, not UTF-8 bytes or visible characters. |
| Images | Composer checks **4** before upload and before accepting GIF picker results. | UI policy. The general transaction constructor and ordinary Tweet parser copy the entire images array. The gallery filters valid source strings and renders its first four; embedded previews normalize to four. This does not bound signed incoming message bytes. |
| Uploaded image resize | Default dimension rectangle **1920 × 1024**, preserving aspect ratio; JPEG output. Target **512 × 1024 data-URL characters** per image, including prefix and base64. | Compression repeats while above target and still shrinking; if size stops decreasing it returns anyway. Therefore 512 KiB is a target, not a guaranteed payload cap. Local GIF uploads become a JPEG still. |
| GIF picker images | Picker result is inserted directly; strings containing `giphy.gif` bypass local resize too. | No image download, encoded byte-size or decoded pixel-dimension cap in these composer paths. A short remote URL does not bound the remote image response. |

Primary sources:
[composer constants](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/ui/overlays/compose.js#L4),
[text count](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/ui/overlays/compose.js#L434),
[funded long-text path](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/ui/overlays/compose.js#L620),
[upload/resize](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/ui/overlays/compose.js#L473),
[GIF picker](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/ui/overlays/compose.js#L395),
[resize algorithm](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/browser.ts#L2286),
[transaction constructor](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1101),
[Tweet parser](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/tweet.js#L180),
[gallery](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/tweet-gallery.template.js#L15).

The inspected receive path constructs a Tweet, checks signature presence,
module/request identity and moderation, then saves it. It does **not** reapply
the composer 500-unit threshold, four-image rule, resize target or pending-balance
test. This is a claim about these module paths, not proof that consensus,
moderation, reverse proxies or every deployed server accepts unlimited content.
[Tweet validation](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/tweets.js#L3),
[receiver](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1505),
[archive save](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1338).

## Browser long-text presentation

Ordinary timeline/focused posts pass the full text through mention markup and
sanitization into a body div. Their body CSS wraps text; no ordinary-body
character truncation or expand-text control was found in the inspected RedSquare
templates, manager and CSS. The five-line clamp applies specifically to
**embedded tweet bodies**, and clips presentation without shortening their
stored text. The template's `expanded` mode refers to the detail identity header.
The footer's “Show more posts” concerns additional thread posts, not long-text
expansion. Therefore neither a 500-character feed preview nor a general five-line
post preview is established vanilla behavior by this source.
[full body template](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/tweet.template.js#L98),
[body renderer](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/tweet-body.template.js),
[normal body CSS](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/web/css/redsquare-tweet.css#L165),
[embedded clamp](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/web/css/redsquare-tweet.css#L448),
[footer](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/tweet-footer.template.js#L24).

## Archive and memory implications

Archive `tx_size` is serialized web-envelope string length. **50,000** is a
file-spill threshold, not rejection: a larger server-side transaction is saved
to a file and loaded back into the returned row. Archive load defaults to ten
rows; a supplied limit is clamped to **10–100**. This caps row count, not total
response bytes. Optional `tx_size_less_than` filtering exists, but is not an
automatic bound. There is no aggregate response-byte limit in this inspected
Archive load path.
[spill](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/archive/archive.js#L455),
[file storage](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/archive/archive.js#L531),
[row limit/filter](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/archive/archive.js#L712),
[file restoration](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/archive/archive.js#L871).

Image data URLs already contain base64; the web envelope's `m` base64-encodes
the whole signed message again. The binary transaction is detached into `t`
and optional metadata independently encoded into `opt`. Inference: four images
near the resize target alone produce roughly 2 MiB of data-URL text, then roughly
2.67 MiB in `m`, before row metadata/JSON overhead. A twenty-row page of such
posts can exceed 16 MiB. This is illustrative arithmetic, **not** a guaranteed
maximum: resizing is soft and other senders can bypass it. Parsing keeps wire
bytes, decoded messages, strings/JSON objects and retained cache data at different
times; a wire-size check is not a bound on peak heap or decoded bitmap memory.
[web serialization](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/transaction.ts#L315).

The current native decoder's 8 MiB envelope check and peer listener's 16 MiB
binary-message check are application acceptance limits, not upstream content
caps. OkHttp 5.4 reads all frames into its message buffer, inflates a compressed
message when applicable, then supplies the full ByteString to the listener.
Consequently the listener can reject an oversized response before application
JSON parsing, but **cannot prevent OkHttp's preceding message allocation**.
It must not be described as capping framework receive preallocation.
[native decoder](../../core/src/main/kotlin/nl/davidengelen/redsquare/saito/RedSquareFeed.kt),
[native peer](../../app/src/main/kotlin/nl/davidengelen/redsquare/saito/SaitoPeer.kt),
[OkHttp 5.4 reader](https://github.com/square/okhttp/blob/parent-5.4.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/ws/WebSocketReader.kt#L247).

The owner's static WebSocket server source does not specify `maxPayload`.
Its installed `ws` package reports 8.21.3, whose server default is 100 MiB
and whose receiver enforces it on incoming messages. That is an inbound node
transport bound; it does not cap outgoing Archive responses or native incoming
allocations. Server source has a known local modification, so this observation
is not a claim of whole-file identity or all-server runtime configuration.
[node server source](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/core/server.ts#L73),
[ws default](https://github.com/websockets/ws/blob/8.21.3/lib/websocket-server.js#L69),
[ws receive enforcement](https://github.com/websockets/ws/blob/8.21.3/lib/receiver.js#L438).

## Official prod comparison and limits of claims

Selected `214504ec` source retains the same 500-unit funded-account exception,
four-image composer policy, soft resize algorithm, full ordinary text rendering,
five-line embedded clamp, uncapped ordinary image-array parsing, Archive spill
threshold and 10–100 row limit. CSS/body line numbers moved; no new hard text or
image-byte cap was found in these compared paths.
[prod composer](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/lib/ui/overlays/compose.js#L633),
[prod resize](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/lib/saito/browser.ts#L2307),
[prod Tweet](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/lib/tweet.js#L180),
[prod embedded CSS](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/web/css/redsquare-tweet.css#L464),
[prod Archive](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/archive/archive.js#L532).

No funded/unfunded posting experiment, image-decoder measurement, live feed
size survey or current official served-bundle reinspection was performed.
Other signed fields, edit envelopes and unsigned optional metadata can add
size independently of visible text/images. These findings support explicit
native receive/cache policy; they do not establish a universal maximum valid
RedSquare post or justify silently treating an oversized page as empty history.

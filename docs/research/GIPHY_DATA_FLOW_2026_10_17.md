# Implemented GIF data flow, 2026-10-17

Source snapshot19e1083dbe38aa0bd2209e535df58a764d3ff654 for #16, supplementing
[the dated release inventory](PLAY_DATA_INVENTORY_2026_10_15.md). These are code
observations for release preparation. They do not establish provider retention,
a completed Play declaration, live-key authorization or Chat GIF integration.

| Boundary | Observed behavior |
| --- | --- |
| Search | [GiphyProvider](../../app/src/main/kotlin/nl/davidengelen/redsquare/media/GiphyProvider.kt) calls the fixed production Giphy API directly from Android. Search sends the exact typed query, section API key, limit24, offset0 and ratingpg-13; blank search uses Trending without q. No wallet identity, signing material or Saito request is included in that API request. |
| Search lifetime | [GiphyPicker](../../app/src/main/kotlin/nl/davidengelen/redsquare/media/GiphyPicker.kt) keeps queries/results in the visible composition, debounces edits and cancels replaced work. This code writes no search history/result catalogue and configures no Giphy SDK or analytics calls. |
| Displayed media | [GiphyImage](../../app/src/main/kotlin/nl/davidengelen/redsquare/media/GiphyImage.kt) fetches approved Giphy media URLs when the view is composed, including existing feed attachments. This can happen independently of opening Search and does not require its API key. Normal network requests expose connection/request data to their destination. Transient bytes/drawables are bounded and tied to view lifetime, without the generic raster LRU or disk-media cache. Returned URL/query values are preserved. |
| Unsent work | [PostDraftStore](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/PostDraftStore.kt) encrypts selected original URLs and available GIF ID/title/creator/source metadata with the wallet/backend draft. Selection alone makes no Saito transaction. Removing an attachment removes its draft metadata. |
| Explicit Send | The signed public RedSquare message contains selected original URLs in images, alongside authored text and existing references. Draft attribution fields are not added to that message. Search queries and the API key are not inserted by the signing path. Replies/quotes preserve their existing signed linkage. |
| Sent-reference presentation | [GiphyAttributionStore](../../app/src/main/kotlin/nl/davidengelen/redsquare/media/GiphyAttributionStore.kt) independently encrypts selected sent URL/ID/title/creator/source references per wallet before transmission can begin. It retains at most128 references/2MiB, evicting older references to fit. It stores neither provider result pages nor media bytes. It is shared across that wallet's backend views. No timed expiry, export or dedicated user clear-all is implemented for this store. |
| Source links | User taps on available creator/source controls dispatch Android ACTION_VIEW with that returned URL. Composer navigation first persists current work. The destination browser/app and its own handling are outside this source observation. |

The integration adds no Saito-side GIF proxy or provider-key discovery. The
production key is explicit per debug/release Posts build configuration. Current
review configuration has no authorized key, so Search reports unavailable;
controlled fixtures do not establish real provider behavior. Historical GIF
references can have no known local creator/source metadata, in which case the
app shows provider branding without inventing it.

The publisher still needs verified provider/operator and retention facts for the
release disclosures. This note supplies implementation facts, without inventing
those answers or changing the existing pending configuration question.

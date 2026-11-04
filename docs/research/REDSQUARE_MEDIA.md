# Existing Red Square media features

Read-only inspection of deployed static sources under `/opt/saito/node` on
2026-10-08. No wallet or database contents were read and no services were changed.

- Image attachments: the composer accepts PNG, JPEG, GIF and WebP and caps a
  post at four images. See `mods/redsquare/lib/ui/overlays/compose.js:4`,
  `compose.template.js:138`, and `mods/redsquare/redsquare.js:36`.
- Posts and replies populate `data.images` (`compose.js:561`); an image gallery
  renders them (`mods/redsquare/lib/tweet-gallery.template.js:34`).
- GIF search: the composer obtains a provider through `returnFirstRespondTo('giphy')`
  (`compose.js:386`), then adds its returned GIF URL as an image. The separate
  Giphy module returns `gif.images.original.url` (`mods/giphy/giphy.js:169`).
  Source hooks are present; runtime Giphy availability was not verified.
- Local image uploads call `resizeImg` (`compose.js:496`), which converts to JPEG
  (`lib/saito/browser.ts:2314`). Consequently, the current local-file GIF path
  does not preserve animation. Giphy selections bypass that conversion.
- YouTube links: Red Square recognizes YouTube URL forms (`lib/tweet.js:306`)
  and renders an iframe (`lib/tweet.template.js:109`). Generic video links use
  an Open Graph image/title/description card rather than a video player.
- Direct video upload: no video upload types or video-file player were found
  in the inspected Red Square composer and renderer. Do not assume direct video
  hosting is an existing Red Square feature.

These findings describe the inspected web implementation. Native feature scope
and media-provider integration are separate product/specification decisions.

## Native link-card compatibility, issue88

Read-only comparison on2026-11-04 used the existing pinned source revision
`9a4ca7e9442eb5e872168ced361ce741d7d139d7`, fetched from official GitHub. This
identifies the inspected source, not a claim about the current public deployment.
[Tweet](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/tweet.js)
uses signed `data.link` when supplied, otherwise derives a URL from post text.
The envelope's unsigned `optional.link_properties` supplies preview hints.
[The preview component](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/ui/saito-link-preview/saito-link-preview.js)
reads `og:image`, `og:title`/`saito:title` and `og:description`/`saito:description`,
with Saito-specific title/description taking precedence. `og:url` is display
metadata, not permission to change the post's clicked target.

Native `RedSquareLinks` keeps a complete validated HTTP(S) target and bounded
node hints. Inline authored text remains verbatim; a standalone trailing URL can
be represented by its actual card. Missing/unreadable hints leave an honest
site/target card, without sample titles, cover art, authors or reading times.
YouTube keeps its existing tap-to-play path, and Giphy keeps its existing media
and attribution path. Relative preview image paths resolve against the linked
page. The existing bounded image loader remains responsible for raster loading.
Real feed links must not open the sample `StackReviewScreen` article.

The existing edit contract is text-only: explicit original signed link/image
attachments remain. `FeedPost.originalText` preserves verified original text
through decoding and revision selection, so a changed text-derived URL cannot
inherit an old article's preview hints. Removing a text-derived URL removes that
card; an unchanged target can retain its hints. These presentation checks do not
establish current public Stack/provider availability or full Stack integration.

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

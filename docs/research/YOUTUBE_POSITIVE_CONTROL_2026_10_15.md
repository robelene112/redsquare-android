# YouTube positive-control candidate, 2026-10-15

Read-only research for #17 during #40 verification. Only this document was
written. Inspected current official documentation and existing fixture/assets;
no media playback, provider probe, prototype, source/test/harness edit, build,
CI, node, wallet or credential operation occurred.

The two retained failures do not demonstrate that every YouTube video fails.
Official errors 101/150 describe an embedding refusal for the requested video;
the existing UI merges those numbers, and neither the exact number nor cause
was retained. Content-specific restrictions remain possible, not established.
Actual public playback is still unproved.
[Official error reference](https://developers.google.com/youtube/iframe_api_reference#onError).

## Current official provenance

Checked the current [IFrame API reference](https://developers.google.com/youtube/iframe_api_reference):
Getting Started still uses `M7lc1UVf-VE`; the `loadVideoById` syntax examples
still use `bHQqvYy5KYo`. These are the existing references, not new controls.
The same document's
[Controlling 360° videos example](https://developers.google.com/youtube/iframe_api_reference#Controlling_360%C2%B0_videos)
constructs `YT.Player('spherical-video-player', {height:'360', width:'640',
videoId:'FAtdv94yzp4'})`.

`FAtdv94yzp4` is therefore a defensible, distinct third **candidate** with
current first-party sample provenance. Documentation inclusion does not prove
current embeddability, content availability, or playback on our app/device/IP.
Its 360° format also adds device/browser compatibility as a confounding factor.
Call it a candidate positive control until Playing actually passes; the docs
do not supply a guaranteed ordinary-video third reference in the inspected
Getting Started examples.

## Existing fixture can support it

[PublicYouTubeReadTest](../../app/src/androidTest/kotlin/nl/davidengelen/redsquare/PublicYouTubeReadTest.kt)
already supplies a synthetic signed archive row through `ControlledFeedPeer`.
It does not require that post to exist on either public feed. It launches the
native feed, scrolls/taps the exact signed post, creates the real YouTube
API/iframe without provider replacement, requires no Playing before the trusted
Play tap, then requires actual Playing, close/feed recovery and zero Saito
transactions. Preserve all those assertions and the same player identity.

A future third reference can use the existing
[independent signed-reference generator](../../build-support/references/video-reference.cjs)
and [pinned WASM/Elliptic signing boundary](../../build-support/references/signed-reference.cjs)
to create another controlled post pointing to the real video. Regenerate the
signed message; do not change a URL inside an already signed row. The current
selector has only default/alternate modes; a new explicit third choice and
[diagnostic frame allowlist](../../build-support/video-browser-diagnostics.cjs)
entry would need review. No such changes were made here.

A successful third case would establish one working real-provider path, not
explain the older failures or guarantee all videos. A third refusal would also
leave the general cause unresolved. Retain all outcomes; one candidate is not
a reason to repeatedly rotate videos until a test passes.

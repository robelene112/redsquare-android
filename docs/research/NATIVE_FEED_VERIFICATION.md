# Native feed verification

Ticket #4 is in progress. Review base: a1973c2. This file distinguishes
independent protocol evidence, controlled Android flows, live read checks and
physical-device acceptance.

The initial protocol decoder test failed before implementation
(`.scratch/feed-decoder-red.txt`) and passed with the independently signed
WASM/Elliptic archive fixture. A subsequent independently signed null-field
fixture exposed rejected compatible fields (`feed-null-red.txt`); the corrected
interpretation passed. Altered/malformed-row isolation failed before Archive
reading was implemented (`feed-rows-red.txt`) and then passed, including an
older cursor from the rejected row. Unsigned Archive request serialization
failed before implementation (`feed-wire-red.txt`) and passed against the exact
WASM network transaction and independently source-defined API prefix.

[Hosted run 37852075248](https://github.com/robelene112/redsquare-android/actions/runs/37852075248)
at 7456359 compiled the Android fixture and reached its test body on Android 10.
It failed waiting for the signed reference post on the native feed, because no
feed connection/rendering existed yet. The downloaded instrumentation output
was inspected: one test, one ComposeTimeoutException at the visible-post wait.
This is the functional red checkpoint, not a build/server setup failure.

The first implementation renders verified original post text and sender key,
uses fixed-owner WSS with ordinary TLS verification, answers nonce challenges,
and sends the exact unsigned Archive request only after handshake completion.
Private keys are never in the request. No transactions were broadcast. Reading
public Archive records does not prove chain inclusion or funded validity.

Cached opening, outage recovery, complete Android 10/17 acceptance and the
owner's live Archive read are still pending. HTTP 200 from both public Red Square
pages on 2026-10-08 establishes web readiness only, not native peer success.
The first feed implementation has not been published as a signed review build.

Physical timing uses [the measurement protocol](FEED_PERFORMANCE_PROTOCOL.md).
Pixel 7 runs remain part of final device acceptance (#35); no emulator timing
establishes the accepted 2,000/5,000 ms physical targets.

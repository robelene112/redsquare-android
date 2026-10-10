# Native quote web proof plan

Read-only source research for #13, 2026-10-10. A/B are official Saito pins
`9a4ca7e9442eb5e872168ced361ce741d7d139d7` and
`214504ecece76d1cd20a245fe4468eccbfca2951`; H is archived saito-lite-rust
`7200a9886f3dd02f66e8d6c146960e2a5402929f`. This note proposes execution;
no harness, constructor or template was executed. No application/server/wallet
initialization, production access or writes occurred. Public child templates
were fetched into `.scratch/quote-proof-source`.

## Smallest honest current-client experiment

Execute each pin's **unmodified Tweet constructor and complete template graph**
against the actual native-generated envelope from green run `38026569765`.
A hand-built object fed directly to TweetTemplate bypasses normalization and
cannot establish the native frame's rendering contract.
[A constructor/parser:31–230](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/tweet.js#L31),
[B parser:179–228](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/lib/tweet.js#L179).

Required evidence input: exact synthetic exported outer envelope and original
envelope, artifact SHA-256, build/run provenance, expected outer/original signer,
signature, timestamp, message and recipient fields. Confirm the fixture came
from Android's actual outgoing path, rather than regenerated research JSON.
Decode and independently verify both signatures with the established native/WASM
reference seam first. Preserve all signed data, including both quote fields.
For the rendering-only experiment, expose that verified decoded transaction as
`{signature,timestamp,from,to,optional,msg,returnMessage()}` without recreating
its message. This adapter proves presentation of decoded data, not web transaction
deserialization or signature verification.

Run offline in a temporary isolated process/container with no network, secrets,
home directory or production mounts. An allowlisted CommonJS loader evaluates
the pinned Tweet source and its real `tweet.template`, header, body, gallery and
footer templates. Record source hashes and Node version. Reject unknown imports.
Tweet's other imports are link-preview class/template and Transaction; use
explicit throw-on-use sentinels for these **unused** dependencies. Require absent
`optional.update_tx` and link-preview metadata, no explicit links or URLs, and
plain ASCII marker texts for this bounded first experiment. Any sentinel use,
DOM access, rejected async link analysis or unexpected effect fails the run.
[imports:1–4](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/tweet.js#L1),
[link/edit paths:286–381,468–488](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/tweet.js#L286),
[template graph:1–4](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/lib/tweet.template.js#L1).

Supply a plain controlled `app`, not an initialized Saito application:
`BROWSER:true`, keychain `returnUsername/returnIdenticon`, browser
`formatRelativeTime/extractFirstValidURL/markupMentions/sanitize`; `mod={}`.
Label lookup and time formatting may be deterministic substitutes. For the
restricted plain-text fixture, URL extraction returns empty and mention markup
is identity; sanitization preserves those safe markers. These substitutes do
not prove actual browser sanitization, mention, username, identicon or date parity.
Invoke `new Tweet(app,mod,decodedTx)` then its real `renderHTML()` or the real
TweetTemplate. Never add `app` to the normalized embedded object afterward.

Record normalized fields and actual HTML. Check outer ID/actor/commentary and
media, exactly one nested original ID/actor/text/media, no duplicate outer media,
and hidden nested controls. Report each result separately. Retain a negative
control without embedded data and a native-generated text-only original; image
presence must not conceal absent original text.

## Source-identified failure to test

Both normalizers construct a plain embedded object **without `app`**. The
recursive template obtains body text only through `tweet.app.browser`, otherwise
passes an empty string. Consequently source predicts embedded original text
will disappear while its author/images can remain. This is a prediction requiring
actual execution, not a passing compatibility result. Injecting `app` into a
template fixture would conceal it; an `app` field inside outgoing embedded JSON
also gets discarded by the normalizer's explicit field list.
[A normalize:240–268](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/tweet.js#L240),
[B normalize:238–266](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/lib/tweet.js#L238),
[body/recursion:98–128](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/lib/tweet.template.js#L98).

## Historical proof and limits

H requires real Transaction deserialization for `retweet_tx`; its constructor
recursively constructs a Tweet. The source wrapper decodes `{t,m,opt}` and delegates
binary transaction decoding to saito-js. H declares `^0.2.83`, A `0.3.2`: replacing
H's codec with a fake that returns the expected original proves no wire compatibility.
A separate offline codec harness must resolve H's public dependency provenance,
execute the real deserializer and check decoded original fields/signatures.
No app initialization is intrinsically required by the wrapper; any underlying
WASM requirement needs a separately controlled crypto-only runtime.
[H reconstruction:151–160](https://github.com/SaitoTech/saito-lite-rust/blob/7200a9886f3dd02f66e8d6c146960e2a5402929f/mods/redsquare/lib/tweet.js#L151),
[H codec:336–356](https://github.com/SaitoTech/saito-lite-rust/blob/7200a9886f3dd02f66e8d6c146960e2a5402929f/lib/saito/transaction.ts#L336),
[H dependency:78](https://github.com/SaitoTech/saito-lite-rust/blob/7200a9886f3dd02f66e8d6c146960e2a5402929f/package.json#L78).

H's pure template produces a preview slot, not nested quote markup. Complete
historical visual proof needs its actual DOM render path, SaitoUser and image
renderers with controlled browser helpers and no event/network side effects.
Rendering outer/original templates separately is narrower evidence.
[H template:1–105](https://github.com/SaitoTech/saito-lite-rust/blob/7200a9886f3dd02f66e8d6c146960e2a5402929f/mods/redsquare/lib/tweet.template.js#L1),
[H DOM nesting:409–469](https://github.com/SaitoTech/saito-lite-rust/blob/7200a9886f3dd02f66e8d6c146960e2a5402929f/mods/redsquare/lib/tweet.js#L409).

The minimal A/B experiment establishes constructor/template behavior at these
pins only. It does not prove current deployments, network acceptance, archive
inclusion, moderation, CSS/device appearance or interaction navigation. If the
predicted missing-text result occurs, record partial interoperability explicitly;
fixing those web renderers would require separately authorized web changes.

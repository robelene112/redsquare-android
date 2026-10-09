# Native post text policy

Read-only ticket #10 research, 2026-10-09. No application initialization,
private configuration/database/wallet reads, transactions or node mutations.
Named deployed public files were inspected through the documented empty-env,
network-disabled, read-only Docker/chroot runner. Git reads were not retried.

## What the 500 threshold means

The deployed browser composer checks `getText().trim().length` against its
500-unit default and blocks longer submissions only when the wallet's reported
pending balance numerically equals zero. JavaScript counts UTF-16 code units.
It subsequently publishes the original text, preserving surrounding whitespace.
This is a browser UI gate, not a text-size condition in the inspected receiving
code. Sources: deployed `compose.js:20,560-564,620-645`; its SHA-256 is
`35819245c4a2e6fb37c69cb651d32d2343a9c76daf297f0a56059793dde3c771`.

The module constructor creates a zero-amount, zero-fee transaction for every
post; it does not change that transaction based on text length or wallet balance.
`receiveTweetTransaction` adds and archives the tweet; `onConfirmation` calls
that path at confirmation zero. Neither reproduces the composer balance gate.
Sources: deployed `redsquare.js:1101-1147,1505-1512,2117-2132`, SHA-256
`e2cf08d95ed0cebc4b4e1106134ccc13c83b2a5814f1b0e9b31579a4c14a7ca8`;
deployed `wallet.ts:78-87`, SHA-256
`1819d89f7bdff56127532f12fd29c4de7de902c4401b7cce9b8961f54e9560b3`.
The [pinned constructor](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1101)
matches this named deployed file; this does not establish the current deployed
Git revision or official server configuration.

The delegated `Tweets.addTweet` verifies signature presence and module/request
discriminators, invokes generic module moderation, then indexes the tweet.
Its message predicate has no length/balance test. `Tweet.parseFromTransaction`
copies `data.text` without imposing 500 units. Sources: deployed
`mods/redsquare/lib/tweets.js:3-27,87-105`, SHA-256
`ba0e01739b2047f8cee0ac52a0274f4cee533dbfa062538012d6c316e1420aca`;
`mods/redsquare/lib/tweet.js:180-192`, SHA-256
`07ea0b50eac7c21ac8452b51fdb7bdda27efb5de7c62e5288a220ffaf23bf908`.

## Funding and consensus

The pinned Rust wallet uses the same zero-required-amount Normal construction
regardless of other holdings. `generate_slips(0)` selects no funded input;
`create_transaction` adds its zero-value identity input and recipient output.
Sources: [wallet selection](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/wallet.rs#L777),
[construction](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/wallet.rs#L1007).

Normal transaction validation checks authorization/signature, routing, slips
and value conservation. The inspected function contains no RedSquare-specific
text, 500-unit or external wallet-balance predicate. Zero-amount slip validation
returns true without a UTXO lookup. Thus, by source inference, a funded identity
can use this same zero-payment posting format; posting need not spend its funds.
Sources: [transaction validation](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/transaction.rs#L853),
[zero-amount slip validation](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/slip.rs#L223).
Inspected raw Rust SHA-256: transaction
`7e985d4dbe167865f954686caaf01d92924c79f0af9a32c6066ad7cd7ac01095`, wallet
`e357b5345d3f5493fccfc6a6613c510534ba280059c30696cbaf83069232753f`, slip
`ff0df3430417b598bde4d18861f0192d6519e21bc1547e70ac0c35c9d313adcd`.

## Recommended native validation

Use an explicitly **native safety bound of 1,048,576 UTF-8 bytes on the complete
serialized signed JSON message**, consistent with the existing native transaction
constructor. Validate the final serialization, including JSON escaping and reply
metadata, rather than assuming the raw text's byte count predicts its size.
Reject blank text-only posts and malformed Unicode on publication. Preserve the
original valid text; never truncate, normalize or trim the signed content silently.
Show a specific validation message before signing or network activity, retaining
editable work within the separately declared draft storage limit.

Do not enforce 500 as a universal protocol maximum or attach a SAITO payment to
longer posts. Recommended initial native policy permits longer posts within this
finite bound without a balance dependency. This deliberately differs from the
inspected browser's unfunded UI restriction and preserves its funded capability;
it is a native product recommendation, not an established Saito rule. If matching
that browser restriction is required, implement it separately with explicit
fresh/unknown-balance behavior, while preserving longer funded posts.

This research establishes source compatibility, not actual acceptance of any
payload size by either live backend. Generic moderation, transport/mempool/block
policies and official deployment differences can still affect availability.
No universal maximum transaction size or guaranteed chain inclusion is claimed.
Keep truthful pending/availability states and test representative multibyte,
escaped, over-500 and boundary cases through controlled peers. See
[the posting contract](NATIVE_POSTING_CONTRACT.md) for transport and provenance.

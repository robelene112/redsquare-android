# First-use wallet and messaging prerequisites

Read-only source inspection on 2026-10-08; no live transactions were submitted
and no wallet keys or balances were read.

## Observed code

- In the pinned Saito core, `core/util/crypto.rs::generate_keys` generates a
  secp256k1 keypair locally. Creating a keypair does not require a server request
  or block confirmation.
- The deployed `lib/saito/wallet.ts` initializes absent keys with `resetWallet`
  and delegates transaction construction to the core. The default amount and
  fee in `createUnsignedTransaction` are both zero.
- `mods/redsquare/redsquare.js::createTweetTransaction` calls that method with
  its default arguments.
- `mods/chat/chat.js::createChatTransaction` explicitly uses zero amount and
  zero fee. Its two-party private-chat path calls `signAndEncryptTransaction`
  with forced encryption; encryption readiness is separate from key creation.
- Chat contains an anti-spam restriction on community messages of at least
  1,000 characters when wallet balance is zero. Do not interpret support for
  ordinary unfunded messages as universal permission for all chat payloads.
- The pinned Rust wallet's `create_transaction` requires funded inputs only
  when payment plus fee is positive. A zero-value input identifies the signer
  when no funded inputs are used.

## Limits and verification needed

These are construction-path observations, not a completed native Android
integration test. Delivery still depends on connectivity, peer/service readiness
and applicable backend or conversation behavior. Verify new unfunded-wallet
posting and ordinary chat delivery against both supported backends.

No Android startup benchmark has been run. Measure separately: key generation,
secure persistence, core initialization, backend connection, first feed content
and first interaction. A VPS benchmark would not establish phone performance.

Primary core sources at the runtime pin inspected previously:

- [Key generation](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/util/crypto.rs#L50)
- [Transaction construction](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/wallet.rs#L1007)

For deployed module behavior, the authoritative inspected files are under
`/opt/saito/node`; recheck the runtime version before implementation.

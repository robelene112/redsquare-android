# Independently signed available search history

Generated on 2026-10-11 by `build-support/references/search-reference.cjs`, using
the existing `signed-reference.cjs` helper. The canonical JSON and native test
asset are byte-identical, SHA-256
`bc652374825ed954b1eb32def2bcb1fbd003ce4031b3c05b8476ff52353edf0a`.

The first page has 100 distinct authenticated root posts, with timestamps from
1700000100100 down to 1700000100001. An older root at 1700000100000 follows it.
Text is `Available page post 001` through `Available page post 099`, plus
`Available newest result` and `Available older result`. An independent
scalar-two commentary repost at 1700000100200 contains `Available quote result`
and points to the existing independent `Native Red Square reference post`.
These distinct streams exercise separate search cursors and short/full pages
without duplicating signatures to make a page artificially full. Archive
metadata is synthetic and unsigned.

The helper constructs actual WasmTransaction/WasmSlip zero-payment messages,
independently assembles signature preimages, compares BLAKE3 hashes with the
transaction-generated hash, verifies each generated signature through pinned
WASM and separate Elliptic, and roundtrips binary serialization. New signatures
use published unsafe scalars 1 and 2. No application or funded wallet initializes.
No live submission, chain inclusion, live archive response or current deployed
Git revision is proved.

The existing helper checks WASM 0.3.2, SHA-256
`f59be69dbb949368ce1fc7ef47d414cbe0281a86a206fe4bfb1128a4d087e01e`,
and Elliptic 6.6.1. It ran with the pinned Node 24.13.0 binary through the
established Docker network-disabled, read-only, empty-environment chroot. Capabilities
SYS_CHROOT and DAC_OVERRIDE allow named public dependency traversal only. Its
stdout was captured to the project JSON. No options, databases, service changes
or network API accesses occurred.

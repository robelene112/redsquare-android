# Independently signed available search history

Generated2026-10-11 with build-support/references/search-reference.cjs and the
existing signed-reference.cjs helper. The canonical JSON and native test asset
are byte-identical SHA256
bc652374825ed954b1eb32def2bcb1fbd003ce4031b3c05b8476ff52353edf0a.

The first page has100 distinct authenticated root posts, timestamps
1700000100100 down to1700000100001; an older root at1700000100000 follows it.
Texts are Available page post001 through099 plus Available newest result and
Available older result. One independent scalar-two commentary repost at
1700000100200 has Available quote result and points to the existing independent
Native Red Square reference post. The two streams therefore exercise distinct
search cursors and short/full pages without duplicating signatures to make a
page artificially full. Archive metadata is synthetic and unsigned.

The helper constructs actual WasmTransaction/WasmSlip zero-payment messages,
independently assembles signature preimages, compares BLAKE3 hashes with the
transaction-generated hash, verifies each generated signature through pinned
WASM and separate Elliptic, and roundtrips binary serialization. New signatures
use published unsafe scalar1/2 only. No application or funded wallet initializes.
No live submission, chain inclusion, live archive response or current deployed
Git revision is proved.

Actual pinned WASM0.3.2 SHA256f59be69dbb949368ce1fc7ef47d414cbe0281a86a206fe4bfb1128a4d087e01e,
Elliptic6.6.1 and Node24.13.0 checked by existing helper. Executed via established
Docker network-none/read-only/env-i chroot, capabilities SYS_CHROOT/DAC_OVERRIDE
for named public dependency traversal only, stdout captured to the project JSON.
No options, databases, service changes or network API accesses.

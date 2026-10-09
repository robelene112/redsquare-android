# Native conversation reference

Prepared for ticket#8 using the existing approved application/protocol testing
boundaries. No new production read/write or node configuration was required.

Pinned source9a4ca7e: `node/mods/redsquare/redsquare.js:2673` loads an exact
target signature when needed, then reads `field1:RedSquare`, `field5:threadId`,
`flagged_ne:1`, limit100. Profile posts/replies share author query
`field1:RedSquare,field2:publicKey`; profile likes use `RedSquareLike` by author
and resolve referenced post signatures. See `lib/manager.js:1060`. Returned
archive rows are backend metadata; derive identity/content/relationships from
verified transaction bytes. Available rows do not guarantee complete history.

[The generator](../../build-support/references/thread-reference.cjs) runs only
pure WASM and Elliptic crypto with env-i, network-none and a read-only host mount,
using the established reference runner. It never initializes the application,
loads a real wallet, accesses a database or propagates a transaction. Node24.13.0,
saito-wasm0.3.2, Elliptic6.6.1 and actual WASM SHA-256
f59be69dbb949368ce1fc7ef47d414cbe0281a86a206fe4bfb1128a4d087e01e are verified.

[The synthetic fixture](NATIVE_THREAD_REFERENCE.json) reuses the independently
signed feed root and adds scalar2's direct reply and scalar1's nested reply.
Both scalar keys are public unsafe test material and must never hold funds.
Each reply's manually built signature preimage matches WASM's signature hash;
WASM and Elliptic verify its signature, and actual transaction serialization/
deserialization preserve message bytes and signature. A deliberately altered
detached message retains the original signature and must be ignored.

The first Android regression opens the root through the actual feed and expects
both valid replies, rejects the altered reply, and exposes Back navigation.
It uses a controlled authenticated peer, not an internal module mock. No production
navigation is implemented at this test commit; actual functional red is pending.

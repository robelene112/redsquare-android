# Selected snapshot ancestry

Independent synthetic descendant: height2000003/time1700000020000, parent the
three-entry payment block6ed747ba..., one empty Fee leaf,482bytes. Pinned Rust
computes its Merkle root, pure WASM and Elliptic verify the creator signature,
and full serialization/hash agree. Literal tiphashbf72deac... is preserved.
The narrow shared header-signing review fix reproduces the entire independently
generated JSON byte-for-byte. No Saito initialization, network access or private
wallet/node state; only published unsafe synthetic identities.

At approved protocol seam2, the public ancestry observer reaches compiled
unimplemented-reader RED:1 failure/47-second build. Minimal implementation
passes the same case:1PASS/48-second build. An additional existing-policy
regression supplies the valid original payment block at the expected ancestor's
height but with a different hash; it cannot prove selected inclusion. Both cases
pass:2/0/0/0,28-second build. No fabricated RED for that regression.

The observer verifies complete content, requested hash, consecutive height and
exact snapshot-tip timestamp before matching the saved payment. It stops after
16 blocks or32MiB. Missing/invalid/unsupported data or exhausted coverage returns
unknown, never a negative payment result or released reservation. The HTTP
adapter must additionally bound response size, request and overall elapsed time.
Eight hashes preserve original three logs/XMLs, reference and counts.

This is controlled selected-server ancestry, not an independent consensus
validator, current production-chain observation or finality. Android status,
last-observation persistence, reorg/unknown journeys and whole-ticket acceptance
remain separate. Bounded source/evidence review is pending.

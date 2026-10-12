# Original payment input coordinates

One additional existing-policy seam2 regression, no production change or
fabricated RED. The independent pinned full-block generator changes the first
positive input block2000000→2000001 and runs pure WASM deserialization/generation:
the creator/block hash, payment author signature and leaf all remain identical,
while the input's public block coordinate changes. Re-serialization preserves
the mutation exactly. No initialized Saito wallet/node/private state/live read
or transaction; only the published unsafe synthetic reference identities.

The native reader can verify that unchanged header/root but must not recognize
the mutated transaction as the saved original payment. That observable matcher
regression passes along with the retained positive case:2PASS/0failure/error/skip,
29-second build. The original payment's independently known first input block
2000000 remains unchanged.4hashes retain original native log/XML/counts and
the independent reference with its altered binary payload.

This establishes exact input-coordinate discrimination, not actual UTXO
eligibility, consensus admission, current-chain membership or finality. It adds
no other mutation cases. Broader framing/system/odd/u64/resource/ancestry and
Android inclusion/status acceptance remain separate. Source/evidence review
is pending.

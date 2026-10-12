# Native inclusion journey preparation

A single Android UI/recovery seam1/3 journey preloads the independently signed
synthetic unsafe1 candidate in isolated .dev/emulator storage. It verifies the
restored public identity and sends no payment. Existing authentication/send
acceptance remains in the separate preview/process journeys. Test setup restores
the prior dev wallet in finally; no production wallet/node access.

Independent Rust-generated odd payment block and child are copied unchanged
into Android test assets. Only creator-signed header timestamp and dependent
parent hash are refreshed with the published unsafe3 identity, so actual Android
snapshot freshness checks remain active. Future actual runtime block artifacts
will require separate pinned WASM/Elliptic/root/full-roundtrip verification.
The new pure verifier dry-runs successfully against the independent1200B
reference (exact payment/root/creator/hash/serialization); not an Android result.

First test APK compile fails1m52s on missing direct test BouncyCastle dependency.
Same production pin1.86 is added only to androidTest. Compile then passes1m38s.
An inspected fixture used incomplete versionless backup JSON; it is corrected
before runtime to validated fromPrivateKey of the two published unsafe identities,
checking both independent public keys. Current fixture compiles1m18s. Terminal
UI assertions for no Pending/no Retry are subsequently added; fresh hosted build
and Android runtime remain pending. Four hashes preserve these preparation logs
and dry-run report. No missing-Included behavior RED is claimed yet.

Expected behavior: exact current selected ancestry produces Included in block
2000002, without Pending/retry labels, while retaining original selected backend
and input reservations. Reopening must keep the same identity and emit nothing.
A controlled missing-action runtime result, minimal UI integration and broader
unknown/reorg/persistence acceptance remain to be exercised.

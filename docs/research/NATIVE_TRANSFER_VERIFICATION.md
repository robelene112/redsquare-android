# Native wallet balances and authenticated transfers

Ticket #23, delivery22 of35. Claimed after #22 closure; review base
`2fe1fa51ed8d0c0e250d77e7c2f4198809418fdd`. Implementation is in progress.

## Accepted testing boundaries

Parent specification #1 already authorizes Android application flows with
controlled services (seam1), independent pinned protocol/wallet vectors (seam2),
failure and persisted recovery through that application boundary (seam3), and
cross-client compatibility (seam4). These are the boundaries for this ticket.
No new test-seam approval or live transfer authorization is inferred.

First tracer: open the native Wallet against a selected-key synthetic balance
snapshot, observe literal7.5 SAITO, and copy the displayed receiving address.
This uses seam1 and contains no spend. Capture the original screen and separately
observed hierarchy. Next cycles cover unavailable/invalid balances, independent
funded bytes, and authenticated controlled sending/recovery. Do not bulk-write
all tests before their corresponding implementations.

No transfer, inclusion, physical Pixel, production-wallet or final acceptance
claim exists yet. Public snapshots are selected-server observations, not an
independent UTXO or finality proof. See NATIVE_SAITO_TRANSFER_CONTRACT.md and
NATIVE_SAITO_TRANSFER.md for researched source and earlier reference limits.

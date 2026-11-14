# Bound Store payment invoices

Status: implementation decision for ticket 112; provider deployment remains an
unchecked STORE_LIVE prerequisite in 36.

The approved Store scope requires alternate payments to remain attached to the
original order through retries and process loss. The pinned Mixin service
(`0db0062d873369dc013046ed77c97410b5683b64`) can return an already open reservation
for the same requester, ticker and purpose, regardless of the new payload. Its
original reply does not identify the retained payload. Re-requesting after
completion can instead allocate another address. Neither a callback identity nor
matching amounts proves that an address belongs to the reviewed item.

Keep the official unfunded purchase carrier and existing BuySaito reservation
RPC. Add a narrowly scoped provider compatibility patch that echoes the saved
carrier signature and saved request ID, serializes reservation creation per
requester/ticker, and provides a read-only purchase-status RPC. Capability
`store_purchase_protocol: 1` identifies that contract. Status reads must never
reserve another address. Existing browser callers continue to receive their
original fields; new fields are additive.

The native request ID is the exact retained carrier signature. The app accepts
payment instructions only when both returned identities match that original.
It retains the selected provider, network, address, exact amount, expiry and
issuance key. An old conflicting reservation or an unbound older provider never
becomes a payable invoice. The app distinguishes that service limitation from
support for the advertised currency. Cancellation or closing the screen cannot
assert that an external transfer was recalled; late payment and on-chain delivery
remain recoverable under the original order.

Provider assertions, the provider-issued Saito transaction, selected-chain
inclusion and exact current entitlement remain separate observations. They extend
the existing Orders experience and encrypted wallet-wide journal. No local clock,
notification or arbitrary increase in NFT count establishes delivery.

The patch is a reviewable implementation artifact, not an activated service or a
new custodial backend. Verify it against the exact pinned source at owned
provider/reference seams. A fully initialized and funded live provider remains
separately pending. On November 14, passive public discovery advertised seven
currencies but Store reported `enabled: false`; no reservation, payment, deposit,
configuration change or public financial test was performed.

Network names come from Mixin's public chain metadata, not an invented payment
list. See [Mixin API guide](https://developers.mixin.one/docs/api/guide) for public
chain reads. All actual offered methods continue to come from the active service.

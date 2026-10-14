# Separate protocol tracer reviews

Fixed base bbdd121c, initial source through73facc22. This is a bounded creation
protocol slice, not #26 completion. Invite/join/leave/UI/recovery remain pending.

## Standards

One receiving-boundary validation breach: readCreation built roles from all signed
output bytes without checking each was a valid Saito public key. Signature
verification authenticates bytes, not recipient curve validity. Saito skill field
validation and repository compatibility rules apply. No actionable Fowler smells.

## Spec

One member-view validation gap against compatible membership rules: a malformed
recipient could enter the role map despite a valid creator signature. Signer/admin
equality and authentic discovery for an unjoined reader otherwise matched the
bounded behavior. Reference limits and future-work qualifications are accurate.

## Correction

Generated an actual WASM/Elliptic signed creation containing one deliberately
invalid 33-byte zero point. Native envelope authentication succeeds. RED4cases,
3PASS plus new rejection assertion failure because nothing was thrown; build35s.
Receiver now validates every public key before publishing roles. GREEN4PASS;
build57s. Prior signature/admin and creation/discovery assertions remain intact.
Bounded correction reviews pending. No app UI or complete-ticket acceptance.

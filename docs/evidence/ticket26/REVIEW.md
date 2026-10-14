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
Bounded correction reviews73facc22...62106e18: Standards0 and Spec0.
Both independently confirm the prior finding is resolved, original assertions
remain intact, hashes and RED/GREEN results match. No residual bounded findings. No app UI or complete-ticket acceptance.

## Invitation slice,058460d3...b75e4592

Standards: zero documented breaches/actionable heuristics. Input/decoded bounds,
URI structure, strict UTF8/JSON fields, public key validation and raw '+' behavior
are appropriate. Returned claims are unsigned; no wallet/backend/membership state
changes. Static cause-free failures protect pasted contents.13updatedmanifest
hashes and RED/GREEN results agree, including documented unchanged RED repeat.

Spec: zero actionable findings. Actual pinned link/codec vectors support stated
compatibility, while link names and roles are not represented as authenticated.
Malformed/duplicate input is recoverable; old signed creation vectors stay separate.
Joining, authority, persistence, groupUI and whole-ticket acceptance remain pending.

## Join and retained controls,86734261...66d9df30

Join: Standards0 and Spec0. Leave/control/restore: Spec0; Standards0 documented
breaches and one possible Duplicated Code heuristic in the independent join/leave
generators. Extracted their shared source verification/VM/wallet/signing setup to
control-reference.cjs. Both public corpora regenerated offline with byte-for-byte
comparisons passing. Native source and acceptance assertions are unchanged.
This bounded refactor is being independently reviewed, not full #26 acceptance.

Correction review66d9df30...05dc3be1: Standards0, previous duplication heuristic
resolved; Spec0. All seven manifest hashes agree. Both preserve fixture-specific
wire proof limits and distinguish unfinished Android/live/whole-ticket acceptance.

Confirmed-invitation origin reviewa2990aeb...c28cad6e: Standards0 and Spec0.
Unsigned provenance remains explicit, receipt/signer validation and replay survive,
no sender membership invented. Active-wallet binding and deliberate confirmation
remain controller responsibilities; no network admission or full UI claim.

Message reviewc28cad6e...b0b9876e: Standards0; Spec1. Native reader used the body
timestamp, while pinned addTransactionToGroup starts from the signed transaction
header timestamp. Original equal-time vectors could not expose this. An actual
constructor reference with different header/body times is being added before
correcting the reader. No claim that the initial review was clean.

Snapshot reviewb0b9876e...f6a6da41: Standards0 hard, possible active-role
duplication; shared role predicate now centralizes three actual uses. Spec1: the
255-entry role-map ceiling was incorrectly borrowed from transaction slip limits.
A group can gain more members via joins, and update outputs do not list all members.
A larger valid-map regression and correction are next. Timestamp correction
f6a6da41...91c65a96 Standards0; Spec review pending.

Timestamp correction reviewsf6a6da41...91c65a96: Standards0 and Spec0, previous
timestamp finding resolved. Source time remains authenticated header; web local
arrival-order adjustment excluded explicitly. Snapshot-map size finding corrected
with256-entry RED/GREEN; active-role duplication extracted separately.

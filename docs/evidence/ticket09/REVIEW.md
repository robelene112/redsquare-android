# Posting review

Review base: `78163d6`. Initial candidate: `6cfa908`; fixes reviewed at `cd1ee24`.
Final reviewed candidate `aec16fc`; both source review and full runtime acceptance pass.

## Standards

No documented-standard breaches. The review found two possible smells:

- Possible Divergent Change: the composer mixes rendering, persistence and
  publication/reconciliation. Move the existing posting behavior into a small
  state object without changing its observable behavior.
- Possible Duplicated Code: posting tests repeat binary frame decoding and
  archive-row construction; core root/reply tests repeat independent-vector
  assertions. Extract shared fixture helpers while preserving case-specific
  assertions and independent expected vectors.

At `cd1ee24`, the small composer state extraction and shared posting/vector
fixtures address both judgment calls. Case-specific assertions and independently
pinned expected bytes remain. Write serialization, cancellation handling,
scoping and original retry identity remain intact. No further abstraction is
needed.

Final Standards: zero hard breaches, zero actionable smells.

## Spec

One P2 defect: replacement UTF8 encoding in `PostDraftStore.save` can rewrite
invalid text and overwrite the last good draft before readback reports failure.
This violates ticket #10's requirement to retain failed work and specification
#1's preservation and failed-save/rollback requirements. Reproduce through the
editor and cold activity recovery, then reject malformed text before any write.

No additional missing behavior or scope creep within this ticket. Automatic
offline delivery belongs to #11. Final Android 10/17 checks remain separate from
source review. No new APK, physical Pixel acceptance or production write is
claimed.

Actual Android run `37991336062` reproduced the P2 through cold recovery. At
`cd1ee24`, strict UTF8 encoding rejects malformed text before key/file mutation.
The Spec reviewer confirms the previous saved record remains untouched. The
composer extraction preserves existing behavior; no remaining source omission,
scope creep or wrong implementation was found within #10.

Final Spec: zero actionable findings.

## Final fixture reviews and acceptance

Both axes approved fresh-activity cache fixtures at `9aa3884`, Android 17
keyboard dismissal/explicit picker-root selection at `41b4b26`, enabled
New post readiness/host failure reporting at `347e27d`, and bounded stale-node
reacquisition at `aec16fc`. Each delta has zero actionable findings on both axes.
Production source remains unchanged from `cd1ee24`. Fresh activity recovery is
distinct from process death; no failed button state is inferred from a missing
source readiness guard.

Full Android10 `37994339188` and full Android17 `38000254350` pass70 completed
cases each, including14 posting cases, plus3 intentional process-kill stages.
Core19, release/debug/test assembly, lint, both public reads, cold-cache/wallet
launches and signed5-to-8 continuity pass. The production source diff between
those candidates is empty. Earlier failed runs are documented in
NATIVE_POSTING_VERIFICATION.md and retained public evidence. Runtime acceptance
is satisfied; no new APK or physical Pixel acceptance is claimed.

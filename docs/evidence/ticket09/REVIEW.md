# Posting review

Review base: `78163d6`. Candidate reviewed: `6cfa908`.
Final acceptance is pending; these findings do not close ticket #10.

## Standards

No documented-standard breaches. The review found two possible smells:

- Possible Divergent Change: the composer mixes rendering, persistence and
  publication/reconciliation. Move the existing posting behavior into a small
  state object without changing its observable behavior.
- Possible Duplicated Code: posting tests repeat binary frame decoding and
  archive-row construction; core root/reply tests repeat independent-vector
  assertions. Extract shared fixture helpers while preserving case-specific
  assertions and independent expected vectors.

Standards: zero hard breaches, two judgment calls. Recovery, privacy and
authorization boundaries otherwise satisfy the documented standards.

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

Spec: one actionable defect. Review the fix before final acceptance.

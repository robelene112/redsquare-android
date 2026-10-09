# Posting review

Review base: `78163d6`. Initial candidate: `6cfa908`; fixes reviewed at `cd1ee24`.
Runtime acceptance remains pending; source review does not close ticket #10.

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

Final Spec: zero actionable findings. Focused green37992419586 passes14 cases plus actual process-death recovery.
Final37993173583 passes source checks, then browse7/8 reveals an outdated
recreation assumption in the cache fixture. The app retains Likes correctly;
fresh activity launches now ensure disk-only profile/conversation recovery.
Both fixture-delta review axes at9aa3884 report zero actionable findings.
Fresh-activity recovery is distinct from process death. Failure screenshots
for these two cases are captured after the failing activity closes and may
miss that screen; success captures remain inside the activity. Replacement
full37994339188 remains pending.
Cancelled37993042535 is superseded.

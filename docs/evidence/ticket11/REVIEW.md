# Final source review of ticket #12

Base `58aed2222096396c10bf8dd7ec2aa112c3cc817b`, candidate
`76197a4b4d04d7b32b83cfe0ce4dc6a06a2b90cc`. Both independent agents reviewed
`git diff 58aed22...76197a4` and the 13 intervening commits, read-only.

## Standards

Zero outstanding documented-standard breaches and zero actionable heuristic
smells. Full acceptance includes social cases and real process recovery. Fresh
responses reconcile counts across the three views. Fixtures and retained evidence
contain public controlled operations; private wallet material remains local.

## Specification

Zero actionable source findings and no scope creep. Both original source findings
are resolved: fresh response reconciliation and separate count-refresh warnings
that preserve verified action success. Original signed candidates are saved before
transmission, remain wallet/backend scoped, and are checked for exact availability
on retry. Served counts are not fabricated local increments. The process harness
checks the original PID correctly.

## Acceptance status

First full `38020831495` passes source checks but fails two Android10 browse
reply visibility waits. The follow-up fixture scrolls to those replies while
retaining content/history assertions. Production is unchanged. Fresh full
acceptance and fixture fix review are pending.
Source review does not establish runtime acceptance. Earlier runtime chronology
and public protocol proofs are in NATIVE_SOCIAL_INTERACTIONS_VERIFICATION.md and
sibling evidence directories. Ticket #12 remains open until full acceptance.

Fixture follow-up at `3824286`: both independent review axes report zero
actionable findings. Scrolling exercises the actual conversation UI without
removing or weakening content, cache, forged-reply or recovery assertions.
Production source equivalence with `76197a4` verified by empty diff. Fresh
full run `38021449614` is pending.

Second fixture review at `49d34a0`: Spec reports zero new findings. Standards
finds that old negative post checks only inspect composed nodes, so taller cards
can make exclusion assertions insensitive. Those post checks now search the full
scrollable list and require the specific missing-node AssertionError. Other
scroll/layout errors fail. Known positive anchors are restored where needed.
Official AndroidX Actions.kt documents full-list search; the pinned Compose
1.11.4 binary contains the expected failure marker. Test assembly52sPASS.
Runtime validation and fix review remain pending.

Reference: https://github.com/androidx/androidx/blob/androidx-main/compose/ui/ui-test/src/commonMain/kotlin/androidx/compose/ui/test/Actions.kt

Negative filtering fix at `afaf68c`: both reviewers report zero actionable
findings. Focused Android10 `38022220807` passes8/8 in21.698s, including complete
list exclusion searches. Public log and inspected author-history image retained
in following-strong-green10. Android17 counterpart38022557015 pending; full
38022046842 at production-equivalent49d34a0 remains running.

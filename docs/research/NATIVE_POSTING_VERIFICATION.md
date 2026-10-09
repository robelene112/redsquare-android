# Native posting and drafts

Ticket #10 (delivery 9 of 35), review base `78163d6`. Candidate `cd1ee24`.
Focused Android10 `37992419586` passes14 cases (65.692s) plus actual process
death recovery. Both final source-review axes find zero actionable issues.
Full37993173583 passes source checks then stops at an outdated cached-profile
fixture (7/8 browse cases pass). Retained navigation preserves Likes, whereas
the fixture expects default Posts after recreation. Both cached browse tests
now close and launch a fresh activity, excluding retained history from recovery
proof. Local test assembly passes1m35. Replacement full37994339188 at9aa3884 has
passed Android10 (70 completed cases, three intentional process-kill stages,
both public reads and signed5-to-8 updates). Android17 browse/following and cold
posting recovery pass; posting12/14, with header visibility and
file-picker fixture failures. Diagnostic37996950887 at3967eec reproduces12/14: IME pans the header offscreen;
the picker stays in Recent after false root-selection detection. Tests now
dismiss the keyboard and explicitly select the test root. Local build53s
passes. Focused37998134266 stopped in process preparation before the14 cases:
New post was clicked without waiting for enabled state, and no editor became
available. The fixture now waits for readiness, and host diagnostics report
preparation failure directly. Local51s build and Python syntax pass. Final
full17 next validates fixture fixes and outstanding acceptance together. Production source remains unchanged fromcd1ee24. Cancelled
37993042535 is superseded. Eight tickets complete.

## Implemented behavior

The actual Android app composes root posts and replies, saves encrypted local
wallet/backend/parent-scoped drafts and signs zero-payment Normal transactions.
Replies retain the selected parent, original thread and ordered inherited
recipients. Independent pinned WASM/Elliptic root/reply vectors verify signed
bytes and routing hops. Shared protocol infrastructure remains distinct from
Red Square's composer operations and rendering.

A candidate's exact signed bytes are saved before transmission. Submitted means
local transport submission. Available on this server requires an exact-signature
Archive response with verified author and original text. Retry checks availability
first, otherwise resends the original candidate. No chain-inclusion claim or
fabricated remote acknowledgement. Controlled peers receive writes; production
checks are read-only.

Drafts do not publish automatically. Navigation to another backend or identity
saves first; failed persistence retains the editor and prevents that transition.
Unfinished composition survives activity recreation in memory without plaintext
saved-state bundles. Signed candidates recover after actual process death.
Corrupt encrypted records remain untouched until recovery. Missing storage keys
fail closed. Wallet replacement explains draft isolation and export limitations.
Confirmed local clearing removes only the local text/retry record and explicitly
warns that the server may already have received the post.

Text is preserved verbatim, including valid multilingual content beyond 500
characters. Whitespace-only sends are disabled. The native bound is 1 MiB for
the complete encoded JSON message, not a universal Saito consensus limit or
browser 500-unit rule. Malformed Unicode is rejected before signing and before
any draft-storage mutation, retaining the last good saved record on save failure.
Sources and policy: [posting contract](NATIVE_POSTING_CONTRACT.md) and
[text policy](NATIVE_POST_TEXT_POLICY.md).

## Verification trace

All UI cases exercise the actual app with authenticated controlled binary peers.
Harness failures are recorded separately from functional red tests.

| Behavior | Functional red | Verified correction |
| --- | --- | --- |
| Explicit draft saving/reopening without transmission | 37964083012 | 37964861535 |
| Zero-payment explicit Send and truthful pending state | 37966333736 | 37967156376 |
| Selected parent for an existing reply | 37971949876 | 37973493246 |
| Save composition before backend navigation | 37976960559 | 37977871622 |
| Recovered original-signature retry | 37978708226 | 37982268510, seven cases pass |
| Unsaved root text after recreation | 37983208565 | 37984084989, eight cases pass plus process recovery |
| Failed saving and damaged ciphertext | Existing behavior regressions | 37984759300, ten cases pass |
| Selected reply and text after recreation | 37985523535 | 37987683585, twelve cases pass |
| Confirmed uncertain-record clearing | 37988651709 | 37991336062 passes this case |
| Invalid save preserves the last good draft | 37991336062, cold UI shows rewritten text | 37992419586, all14 cases pass |

Run `37991336062` at `67e883d` passes 13/14 UI cases; its only failure is cold
draft recovery after invalid text saving. It also passes actual process-death
reconciliation without rebroadcast. Run `37990196219` at `6cfa908` passes13/14;
its clearing case reaches the persisted blank editor but the fixture rejects an
exact-signature Archive request. The fixture was corrected; this is not a
production clearing failure.

Local `cd1ee24` core tests, debug/test assembly and lint pass (2m38s). Source push
checks also assemble release; they do not establish Android runtime acceptance.
Public logs and selected images live under `docs/evidence/ticket09/`. The invalid
save screenshot confirms `Keep ? as entered` replaced the last valid draft.
Earlier detailed checkpoints remain available in Git history at `67e883d`.

## Review and limits

[Two-axis review](../evidence/ticket09/REVIEW.md) at `6cfa908`: Standards zero
hard breaches/two possible smells; Spec one P2 draft-data-loss defect. Candidate
`cd1ee24` addresses the defect and separates composer operations/shared fixture
helpers. Both fix-review axes pass; do not close #10 before final verification.

No production transaction, new APK publication, physical Pixel timing or push
delivery is claimed. The stable review APK remains version10/source5225d06.
Certain official-backend bitmaps show an inconsistent caption despite passing
selected-label semantics and separate peer broadcasts. Their cause is unproved;
exclude those captures from visual backend acceptance. The final focused cold
draft bitmap also shows a blank/not-ready editor despite passing subsequent
semantics; it is excluded from recovery visual evidence, whose proof is the
completed UI assertion log. Automatic offline queues
belong to #11, professional UI and physical performance acceptance to #35.

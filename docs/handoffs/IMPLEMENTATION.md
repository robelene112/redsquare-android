# Current implementation handoff

Updated2026-10-16. Owner authorizes continuing all remaining issues sequentially.
Use [workflow](../WORKFLOW.md) and the project skills. #32 is the sole active
implementation. Preserve unrelated untracked
`docs/research/NATIVE_GROUP_CHAT_CONTRACT.md` without adding or editing it.

## Resume #32

Review base: `dd89bc9108eb002bb260eee709c656cd45348940`. Dependency#24 is closed.
Production receiver/consent/receipt ledger: `86e408ee5a257e9817885c3286bd6b9ba6580b57`.
It handles verified replies locally, with encrypted wallet/backend-scoped opt-in,
fresh signed candidate/owner-parent reads, current policy/OS checks and stable
Android alert identity. No existing production entry point invokes the receiver.

Completed native Android10 evidence:

- First reply: [RED](../evidence/ticket32/reply-alert-red/android10/README.md) and
  [GREEN](../evidence/ticket32/reply-alert-green/android10/README.md).
- Physically dismissed replay: [RED](../evidence/ticket32/dismissed-reply-red/android10/README.md)
  and [GREEN](../evidence/ticket32/dismissed-reply-green/android10/README.md).
- [Actual process death and recovery](../evidence/ticket32/dismissed-reply-process-green/android10/README.md):
  run38827943929 at `cfb7c19816411940e430d946b3aa67798420e1ea` SUCCESS.
  PIDs3447→3632, duplicate stays silent, independent fresh reply visibly alerts,
  full wallet/backend invariants and independent installation restoration pass.
  Local corrected core/debug/test build46983 also completed0 in1m51s.

No owned local Gradle process or CI run remains active. Focused scopes run
core/debug/test only. New hot-class wallet/backend assertions at3010896e/cfb7c198
have not run in the process-only scope. Android publication and disk acknowledgement
are not atomic; no exactly-once or all-interruption-window claim.

Process evidence reviews are complete: Spec0/Standards0 at628e6189.
Actual system-tap tracer5598e92e is added; corrected35da963a preserves validated
DEBUG/emulator transport metadata across the notification launch and explicitly
finishes test-created Activities. Initial run38829039090 was cancelled before
build/runtime; cancellation metadata is retained separately. Local corrected
core/debug/test build88815 completed0 (see scratch log for duration). A further
cleanup guard prevents installation restoration when an Activity has not stopped.
Source review and actual native RED for the corrected tracer are next, followed
by implementation of system-notification navigation while preserving unsent work. Read
[the tap proposal](../research/SOCIAL_PUSH_TAP_ROUTE_2026_10_15.md) before edits.
Fresh verification precedes a guard covering final form save, scope/policy recheck
and navigation commit. Keep initial Activity launch configuration separate from
notification intents. Test real cold/warm taps, save failure and stale bindings.

Remaining#32: mentions with independent registration proof, negative policy/scope/
permission/forgery/expiry/fault cases, optional registration UI, real FCM/gateway,
full SDK acceptance. Firebase project/IAM and concrete gateway setup remain external
requirements; the existing owner question stays pending. Local injection cannot
close this issue. Live node changes and gateway deployment need a concrete bounded
separate authorization. See
[local seam](../research/SOCIAL_PUSH_LOCAL_SEAM_2026_10_15.md),
[closed-app requirements](../research/NATIVE_CLOSED_APP_PUSH_REQUIREMENTS_2026_10_15.md),
[observer](../research/SOCIAL_PUSH_GATEWAY_OBSERVER_2026_10_15.md) and
[Archive discovery](../research/SOCIAL_PUSH_ARCHIVE_DISCOVERY_2026_10_15.md).
Future Chat push transport gaps are traced in
[the source audit](../research/CHAT_PUSH_EXISTING_TRANSPORT_2026_10_15.md).

## Current public build

#41 is CLOSED. Canonical [final acceptance](../evidence/ticket41/FINAL_ACCEPTANCE.md)
records full Android10/17 gates and final review results.
Signed beta24/0.1.24 is published and verified at https://davidengelen.nl/redsquare/.
APK SHA256 `7d6e8e746f3d1cb68969e302245d6868bb4edcdd6cb1560ed6fe5344d97f8c16`.
No version25 is reserved. Keep signing material outside Git. Use
[review-build operations](../REVIEW_BUILDS.md) for the next authorized publication.

## Other remaining issues

| Issue | Current condition / next useful work |
| --- | --- |
| #16, #31 | Giphy Android key pending owner setup; question already asked. |
| #17 | Real native YouTube Play fails error150. Preserve failed run38791958201. Prepared bounded provider-reason observation is `.scratch/video17-next-native-observation.md`; unchanged rerun adds no evidence. |
| #19 | Owner Registry name checks time out despite authenticated peer/key mapping. Frozen pending concrete cause/change. Canonical `docs/evidence/ticket19/FINAL_REGRESSION_CHECKPOINT.md`. |
| #33, #34 | Depend on#32. Chat transport source audit above is preparation only. |
| #35 | Integrated acceptance and actual Pixel7 startup/update checks remain. Emulator results do not prove physical targets. |
| #36 | Real publisher/legal/contact/retention/declarations/submission facts remain. Read the Play research documents before release work. |
| #38 | Branding decision, not a release blocker. |

Parent#1 remains open. Original35 tickets:26closed,9open (#16,#17,#19,#31–#36).
Extra#37,#39,#40,#41 are closed. Fetch actual tracker state before changing claims.

## Evidence and environment traps

Large artifacts go under `/mnt/storage/redsquare-android-artifacts`; verify the
mount/space before large writes. Build instructions are in the operations docs.
No local KVM: follow
[local feasibility](../research/LOCAL_ANDROID_TEST_FEASIBILITY.md), use hosted KVM.
Preserve current pins. Source edits wait until any owned local Gradle build finishes.

Some earlier image previews contradicted the actual PNG bytes. When that happens,
use original hashes, XML and independent read-only pixel decoding. The corrected
[dismissal RED package](../evidence/ticket32/dismissed-reply-red/android10/README.md)
retains decoded observations and the reproducer. No source image was edited.
Do not infer a black UI or an Android rendering cause from a contradictory preview.

For earlier ticket chronology, failed attempts, detailed source provenance or old
session IDs, consult [the historical checkpoints](IMPLEMENTATION_HISTORY_2026_10_16.md).
Those old active labels are historical; canonical ticket evidence and current code
remain authoritative. This archive preserves the previous handoff body unchanged.

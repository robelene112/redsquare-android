# Current implementation handoff

Updated2026-10-16. Owner authorizes continuing remaining issues sequentially.
#32 is the sole implementation, review base
`dd89bc9108eb002bb260eee709c656cd45348940`, dependency#24 closed.
Preserve unrelated untracked `docs/research/NATIVE_GROUP_CHAT_CONTRACT.md`.

## Latest owner scope

The owner explicitly asked to stop cybersecurity requests. No Daybreak application
or cybersecurity service/account request was submitted. Stop separate research
and targeted campaigns in that area; continue ordinary app functionality and
correctness. The optional extra test and dedicated dispatch choices were removed
at50ac874d. Subsequent cases cover ordinary app functionality. Keep existing
app fixes.
Do not retry, rename or reroute blocked work. AGENTS.md records this instruction.

The ChatGPT interface displayed a content-blocked notice. Root explained that it
cannot switch that platform notice off or guarantee absence, and pointed to official
feedback guidance. A screenshot does not identify an exact rejected tool action.
Do not invent one. Repository state was verified before ordinary app work resumed.

## Current bounded work

The current #32 slice is scheduled social receipt through real WorkManager,
selected-Archive retry and an actual Android alert without an Activity.
[Canonical verification](../evidence/ticket32/scheduled-receipt-green/README.md)
records both passing SDKs at exactc69a1896d01b23c6bb41b3f9e248410517017ac7:
- Android10 run38981302625: old11PASS84.608s plus worker1PASS12.005s.
- Android17 run38981305637: old11PASS96.847s plus worker1PASS11.792s.
- Source38981302496 SUCCESS includes release/lint; both native runs core114/0/0.
- Root viewed6new GREEN originals and parsed6XML. Earlier11 screenshots are not
  duplicated; their exact passing raw logs remain retained.

Minimum uses CONNECTED/10s exponential backoff, an execution-time7day processing
expiry and the shared receiver's fresh wallet/backend/policy/signed-Archive/ledger
checks. Clock rollback conservatively retains processing; this does not establish
WorkManager-history deletion after7days. Enqueue returns the actual persisted
unique-work UUID, including pending duplicates. A shared execution mutex is drained
after owned cancellation before fixture files/preferences/wallet/backend restore.
The native journey checks pending duplicate identity, actual recovered alert,
completed duplicate's unchanged OS postTime, no Activity, fullwallet/backend,
zero outgoing writes and scoped restoration.

[First attempt](../evidence/ticket32/scheduled-receipt-group-observation-failure/android10/README.md)
38979224052 remains FAILURE:10/11PASS82.607s, group editor observation before the
worker case. The correction explicitly compares exact full EditableText.text
rather than AnnotatedString metadata, keeping15s/finalassert and persisted
text/photo checks. Installed1.11.4 bytecode confirms those comparisons differ;
the actual old predicate-time cause remains unknown.
[Intended RED](../evidence/ticket32/scheduled-receipt-red/android10/README.md)
38980190191 at ee1bc69b: old11PASS83.64s, worker1FAIL1.88s rejecting the no-op
worker's SUCCEEDED state. Later newcase tails did not execute in that failed run.

Corrected source reviews0/0 at1a62909d; pre-review minimum local core/debug/test
PASS3m20s (exec76439 ended). The simple review fix is compiled by exact-source CI.
No owned local Gradle build remains. Bounded review base8e6498a2; minimum base
ee1bc69b. Final evidence review at2fd7079e found only stale current-handoff wording,
resolved at81624363 with final Standards0/Spec0. This bounded local slice is
accepted; actual timeout/process/external gates below remain.

The additional[unanswered-Archive observation](../evidence/ticket32/scheduled-timeout-green/README.md)
now passes on both SDKs at5463170d091ed58f3ad90686c184ca0875cea08f:
10run38982505303 old11PASS90.863s+worker2PASS35.448s;17run38982507198
old11PASS128.618s+worker2PASS35.981s. Source38982505012 SUCCESS release/lint.
Default-false withholding suppresses only actual Archive response emission after
its normal callback observed/validated the request. The withheld case checks real
≥10s elapsed from submission to retry, then full recovery/duplicate/alert and
restoration assertions; scheduling/authentication are included in that interval.
The peer timer is12s inside15s verification, not proof the outer15s specifically
fired. Same150s/180s class limits, no fake clock or shortened deadline. Production
trees c69→546 exactly equal. Root6timeout originals/12XML; malformed-mode captures
also retained with passing assertions/logs. Source reviews0/0 at0f50c2a4; final
evidence review is next. No owned local Gradle build or SDK/config/live change.

Next same-ticket work is the actual scheduled-receipt process boundary below.
No source for it is implemented yet. Remain sequential. Current passes are live
process with noActivity, not process death/autonomous restart/FCM/locked/Doze/
Pixel or full#32 acceptance.

A source-only[process-boundary proposal](../research/NATIVE_SCHEDULED_RECEIPT_PROCESS_BOUNDARY_2026_10_16.md)
recommends reusing the existing JVM peer outside the target process via owned
adb reverse. No shared-fixture move, host launcher, build hook or process test
has been implemented. No SDK/client config/service/gateway/node change was made.
The existing owner Firebase setup question remains pending, do not repeat it.
Public signed beta24 is unchanged.

## Earlier accepted username slice

[Verified username acceptance](../evidence/ticket32/username-mention-green/README.md)
retains intended RED38975655606, first minimum failure38976627865 (username passed,
group observation failed), corrected10 run38977710046/78e4172d11PASS81.609s and17
38976630590/7824504111PASS124.127s. Both notification compatibility runs
38976632886/38976636125 pass32read cases, applicable permission choices and actual
read-history recovery PIDs6113→6203/5326→5398. Production equivalence782→78 and
source release/lint38976622437/38977709969 are retained. Final evidence reviews0/0
at3f3c5bb4. Root10username originals+groupdraft+5permission/recovery originals,
allXML parsed. Loading/failed history and earlier failed-run tails remain qualified.

## Accepted bounded #32 work

These are local controlled-transport slices, not actual FCM/closed-process push.
Every prior failure retains its actual conclusion and qualified limits. Full raw
chronology is in the historical handoff; linked evidence is authoritative.

| Behavior | Canonical evidence |
| --- | --- |
| First reply alert | [Reply GREEN](../evidence/ticket32/reply-alert-green/android10/README.md) |
| Dismissal replay and actual process recovery | [Replay](../evidence/ticket32/dismissed-reply-green/android10/README.md), [process](../evidence/ticket32/dismissed-reply-process-green/android10/README.md) |
| Warm reply tap and post draft/photo | [Tap GREEN](../evidence/ticket32/reply-tap-green/) |
| No-Activity tap/recreation/consumed route | [Cold tap GREEN](../evidence/ticket32/reply-cold-tap-green/) |
| Public-key mentions | [Mention GREEN](../evidence/ticket32/mention-alert-green/) |
| Wallet-scoped alert choice | [Choice GREEN](../evidence/ticket32/alert-choice-green/) |
| Actual Android category shortcut | [Categories GREEN](../evidence/ticket32/android-category-controls-green/) |
| First-use social consent | [Consent GREEN](../evidence/ticket32/social-consent-green/), [compatibility](../evidence/ticket32/social-consent-regression/) |
| Private Chat draft/photo through social tap | [Private acceptance](../evidence/ticket32/private-chat-reopen-green/README.md) |
| Group Chat draft/photo through social tap | [Group acceptance](../evidence/ticket32/group-chat-tap-green/README.md) |

Latest group minimum0b5dc394, exact runtime source4419fd1a: social10 run38973726401
10PASS78.837s, social17 38973729273 10PASS103.434s. Group compatibility
38973732283/38973734828 each26 final completions (actions1/mixed1/group22/recovery1/
restoration1), actual PIDs13930→14322 /5236→5318. Separate source38973706184
SUCCESS release/lint; localminimum3m45s. Root14originals/167XML, both final evidence
reviews0/0 at041de29f, finalizedec025f6b. Loading destinations, delivery-unconfirmed
forwarding and intentionally unavailable-history recovery are qualified. No
unchanged rerun needed. Management dialogs keep their current work deferred.

Private photo failures and follow-ups remain qualified in canonical acceptance.
The earlier Android10 16MiB OOM cause remains unknown; later actual storage process
maxMemory is512MiB. A pass does not establish an earlier failure's cause. Additional
instrumentation status0 diagnostic messages are progress, not extra passing cases.

## Remaining #32

Native-listed mention proof, receipt permission/category controls and
recovery, other mounted forms, registration, real transport and full Android10/17
acceptance remain. Legacy unguarded forms retain work with Retry. Local injection
cannot close#32; no production caller invokes the local social receiver yet.
Firebase project/IAM and gateway setup remain external; the existing owner
question stays pending. Do not repeat it. Production gateway deployment/node
changes require concrete bounded separate authorization, after reviewable work.

Transport references: [local seam](../research/SOCIAL_PUSH_LOCAL_SEAM_2026_10_15.md),
[closed-app requirements](../research/NATIVE_CLOSED_APP_PUSH_REQUIREMENTS_2026_10_15.md),
[observer](../research/SOCIAL_PUSH_GATEWAY_OBSERVER_2026_10_15.md),
[Archive discovery](../research/SOCIAL_PUSH_ARCHIVE_DISCOVERY_2026_10_15.md),
[future Chat transport](../research/CHAT_PUSH_EXISTING_TRANSPORT_2026_10_15.md).
No public/private wallet data or live-node changes are authorized by fixture work.
Later source-only[FCM API observation](../research/FCM_ANDROID_API_2026_10_16.md)
confirms new FID APIs in current Messaging26.0.0 and server fid target, superseding
only the dated token API observation. No SDK/config/service added.

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

For earlier ticket chronology, source provenance, failed attempts or previous
session IDs, read [historical checkpoints](IMPLEMENTATION_HISTORY_2026_10_16.md).
The snapshot before the username minimum preserves the previous handoff verbatim.
Historical active/pending labels are not current authority.

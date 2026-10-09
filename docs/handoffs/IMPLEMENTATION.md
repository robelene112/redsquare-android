# Sequential implementation handoff

Updated 2026-10-09. The owner authorized implementing all 35 proposed
tickets sequentially, committing after each, and reviewing the app at the end.
Continue without routine permission requests or a grill per ticket. Ask only
one question at a time when a material choice really needs the owner.

## Current work

- Ticket #8, "07: Open threads and browse user profiles", is complete at final
  source c8fe9b7 against base 44cae5d. Full run 37943159613 passes both SDKs:
  core15, release/debug/test assembly and lint, 47 completed instrument cases
  each including8 browse cases, plus two intentional process-kill stages each.
  Both public feeds, cache/wallet cold launches and signed5-to-8 updates pass.
  Public logs and representative images inspected/retained ticket07/final-full.
  Both final source-review axes have zero actionable findings. References and
  functional red/green chronology are in thread/profile verification and
  ticket07/INTERIM_REVIEW/REVIEW. No production writes or new APK publication.
- Ticket #9, "08: Follow accounts and read a Following feed", complete at
  reviewed source6d5d9da against base6dd511d. Final full37960235937 passes:
  core15, release/debug/test assembly/lint,55 completed instrument cases each
  SDK (53 top-level+2 recovered process cases),2 additional intentional
  process-kill stages each. All8 Following cases pass19.341/44.848s on29/37.
  Both source-review axes have zero actionable findings. Public logs and
  representative Following/recovery images inspected/retained ticket08/final-full.
  Wallet-scoped follow choices shared across backend sessions; selected-peer
  signed-author queries; independent wallet/backend Following cache; Retry after
  temporary choices-read failure; preserve verified partial pages before later
  author failures. No cross-device/on-chain following promise. Prior cache
  formats unchanged. Details NATIVE_FOLLOWING_VERIFICATION.md/REVIEW.md.
  Both native public reads pass selected-label semantics. Official bitmap
  captures display owner's label despite assertions; cause uninvestigated,
  excluded from visual acceptance. Controlled switching images are correct.
  Wallet/cache recovery and signed5-to-8 continuity pass. No new APK, private
  uploads, production writes or physical-device/push proof. Continue all35.
- Active #10, "09: Publish text posts and replies with recoverable drafts",
  assigned@me, review base78163d6, all native blockers complete; #9 closed
  comment6085557419. Production candidatedca6cf1, latest reply test9331e52. Actual four-case
  red37969745626 passes the first3 and fails missing Reply (line70),12.732s;
  inspected public log/Conversation image retained ticket09/reply-red.
  Minimal68b7e9d adds Conversation Reply/shared composer, separate parent
  drafts, signed parent/root thread, ordered parent-author+inherited recipients,
  sequential output indices and zero-only restore guards. Verified envelopes
  expose signed output recipients. Local build failed missing FeedPost import;
  correcteddca6cf1 passes core16/debug/test assembly1m27s, no local build active.
  Four-case green37970835482 atdca6cf1 passes Android10 in10.785s; public
  log/reply-in-Conversation image inspected/retained ticket09/reply-green.
  37970627296 canceled after import failure; not runtime proof. At23f163a,
  pure reference helper now supports ordered public scalar1/2 output recipients;
  WASM signature preimage checks/serialization and Elliptic independently verify
  recipient reply/output index1/hop. Both public root/reply core tests pass24s
  (new full17-case core run not yet done). Fifth actual UI case c348f14 taps
  independently signed existing reply then Reply, requires its author/parent,
  original root thread and recipient order[self,parentAuthor,ancestorAuthor].
  Five-case red37971949876 fails only nested selection at line78,32.55s:
  tapping the existing reply leaves the root selected. Public log and image
  inspected/retained ticket09/nested-reply-red. Minimal850c098 adds verified
  post selection through the existing Conversation navigation stack. Five-case
  green37972847178 is running; build passed, Android10 runtime pending.
  GitHub #10 had been closed without acceptance evidence by earlier commit
  4d1e91a; reopened on2026-10-09. It remains in progress, not a ninth delivery.
  Run37972847178 passes nested/root replies, drafts and explicit Send; the
  publication-to-feed case raced its asynchronous Back transition and matched
  the outgoing editor before feed navigation completed. It fails visibility
  at line193,18.208s. Public log/nested-success image inspected and retained
  ticket09/nested-reply-transition. Corrected42c5774 waits New post and scrolls
  the feed to the observed publication. Five-case37973493246 is running.
  Both interim source reviews at42c5774 are in ticket09/INTERIM_REVIEW.md.
  Three hard Standards and six Spec findings remain: unsaved navigation loss,
  concurrent Save/Send candidate overwrite, inaccurate recovered pending/error
  state, absent same-screen retry/reconciliation, native work missing from
  wallet replacement warning, pending/fault/isolation/two-backend proof, and
  declared text policy. Do not close #10 before fixes/review/full evidence.
  First3 cases pass8.119s in37968817970 atfdcc528: encrypted draft save/reopen
  without broadcast; explicit signed zero-payment submission with pending copy;
  matching verified exact-sig Archive observation marks Available, Back clears
  completed draft and refreshes selected feed, showing actual new post.
  Public logs/images inspected and retained ticket09/{draft,send,observation}-green.
  Actual functional reds37964083012 missing New post,37966333736 missing Send,
  37968017392 no Available and absent received frame (immediate socket close).
  Minimalfdcc528 keeps peer open through up to6 exact-sig queries2s apart.
  Draft storage AES256GCM Keystore/package+filename AAD/noBackup AtomicFile,
  fd+directory sync/decrypted readback; wallet/backend-specific,1MiB text and
  5MiB encoded record bounds. v1 raw text migration; v2 text+exact signed Normal
  candidate persisted before submit, restored signature/author/text verified.
  SaitoTransaction creates zero-payment root, detached envelope, immutable raw
  restore; hop signs signature64+destination33, type4 without index or RPC ack.
  Pure independent posting-reference.cjs verified WASM0.3.2/Elliptic6.6.1 plus
  serialization roundtrip, public unsafe scalar1/2 only. At2e62a19, new public
  SaitoTransactionTest matches Unicode root/network/envelope/hop bytes exactly;
  focused root core test passed26s; full16 cases now pass at68b7e9d. References
  docs/research/NATIVE_POSTING_REFERENCE.json and core resource.
  Fourth test requires Reply from actual Conversation, parent/root thread and
  parent author output, actual archive observation and reply in thread on Back.
  Current ControlledFeedPeer has onTransaction before last archiveResponse
  parameter to preserve existing trailing lambdas; records public frames,
  atomic indexed-row fixture. Post tests Before guards .dev+ranchu/goldfish and
  clears synthetic postdraft records only, never wallet keys; no case-order
  dependence.37965701990 was editor-ready harness timing failure; corrected
  actualred above.37965626375/37967863806/37967946413 superseded during setup.
  Local full core/debug/test assembly atfdcc528 passes1m30s, no build active.
  Remaining: replies (including nested/inherited recipients), draft/pending
  identity/backend isolation/retry/cold recovery/navigation/save faults, funded
  text policy (>500 UTF16 web threshold permitted with nonzero balance), both
  backend fixtures, independent reply vectors, final source axes/full proof.
  Primary contractf31ed06 in NATIVE_POSTING_CONTRACT.md cites named public
  deployed files/hash and pinned Rust sources, no new Git revision claim.
  Never claim socket enqueue as archive availability or archive as chain proof.
  No production writes, private uploads or new APK. Continue all35 sequentially.

- Authoritative release spec: https://github.com/robelene112/redsquare-android/issues/1
- Implementation tickets: #2 through #36, with native GitHub dependencies.
- Eight of35 tickets complete: #2 crypto compatibility, #3 persistent wallet
  and signing/updates, #4 feed/cache, #5 fixed backends, #6 authenticated export,
  #7 safeguarded import, #8 threads/profiles, #9 following. #10 active,27 tickets
  still open. Preserve all accepted features; no Ralph or parallel implementation.

## Completed foundation references

#5 reviewed source177a647, final full37868642713 and focused public reads
37870028403/37870030262. Fixed backend selection, checked persistence and
uncertainty fail closed. Report NATIVE_BACKEND_SELECTION.md; ticket04/REVIEW.md.

#6 reviewed source7a2ab0b, full37892360326 passes Android10/17/core14,29 normal
cases per SDK plus actual export process-kill recovery, assembly/lint and signed
updates. Real PIN/SAF, provider failures/deadline and new authenticated retry.
Report NATIVE_WALLET_EXPORT_VERIFICATION.md; ticket05/final-full and REVIEW.md.

#7 reviewed source5225d06, closure44cae5d, full37921133133 passes both SDKs:
core15,39 completed instrument cases each plus2 actual process-kill stages,
release/debug/test assembly/lint, signed5-to-8 updates, both public feeds and
cold-cache/wallet recovery. Seven import UI flows, strict byte rejection,
same-wallet vs replacement, fresh balance-dependent outgoing backup gates,
verified old-state rollback and actual pre-commit cold recovery. Both axes
zero actionable findings. Detailed history and safeguards remain authoritative
in NATIVE_WALLET_IMPORT_VERIFICATION.md and ticket06/REVIEW.md/final-full,
and older handoff checkpoints are preserved in Git at44cae5d.

Initial wallet/live-feed milestone is published as stable signed version10
(source5225d06). No physical Pixel/update8-to-10/power-cut claim. Prior official
Compose bitmaps had inconsistent labels; use each report's stated evidence
limits. #9 also has official bitmap inconsistencies; no invented cause.

Current next: finish#10 then all remaining tickets sequentially. Owner reviews
completed app; no intermediate phone-review gate is required.

## Verification already inspected

Ticket #2: hosted run 37834551583 passed Android 10/17 independent signature
and visible diagnostic checks. See [native proof](../research/NATIVE_PROOF_VERIFICATION.md).

Ticket #3: run 37849279965 at 35dff32 passed both SDKs: eleven instrumentation
tests per SDK, cold-process identity preservation and actual signed 5-to-8
updates. Final two-axis reviews passed through 1b50e2f; a1973c2 is its final
status/evidence commit. Recovery fails closed for missing/corrupt published
wallets. Controlled interrupted-initialization fixtures model crash states;
they do not prove arbitrary physical power-cut behavior. Directory/file sync,
reopening verification and a nonsecret publication marker prevent Retry from
silently replacing an exposed identity. Detailed failures and fixes are in
[wallet verification](../research/WALLET_PREVIEW_VERIFICATION.md), with durable
public evidence under docs/evidence/ticket02.

Ticket #4: full run 37857285246 at 4fd87bd passed both SDKs, including separate
fresh native owner reads, cached cold launches, wallet recovery and signed
updates. Downloaded outputs/screenshots were inspected; public feed evidence
is retained under docs/evidence/ticket03/first-full. This predates later fixes.
Focused run 37858491264 at c17e0b5 passed reconnection and corrected refresh/
recreation position checks. It exposed a missing cached unreadable warning
(now fixed) and a storage fixture error: `.new` is not Android 10's write path.
The corrected fixture obstructs the actual public cache base and restores its
preserved contents in finally, without changing wallet storage. All four final
controlled feed cases passed on both SDKs in 37859209011, before its later live
Android 17 failure. See [feed verification](../research/NATIVE_FEED_VERIFICATION.md).

## Build and review operations

Repo: /home/david/projects/redsquare-android, private GitHub robelene112/redsquare-android,
main branch. Kotlin/JVM core plus Compose Android app, Android 10+; Docker
supplies Java 17, Gradle and SDK. Run scripts/android-build for local checks.
Hosted KVM provides actual Android 10/17 runtime verification. Intermediate
focused checks do not replace final full verification. Capture failures and
separate functional failures from fixture/build/emulator setup errors.

Stable review: https://davidengelen.nl/redsquare/. Versions5,6,8,10 published;
2,3,4,7,9 remain unpublished. Version10 source is5225d06. Signing state
is private under the operator's local share directory, outside Git/public
hosting; never print or copy its contents. Public certificate fingerprint and
publication/update checks are documented in [review operations](../REVIEW_BUILDS.md).
Review publication touches only the app's existing static directory.

Use project-local implement/tdd/two-axis code-review and applicable Saito
skills. Node access follows [operations](../OPERATIONS.md) and maintained Snake
routing. Production configuration, block production, on-chain actions and store
submission require their own explicit authorization. No production writer was
changed for the Android app. Read-only public integration is separate from
ordinary controlled tests; no production transaction was broadcast.

## Outstanding external acceptance

Owner requested physical app review at completion. Pixel 7 Android 17 checks
are tracked in #35. Emulator results do not establish physical timing, updates
or push behavior. Use [the timing protocol](../research/FEED_PERFORMANCE_PROTOCOL.md).

Closed-app notifications require Firebase access and a gateway. A prior optional
one-question prompt asked dedicated versus existing Firebase project; no answer
has arrived. Continue independent work and never invent credentials or upload
wallet private keys. Source research is documented, not implemented delivery.

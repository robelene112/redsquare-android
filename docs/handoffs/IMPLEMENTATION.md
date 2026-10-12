# Sequential implementation handoff

Updated 2026-10-12. The owner authorized implementing all 35 proposed
tickets sequentially, committing after each, and reviewing the app at the end.
Continue without routine permission requests or a grill per ticket. Ask only
one question at a time when a material choice really needs the owner.

## Current work

#23, "Show balances/receiving details and send authenticated SAITO", is assigned
and active. Its sole blocker #7 is closed. Whole-ticket review base:
`2fe1fa51ed8d0c0e250d77e7c2f4198809418fdd`. Parent seams1–4 and sequential
implementation are authorized; do not ask again. Eighteen of35 tickets are closed.

Payment chronology and original evidence are in
[NATIVE_TRANSFER_VERIFICATION.md](../research/NATIVE_TRANSFER_VERIFICATION.md)
and `docs/evidence/ticket22/`. The folder follows the ticket's delivery number22,
although its GitHub issue is #23. The source contract is
[NATIVE_SAITO_TRANSFER_CONTRACT.md](../research/NATIVE_SAITO_TRANSFER_CONTRACT.md).

Current implemented and verified slices:
- Selected-backend reported balance, receiving address and exact clipboard copy.
- Independently compatible funded payment construction/restoration, exact
  recipient/amount/fee review and real Android device authentication before send.
- Durable wallet-wide input reservations, no automatic monetary queue/send,
  real PIN cancellation with no send or reservation, exact explicit retry with
  fresh review/authentication and original selected backend.
- Activity recreation and actual process-death recovery preserve the same wallet,
  backend and candidate, emit nothing automatically, and require a fresh PIN
  before byte-identical retry. Host-owned test PIN cleanup is positively checked.
- Full-block content verification and exact saved-payment matching, including
  assigned output coordinates, original input-coordinate discrimination, and
  odd entries containing opaque GoldenTicket and empty Fee payloads.

Latest Android17 focused proof:38193034021 at1818ea7, ordinary3PASS70.424s,
actual death4698→4814/cold1PASS9.897s/credential cleanup1PASS0.062s;5 completed
cases/3 records/1 interrupted stage/core57. Both actual frames independently
pass pinned WASM/Elliptic.22 hashes in `process-shared-green/android17`. Root
inspected four separate XMLs; two original PNGs were fully viewed and two tool
strips were proven byte-identical to the fully viewed Pending original. Both
bounded source/evidence axes are clear. Earlier genuine retry RED and fixture
interruptions remain qualified in the verification chronology.

Latest core proof: all59 pass at172ac46 after the narrow shared raw transaction
preimage review fix.16 hashes include all14 original core XMLs. Both bounded
input-coordinate/source-fix/evidence axes are clear. Sourcepush38194173861
passes release/lint/core59. Odd-entry regression at82ed842:3PASS31s, four hashes
in `block-odd-entries`; sourcepush38194771230 passes. Spec0; Standards0 production
hard breaches, one current-handoff correction and one header-signing duplication
judgment. The review-stage corrections now consolidate this active status and share only
actual header signing, preserving literal vector assertions and original evidence.

Current vertical slice: exact payment inclusion through verified contiguous
ancestry from a selected-server snapshot. Independent synthetic descendant
482bytes/tipbf72deac...; compiled missing-reader RED1failure47s followed by
GREEN1PASS48s. A valid but unrelated ancestor containing the payment cannot
prove membership: retained positive plus regression2PASS28s. Eight hashes in
`chain-reference-first`. The shared signing helper produces byte-identical full
reference JSON. Bounded source/evidence review pending. Android status, durable
last observations, reorg/unknown handling and HTTP budgets are not implemented.
After those, complete receiving QR, meaningful remaining failure cases, final
full Android10/17 and whole-ticket two-axis review. Payments remain unfinished.
No new phone APK, Pixel performance result, live transfer or node change.

## Earlier completed ticket checkpoints

Profile #18 is closed, final evidence commit ed5742f pushed. Eighteen of35 tickets
are complete (#2–#15, #18, #20, #21 and #22). Parked #19, "Register an optional Saito username",
parked on the external Registry prerequisite, review base ed5742fd0f57958d284073b7145f1844b72b1dbd. #16 Giphy still awaits
Android-authorized credentials; #17 actual YouTube provider playback remains
unproved. Do not close those from controlled source/layout successes.

Closed #18 reference: NATIVE_PROFILE_EDIT_VERIFICATION.md and
 ticket17/REVIEW.md/final-full/README.md. Both source axes clear throughb0e941b.
Final full10run38110790124 and17run38110321794 have145reported cases,
41completion records/13actualprocessdeaths each;10 has144PASS+oneAPI30+
assumption,17all145PASS. Core49, release/lint, affected regressions, publicfeeds
and signed5→8 update continuity pass. Production/build/reference/scripts identical
between b0e941b/aea25ca; only test/evidence/docs differ. No physical-device/current
DOM/chain proof. Do not repeat unaffected checks for historical #18 acceptance.

#19 native checkpoint10c59c5 now passes final full10run38130613902 and
17run38130615551. Each161reportedcases/43records/14actualprocessdeaths;10 has
160PASS+oneexistingAPI30+profileassumption,17all161PASS. Core55/0/0,
release/lint, both opted-in publicfeeds and existing signed5→8/cold continuity
pass. Username15PASS79.348s/89.192s plus actualcold1PASS3.476s/4.193s,
PID19067→19169/9653→9734 exactsamewallet/backend/sig/frame/envelope;
no auto send, explicit exact Retry. Both current nativeframes independently
pass pinnedWASM/Elliptic. Authenticated retirement corruption/repair/newrequest,
own-name cache warning retention/repair and wallet safeguards pass.

Implementation/evidence chronology: NATIVE_USERNAME_CONTRACT.md,
NATIVE_USERNAME_VERIFICATION.md; ticket18/final-full and other retained RED/GREEN
folders. Root inspects16criticalusernamePNG+XML and4publicfeeds. Both final
review axes complete:0 native/source findings,1 external Spec acceptance gap. No new APK/Pixel/chain/live registration.

#19 stays OPEN for demonstrated external Registry readiness: native10/17 public
known/unknown namechecks unusable21bytes; fresh host09:53:48Z onboth returns
{"err":"no response"} despite authenticated archive/key-map reads. Host helper
filtererror is its own summary defect, not server key-map failure. Source/host/
native observations remain distinct. No node changes/trust fallback/livewrites.
#20, "Search posts and people in available backend history", is CLOSED.
Final evidence commit99e58c6 is pushed; closure comment6110277037 records proof. Review base is
3be555be85586b68c0d05c2c8e69d3976bbffe89. Accepted seams 1–4 apply.
No additional owner grill or intermediate phone gate is required.

Implementation includes signed posts/replies/quotes on the selected backend,
independent bounded paging and partial retry, available offline cached history,
profile/thread/back/recreation navigation, canonical exact keys, verified exact
usernames and prefixes of known accounts. Historical names receive provenance.
Name-check failures are independent of post history. Post keywords and prefixes
are matched locally; an explicit exact-username check sends that public identifier
to the selected Registry service. Save warnings and retained author edit/deletion proofs are
integrated. No exhaustive chain history or permanent archive is promised.

Whole-ticket source review through fd35b02: Spec 0 actionable findings;
Standards 0 residual hard violations or actionable heuristic smells. Subsequent
existing posting import fixture corrections also clear both axes. Contracts and
chronology: NATIVE_DISCOVERY_MODERATION_CONTRACT.md, NATIVE_SEARCH_REFERENCE
(.md/.json), NATIVE_SEARCH_VERIFICATION.md and ticket19/REVIEW.md.

Final full acceptance results, each opted into both public backend reads:
- Android 17 run38145373388 at6c489bf: 170 reported/all PASS, 44 completion
  records/14 actual process-death stages. Search9PASS58.789s.
- Android 10 run38147131217 at7b4dd14: 170 reported/169PASS plus one existing
  API30+ profile assumption, 44 records/14 actual process-death stages.
  Search9PASS54.238s; corrected posting18PASS100.284s.
- Both: core55/0/0, source/debug/test/release/lint, signed5-to-8 updates and
  actual cold wallet/cache continuity PASS. Public owner/official each1PASS:
  17 takes9.678s/7.672s; 10 takes10.869s/14.748s. Each Search ordinary stream
  returns100rows/90posts/0ignored; quotes4rows/0commentaryposts/0ignored.
  Counts do not establish exhaustive history, explain row/post differences,
  chain inclusion or external Registry readiness.

Root individually viewed all15 Search originals and separately parsed15XMLs on
EACH SDK, plus both fresh public feed originals on each. Durable
 ticket19/final-full/android10 and android17 retain119hashes each and all raw
countable completion/death logs. Root recomputed durable counts equal to the
original artifacts. Android17 bounded evidence reviews: both axes0 findings.
Android10/final evidence review also complete: both axes0 findings or closure
blockers. Both independently verify119hashes, counts, originals and paired source
equivalence. No need to repeat accepted #20 verification.

Two earlier full10 failures remain in full10-first-failure and
full10-second-failure. The first stopped at an old wallet-picker selector; exact
scheduling cause remains unproved. The next failed an introduced direct File
metadata guard before the maintained chooser ran. Fixture-only correction uses
fixed run-as stat under the provider identity and maintained native drawer/preview
synchronization. Final full10 passes the unchanged safeguards. Production/core/
build/reference/scripts/workflow and Search/public acceptance assertions are
identical between successful full17 and full10, as allowed by WORKFLOW.md.

No new phone APK, actual Search process-death, Pixel performance or live write
claim. Stable review remains version10/source5225d06. #19 remains externally
parked. #21, "Mute and block accounts across backend sessions", is CLOSED (closure comment6112726266).
All blockers complete (closed#8); review base99e58c6a49f5a10ee630f948ad3a18c9f5fb5592.
Implementation and focused verification are complete: independent encrypted
wallet-scoped Mute/Block across both backends; filtering in Feed, Profile,
Conversation, Search and quoted originals; management and truthful save/recovery
errors. Chat/notifications are dependent tickets, not delivery claimed here.

Focused run38161237509 ataa91ff2 passes8ordinary cases111.163s, including actual
SAF/authenticated wallet roundtrip/full opaque options, corruption repair and
actual PIN cleanup. Separate pending-save process death5044→5131 passes one
independent cold case6.427s without identity/backend/policy reset. The durable
previous-policy guard and independent rollback resolve the demonstrated late
save/removal/recreation defect. Root inspected the final six identity/corruption
and four process original PNGs, then separately parsed all ten XMLs. See
NATIVE_PERSONAL_CONTROLS_VERIFICATION.md and ticket20/identity-green/28hashes
for the full qualified chronology. Whole source and bounded focused reviews are
clear. Both final full Android17 and Android10 acceptance passed, as recorded below.

Completed final acceptance runs and earlier fixture interruptions are below. Preserve the
six future research files; continue one ticket at a time. No new phone APK.

Protected future untracked: build-support/references/private-chat-reference.cjs;
NATIVE_NOTIFICATIONS_CONTRACT,
NATIVE_PRIVATE_CHAT_CONTRACT,
NATIVE_GROUP_CHAT_CONTRACT, NATIVE_PRIVATE_CHAT_REFERENCE(.json/.md).
Include only the relevant research with its ticket. Review agents inspect only;
never run parallel implementation/CI dispatch or claim another ticket.

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

Next: continue active #23 sequentially. Owner reviews the
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

Repo: /home/david/projects/redsquare-android, public GitHub robelene112/redsquare-android,
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

## Completed personal-controls acceptance checkpoint

#21 is CLOSED, comment6112726266;17/35 closed. Final product checkpoint aa91ff2,
whole-ticket review base99e58c6. Subsequent changes correct existing fixtures
and retain evidence; production/core/build/reference source is unchanged.

- Full Android17 run38161793299 at781bd1a PASSES:179cases/46records/15actual
  process deaths; core55/release/lint/signed5→8/cold continuity and both public
  reads pass. Personal8PASS82.867s, death10423→10513/cold1PASS6.65s. Root all29
  original personal PNG/separate29XML plus2public originals inspected. Durable
  final-full/android17 retains162hashes; both evidence review axes0final findings.
- Android10 full38163120112 atf0c6191 stops in the existing author delete
  fixture. Corrected offline profile tests pass. Fixture732e67f waits for
  displayed/enabled Confirm delete and captures live PNG/XML before one click,
  keeping all subsequent assertions. Focused author38164253681 PASSES
  ordinary8cases46.026s plus2independent cold cases/2actual deaths:10/3/2.
  Root2readiness originals/separate2XML; author10-ready-green retains13hashes.
- Focused controls38164297170 at732e67f passes all8ordinary cases74.394s but
  host metadata check stops before cold recovery. Discarded command output
  prevents proving cause or guard absence. Corrected host fixturefe710dd uses
  established stat positive-size gate and retains metadata only. Retry
  38164957454 PASSES8ordinary73.893s and actualdeath4897→5093/cold1case5.679s:
  9/2/1, guard return0/213bytes. Same wallet/backend, independent choices,
  actual peer keys and zero transactions pass. Root4process originals and
  separate4XML inspected. controls10-metadata-green retains18hashes.
- Both bounded focused review axes clear througha43e046. One evidence-index
  finding fixed by retaining process logs under their counts.json paths; root
  and reviewers reproduce exact durable9/2/1 and all18hashes. Sourcepush
  38164949469 passes. Python harness compile passes. No product/acceptance
  predicate changes; WORKFLOW permits retaining full17 after fixture correction.
- **Full Android10 run38165304273 atfe710dd PASSES**, opted into public
  reads:179reported/178PASS+existingAPI30+profileassumption,46records/15deaths,
  core55/release/lint/signed5→8/cold continuity. Personal8PASS68.771s and
  death21951→22148/cold1PASS5.708s, guardstat0/213bytes. Root all29original
  personalPNG/separate29XML,2author readiness originals/separate2XML and both
  public originals inspected. Durable final-full/android10 retains168hashes;
  exact root recount179/46/15+assumption equals original. Both public feeds
  pass8.2s/4.011s; ordinary100rows/92posts/0ignored andquotes4rows/0posts/0ignored.
  Final bounded pair evidence review through7f54d98 clears both axes after
  correcting2Android10captions. Acceptance complete; GitHub closure recorded2026-10-11T19:17:39Z.

Failure chronology and qualified retained captures: NATIVE_PERSONAL_CONTROLS_VERIFICATION.md
and ticket20/REVIEW.md. Earlier full10 stopped at existing lazy-profile
pre-scroll/readiness fixtures; finalf0c6191 corrects all3tabs. No offscreen
cache/deletion/timing cause was inferred. Preserve already accepted full17.

## Current reporting acceptance checkpoint

#22 is CLOSED2026-10-11T23:23:14Z, comment6115065628, final acceptance
commita797276 pushed;18/35complete. Whole-ticket review base9ecb97b. Product source complete
atac808f9; whole Standards0/Spec0. Reports name the selected destination before
consent, use compatible signed type12/self outputs/no type4, and distinguish
strict handler acknowledgement from moderator review/removal/chain inclusion.
Wire14/application errors remain delivery-uncertain and persist through reopening;
missing/malformed replies remain unknown. Exact signed intent is saved before
possible transmission; explicit Retry preserves candidate/wallet/backend. Noauto
resend, rerouting or removal. Newer genuine receipts are adopted atomically rather
than overwritten or resent. Accepted parent seams1–4 already authorized.

Final full acceptance PASSES on both SDKs, separately opted into public reads:
- Android10run38180155391 at709b58d:191reported/190PASS+existingAPI30+profile
  assumption,48records/16actual interrupted stages; report11PASS92.703s,
  actualdeath23785→23879/cold1PASS4.34s/635-byte record.
- Android17run38181575469 at92d80f3:191/allPASS/no assumptions,48records/16
  actual interrupted stages; report11PASS108.155s, actualdeath11147→11225/
  cold1PASS5.35s/635-byte record.
- Both core55/0/0, release/lint/debug/test, signed5→8/cold wallet/cache continuity
  and both approved public reads pass. Native reports preserve originalidentity/
  backend/candidate, noauto/exact explicitretry/no type4. Each public metadata
  read returns100rows92posts0ignored/4quote rows0commentaryposts0ignored;
  ordinary owner/official timings10:9.48s/6.512s,17:8.055s/7.132s.

Root individually viewed41ordinary+3process originals/separate44XML and2public
originals perSDK. Qualified ACK strip byte-equivalence and public header-only
observations are in eachREADME; retained Conversation still Loading replies,
not completed thread-loading proof. Runtime candidate/report count/authentication/
full opaque wallet restoration gates are separate from bitmap claims. Each durable
final-full SDK folder retains202hashes/allcountable logs, exact original/durable
191/48/16 recount (with10's sole documented assumption). Both final10 bounded
evidence axes0. Final17 paired evidence review through6d3888a also clears both
axes:0findings/no acceptance gap, independently verified202hashes/rawcounts/
originals/continuity/source-equivalence. Sourcepush38183784351 PASSES.
Ticket22 acceptance is complete and its GitHub closure is recorded above.

Full17 includes a reviewed existing-image fixture correction after prior setup
FileNotFoundException; product/core/reference/build/harness/workflow unchanged
709→92. Acceptance predicates retained; recognizable oversized source has a new
literal positive-size setup guard. Both source/docfix axes0 through92, focused
images17run38181457132 passes20/5/3 with13hashes; both evidence axes0 ate8b45e5.
WORKFLOW companion rule permits retaining full10. No successful rejection PNG/XML
or exact provider failure cause claimed. Earlier browse/posting viewport failures,
restored-receipt RED, timeout/save/failure/isolation chronology and all qualified
native/independent proof live in NATIVE_REPORT_VERIFICATION.md andticket21/REVIEW.md.
Do not reconstruct or repeat accepted checks from this handoff's old chronology.

Next ready: #23 balances/receiving/authenticated SAITO transfers, not yet claimed. Read-only preparation .scratch/ticket23-readonly-plan.md records
public parameter observations/pure reference gaps; it is not implementation or
acceptance. Protected future files remain untouched. No newphone APK/Pixel/live
report/moderator outcome/permanent history/original scheduler reproduction claim.

## Outstanding external acceptance

Owner requested physical app review at completion. Pixel 7 Android 17 checks
are tracked in #35. Emulator results do not establish physical timing, updates
or push behavior. Use [the timing protocol](../research/FEED_PERFORMANCE_PROTOCOL.md).

Closed-app notifications require Firebase access and a gateway. A prior optional
one-question prompt asked dedicated versus existing Firebase project; no answer
has arrived. Continue independent work and never invent credentials or upload
wallet private keys. Source research is documented, not implemented delivery.

Giphy Android credentials are also unresolved. An optional single question on
2026-10-10 asked whether an existing Android-authorized key is available and
where configured; no response yet. Do not ask again routinely, invent a key or
assume a backend's browser key is authorized for Android. Source contract is in
NATIVE_GIF_VIDEO_CONTRACT.md. Continue independent tickets and controlled
implementation while external configuration remains pending.

Read-only research notes for later tickets were prepared during hosted checks:
NATIVE_QUOTES_CONTRACT, NATIVE_EDIT_DELETE_CONTRACT, NATIVE_IMAGES_CONTRACT,
NATIVE_GIF_VIDEO_CONTRACT, NATIVE_PROFILE_EDIT_CONTRACT, NATIVE_USERNAME_CONTRACT,
NATIVE_NOTIFICATIONS_CONTRACT,
NATIVE_SAITO_TRANSFER_CONTRACT, NATIVE_PRIVATE_CHAT_CONTRACT and
NATIVE_GROUP_CHAT_CONTRACT (all .md under docs/research). The protected future notes remain uncommitted and must be included only with
their own relevant tickets; earlier contracts are already committed.
They establish source contracts, not runtime or feature completion.


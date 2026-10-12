# Sequential implementation handoff

Updated 2026-10-12. The owner authorized implementing all 35 proposed
tickets sequentially, committing after each, and reviewing the app at the end.
Continue without routine permission requests or a grill per ticket. Ask only
one question at a time when a material choice really needs the owner.

## Current work

#23, "Show balances/receiving details and send authenticated SAITO", is now
assigned and active. Its sole blocker #7 is closed. Review base
2fe1fa51ed8d0c0e250d77e7c2f4198809418fdd; first tracer is the native balance and
receiving-address flow against an isolated synthetic snapshot. Parent seams1–4
are already authorized. NATIVE_TRANSFER_VERIFICATION.md records new evidence;
NATIVE_SAITO_TRANSFER_CONTRACT.md is the relevant research for this ticket.
Minimal checked balance/receiving/copy source is committed at48e8c44. Native
missing-action RED38184572679 and GREEN38184994485 (1PASS14.8s) are established.
The independent funded WASM/Elliptic vector and native constructor also pass;
zero-payment restore remains strict. Current evidence: ticket22/balance-green
and ticket22/payment-reference. Funded restoration and shared-serializer review
fixes now pass all57 core tests (0 failures/errors/skips); see ticket22/payment-restore.
Their bounded source reviews both report0 findings.
The authenticated send cycle now passes focused38187897889 at0b0d75d
(2PASS44.412s, core57). Root views4originalPNG+separateXML, and the actual
762-byte frame independently passes pinnedWASM/Elliptic verification. Source
push38187897999 also passes release/lint. Both source fix reviews0findings.
See ticket22/authenticated-send-green/android17 and the qualified earlier failures.
The real-PIN cancellation/reopen regression passes38188796688 at9a8b043
(3PASS49.799s/core57), local compile1m22s and source release/lint38188796707.
Root views2originalPNG+separateXML;5hashes in ticket22/auth-cancel-green/android17.
Both bounded source axes0findings. Same identity, all7.5 available, no Pending or
frame and positively cleared PIN. Existing-policy coverage, no fabricated RED
or production change. Both cancellation evidence review axes also0findings.
The next tracer extends the authenticated send journey with Activity recreation,
no automatic resend and exact reviewed/fresh-PIN retry. Retry is currently absent;
local test APK compile passes1m18s and both bounded test-source review axes0.
Focused38189355347 atb117ddf stops atline67 Review visibility after Back
(3cases/1failure59.925s), before initial auth/reopening/Retry. Cancellation and
balance still pass; source release/lint38189355162passes. Root originalfailure
PNG+separateXML shows Wallet/formclosed; preceding IME state unrecorded. This is
a fixture interruption, NOT missing-Retry RED.3hashes in retry-fixture-failure.
Correction conditions keyboard dismissal on positively observed real IME and
retains all assertions; local test APK compile passes1m22s/bothboundedreviews0.
Corrected38189921586 atea0df3d reaches genuine missing-Retry actionREDline116
(3cases1failure54.444s/core57/sourcepush38189921312releaseLintPASS). Initialsend
and reopening samewallet/Pending/zeroavailable/exactly1frame pass; no auto resend.
Root views2originalPNG+separateXML/5hashes in ticket22/retry-red/android17.
Minimal explicit exact-candidate retry is being built: fresh reviewed device
auth, original selectedbackend, fresh exact eligibleinputs, and locked physical
enqueue rechecks of saved/live identity/backend and exact durable candidate.
No re-signing/newreservation/automatic resend. Debug/test build passes2m37s;
focusedGREEN38190583142 atf37feba passes3cases66.944s/core57 and sourcepush
38190582887release/lint. Both boundedsourceaxes0. Root3originalPNG+separateXML
and independently verifies actual762byte frame (hash5005caf2...) with purepinned
WASM/Elliptic;9hashes in ticket22/retry-green/android17. Samewallet/Pending/0
after Activityrecreation/noauto, freshrealPIN/exact2ndframe/clearedPIN pass.
Both boundedGREEN evidence axes through849a960 report0 findings;
inclusion/reorg and failure/process-death acceptance remain separate. Activity recreation is not process
death, nor complete retry/inclusion acceptance. Failure/retry/recovery/inclusion, receivingQR
and final10/17 remain.
Payments and their recovery, compatibility and final acceptance remain unfinished.

Next existing-policy tracer adds TransferProcessRecoveryTest and the host
transfer-process-check, invoked by focused/full transfer scopes. It deliberately
kills the real authenticated pending-send process and cold-recovers without
resetting wallet/backend/work. Require no automatic frame, exact review and
fresh real PIN before the byte-identical explicit retry; positively verify
host-owned credential cleanup. Corrected local test APK compile passes1m28s;
initial52s compile failed at an incorrect fixture public-signature reference.
Python/shell syntax checks pass. Focused38192305814 atd0db1e3 passes ordinary
3cases47.058s,actualdeath4785→4877/cold1case8.964s andpositivecredentialcleanup
1case0.043s:5/3/1. Core57/source/debug/test plus sourcepush38192305645release/lint
pass. Root4originalPNG/separate4XML, bothactualframes independentlypass pure
pinnedWASM/Elliptic;25hashes in ticket22/process-green/android17. Baseline Spec0,
Standards0hardbreaches/1duplication improvement. Narrow shared funding/IME
fixture extraction test APK compiles1m21s, bothboundedfix/evidence axes0findings.
Rerun38193034021 at1818ea7 passes ordinary3cases70.424s/death4698→4814/cold1
case9.897s/positivecleanup1case0.062s:5/3/1/core57. Sourcepush38193034164release/
lint passes.22hashes in process-shared-green/android17; root4XML,2fullPNGand2
toolstrips byteidentical to fullyviewedPending original; bothactualframes
independentlypass identical421370ca...hash. Current rerun evidence review pending.
No missing-feature RED is assumed for behavior already implemented.

Next seam2 tracer introduces SaitoFullBlock public full-reader/exact saved-payment
matcher. PurepinnedWASM/Elliptic synthetic1010byteblock reference runs0.38s,
fullserialization/outputassignment/hashb33d2fc4...verified. Native compiled
unimplemented-reader RED49s →minimalGREEN49s →allcore58PASS18s.8hashes in
block-reference-first; source/evidence reviews pending. This is content binding,
NOT currentselectedchain membership/consensus/finality. Odd/system/SPV/resource/
largeinteger/mutation and ancestry/reorg cases remain, then Android inclusion UI.

The next sole existing-policy input-coordinate mutation regression passes:
independent WASM retains blockhash/paymentleaf/signature despite changed first
positive input block2000000→2000001; native refuses to match the saved original.
Retainedpositive+newcase2PASS29s, no production change/fabricatedRED.4hashes in
block-input-coordinates; source/evidence review pending. No other mutant cases
added. First full-reader Spec review throughd99c779 has0findings; Standards has
0hardbreaches/1preimage-duplication improvement, nowfixed narrowly across actual
Envelope/fullblockbinary consumers; caller checks unchanged. Full core59PASS50s;
all14XML/16hashes in block-preimage-review-fix. Input-mutation/source-fix/evidence
reviews pending. Sourcepush38193713102 atd99release/lint/core58 passes.

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

Continuation 2026-10-12: bounded original-input/shared-preimage reviews at172ac46
are both clear; sourcepush38194173861 passes release/lint/core59. Next single
odd/system-entry regression independently computes a three-leaf root with
opaque GoldenTicket and empty Fee surrounding the saved payment at ordinal1.
Native public reader/matcher3PASS31s; four hashes in block-odd-entries. No
production change or fabricated RED. Bounded source/evidence review pending.
#23 stays active; selected-chain status, receiving QR and final acceptance remain.

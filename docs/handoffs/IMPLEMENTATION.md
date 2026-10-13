# Sequential implementation handoff

Updated 2026-10-13. The owner authorized implementing all 35 proposed
tickets sequentially, committing after each, and reviewing the app at the end.
Continue without routine permission requests or a grill per ticket. Ask only
one question at a time when a material choice really needs the owner.

## Current work

#24, "Show in-app social notifications", is complete after both full emulator
runs and both final evidence reviews. Twenty of35 tickets are closed
(#2–#15,#18,#20–#24). No new phone APK; stable previewv10/source5225d06.

Final notifications source68633d976a5ef6dfa4ce38d982af46ea2f24edb1;
whole source reviews d1e65f5...0798b55 both axes0 after self-like warning and
remaining-parent discovery fixes. Full10run38330887204 atb80b878 passes236
reported/235PASS+one existing API30profileassumption/54completed/18actualdeaths.
Full17run38328912121 ata5648ee passes237PASS/55completed/18actualdeaths.
Each passes core74/release/lint/debug/test/signed5->8/cold wallet/cache and both
separately enabled public reads. Production equivalent; reviewed test scrolling
and diagnostics-only changes qualify under WORKFLOW companion rule. Final
full10 evidence review982c502...9befdf8 both axes0; full17 Spec0/Standards0source
findings and one corrected caption. Durable evidence: docs/evidence/ticket23/
final-full/android10 and android17; canonical chronology and separate reports
remain NATIVE_NOTIFICATION_VERIFICATION.md and docs/evidence/ticket23/REVIEW.md.
Failed intermediate runs remain retained without invented causes. Earlier
accidental auto-close was reopened; close only after final acceptance.

#25, "Exchange encrypted private Chat messages with existing clients", is assigned
and active. Review basec102f891f6041f08199905728d302e19b7588abd. Read parent#1/issue25, NATIVE_PRIVATE_CHAT_CONTRACT and reference docs.
Accepted seams1–4 cover native UI/controlled peers, independently signed vectors,
recovery/isolation and compatibility. Record the new review base after #24's
completion commit. Implement sequentially using TDD. No additional grill needed.
Cache recreation GREEN10run38342240579 at0620289 PASS1/8.768s; originalPNG/XML inspected. Both bounded cache reviews found loss on Refresh after failed save. Genuine RED10run38342655104 ate8f6808 reports2cases/1PASS+1failure22.977s atline93; original captures prove message loss, no actual process death. Minimal retained-entry/ciphertext fix core82/debug/testAPK3m5s PASS. Actual10run38343321818 at43399a7 PASS2/9.458s; root fourXMLs/threeoriginals inspected. Both fix reviews0codefindings; current UI README drift fixed. Delayed live-relay RED10run38344010177 atdb62258 reports3cases/2PASS+1failure26.503s; failure original shows completed empty conversation, no message. Minimum live-selected-conversation handler keeps authenticated peer open and persists ciphertext before acknowledging; core82/debug/testAPK3mPASS. Actual10run38344712980 atfed20de PASS3/12.975s; original fiveXMLs/live+recreated PNGs inspected. Source reviews Spec0/Standards1 stale-warning ordering. Two-line fix d62a964 compiles Kotlin1m25s, both fix reviews0; corrected17run38344995062 PASS3/41.36s, fiveXMLs/threeoriginals inspected. FirstChat optional-permission actualRED10run38345645504 ata3b148a reports4cases/3PASS+1failure28.341s; root sixXMLs/failureoriginal inspected. One-call sharedprompt integration Kotlincompile1m23sPASS; same-source actual4case10run38346218117 PASS4/18.809s and17run38346220216 PASS4/51.82s; each root eightXMLs/offer+recreated originals inspected. Both bounded source reviews0. Native outgoing send/Relay actualRED10run38347083896 atf072178 reports5cases/4PASS+1failure36.397s line173 missing Message. Root nineXMLs/failureoriginal inspected. Minimum composer/workstore/claim/propagation/Relay now core82/debug/testAPK container exit0/SUCCESS3m2s; command connection exit143 separately retained. Actual5caseGREEN10run38348001347 ate06a22c PASS5/20.851s; root tenXMLs/threeoriginals inspected, recreated outgoing lastline clipped until scrolling. Independent existing-client Crypto/WASM verifies actual unsafe native random-salt envelope; default reference unchanged. Reviews Spec0/Standards1 incorrect Refresh recovery guidance. Guidance split and compact composer compile1m26s/1m22s; corrected17runtime/fix reviews next; next native send/listener and actual recovery/isolation. No sending, list listener, actual death/fullSDK or ticket completion inferred.
First read-only history UI10run38340335366 at7e5811e PASS1/4.923s; core82, originalPNG/XML inspected and retained. Both bounded source reviews0. Cache/recreated-screen tracer compiled1m34s; actual RED next.
First core receive tracer RED: missingdecoder/31s; GREEN1/56s. Send tracer
RED: missingcreate/28s; GREEN2/58s matches exact pinned ciphertext/signature/
network/envelope and restore. Fullcore76/debug/testAPK passed2m59s. Imported-secret tracer actual RED3/1
failure55s, then fullcore77/0fail/0ignored GREEN1m. Source protocol increment7dbaba3 reviewed: Spec2 (legacyfield/case), Standards2
(case/formatter primitive). GenuineRED then minimal fixes now fullcore81 GREEN1m.
RelayJSON independent inner serialization/read also passes4/56s. Final reference
hash886ff622...ee1c06 (fourvalid/threecorrectlysignedmalformed), twice identical
isolated executions. Bounded fixes review Spec0/Standards0hard+1duplicate-string heuristic.
Shared validator applied; core81/debug/testAPK3m24s PASS. Actual peer discovery
tracer cleancompileRED2m33s (missingservices only), minimum sourceGREEN above.
Initial fixture trailing-lambda break fixed, mixedRED and stopped143 qualified
under evidence/ticket24/peer. Discovery0997419 both source reviews0; actual10run38337011297 PASS1/0.471s,
core81/source gates, four hashes/no UI captures. New unsignedRelay receive
core RED31s→GREEN8/1m2s validates original/forgedinner rejection. Incoming
socket callback RED2m7s→minimum source core82/debug/testAPK3m12s PASS. Incoming source099...3c35 both axes0codefindings; handoff untracked note fixed.
Actual10run38338221475 PASS2/0.788s/oneOK/noassumptions/deaths/core82; four
hashes/no UI or durable-cache proof. First available-history UI tracer compiled
1m43/cleanup1m26; missingChat UI runtime RED next; UI/privatecache/send/backend/recovery pending. First actualUIRED10run38339184559 at6df3ef0: missingChat beforecontact/history,
1failure18.154s/zeroOK/deaths/assumptions/fivehashes/rootoriginalPNG+XML.
Minimum Chat menu/contactlist/verified availableArchive reader core82/debug/
testAPK3m34PASS; nativeGREEN next. Privatecache/send/recovery not implemented.
Both public backends advertiseChat/Archive/Relay and accept freshunknownGroup
load0rows in read-only nonce/TLS probe; no live writes or KotlinpublicDM claim.
Chronology NATIVE_PRIVATE_CHAT_VERIFICATION.md.
Private contract/reference/generator and native resources are committed with
#25; NATIVE_GROUP_CHAT_CONTRACT remains protected for#26. Read-only
preparation: .scratch/ticket25-readonly-plan.md. Chat signing must authenticate
JSON-string ciphertext without weakening existing object consumers, use exact
CryptoJS-compatible encryption, and preserve private owner/backend cache.

No production node/funded/live writes, physical Pixel proof, closed-app push or
store submission authorized. Continue ready work autonomously.

## Earlier completed ticket checkpoints

Profile #18 is closed, final evidence commit ed5742f pushed. Nineteen of35 tickets
are complete (#2–#15, #18, #20, #21, #22 and #23). Parked #19, "Register an optional Saito username",
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
remaining group-Chat research file; continue one ticket at a time. No new phone APK.

Protected future untracked: docs/research/NATIVE_GROUP_CHAT_CONTRACT.md for#26.
Private Chat and notification references/contracts are already committed.
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

Historical after-wallet milestone note: continue #23 sequentially. It is now closed; active work is #24. Owner reviews the
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

Historical next-ready note after #22 closure: #23 balances/receiving/authenticated
SAITO transfers. It is now claimed and active as described under Current work. Read-only preparation .scratch/ticket23-readonly-plan.md records
public parameter observations/pure reference gaps; it is not implementation or
acceptance. Protected future files remain untouched. No newphone APK/Pixel/live
report/moderator outcome/permanent history/original scheduler reproduction claim.

## Completed authenticated-transfer acceptance checkpoint

#23 CLOSED2026-10-12T15:18:29Z, comment6127183552, finalacceptance commitd1e65f5
pushed. Whole-ticket base2fe1fa51ed8d0c0e250d77e7c2f4198809418fdd; app/protocol
sourcefcd9771e232007e3c4c6702bb3c135661639b654. Both fullSDKs pass that same source:
- Android10run38265052461:202reported/201PASS+one existingAPI30profileassumption,
  51records/17actualdeaths; core66/release/lint/signed5->8/coldcontinuity/publicreads.
  Transfers9PASS87.886s; actual25060->25226/cold5.976s/cleanup.08s.
- Android17run38220054361:202allPASS/noassumptions,51records/17actualdeaths;
  samegates. Transfers9PASS98.055s; actual11646->11742/cold9.168s/cleanup.043s.

Selectedbalance/fullreceivingkey/copy/QR, compatiblefundedpayment/exactreview/
realdeviceauth/cancel/freshretry, durable wallet-wide reservations, verifiedfull-
block/linkedancestry inclusion/Unknown/Reorganization, noautoqueue/rerouting,
funding/storage recovery andrealbackend/SAFwalletisolation verified. Separate
publicwallet journeys read actualapprovedHTTPSfreshunfunded0balance/funding/fullkey/
QR/returnfeed on bothbackends withnoauth/review/send. Notpositivefunding/admission/
consensus/livepayment proof. IndependentpinnedWASM/Elliptic/fullblock/oddMerkle/
wire compatibility remains qualifiedsynthetic evidence.

Finaltwo-axis whole/source/bounded/evidence reviews0residualhard/heuristics/Spec.
200/201finalSDKhashes/exactrawcounts/checkpoints verified. Root33/34nativeoriginal
PNG+35XML each,6publicoriginalPNG+4XML each; IMEfooters/clipboard/black-or-refused
systemPIN/runtimeauthority/SAF/duplicatebytes qualified in final-full READMEs.
No newphoneAPK/physicalPixel/independentscanner/arbitrarypowercut/Home-policy/node
change. Stablephonepreview remainsv10/source5225d06.
Canonicalchronology: docs/research/NATIVE_TRANSFER_VERIFICATION.md.
Separate reports: docs/evidence/ticket22/REVIEW.md.

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
NATIVE_GROUP_CHAT_CONTRACT (all .md under docs/research). Only the group-Chat contract remains uncommitted for#26; other listed contracts
are committed.
They establish source contracts, not runtime or feature completion.

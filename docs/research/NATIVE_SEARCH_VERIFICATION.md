# Native post and people search verification

Active #20, parent #1, review base3be555be85586b68c0d05c2c8e69d3976bbffe89.
Owner authorizes sequential implementation; applicable seams1–4 already accepted.
Native full Android10/17 final verification follows bounded focused checks.

Contract: NATIVE_DISCOVERY_MODERATION_CONTRACT.md. Both pinned web modules
lack text-search RPC, so search verifies available Archive records and matches
locally. Include replies and current quotes; preserve selected backend, public
identity, author edits/deletions and bounded cache. Show available-history limits,
empty/error/partial results separately. Known-people matches and exact signed
username lookup must never treat unsigned hints as ownership or imply a global
prefix directory. Public Registry availability remains a separate known failure.

## First vertical tracer

SearchPreviewTest.anArchivedReplyCanBeFoundAndExploredWithoutLosingTheSearchOrWallet
uses existing independent pinned WASM thread fixtures and an authenticated
controlled peer. General feed has no matching reply. A fixed-field Archive read
without root-only field4 returns the signed root/reply. Assert explicit search,
available-history copy, result→conversation→profile→back, retained query/results
through recreation, same active identity/client keys and zero transactions.
Capture failures inside the Activity scope without replacing the primary error.
Only public emulator caches are reset, not a production wallet or live history.

Focused search/search-android17 scopes retain raw logs and original captures;
full harness also runs this class. Existing scopes/gates are preserved.
Initial test APK compilation PASS1m23s. Native RED run38132968601 at511a214
fails only at the missing Search action,12.99s, before any search request.
Original log/PNG/XML retained in ticket19/first-red; root inspected the in-scope
bitmap and separate XML. First implementation is underway, no acceptance yet.

## First tracer GREEN

Android17 focused38133595364 at eceb9d6:1PASS/14.052s; hosted core/debug/test
assembly pass. Original log/PNG/XML retained ticket19/first-green; root individually
viewed recreated reply/query/server/coverage and separate XML. Runtime verifies
conversation/profile/back, recreation, same exact identity and zero transactions.
Interim Spec0 source findings; Standards1 source race, guarded in working source
but not yet device-verified. People/paging/cache/full acceptance still pending.

## Partial history and paging tracer

The next primary Android test uses100 distinct independent WASM first-page
posts, an older post and a separate commentary repost (NATIVE_SEARCH_REFERENCE).
Ordinary stream succeeds, quote stream fails. Assert retained visible results
and no empty claim; recreation must not repeat completed reads. Explicit Retry
search reads only the failed quote cursor, then Load more reads only the remaining
ordinary cursor and finds the older post; exhausted streams stay stopped.
A subsequent nonmatching query distinguishes verified empty from unavailable,
with same wallet/authenticated client keys and zero transactions.
The reviewer-found old-revision publication race is guarded by captured-list
identity plus revision stamp before assignment. First tracer acceptance assertions
are unchanged. This second test is compiling; native RED pending.

Interim review atcc590f0: Spec1 tracer-sensitivity gap. Added positive reachable
newest-result assertions after quote Retry and older paging, then returns to the
older result for capture. Existing exact cursor/read-count/identity/nosend gates
are retained. Source guard addresses the prior race; final source/device review
is still required. Second RED run38134147254 pending.

Standards review also identified that waiting for the older row's semantics
before scrolling could reject a correctly loaded offscreen lazy item. The tracer
now waits for the settled enabled Search history action, scrolls to the exact
older text, and retains its positive display assertion and exact stream counts.
Both findings concern test sensitivity/reachability, not a reproduced protocol
or application failure. The captured-list/revision-stamp guard is clear in the
committed source review.

Second RED 38134147254 at cc590f0: two cases / 45.209s. Reply tracer PASS with
guarded revision publication; partial-history tracer fails only at absent Retry
search. Earlier positive newest-result/error/no-empty/recreation/read-count gates
pass. Root viewed original failure bitmap and separate XML; artifacts retained
in ticket19/paging-red. Reviewed test-only sensitivity/lazy reachability fixes
are after the observed failure point and precede paging implementation.

## Paging source and interim review fixes

First paging implementation at local commit 42ebbb8 compiled debug/test APKs
in 2m58s. Retained independent cursors and pending streams advance only after
validated publication. Retry resumes failed/unattempted reads; Load more skips
exhausted streams; each action reads at most one 100-row page per stream.

Interim Standards identified continued paging after the 1,000-post retention
bound could discard older matches. Working fix prioritizes discovered matches
within the bound, stops paging at capacity and explicitly explains retained-only
coverage. No 1,000-row runtime claim yet. Both review axes identified that a
prepended quote may leave the keyed list anchored to the previous first result.
The test now scrolls to the exact quote before its unchanged display assertion.
This is source-supported fixture navigation, not a reproduced runtime failure.
Combined debug/test compilation is underway; paging GREEN remains pending.

## Paging GREEN

Combined source/test compilation PASS2m43s at f1ebc3e. Focused Android17
38135039545 at54e806f passes both native journeys,2PASS/40.598s. Hosted
source/core/debug/test assembly PASS. Original log and three PNG/XML pairs
retained in ticket19/paging-green. Root viewed each image separately and its
separate XML. Runtime verifies retention, exact stream reads/cursors, successful
empty versus unavailable and unchanged wallet/noTX. No1000-row/full/live/Pixel
claim. Next single tracer: reopening with unavailable owner server retains
public search results; Settings switching isolates each backend with same wallet.

## Offline cache and backend isolation tracer

One new primary test closes the first Activity after a successful selected-owner
reply search, stops that controlled peer, and opens a fresh Activity. Explicit
same-query search must retain the signed reply while qualifying the failure.
Saito remains untouched until the user switches through actual Settings; its
empty search must not borrow owner history. Returning restores owner query and
results, with the same exact wallet/authenticated client keys and zero TX.
No wallet reset/private writes or actual process-death claim. Initial compile
and native RED are pending; production cache is still absent.

First cache-tracer compile failed57s: the failure-capture helper returns Unit,
so the address capture was corrected to assign the outer test value. Assertions
and production source are unchanged. Corrected compilation pending.

Corrected address capture compiled PASS1m27s. Both review axes found one
source-supported fixture defect: OFFICIAL's default peer handler required the
root-only field4, rejecting valid search. Added an explicit healthy empty search
handler, preserving positive empty/isolation/wallet/nosend assertions. This later
path was not runtime-reproduced; cache RED precedes it. Fixture compile pending.

Fixture compilation PASS1m26s at0190919. Both bounded re-reviews report0
residual findings. Pushed and dispatched focused Android17 run38135929831
for the cache RED. Production cache remains absent until observed RED.

Cache RED38135929831 at0190919:3cases/72.918s, existing2PASS, new tracer
fails only at missing reply after offline Activity reopen (line73). Earlier
online search/settled gates pass. Root viewed original in-scope PNG/separateXML,
retained ticket19/cache-red. Later backend/identity gates unexecuted. Focused
source/core/debug/test PASS; push38135930222 source/core/release/lint alsoPASS.
Production backend-specific cache implementation now begins.

## Cache implementation

After observed cache RED, added search.<backend>.v1 public history. Strict signed
decoding and author proofs validate restored search and same-server feed/profile
seeds. Entry/encoded-byte retention is bounded, matches first. Async initial
load gates network reads and guards captured list/revision before publication.
Verified page results publish before persistence; save failure retains them and
qualifies possible loss after closing. Completion follows persistence attempt.
No query/private Chat/wallet contents are cached. Compilation/GREEN pending.

Initial cache source32770eb combinedcompile PASS2m42s. InterimSpec0;
Standards1 source archive-publication race fixed with list/stamp guard and
received-page merge retry, cursor advancement only after valid publication.
No new archive read or weakened primary assertions. One budget-duplication
judgment remains for post-GREEN review cleanup. Guard compile/GREEN pending.

Archive-guard combined compilation PASS2m34s at90346aa. Both bounded
source re-reviews report0 residual race findings. Budget-duplication judgment
remains for review cleanup after first cache GREEN; no race runtime claim.

## Cache GREEN

Focused38136755529 at90346aa:3PASS80.069s. Hosted source/core/debug/test
PASS; push38136755184 source/core/release/lintPASS. Original log and two new
criticalPNG/XML pairs retained ticket19/cache-green, each individually viewed
and separateXML inspected. Native verifies Activity reopen/cache, offline
retention/no failover, healthy separateSaito empty results, ownerquery/results
return, samewallet/allclientkeys/noTX plus previousreply/pagingregressions.
No actualprocessdeath/powercut/1000-row/Pixel/full/live acceptance. Budget
review cleanup now proceeds; remaining people/recovery work still pending.

Post-GREEN review cleanup: one private PublicStringStore encoder now determines
both save bytes and fittingEntryCount; search keeps match ordering and uses
that count. Removes duplicated Gson/byte arithmetic without new speculative
interfaces or implementation-only tests. Compile/re-review pending; native
regression joins the next focused search proof rather than an extra full run.

Shared budget compilation PASS2m25s at9a45b79. Both source axes0 residual
findings. Next single primary tracer searches exact NativeCase7@saito while
all public post archives are empty and unsigned hints return{}. Existing
independent pinned registrar-signed fixture must resolve the known publickey,
open its selected-backend Profile, retain result/query through back/recreation,
and preserve wallet/allauthenticatedclientkeys/noTX. Uses existing debug-only
emulator/loopback registrar guard; no wallet replacement/private writes or
production authority fallback. First people compilation/nativeRED pending;
no production people implementation yet. Existing three cache/search journeys
will also verify the shared-budget cleanup in that focused run.

First exact-name tracer compilation PASS1m25s atd32c5d0, both source axes0
tracer findings. Focused Android17 RED38137503072 dispatched on pushedsource;
production people handling remains absent. Shared-budget and existing three
primary search regression results will be audited from this same run.

People RED38137503072 atd32c5d0:4cases50.979s, existing3PASS (sharedbudget
regression included), new1FAIL only missingresolvedkey atline67. Earlier empty
Archive/settled action gates pass; later signedlookup/profile/recreation/identity
gates unexecuted. Root viewed in-scope original PNG/separateXML; rawlog and
pair retained ticket19/people-red. Focused source/core/debug/testPASS. Exact
name production implementation now begins; no live namespace/GREEN acceptance.

## Exact username implementation

After observed RED, exact supported@saito names use selected authenticated
peer/current registrar verifier before publishing an active ownership record.
Unsigned hints are unused. Optional verified-name persistence failures preserve
mounted person with local-save qualifier. Registry failure is qualified
independently and does not suppress available Archive results; Search history
rechecks names, while post paging/retry does not repeat a finished name attempt.
Selected-backend Profile navigation and retained exact-person state reuse the
existing wallet/backend navigation. Compilation/review/GREEN pending. Known
people prefixes, exact public keys and negative/recovery cases remain future.

Exact-name source c9bb994 combined compilation PASS2m21s. Standards0;
Spec1 source finding: username-save warning survived discarding its different
query/person. Reset that qualification with the discarded person, retaining it
for the same person and post-only retry. No runtime reproduction claim. Fix
compilation/re-review pending. Earlier push38137503336 source/core/debug/test/
release/lint completed PASS.

Stale-warning fix7417559 combinedcompile PASS2m14s; bounded Standards0/Spec0
residual findings. First exact-name native GREEN pending.

Exact-name GREEN38138427233 at e505de2:4PASS83.402s, focused source/core/
debug/testPASS; push38138427894 source/core/debug/test/release/lintPASS.
Original log and one new individually viewed PNG/separateXML retained in
ticket19/people-green. Exact signed namecheck/Profile/back/recreation/wallet/
allclientkeys/noTX PASS. No liveRegistry/physical/full acceptance.

Next single primary tracer: empty public archives, exact independent scalar1
publickey selects correct Profile and retains query/result through recreation.
Its result matcher explicitly excludes the editable query, which otherwise
could falsely satisfy a missing-result assertion. The later invalid-point key
is Base58 of compressed02+32FF, x outside the field; cannot become a person.
Wallet/clientkeys/noTX gates unchanged. Compile/nativeRED pending; no exact
publickey production handling yet. Accepted seams1–4 unchanged.

Exact-key tracer6547a3a compilation PASS1m21s; both source axes0 findings.
First exact-key native RED pending; production handling still absent.

Key RED38139013779 atb161c12:5cases68.636s, existing4PASS; new1FAIL
only atline53 missing clickable non-editable result. Later Profile/recreation/
invalidpoint/wallet gates unexecuted. Root viewed originalPNG/separateXML;
rawlog/pair retained ticket19/key-red. Focused source/core/debug/testPASS;
push38139013987 source/core/debug/test/release/lintPASS.

After observed RED, canonical compressed33byte secp256k1 validation admits exact
publickey as a selectable person. Reuses the signed-name person card, selected
Profile/navigation and optional verified/generated label. No ownership inferred
from key entry and no fake Registry namecheck. Invalid point remains ordinary
query with no person. Initial compile/review/nativeGREEN pending.

Exactkey source bfe8a76 combinedcompile PASS2m47s. Spec0/Standards0 hard
findings; possible duplicatepointvalidation is reviewcleanupafter firstGREEN.
New exactkey nativeGREEN and broader #20 acceptance remain pending.

Key GREEN38139610537 at75aec86:5PASS82.727s, focused source/core/debug/test
PASS; push38139610540 source/core/debug/test/release/lint PASS. Rawlog plus
two new individually viewed PNG/separateXML pairs retained ticket19/key-green.
After GREEN review cleanup shares a private wallet point decoder across key
admission/signature verification, reuses boolean admission for Registry owner
and transaction recipient checks. No new speculative public interface.
Cleanup compile/re-review pending; regression joins next focused native proof.

Next known-prefix tracer being prepared: signed root/reply public history,
partial author key, separately verified name prefix, actual follow, Activity
close/offline reopen of cached known name. Native RED/production handling absent.
An incorrectly copied reply author literal was caught against canonical
NATIVE_THREAD_REFERENCE.json before dispatch; correction waits for the active
compiler to finish. No native failure or acceptance inferred from that typo.

Shared point cleanup plus initial tracer compile PASS2m42s. Corrected the
reply author to canonical pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU,
then explicitly modelled absent public history between Activities while
retaining actual Follow preference and verified public name cache. This isolates
followed known people as the offline source, preserving wallet. Correctedtracer
compile/RED pending; no native run was dispatched with the wrong copied address.

Known-prefix traceredd3e21 corrected-literal compilation PASS1m19s. Spec0;
Standards1 fixture sensitivity: prior Unfollow state could skip a fresh Follow
action. Fix waits for an enabled loaded Follow/Unfollow, explicitly unfollows
if necessary, then always Follow and requires enabled Unfollow after persistence.
No runtime failure claim. Fix compile/re-review and native RED pending.

Fresh-Follow fixturefix62aa7ce compilation PASS1m22s; both bounded axes0
residual findings. First known-prefix native RED pending. No production prefix
implementation yet. Shared-point runtime regression joins this focused run.

Known-prefix RED38140656219 at855d16a:6cases84.668s, existing5PASS;
new1FAIL atline79 missing clickable known reply author afterpncSjs. Later
Profile/freshFollow/nameprefix/offline gates unexecuted. Existing five also
exercise shared-point cleanup. Root individually viewed originalPNG and
separately parsedXML; rawlog/pair retained ticket19/prefix-red. Focused source/
core/debug/testPASS; push38140656411 source/core/debug/test/release/lintPASS.
Known-prefix implementation now begins; no final #20 acceptance.

After observed RED, cbea3b1 adds known-account prefixes from signed post
authors, wallet, validated follows/visited profiles and verified labels. Only
known keys load cached name proofs on IO; publication cannot replace a newer
in-memory label. Key prefixes are case-sensitive, name prefixes ignore case.
No wildcard/global directory request. Combined compile and both source reviews
active; first native GREEN pending.

Known-prefix cbea3b1 combined compile PASS2m38s; bounded Standards0
hard/actionable findings, all four RED hashes verified. Spec review and first
native GREEN pending.

Bounded Spec0/Standards0 actionable findings atcbea3b1; both reviewers
independently verified the observed RED and four evidence hashes. Compile
PASS2m38s. First known-prefix native GREEN pending.

Known-prefix GREEN38141685280 ataf77aeb:6PASS70.885s, focused source/core/
debug/testPASS; push38141676801 source/core/debug/test/release/lintPASS.
FreshFollow, verifiedprefix, offline recovery without publicposthistory, wallet/
allclientkeys/noTX PASS. Root individually viewed two neworiginalPNGs and
separately parsed eachXML; rawlog/pairs retained ticket19/prefix-green.
No actualprocessdeath/physical/liveRegistry/final#20 claim. Next single primary
tracer qualifies cached exact-name fallback during failed/negative namechecks.

Next cached-name provenance primary tracer7df2863: valid independent signed
name/Profile then invalid fresh check, healthyempty cached fallback, forged
identifier/signature rejection, ordinary post search and valid recovery. Same
wallet/auth/noTX gates retained. Qualification production absent; RED pending.
Bounded Spec0/Standards0hard findings; possible repeated fixture setup judgment
reserved for review cleanup after GREEN. PrefixGREEN six hashes checked by
both reviewers. Local terminal reported143 while build container independently
exited0 and daemon reports BUILD SUCCESSFUL1m19s; up-to-date packaging
confirmation active. No cause established for the contradictory terminal result.

Packaging confirmation PASS20s, all50tasks up-to-date; no source changes.
New cached-name qualification first native RED pending.

Cached-name qualification RED38142405104 at752d944:7cases65.88s, existing
6PASS/newmissingprovenanceFAILline83 afterinvalidfreshnamecheck. Earlier
validname/Profile/cache/error gatesPASS; laterempty/forged/recovery/wallet
gates unexecuted. Root viewedoriginalPNG/separateXML, rawlog/pair retained
ticket19/name-qualified-red. Focused source/core/debug/testPASS;
push38142377442 release/lintPASS. Ambiguouscachedprovenance, not accepted
invalid fresh ownership. Minimal qualification implementation now begins.

Initial qualification source653367d under compile. Both bounded axes identify
one source-reachable edge: query-wide fresh confirmation suppresses historical
qualification for a different cached owner matching the same name. No runtime
reproduction. Fix will scope the explanation per person card, preserving cached
proof and exact confirmed owner. Wait for compiler before source mutation.

Initial qualification compilePASS2m17s. Reviewer source edge corrected after
compiler completion: each person card qualifies its historical exact-name
label unless that exact key is covered by the active freshly confirmed record.
No cache eviction or new network read. Fix compile/re-review/GREEN pending.

Per-card sourcefix474e01f compilePASS2m20s; bounded Spec0 residual.
Standards sourceedge resolved, fixture precision finding: merged clickable
card bounds could count as visible while the copy itself is offscreen. Test
now scrolls to the qualification and asserts its unmerged Text bounds; negative
copy checks also use unmerged nodes. No observed viewport failure or acceptance
weakening. Testcompile/re-review/first qualification GREEN pending.

Visibility fixturefixf1f7a6f bothboundedaxes0 residual findings. Copy gates
measure actual Text bounds; error/person/recovery/wallet/nosend assertions
remain. Testcompile active, first qualification GREEN pending.

Visibilityfixturefix compilationPASS1m19s; sourceper-card confirmation
compiledPASS2m20s, bothboundedreviewaxes0 residual. First native qualification
GREEN pending; setupduplication remains post-GREEN review cleanup.

Qualification GREEN38143378676 at729acfb:7PASS72.724s, focused source/core/
debug/testPASS; push38143343479 source/core/debug/test/release/lintPASS.
Invalidfresh/healthyempty/forgedsignature/postsearch/validrecovery/wallet/
allclientkeys/noTX gatesPASS. Root individuallyviewed neworiginalPNG and
separateXML; rawlog/pair ticket19/name-qualified-green. Different-owner edge
is source-reviewed, not a separate runtimefixture. No final#20/live/physical
claim. Next verify existing savewarning/repair at primary application seam.


Save-failure recovery tracer aa112f4 compiles PASS1m17s. It exercises existing
live-result/savewarning behavior through the actual public-cache obstruction,
repair and a fresh offline Activity, with same-wallet/all-client-key/no-TX gates.
No manufactured missing-feature RED. Bounded review found setup outside protected
cleanup and sequential restoration that could mask the primary failure. Fixed
952fb55: protect setup immediately after successful rename, reject stale preserved
fixture, attempt all cleanup operations and suppress secondary errors. Both
bounded axes now0 residual findings; fixed test compilation/native pending.
Post-GREEN fixture cleanup6d22b40 shares unchanged guarded public-history reset.
One remaining consequential integration journey: independently signed author
edit and confirmed deletion must affect search and survive stale Archive reopen.
No final #20 acceptance or live/physical claim.

Fixed cache-failure fixture952fb55 compiles PASS1m18s; both bounded axes0.
First focused native verification of the eight search journeys is next.


Cache-save recovery GREEN38144386270 atfcfb404:8PASS78.271s, focused source/core/
debug/test PASS; push38144377059 release/lintPASS. Root individually viewed
both new originals and separate XMLs, rawlog/pairs retained ticket19/save-green.
Live results/savewarning, repair/fresh offline Activity, wallet/allauth/noTX PASS.
No manufactured RED, actualprocessdeath/live/physical/final acceptance.
Author revision journey d1785e5 compiledPASS1m19s. Both axes found final-query
falsepass if edit survives but deletion does not; fixed5302544 query'ed' matches
both original'Red' and'edited'. FixedcompilePASS1m19s/bothboundedaxes0.
New optedpublicreads fd35b02 inside existing live-feed journey check both search
streams with fixed100/flaggedne1/timestamp/no field4 criteria and strictsigned
readers; allow validempty and retain rejected-row counts, require harnessreport.
CompilationPASS1m28s. Whole-ticket Spec0 source findings; Standards pending.
First author native/publicreads and final full10/17 pending.


Whole-ticket review3be555be...fd35b02: Spec0 actionable source/fixture findings;
Standards0 residual hard violations/0 actionable heuristic smells. Existing
fixes remain intact. Both reviewers validate save-green six hashes/raw8PASS.
At34c4c67, focused author/search9run38144936110 and optedpublicfeed17
run38144937787 are active. Author/source and publicreadfixturecompiled as
recorded; no new runtime acceptance or finalfull10/17 claim yet.


Author-revision GREEN38144936110 at34c4c67:9PASS70.297s, all existing8PASS
plus independentedit/stale-reopen/deletion/exact2streamabsence/freshstaleempty
journey. Root individually viewed2neworiginalPNGs/separateXML; unchangedrawlog/
pairs retained ticket19/author-green. Wallet/allauth/noTX/peerhealthy PASS.
Focused source/core/debug/test PASS. Optedpublicread38144937787 remains active.
Finalfull10/17 notyetdispatched. No manufacturedRED/processdeath/physicalclaim.


Opted public search GREEN38144937787 at34c4c67: OWNER1PASS9.214s/
OFFICIAL1PASS7.759s. Both ordinary100rows/90posts/0ignored and currentquote
4rows/0commentaryposts/0ignored respond to fixed100/flaggedne1/currentcursor/
no-field4 criteria. Root inspected rawnative logs/metadatareports retained
ticket19/public-green. No new searchbitmap/directread UIclaim; no livewrites.
Wallet/selectedbackend continuity PASS. Push38144926841 release/lintPASS.
Bothwhole source axes0. Finalsame-sourcefull10/17 next. Registry limitation
remains explicit; no final #20, chain or physical claim.


Final fullpair dispatched at6c489bfa700e1fde73c3f43ba446980c11ad58d4:
Android10run38145371958 andAndroid17run38145373388, separately optedpublicreads.
Both are active; source is fixed. Both axes validate author-green6/public-green5
hashes and claims with0 findings. Current handoff condensed to active state and
authoritative evidence pointers; chronology remains here and in Git. No final
acceptance inferred from dispatch.


First finalfull10run38145371958 at6c489bf FAILED before Search: Browse8PASS
21.019s, Following8PASS23.037s, actualpostingdeath/coldreconciliationPASS.
Posting18cases17PASS1FAIL102.61s; oldqueuedwalletimportselector cannotlocate
reference atPostPreviewTest:762. Root individuallyviewedoriginalPNG and
separateXML: nativewalletdialog,noDocumentsUI/validatedpreview. Exacttiming
causeunproved. Rawlog/pair/README4hashes retainedticket19/full10-first-failure.
15a3986 reusesmaintainedchooseWalletImport synchronization/scopedprovider/
positiveincomingpreview and checksprovisionedreferenceisFile/positivesize only;
retainsbackup/identity/queue/no-send gates andcapturedprimaryfailure.
Fixturecompile/reviewactive; no productsourcechange. Android17full38145373388
continues; next outstanding10full onlyafterreview/fixcompile, notblindrerun.


Existingimportfixture15a3986 compilationPASS1m24s, both bounded axes0 findings.
Both reviewers verify failure4hashes and native-dialog observation; no exact
timing cause claimed. Production/app/core/build-support/scripts/workflow diff
since6c489bf is empty. Dispatch corrected outstanding full10; Android17continues.

Corrected outstanding full10run38146223301 ACTIVE at0caebdac92af2e5104844034ba183b5ae97e4087.
Android17full38145373388 still active at6c489bf; production/harness identical.
No final acceptance yet.


Second outstandingfull10run38146223301 at0caebda FAILED beforeSearch: posting
18cases15PASS3FAIL76.329s, all atintroduceddirectFile metadata guard, before
sharedchooserexecutes. Root viewedoriginalPNG(nativewalletmodal) andseparate
XML(DocumentsUIRecent/testproviderentries); non-atomicwindowtransition, not
provenmissingfile/provider. Rawlog/pair/README4hashes ticket19/full10-second-failure.
Directinstrumentationprocess probe unreliableforotherappprivateproviderdir;
ee70a83 uses establishedfixedrun-asstatpositiveSizeasusername/profilefixtures.
Correction ee70a83 compiles PASS1m30s. Both bounded review axes report0
residual findings and verify all4 second-failure hashes. No product changes;
exactfirstcauseunproved. Dispatch the outstanding full10 after pushing.

Final Android17run38145373388 at6c489bf SUCCESS:170reported cases/allPASS,
44completion records/14actual process-death stages, core55/0/0, source/debug/test/
release/lint PASS. Search9PASS58.789s; optedpublicowner1PASS9.678s/official1PASS7.672s,
ordinary100rows/90posts/0ignored andquote4rows/0commentaryposts/0ignored each.
Root individually viewed15original Search PNGs and parsed15separateXML, plus2fresh
publicfeed originals. Durable final-full/android17 retains119hashes, rawlogs,
counts, observations and continuity. No Search process-death/physical/chain claim.
Outstanding full10run38147131217 ACTIVE at7b4dd14, product/Search/harness unchanged.
Both SDK acceptance/finalreview still pending; #20 remains OPEN.

Bounded Android17 evidence review7b4dd14...7cf208d: both axes0 residual findings.
Each independently verifies119hashes,170cases/44records/14deathlogs, core55/0/0,
Search/publictimings/counts and source equivalence. Selected original PNGs/separate
XML agree; Android10 remains outstanding, no final closure inferred.

Final outstanding Android10run38147131217 at7b4dd1498f7e5c085ac854af80f7b60ea7759746
SUCCESS:170reported cases/169PASS+oneexistingAPI30+profile assumption,
44completionrecords/14actualprocessdeath stages; core55/0/0, source/debug/test/
release/lint PASS. Search9PASS54.238s, corrected existing posting18PASS100.284s.
Publicowner1PASS10.869s/official1PASS14.748s; both Search ordinary100rows/90posts/
0ignored andquotes4rows/0commentaryposts/0ignored. Existing signed5-to-8/cold
identity/cachecontinuity PASS. Root individually views15Search originals and
parses15separateXMLs plus2publicfeed originals. Durablefinal-full/android10
retains119hashes and raw independently countable logs; copied counts match original.
No Search actualprocessdeath/physical/chain/Registry/livewrite claim. Actual later
picker PASS does not establish the first scheduling cause. Production/build/core/
reference/scripts/workflow and Search/public assertions unchanged since successful
full17source6c489bf; only existing posting import fixture/evidence/docs differ.
Both finalSDK acceptance results present; final evidence review before closure.

Final bounded paired evidence review64a1408...10502a1: Spec0 actionable findings/
closure blockers; Standards0 residual findings/blockers. Both independently verify
119Android10hashes,170reported/169PASS+oneexistingAPI30+assumption,44records14death
logs, Search/posting/publiccounts/timings, original observations and source/
acceptance equivalence. Android17 evidence already clears both axes. #20 acceptance
complete; #19 external Registry blocker remains separate and OPEN. No extra
physical/Searchactualdeath/chain/livewrite acceptance inferred.

Documentation qualification: post keyword/prefix matching is local, but explicit
exact-username resolution sends the public identifier through RegistryLookup's
existing namecheck RPC. This was always the implemented/tested behavior. Closure
comment and handoff wording corrected; no runtime or acceptance change.

# Search ticket review

Ticket #20, parent #1. Review base
`3be555be85586b68c0d05c2c8e69d3976bbffe89`. Reviews inspect actual committed
diffs, with separate read-only specification and standards agents.
This is interim evidence; people, durable cache and final verification remain
unfinished. No ticket closure or physical-device acceptance.

## Standards

At `eceb9d6`, one source race: revision validation could republish an older
captured result list after archive publication. Fixed at `cc590f0` by validating
both captured-list identity and the current revision stamp before assignment.
Source review clears the fix; the unchanged reply tracer passes on that commit.
The race itself was not reproduced in runtime.

The paging tracer waited for an uncomposed older lazy row before scrolling.
Corrected at `4498614`: wait for the observed completed read and enabled action,
scroll to the exact older text, and retain its display assertion.

At `42ebbb8`, the 1,000-post retention bound could discard newly fetched older
matches while continuing to advance cursors. Fixed at `f1ebc3e`: prioritize
matching posts, explain retained-only coverage, clear paging/retry targets and
stop subsequent stream reads at capacity. No 1,000-row runtime claim yet.

Source also supports explicitly scrolling to a prepended quote before asserting
visibility, because a keyed list can retain the prior first-item anchor.
Fixed at `f1ebc3e` without reducing the exact display/cursor/count gates.

Re-review `42ebbb8…f1ebc3e`: zero residual documented breaches or actionable
Fowler smells. Focused paging GREEN38135039545 at54e806f now passes2tests/40.598s;
original artifacts and inspection limits are recorded in paging-green.
No1,000-row runtime or final ticket acceptance claim.

## Specification

At `eceb9d6`, zero findings in the first reply-search/navigation slice. At
`cc590f0`, one tracer-sensitivity gap: Retry and paging could discard previously
verified results without failing the existing test. Fixed at `4498614` by
positively scrolling to and displaying the newest result after both operations.

At `42ebbb8`, explicitly reach the prepended quote before its exact visibility
assertion. Fixed at `f1ebc3e`; reviewed against the parent's requirement to
verify displayed state, with AndroidX keyed-list source support.

Re-review `42ebbb8…f1ebc3e`: zero residual findings in the bounded implemented
slice. Independent stream cursors/pending targets, successful-read retention,
strict progression and explicit retained-history limit are coherent. People,
durable cache, broader failures/recovery and final native/public acceptance
remain known unfinished work.

## Offline cache tracer review

Both axes at7c897e1 identified OFFICIAL's default ordinary Archive callback
rejecting search without field4. Test-only fix supplies explicit healthy empty
fixed-criteria search responses. The primary app assertions are unchanged.
Other recovery/selection/identity/nosend gates are coherent in source. Native
cache RED and production implementation remain pending.

Bounded re-review7c897e1…0190919:0 residual findings per axis. Corrected fixture
compilation PASS1m26s; native cache RED38135929831 pending.

## Cache implementation review

At32770eb, Spec0 implemented-slice findings. Standards1 hard source race:
archive publication could overwrite a newer author revision during IO retention.
Fix validates actual list identity and revision stamp before publication/cursor
advancement, retrying the merge with the already-received page, without another
network read. No runtime reproduction claim. Standards1 judgment smell: search
duplicates the public store encoded-size budget; consolidate in review cleanup
after first cache GREEN. Combined initialcache compilation PASS2m42s; guard
compilation/re-review and native GREEN pending.

Bounded re-review32770eb…90346aa:0 residual archive-race findings per axis.
Actual-list/stamp guard retries local merge and does not advance cursor or repeat
a network read on conflict. Guard combined compilation PASS2m34s. Budget
duplication judgment remains open for review cleanup.

Cache GREEN38136755529 at90346aa passes3native journeys80.069s. Rawlog and
two new criticalPNG/XML pairs retained cache-green with stated inspection limits.
Post-GREEN review cleanup removes the duplicated encoded-budget algorithm:
PublicStringStore's fittingEntryCount uses the same private encoder as save;
search retains ordered posts by that count. Atomic commit/readback/load behavior
is unchanged. Compile and bounded source re-review pending.

Budget cleanup source re-review90346aa…9a45b79:0 findings per axis.
CombinedcompilePASS2m25s; native regression joins the next exact-username
people RED run. People production implementation is still absent.

Exact-name tracer atd32c5d0:0 source findings per axis. Empty posts/unsigned
hints cannot supply the literal name-to-key result, positive exact signed
namecheck/profile/back/recreation/identity/nosend gates retained. Compilation
PASS1m25s; native RED38137503072 pending. No live namespace readiness claim.

Exact-name implementation d32c5d0…c9bb994: Standards0, Spec1 stale
username-save qualifier after discarding a different-query person. Fixed by
clearing that qualifier with its originating person. Same-person/post-only
retry retains it. Initial compilation PASS2m21s; fix compilation/re-review
and native GREEN pending. No runtime reproduction of the source finding.

Stale-warning fix7417559 combinedcompile PASS2m14s; bounded Standards0/Spec0
residual findings. First exact-name native GREEN pending.

Exact-name GREEN38138427233 at e505de2:4PASS83.402s, focused source/core/
debug/testPASS; push38138427894 source/core/debug/test/release/lintPASS.
Original log and one new individually viewed PNG/separateXML retained in
ticket19/people-green. Exact signed namecheck/Profile/back/recreation/wallet/
allclientkeys/noTX PASS. No liveRegistry/physical/full acceptance.

Exact-key tracer e505de2…6547a3a: Standards0/Spec0. Both axes independently
confirm invalid literal33bytes02+32FF, existing good key provenance, editable
query excluded from clickable-person matcher and truthful pending RED/source.
All four people-green hashes, raw4PASS83.402s and original image/XML agree.

Exact-key implementation b161c12…bfe8a76: Spec0, Standards0 hard findings.
Standards possible duplicate compressed-point validation with wallet.verify is
a judgment cleanup for after first key GREEN, not a correctness blocker.
Both reviewers validate keyRED rawlog/hashes/original PNG/separateXML; screen
shows horizontally-scrolled input, XML establishes full literal key.
Combined compile/keyGREEN/knownpeople/failure/full checks remain pending.

Key GREEN38139610537 at75aec86:5PASS82.727s, focused source/core/debug/test
PASS; push38139610540 source/core/debug/test/release/lint PASS. Rawlog plus
two new individually viewed PNG/separateXML pairs retained ticket19/key-green.
After GREEN review cleanup shares a private wallet point decoder across key
admission/signature verification, reuses boolean admission for Registry owner
and transaction recipient checks. No new speculative public interface.
Cleanup compile/re-review pending; regression joins next focused native proof.

Point cleanup75aec86…dd00cc3: both axes0 hard/findings/smells. Registry
owner and outgoing-recipient checks reuse canonical point admission; wallet
verify and admission share private decoder. Serialization/preimages/output
ordering unchanged. Six keyGREEN hashes/raw5PASS82.727s/images/XML agree.
Cleanup regression joins next known-prefix tracer.

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

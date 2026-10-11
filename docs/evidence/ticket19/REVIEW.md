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

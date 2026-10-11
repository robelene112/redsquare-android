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

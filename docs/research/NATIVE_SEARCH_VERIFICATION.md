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

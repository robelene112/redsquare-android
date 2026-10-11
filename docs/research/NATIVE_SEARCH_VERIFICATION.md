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
Initial test APK compilation PASS1m23s. Native RED pending; no production search
implementation or feature acceptance claimed yet.

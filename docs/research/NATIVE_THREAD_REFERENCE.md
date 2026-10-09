# Native conversation reference

Prepared for ticket#8 using the existing approved application/protocol testing
boundaries. No new production read/write or node configuration was required.

Pinned source9a4ca7e: `node/mods/redsquare/redsquare.js:2673` loads an exact
target signature when needed, then reads `field1:RedSquare`, `field5:threadId`,
`flagged_ne:1`, limit100. Profile posts/replies share author query
`field1:RedSquare,field2:publicKey`; profile likes use `RedSquareLike` by author
and resolve referenced post signatures. See `lib/manager.js:1060`. Returned
archive rows are backend metadata; derive identity/content/relationships from
verified transaction bytes. Available rows do not guarantee complete history.

[The generator](../../build-support/references/thread-reference.cjs) runs only
pure WASM and Elliptic crypto with env-i, network-none and a read-only host mount,
using the established reference runner. It never initializes the application,
loads a real wallet, accesses a database or propagates a transaction. Node24.13.0,
saito-wasm0.3.2, Elliptic6.6.1 and actual WASM SHA-256
f59be69dbb949368ce1fc7ef47d414cbe0281a86a206fe4bfb1128a4d087e01e are verified.

[The synthetic fixture](NATIVE_THREAD_REFERENCE.json) reuses the independently
signed feed root and adds scalar2's direct reply and scalar1's nested reply.
Both scalar keys are public unsafe test material and must never hold funds.
Each reply's manually built signature preimage matches WASM's signature hash;
WASM and Elliptic verify its signature, and actual transaction serialization/
deserialization preserve message bytes and signature. A deliberately altered
detached message retains the original signature and must be ignored.

The first Android regression opens the root through the actual feed and expects
both valid replies, rejects the altered reply, and exposes Back navigation.
It uses a controlled authenticated peer, not an internal module mock. No production
navigation is implemented at this test commit; actual functional red is pending.

Actual Android10 red37925240595 at39fa942 reaches the verified feed root, taps
its body and times out at the reply wait (test line48). The inspected screenshot
still shows All posts with the root. Public log/image retained in
ticket07/conversation-red. Minimal implementation now makes feed cards open a
conversation, reads selected-backend field5 history and verifies each entry
through the existing signed-envelope decoder. It retains the selected post,
rejects altered/unrelated entries and adds Back, refresh and truthful bounded
history/error copy. Cache persistence and profiles remain upcoming within#8;
actual green and final acceptance are pending.

First application green:37926293893/37926297773 at0c66d5b pass the one conversation
flow on Android10/17, with the direct and nested independently signed replies,
altered reply exclusion and Back control. Two inspected public screenshots/logs
are retained under ticket07/conversation-green. Core15/debug/test assembly passes.
This does not complete#8. A new primary-application case loads the conversation,
stops its controlled server, recreates the activity, reopens the cached feed root
and requires the replies plus truthful available-history/refresh-failure copy.
It compiles; actual persistence red is pending. No conversation cache is yet
implemented at this test commit.

The persistence test's actual Android10 red37927325694 at7490215 loads both
replies successfully, closes its server, recreates the activity and reopens the
cached root. It then times out waiting for the direct reply at line53. The
inspected failure screenshot shows only the root and the truthful refresh error.
Log/image are retained in ticket07/conversation-cache-red. Minimal conversation
caching reuses bounded, atomic public-envelope persistence, verifies entries on
reload and keeps separate files for each backend/thread. It merges retained
replies with refreshed available history. Local/device green is pending.

Android10 green37928478015 at5fc64af passes both conversation cases. Its inspected
restart/offline image shows the root, direct reply and nested reply retained.
Evidence is in ticket07/conversation-cache-green/api-29. Android17 green37928482438
passes the same two cases at5fc64af. Its inspected public restart image/log are
retained in conversation-cache-green/api-37.0. These are controlled emulator
recreation/outage checks, not arbitrary physical process/power interruption.

## Profile reference

[Independent like fixtures](NATIVE_PROFILE_LIKE_REFERENCE.json) come from the
[pure generator](../../build-support/references/profile-like-reference.cjs),
under the same network-none/read-only/env-i constraints. It asserts WASM0.3.2,
Elliptic6.6.1 and the pinned binary hash before generating anything. Manual
preimages match WASM hashes, both libraries verify signatures and actual WASM
serialization round-trips the signed message and signature. No real wallet,
database or propagation is involved. Scalar1 likes scalar2's direct reply and
an unavailable signature. A scalar2 like and an altered detached message must
not appear as scalar1's likes or trigger their target lookups.

The next application tracer taps the feed identity, visits Posts, Replies and
Likes, excludes other-author/altered content and requires truthful unavailable
liked-post copy. Author queries and exact liked-target resolution match the
pinned manager source described above. This test's compile/functional red is
pending; no production profile navigation or tabs are implemented yet.

Actual profile red37929183997 atcec9c66 passes both established conversation
cases, reaches/taps the verified feed identity and times out at the missing
Posts tab (line69). Its inspected screenshot shows a conversation instead.
Log/image retained in ticket07/profile-red. Minimal native profile navigation
and author Posts/Replies plus signed-like/exact-target resolution are now added.
Common verified envelope decoding serves both post and like modules. Cached
profile views and final acceptance are not implemented yet. Local/device green
are pending.

First implementation run37930169685 at9eb69e5 passes the new profile case but
fails both earlier conversation cases at their first reply waits (lines181/126).
Both inspected failure screens show Profile after tapping the post body; its
merged card target overlaps separately clickable identity text. The profile
success image shows only the verified scalar2 reply in Likes and the truthful
unavailable-target warning. All three images/log are retained in
ticket07/profile-navigation-failure/api-29. The post body now has its own
conversation tap target, without weakening or changing the tests. Local/device
verification of that correction remains pending.

Android17 first run37930173464 reproduces the same two conversation failures
and passes the profile flow at9eb69e5. Inspected conversation failure and profile
Likes success images, plus the remaining failure image/log, are retained in
profile-navigation-failure/api-37.0. Neither first run is an overall pass.

Corrected tap-target green37931122222 atf8e6b6e passes all three application
cases on Android10. Public success screenshots/log inspected and retained in
ticket07/profile-green/api-29. Android17 is pending. New primary-application
tracer visits all three profile tabs, closes the server, recreates, reopens the
identity and requires cached Posts, Replies and Likes with honest outage/history
copy. Its compile and functional red are pending. No profile persistence exists
yet; this is the next slice within#8, not acceptance of the ticket.

Android17 corrected green37931125949 atf8e6b6e also passes all three cases.
Public success screenshots/log inspected and retained in profile-green/api-37.0.
The new offline-profile tracer compiles atc2061a3; its first actual red is
37931907251. Inspect its failure point before changing persistence. Profiles
still have no persistent cache, and the ticket remains open.

Actual offline-profile red37931907251 atc2061a3 passes the three earlier flows.
The new case successfully loads all tabs, stops the peer and recreates. It
reopens the profile and times out on its cached Posts entry at line74. The
inspected image shows Profile/Posts empty with the truthful outage message.
Public log/image retained in ticket07/profile-cache-red. Minimal backend-scoped
profile persistence now keeps at most200 public post/like envelopes within16MiB,
atomically saves/verifies the file and re-verifies every signature on reload.
Tab membership derives from signed authors/parents and signed like references,
including cache restoration. Cached entries merge with available refreshed
history. Local/device green and final review remain pending.

Profile-cache green37932930501/37932934354 at aef6742 passes all four application
cases on Android10/17. Inspected public offline-Likes images/logs are retained
in ticket07/profile-cache-green. The Android10 viewport shows the reply at the
bottom of its scrollable content; Pixel performance/polish is not claimed.
Independent review at this source found three spec gaps and two duplication
judgements; see ticket07/INTERIM_REVIEW. Cache durability and pure reference
signing have now been shared without changing either cache's on-disk format
or any existing signed fixture. Fresh test setup clears only fixture-owned
public caches in the isolated .dev app, preventing retained text from satisfying
fresh-read waits. These changes need final runtime verification.

The next app regression rejects an independently valid signature whose signed
thread_id is malformed, while preserving an authentic conversation. Its fixture
uses the same shared WASM/Elliptic reference checks, not a native self-generated
expectation. The decoder is unchanged at this test point; compile/red pending.

Malformed relationship actual red37934353219 atc631f4d passes all four established
flows after storage/reference refactoring and fresh-cache isolation. The new
case fails assertDoesNotExist because the signed malformed thread is shown in
the feed. Inspected failure image/log retained in ticket07/malformed-thread-red.
The decoder now validates nonempty parent and thread identifiers as compact
signature strings before exposing the post. Minimal local/device green pending.
This regression proves rejection at the app boundary, not an observed process
crash; the crash risk was diagnosed from the decoder/cache source mismatch.

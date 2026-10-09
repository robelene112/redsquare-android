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

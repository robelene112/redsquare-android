# Native profile editing verification

Active #18, review base afce6610e4c78a2183bf154ac99cc35d7917b03c.
Approved parent #1 seams1–4 cover the shared native signed-profile interface,
independent pinned web transactions, observable Android editor/image selection
against controlled peers, and persistence/interruption recovery. No new testing
seam or production broadcast. Source contract: NATIVE_PROFILE_EDIT_CONTRACT.md.

First tracer reconstructs an independently signed Profile snapshot, retaining
archive and extension fields, ignoring another signer and selecting the snapshot
ahead of a later legacy update. The missing reader produced compileRED22s.
The first implementation verifies each envelope, orders by signed timestamp and
preserves complete snapshot fields; focused GREEN42s. The second tracer changes
the bio and explicitly removes the avatar, requiring a signed self-recipient
complete snapshot that retains banner, archive and extension fields. The sender
interface's absence produced compileRED22s. The minimal complete-snapshot merge
now omits explicitly removed fields and keeps unedited values; focusedGREEN40s.
The next reconstruction tracer uses an independently signed rawJSON snapshot1.0,
which JavaScript treats as numeric1. It must win over a later legacy update;
the decimal-scale behavior failed the positive snapshot expectation. Native now
compares numeric value instead of BigDecimal scale; focusedGREEN30s afterRED25s.
The independent fixture uses only the existing pinned pure WASM/Elliptic helper
and public unsafe identities, with no Saito application initialization.

Read-only avatar research independently ran pinned WASM BLAKE3 over synthetic
public Base58 address strings and the exact author identicon.js2.3.3 bytes,
SHA25617994ee41aae1f7057151524517f74a3489b58ea08f56c1cff33992774262e68.
Three public vectors include a leading-zero hash and varied foreground colors.
The vectors are now independent core test inputs. The missing native avatar
produced compileRED20s. Native BLAKE3, palette and mirrored cells are implemented;
focusedGREEN43s. Reproducible generatoridenticon-reference.cjs checks the exact
author-source hash before evaluation and the shared pinned crypto metadata.
The source license is included in the app's distribution assets. No Android
avatar rendering is established yet.

All48 core cases and debug/test assemblyPASS1m58s before the first Android
profile tracer. The new native-screen tracer opens an exact signed post's author,
then requires its verified snapshot bio and generated avatar with no transaction.
The controlled peer's explicit Profile archive boundary is separate from the
existing Red Square callbacks; existing trailing-lambda callers retain their
archive callback. New focused profiles/profiles-android17 scopes do not contact
public services. Android17 run38089539658 at73f0e17 fails the exact signed-bio wait32.732s.
Its original in-scope bitmap and separate XML establish the correct author
profile, with bio/avatar absent. Retained ticket17/profile-reader-red/android17.
The minimal native signed-details/header implementation is now being built;
no editor/image UI proof yet. Local header debug/test assemblyPASS1m56s.
Android17 run38090029603 at583c0cc passes the exact signed bio/avatar display
gates, then fails17.755s at peer health: the generic controlled callback expects
feed field4 but receives the valid author-history field2 query. Its original
in-scope bitmap visibly shows the matching native identicon and signed two-line
bio, with loading history. Retained profile-reader-fixture-red/android17. This
is a fixture defect, not full runtimeGREEN. The fixture now explicitly validates
feed/author-history requests and serves the same independently signed post.
Positive bio/avatar/no-transaction and peer-health assertions are retained.

Both interim review axes identify the same functional layout finding: unrestricted
bio sits before tabs/history in a non-scrollable Column. Long signed multiline
bios can make history inaccessible. Independent longBio reference adds40 lines;
all pre-existing reference objects were asserted identical. New Android flow
requires the full bio, then reachable Posts/Replies/Likes and post after scrolling.
Long-bio run38090221063 atad4e5bf times out at the180s harness boundary on10.
It records the first case starting, with no completed assertion result or valid
in-scope screenshot archive. This is a failed runtime check, not proof of a
specific scroll failure or root cause. Retained profile-long-bio-timeout/android10.
The original Compose automatic scroll could loop on a zero-height history list;
this remains a hypothesis. The tracer now uses bounded native Android scrolling
and captures the full-bio state before its gesture, retaining positive visible
Posts/Replies/Likes/post checks and complete authored bio text. The harness also
captures the original Android system screen and narrowly filtered runtime errors
on failure. Based on both source-review findings, the native header, tabs, status
and history now share one weighted scrollable profile list. No truncation or
stored-user-text rewriting. Debug/test assemblyPASS1m49s; only whitespace formatting afterward. Fresh
Android10/17 runtimeGREEN pending; no header acceptance or ticket closure yet.

#17 remains open with an unresolved actual-provider playback blocker. Its two
immutable full regression runs38088542044/38088569769 at534330e PASS127 cases
each; continuing this independent ticket does not accept #17 or its earlier
failed full runs and those old runs do not prove the new profile implementation. No review APK or
node/wallet/store change.

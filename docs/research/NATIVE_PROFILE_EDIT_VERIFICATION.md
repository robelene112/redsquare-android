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
no runtimeGREEN or editor/image UI proof yet.

#17 remains open with an unresolved actual-provider playback blocker. Its two
immutable full regression runs38088542044/38088569769 at534330e PASS127 cases
each; continuing this independent ticket does not accept #17 or its earlier
failed full runs and those old runs do not prove the new profile implementation. No review APK or
node/wallet/store change.

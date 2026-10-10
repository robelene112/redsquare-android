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

Both source-review axes confirm the weighted shared list resolves the header
layout finding at3ec9cb7,0 remaining bounded findings. Dual runs38090924525/
38090927117 still fail the long-bio fixture, while shortbio/avatar/healthy/noTX
passes both. Overall2 cases/1 failure24.859s on10 and16.229s on17. Original10
long-case bitmap stays on the feed after unsynchronized UiDevice wait;17 stays
on Loading posts and fails its initial scroll before any profile/bio lookup.
Original17 shortbio bitmap visibly includes bio/identicon;10 shortbio bitmap
shows identicon/loading but omits the separately passing bio, excluded from bio
visual acceptance. XML is a separate later observation. Retained
profile-scroll-fixture-red/android10 andandroid17.

The fixture now waits two archive responses, enabled New post and absent Loading
posts before its author tap. Full-bio lookup returns to Compose waitUntil, and
scroll gestures use at most10 synchronized Compose touch swipes with displayed
target checks. Positive fullbio/three tabs/post/zeroTX/healthy gates remain.
[Android synchronization contract](https://developer.android.com/develop/ui/compose/testing/synchronization)
requires synchronized test methods to drive recomposition and gestures; a raw
Android wait can leave Compose paused. This supports a synchronization hypothesis
for10, not a demonstrated production defect. Local debug/testPASS1m51s.

Next pure state tracer requires older verified reads to preserve a newer signed
snapshot and its avatar/banner/archive/extensions. Missing merge compileRED22s;
focused four profile casesGREEN43s,0 failures/ignored. Native merge revalidates
retained envelopes, keeps the newest authoritative snapshot even ahead of later
legacy rows, and preserves it through stale/empty reads. Without a snapshot,
legacy cache inputs are bounded to100 newest rows/16MiB, matching available
history limits; older legacy-only fields may be unavailable after pruning. The
ignored count describes the latest read; Android cache errors will have their
own warning. No disk cache or editor behavior is implemented yet.

Android10 run38091561793 and17 run38091563516 at2f0295a PASS two cases,
6.764s and19.285s respectively. Both original long-bio screens individually
inspected: beginning lines with identicon, then final lines33–40 (10) or32–40
(17), displayed tabs and the reference post. Short-bio semantic gates pass,
but both short-bio bitmaps show Loading profile details without the bio and
are excluded from bio visual acceptance. XML is a separate later observation.
Retained profile-scroll-clock-green/android10 andandroid17. Push source run
38091561542 also succeeds. These runs precede the following fixture fixes:
two readiness literals used ASCII periods while feed_loading uses U+2026;
both reviewers caught the vacuous absence gate. Now use the actual resource,
keeping archive/readiness/display gates. Captures now await Compose idle;
no claim that this proves the prior bitmap timing cause.

Next Android tracer reloads the same author's signed short bio in a fresh
Activity after the controlled selected server closes. Require actual failed
lookup copy plus cached bio/avatar and unchanged backend, with no live writes.
Only public profilemeta fixture files are reset between emulator cases. Online
baseline waits both metadata/history completion and enabled Refresh before
closing the Activity and peer. Local test assemblyPASS1m2s before the added
capture-idle call. Android10 run38091914532 and17 run38091916534 at41f5d8e
produce the intended missing-cached-bio RED atline170, three cases/one failure
9.177s/19.868s. Both original in-scope failure bitmaps individually inspected:
correct author/defaultavatar, failed profile lookup/history and cached reference
post, absent bio. Separate XML matches those observations. Other two flows pass
with corrected resource-based loading checks. Retained profile-cache-red/android10
andandroid17; push source38091914178 succeeds.

Minimal Android public metadata cache now revalidates stored signed envelopes,
namespaces author/backend, loads before the peer request and merges fresh data
through the already tested newest-snapshot rule. Bounded100 entries/16MiB public
store uses atomic fsync/verified commits. Concurrent older saves cannot replace
a newer snapshot. Verified remote data remains visible if disk persistence fails,
with cache warning; rejected remote rows have an unreadable warning. Failed
requests preserve cached metadata; internal request timeout becomes failed-lookup
copy while parent cancellation still propagates. Local debug/test assemblyPASS
1m58s before the final profile-specific warning resource replaces misleading
posts wording; final-source compilation and fresh dual runtimeGREEN pending.
No encrypted editor, custom images or recovery completion.

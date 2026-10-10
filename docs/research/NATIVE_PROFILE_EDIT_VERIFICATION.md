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

Final-sourcea60c425 focused Android10 run38092322711 and17 run38092324241
PASS three cases8.844s/36.901s, including fresh-Activity cached metadata after
the controlled selected server closes. Both original outage screenshots viewed
individually show correct author/identicon, retained signed two-line bio, failed
metadata/history refresh copy and cached reference post. Retained
profile-cache-green/android10 andandroid17. Source38092322568 PASS build/release/
lint. Both bounded source review axes0 findings througha60c425. These focused
runs are not final full ticket verification.

Next Android tracer opens Your profile before any own posting, edits a Unicode
multiline bio, saves a draft, then requires the same draft in a fresh Activity.
No transaction may be transmitted or draft bio shown on the public profile.
Only disposable dev/emulator profiledraft fixtures are additionally reset;
wallet and unrelated work stay untouched. Local test assemblyPASS1m45s. Intended runtimeRED pending, with own
entry/editor/private draft storage still absent.

Android17 run38092752725 at846bd54 yields intended missing-Your-profile RED
atline195, four cases/one failure37.9s. Other three profile flows pass. Original
in-scope bitmap individually inspected: feed with existing cached reference
post, no own-profile entry. Controlled empty feed responses do not erase cached
posts. Retained profile-bio-draft-red/android17. Source38092752140 succeeds.
The next fixture waits for the clickable Edit profile button after saving,
not its matching editor title; original private-bio absence/noTX assertions stay.

Minimal own-profile entry and bio draft editor debug/test assemblyPASS2m4s;
fresh dual nativeGREEN pending. Private changes use AES-256-GCM with app/filename associated data,
Android Keystore, owner/backend-scoped no-backup file, strict UTF-8/JSON and
5MiB local bound, atomic write/fsync/reopened equality verification. Missing key
with existing drafts refuses a fresh key. Editor memory uses a scoped ViewModel,
not saved-state content; Back/Save draft and navigation persist under the shared
wallet/backend lock. Failed saves keep the editor open with truthful copy.
Untouched baseline bio does not become edit intent. Wallet replacement accounts
for saved profile work on either backend and treats unreadable work as unknown.
No Save profile/publishing/candidate/media behavior implemented yet. Encrypted
file logic initially duplicates the existing post adapter; shared extraction
belongs to the review stage after both real consumers pass their native flow.

Bounded846bd54...f6593cf Spec review identifies unsaved-editor work missing
from the wallet-import preview: current nativeWork reads disk before final
activation invokes navigation persistence. A newly typed unsaved bio can therefore
miss the required pending-work warning. Resolve with a native typed-without-Save
draft import-preview tracer; persist successfully before import selection or
include live intent, refuse the transition on failure, retain outgoing identity
isolation. Standards:0 hard breaches, duplicated encrypted-file handling is one
actionable judgement concern for the already planned shared extraction. Other
reviewed source/evidence0 findings; no ticket acceptance.

At f6593cf Android10 run38093238455 and17 run38093240150 each complete four
cases with one fixture assertion failure atline219,15.422s/48.374s. Both expose
the exact saved Unicode multiline bio as EditableText; Text also includes the
field label Bio, so assertTextEquals incorrectly compares their combined set.
Original restoration-failure PNGs individually inspected show the restored
two-line draft in the editor; separate XML and captured semantics agree.
Retained profile-bio-draft-label-failure/android10 andandroid17. Change only
the assertion to exact EditableText on the editable node; no product edit or
relaxation of private-only/no-transaction requirements. Fresh dual proof pending.

Before the review-stage encrypted-file extraction, add a native regression
for the historical post v1 raw-text format. An independent disposable emulator
fixture writes the original AES-GCM framing/AAD with existing post key alias,
then actual New post UI must expose exact Unicode multiline text. Save draft
through UI and require it again after a fresh Activity, with no transmission.
This is a preservation regression, not a new feature RED. Assembly PASS1m4s
(.scratch/profile-legacy-regression-build.log); runtime baseline pending.

Exact-value candidate35cd09b: Android10 38093681635 PASS4 cases10.545s.
Android17 38093683708 finishes4 cases/one failure26.104s; own bio restoration
and other two cases pass. Originals restored-bio-draft.png inspected individually
on both show exact private two-line bio; separate XML agrees.17 failure occurs
before opening the cached author atline170: merged ScrollBy finder has no node.
In-scope post-failure original shows cached reference post and author already
visible in the feed; this does not prove the exact state at the failed action.
No profile read/retention assertion was reached on that attempt. Helper now
waits bounded for the positively displayed scoped author, scrolling only if
the reference post is offscreen. Exact cached bio/outage assertions unchanged.
Retained profile-bio-draft-value/android10 andandroid17; fresh17 proof pending.

Bounded f6593cf...bb668c6 review: Spec0 new findings; Standards0 hard and
one fixture readiness concern. The historical post check waited New post text,
which also matches the composer title. It now requires the enabled clickable
feed button after saving before closing the first Activity. Exact old-format
text restoration and no-transaction requirements remain. Other pending review
findings (encrypted duplication and unsaved import preview) are still unresolved.

Legacy baseline bb668c6: Android10 38093829543 PASS18 posting cases80.974s;
Android17 38093831384 PASS18 cases95.052s. Both original restored-legacy-draft
PNGs individually inspected show the exact two-line unsent legacy text fixture
in New post. Existing posting screenshot helper captures PNG only; no separate
XML exists for these legacy captures. Retained profile-legacy-baseline/android10
andandroid17. These baseline results precede the stronger reviewed clickable
New post post-save readiness gate.

Corrected profile navigation at1963c0e: Android17 38093960556 PASS4 native
cases50.57s. All original profile assertions remain; fresh cache-outage/bio-draft
checks pass. Original captures and detailed logs remain in ignored downloaded
artifact .scratch/profile-positive-navigation17-proof; acceptance is focused.

Review-stage extraction95eda3c: EncryptedLocalFile owns common bounded strict
UTF-8 AES-GCM framing, Android Keystore guard, atomic fsync and verified reopen.
Post and Profile adapters keep separate key aliases/families and feature payload
validation. Exact filenames and package/filename associated data are unchanged;
Post raw v1 and JSON v2 meanings and Profile changes-only v1 remain unchanged.
All calls retain the shared wallet/backend lock and family-wide refusal to
replace a missing key when draft files exist. Local debug/test assembly PASS
1m56s (.scratch/profile-shared-encryption-build.log). Fresh post runtime
38094236367/38094238073 and profile38094240194/38094242194 pending, as is bounded
review. This is a review refactor, not feature completion.

Shared storage95eda3c focused GREEN: Android10 posting38094236367 PASS18
cases85.755s;17 posting38094238073 PASS18 cases97.012s. Profiles10
38094240194 PASS4 cases10.421s;17 38094242194 PASS4 cases41.835s. Both original
restored bio drafts and both legacy post PNGs individually inspected; profile
captures additionally have XML, posting captures PNG only. Retained
shared-encryption-green/profile-shared-encryption andprofile-shared-post with
Android10/17 raw UI logs. Bounded1963c0e...5bbf7d1 Spec0, Standards0 hard/0
heuristic findings: duplication resolved. Source38094278321 at5bbf7d1 PASS
49core/debug/test/release/lint; no final full18 or APK claim.

Next tracer requires the pending-work warning before wallet replacement for
a freshly typed Unicode multiline bio, with no Save draft/navigation beforehand.
It selects an independently pinned scalar1 backup using actual SAF and verifies
the selected public address, visible pending-work warning, disabled confirmation,
unchanged outgoing identity and retained editor text after cancelling. A fresh
disposable dev/emulator wallet avoids unrelated pending-work false positives;
it is restored in finally without printing private data. The harness provisions
the public backup under the standalone document-provider UID; a positive size
check precedes UI launch. Picker waits for the exact file, not an ambiguous
provider title, and never uses an unscoped scroll. Initial test assembly PASS
1m3s before final provider-UID provisioning adjustment; final compilation/RED
runtime pending. No import preparation fix is implemented yet.

Atf141190 Android17 38094856188 gives intended RED:5 cases/one failure62.919s
atline275, pending-work warning absent after actual SAF selection and exact
incoming public-address verification. Other four profile cases pass. Original
in-scope failure PNG individually inspected shows active outgoing public address,
correct selected scalar1 backup, replacement detail and required-backup copy,
with no specific pending-work warning. Separate XML agrees; retained
profile-import-preview-red/android17. Source38094855735 PASS. Both bounded
5bbf7d1...f141190 review axes0 findings.

WalletImport now reserves busy state and awaits the mounted editor's durable
save before document selection. It checks the same wallet object/backend before
opening SAF, and cancellation cannot launch a picker or reset newer attempt UI.
Failure reports that local work could not be saved and import has not started.
Final activation keeps its existing save check. This protects the real existing
post/profile consumers without copying their persistence logic into wallet UI.
Local debug/test assembly PASS1m54s before final cancellation guard in the
exception path; fresh dual final-source runtime and bounded review pending.
No wallet replacement or on-chain action occurred during the RED tracer.

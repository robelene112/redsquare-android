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

Final source3d8c9db: source38095429841 PASS; bounded f141190...3d8c9db
Spec0, Standards0 hard/0 heuristic findings. Android10 38095429957 PASS all
five cases17.174s, including actual SAF selection, fresh typed-bio pending
warning, blocked confirmation and retained editor after cancelling. Original
PNG individually inspected shows the specific pending-work warning, current
wallet recovery explanation and disabled confirmation; separate XML retained.
Android17 38095431855 completes five cases with one picker-navigation failure
64.357s atchooseProfileImport before the selected-address/pending assertions.
Other four pass. Original failure PNG and XML show Downloads, Show roots and
no document; they do not establish the state of each earlier click or root cause.
Retained profile-import-preview-first-green/android10 andandroid17.
The helper no longer remembers an attempted drawer click as successful forever.
It waits for the exact provider row after Show roots, reobserves actual picker
state within the same30s bound, and succeeds only on the exact document click.
No production code or acceptance assertion changed; corrected17 runtime pending.

Next native regression covers a real save failure at the agreed filesystem/UI
boundaries. It first saves and reloads an older bio, types newer work, preserves
the actual encrypted draft file and obstructs its AtomicFile base with a
directory. Import must show the save-failed message without opening SAF or
exposing a selected identity. Cancelling retains the exact edited Unicode bio.
After repairing storage, the same Import action must open actual SAF, show the
independent scalar1 public address and leave the outgoing identity unchanged.
A fresh Activity must restore the newer bio saved during retry. All obstruction
cleanup preserves the primary assertion failure. Guards restrict this to the
dev package/emulator; no wallet is replaced or transaction broadcast. Local
debug/test assembly PASS1m52s; six-case native runtime and bounded review pending.
This is a failure/recovery regression for the existing3d8c9db save guard, not
a new publishing behavior.

Corrected c5e44a6 Android17 38095803727 PASS5 cases57.64s. Original unsaved
bio preview PNG individually inspected shows the specific pending-work warning,
backup requirement and disabled confirmation; separate XML retained under
profile-import-picker-corrected/android17. Together with immutable3d8c9db
Android10 this establishes the save-before-preview behavior on both versions.
Both bounded3d8c9db...c5e44a6 review axes0; production identical.

Failed-save regression495bc97: Android10 38095985570 PASS6 cases21.46s.
Individually inspected original blocked-import PNG shows the app wallet dialog,
truthful save-failed/import-not-started copy and usable import action. Fresh
Activity recovery PNG shows exact newer Unicode multiline bio; separate XMLs
and raw log retained profile-import-save-failure/android10. Native assertions
require real SAF retry, selected independent address, unchanged outgoing wallet
and zero transactions. Both bounded c5e44a6...495bc97 review axes0. Android17
38096072885 pending; no actual process-death/publishing claim.

Next publishing tracer replaces only the disposable dev wallet with public unsafe
scalar1, restoring its previous identity in finally. It loads the independently
signed withMedia profile, edits the bio and presses Save profile. The controlled
peer inspects an actual signed self-recipient Profile full snapshot and checks
unchanged avatar/banner/archive/extension literals. Only verified server
availability may show Profile available on this server, and closing the editor
must expose the new public bio. Real frames are retained for later independent
WASM/web receiver verification; this first tracer is not that cross-client proof.
Source fixture checks verify zero-value transaction decoding, but independent
wire verification remains required. Local test assembly PASS1m5s; intended
Save-profile-missing RED and bounded review pending. Harness includes the new
ProfilePublishingPreviewTest alongside existing six native flows.

Failed-save495bc97 Android17 38096072885 PASS6 cases71.054s. Exact original
blocked-import and fresh-Activity recovery PNGs individually inspected, matching
truthful save-failure/no picker and newer Unicode bio; separate XML/raw log
retained profile-import-save-failure/android17. This completes dual focused
save-failure/retry proof, not actual process-death or full18 acceptance.

Publishing601e518 Android10 38096204770 yields intended RED: seven cases,
one failure25.828s atline92 because Save profile is missing. Other six pass.
Original in-scope PNG individually inspected shows exact edited bio, old Save
draft action and no Save profile; XML/raw log retained profile-publishing-red/
android10. Source38096204586 PASS. Bounded Spec0; Standards one verification
gap: a single premature auto-send could satisfy the later one-TX assertion.
Added zero-TX assertion immediately after typing and before explicit Save.
This strengthens the subsequent GREEN tracer without changing the RED's
missing-Save diagnosis. No production publication existed in that RED source.

First publisher implementation: explicit Save profile merges a successful fresh
own lookup with verified known/cached state, preserving all existing fields.
Edits are durably encrypted before lookup and complete signed candidate before
submission. Retry uses the retained candidate; reopening checks it read-only.
Current wallet/backend and durable candidate identity must still agree before
broadcast. Exact signed author/body/time/recipients and latest snapshot ordering
are checked before availability; newer verified snapshots stop older retries.
Full-profile message cap and zero-payment self recipient use the established
shared signing interface. Profile draft v2 adds candidate/availability record;
v1 direct changes remain readable with unchanged filename/AAD/key alias.
Wallet import checks pending profile work through that record and treats read
failure as unknown. UI keeps pending fields immutable and reports uncertainty.

Local debug/test assembly PASS2m4s; dual seven-case GREEN and bounded review
pending. Independent actual-frame verifier added, syntax checked using isolated
Node24; no specimen exists yet, so it is not runtime compatibility evidence.
Avatar/banner selection/display, richer candidate recovery/conflict handling,
failure traces, true process death, cross-client receiving proof and final full
verification remain unfinished. No APK, node change or on-chain write.

First publisher6db0e74 focused native GREEN: Android10 38096716281 PASS7
cases26.877s; Android17 38096718157 PASS7 cases73.156s. Both actual original
availability PNGs individually inspected: selected owner server, exact frozen
edited bio, disabled Retry profile and Profile available on this server.
Raw UI logs, separate XML, public synthetic actual frames and isolated pinned
WASM/Elliptic verification retained profile-publishing-first-green/android10
andandroid17. Independent frame checks PASS both: transaction and hop signatures,
zero-valued self-recipient slips, full body preserving exact bio/image/banner/
archive/extensions and detached-envelope equality. SHA25610
5a7028c90d3d544e1be4109c542d141a6ec3c57fa6a78d4c8327a823d84dbb59;17
cb4f60cecb5945f6bf2c18f469de0e4c14f578fbef70a21e67ddc92c8bfc1874.
Source38096715823 PASS. This proves the controlled first publisher path and
independent native wire compatibility, not a receiving-web/DOM/live-chain proof.

Bounded601e518...6db0e74 reviews found Spec2/Standards1 hard preservation
issue, sharing the supersession finding. A newer verified snapshot observed
only by candidate reconciliation is not saved before returning, so fresh
Activity/stale reads can lose the barrier and permit an older retry. Also a
previously available record is reported as currently available even when a
successful exact query returns nothing; closing can clear that retained record.
The prior pre-submit zero-TX assertion gap is fixed. Both current source findings
remain open; normal-path GREEN does not override them or complete18.

Next single tracer exercises newer-snapshot durability: the actual initial
transaction is inspected, then a same-author newer signed snapshot appears only
in reconciliation. The editor must report supersession. Close the Activity
without going Back or refreshing the public profile, then serve only the stale
original snapshot. A fresh Activity must show the newer verified bio, retain
original unsent edits, show supersession and disable old Retry with no additional
transaction. Existing happy-path assertions are preserved in shared fixture
setup. Local client exited143 without a compiler diagnostic; the same build
container continued. Reattached logs and docker wait confirmed exit0 and
BUILD SUCCESSFUL1m4s, not a code failure or rerun. Intended eight-case RED and
bounded fixture review pending. Historical availability gets its own next
tracer after this slice; no barrier fix is implemented yet.

Controlled receiving compatibility now PASS for both independently verified
actual Android specimens. New helper executes unmodified hash-pinned A/B
Profile confirmation/receiving/reconstruction/cache methods and the original
profile-index metadata constant with in-memory Archive/keychain/UI adapters.
Both server and watched-own-browser cases ignore conf1, accept synthetic conf0,
preserve full fields/archive flag, reconstruct the newest snapshot, and update
own browser profile options/index/event payload while retaining archive nodes.
Pinned source hashes A15777808a5e4e88603b87dec7c28af177b418a9a46d0d8278ed569a25fc23d93
and B3839e35cbdf330fae46b5a6d92b71802b801054bbcbc8589aa0718e539d6aad3
are checked before VM execution. Both10/17 produce four accepted observations
each; retained profile-publishing-first-green/android10 andandroid17/
web-receiver.json. Initial adapter compared objects across VM prototypes and
failed despite equal metadata; normalize public metadata structurally before
asserting. Only the final hash-pinned runs are acceptance evidence.
No Saito initialization, private options, networking or deployment writes:
isolated read-only/env-i/network-none Node24 runner. This proves controlled
receiving methods and browser update event payloads, not DOM pixels, deployment
or chain inclusion. Supersession/historical-availability source findings remain
open; native eight-case RED38097083171 pending.

Supersession tracer8212022 native RED38097083171 confirmed: Android10 eight
cases29.678s, exactly one failure at ProfilePublishingPreviewTest.kt:148,
missing Newer profile from another device after a fresh Activity receives only
the stale original snapshot. Both original diagnostic PNGs individually viewed:
first editor positively reports supersession and disables Retry; fresh public
profile displays Original bio. Separate XML/raw UI log retained under
profile-supersession-red/android10. Other seven flows pass. This isolates the
missing durable observation, not a missing initial detection or fixture setup.

Bounded production fix saves the merged newer verified public profile cache in
NonCancellable reconciliation after immediately blocking retry in memory and
before returning settled. A failed write propagates the recovery error with
retry still blocked for that editor. Healthy-storage fresh reopening/stale reads
is the current tracer; storage-failure restart durability is not established by
this result. Historical-availability handling remains a separate open finding.
Local compilation and dual focused eight-case GREEN are pending.

Healthy-storage fix5fc6d78 local debug/test assembly PASS1m55s and source
38097639261 PASS. Focused Android10 38097639188 advances past the original
RED: newer public bio, superseded status and disabled Retry all pass, then
fails at line153 because hasSetTextAction is absent on the deliberately disabled
candidate field (eight cases31.559s, one failure). The retained diagnostic PNG
and separate XML show the editor, superseded status, disabled Retry and the exact
retained two-line bio. An earlier visual description incorrectly called it a
public-profile window; independent original-resolution review and PNG hash
eb484cd8b59b99793091bb0593267830e3fb7ece3b1b8f47008908caf73285df confirm
the committed and extracted artifact agree. Correct only the field selector
to exact EditableText and retain explicit displayed/disabled assertions. No
production change. Android17 38097640515 also fails only that selector at153:
eight cases79.589s. Original PNG individually viewed shows superseded status,
disabled Retry and exact retained disabled bio. Both SDK logs/diagnostics retained
profile-supersession-disabled-field. Selector-only local assembly PASS1m2s.
Successful publisher captures now also require positive expected status text
in the actual UiAutomator window before PNG/XML capture. Failure diagnostics
remain ungated to retain the actual unexpected window. This guards against
claiming a capture without positive native-window status as editor evidence.
Window-gated local build client exited143 without a compiler diagnostic and
container was already removed when checked. A retained fresh verification
container exited0, BUILD SUCCESSFUL18s with all68 tasks up-to-date. This confirms
assembly of the corrected fixture; do not infer an exit status for the original
interrupted client. Corrected dual runtime verification is pending.

Bounded ea37571...5fc6d78 Spec0 new; Standards1 residual hard preservation
finding: if public-cache saving fails, saving the separate encrypted old candidate
can still permit Back/settings/import and a new editor can lose the RAM-only
block. Honest evidence limits do not resolve this source path. After healthy
supersession GREEN, next tracer must exercise this failed-save/navigation escape,
then fix durable-barrier or fail-closed navigation. Historical availability stays
separate and open. No first publisher or ticket18 completion claimed.

Corrected healthy-storage supersession at539af56 focused GREEN: Android10
38098119577 PASS8 cases32.48s; Android17 38098120892 PASS8 cases80.879s.
Evidence profile-supersession-first-green/android10 andandroid17 contains exact
before/reopened PNGs, separate XML and raw logs. Independent reviewer viewed all
four original PNGs individually: each shows Edit profile, superseded status,
disabled Retry and exact frozen two-line edits. PNG bytes are identical within
each SDK, SHA25610 8c3e33259b7bf492a67cf615107d922209c7166322483046ea7d78585e541515
and17 72408b36fd471a91370c9dcaeb6bd42877b57953d23232abfa3be146ccf6195f.
Do not describe identical pixels as different windows or infer a viewer cause.
Fresh-Activity continuity/stale responses are established by fixture/log
assertions; screenshots prove the visible retained editor state. Bounded fixture
reviews found only the Android10 caption error, corrected9dcf5ec and both axes
zero residual for that correction. Production failed-save/navigation barrier and
historical availability remain open; healthy GREEN does not resolve them.

Next single native regression exercises the actual failed-cache-save/navigation
path. A nonempty AtomicFile .new directory blocks only replacement of the
public profile cache, leaving its old verified base readable and encrypted draft
saving unaffected. After real supersession and displayed persistence error,
serve stale original data, go Back, reopen editor, require retained supersession,
disabled Retry/exact frozen edit and no additional TX. The controlled obstacle
and disposable wallet restore independently; cleanup errors retain the primary
failure. This fault injection is explicitly API30+; API29's AtomicFile renames
to .bak and does not use that replacement path. Healthy supersession and existing
failed-draft-save/import tests run on both versions. Source mechanics checked
against Android platform tags android-10.0.0_r1 and android-11.0.0_r1; no production
or node operation. New test assembly and intended Android17 RED are pending;
no durable private barrier implemented yet. New debug/test assembly subsequently
PASS1m7s, retained container exit0; intended nine-case Android17 RED pending.

Actual cf985f9 RED38098588719: nine Android17 cases94.046s, two failures.
Targeted failure at ProfilePublishingPreviewTest.kt:159 after successful initial
supersession/error and Back/reopen: superseded status missing. Exact originals
individually viewed: cache-write-failed shows supersession/error/disabled Retry;
navigation-failure shows pending status and enabled Retry with exact frozen edits.
This positively demonstrates the guard loss; no extra submit was attempted.
The separate existing failed-draft-save/import flow fails at ProfileEditPreviewTest
line380 with No compose hierarchies while returning from SAF. Its exact PNG/XML
show open roots drawer, provider breadcrumb/content and the filename behind that
drawer. The fixture selected file before considering an open drawer and returned
on click without positive app-window observation. Seven other cases pass, including
both existing publication/supersession paths. All three original diagnostics,
separate XML and raw UI log retained profile-cache-failure-red/android17.

Bounded fix retains the full verified newer snapshot as an optional encrypted
owner/backend draft field, signed envelope on disk. Restore verifies snapshot,
owner, body, different candidate ID and signed timestamp before exposing it.
Persisted barrier blocks claim/availability clearing and is carried through
navigation; editor reopening restores it before any read and merges it into
reconciliation even with stale reads. Save private barrier before public cache;
failure to preserve it keeps the in-memory block and prevents successful navigation.
Existing v1/v2 readers, alias, filename/AAD and shared framing remain compatible.
The picker fixture now prioritizes/scopes open roots drawer before file selection,
requires positive returned app-window observation, and permits zero Compose roots
only while waiting for the same exact selected-address assertion. Same timeouts;
no application import behavior or acceptance assertion relaxed. Local assembly,
focused dual GREEN and actual committed-diff review are pending. Historical
availability remains separate/open; no ticket18 completion.
Bounded fix local debug/test assembly subsequently PASS1m55s, retained container
exit0. Dual focused device verification and committed-diff review remain pending.

Private signed barrier at2c446d6 focused dual GREEN: Android10 38099229129
PASS eight applicable cases plus one explicit API30+ assumption skip, runner
OK9 in32.191s. Android17 38099231234 PASS all9 in57.316s; source38099229274
PASS49 core/debug/test/release/lint. Bounded cf985f9...2c446d6 reviews Spec0,
Standards0 remaining source findings. Original screenshots individually viewed:
Android10 and Android17 healthy stale reopen show superseded/frozen editor.
Android17 initial failed cache and after navigation show superseded/frozen editor
with persistence error. Busy controls can differ at capture; do not claim all controls
settled from pixels alone. Separate XML/raw logs retained profile-private-barrier-green.
The observable acceptance assertions prove disabled older Retry and no extra TX.

Next single historical-availability tracer keeps the real confirmed signed
candidate through direct Activity closure, then serves successful empty exact
and latest Profile queries. Fresh editor must retain frozen bio with qualified
previous availability, never current availability. A read-only Check profile and
Back/reopen must retain that qualification and original candidate without any
additional transaction. This differs from outage or supersession; cached public
bio is still useful without claiming current server history. Local assembly and
intended Android10 RED pending; production unchanged for this tracer.
Historical tracer local debug/test assembly PASS1m8s, retained container exit0.

Actual historical RED de5a883, Android10 38099752101: ten runner cases39.514s,
eight pass, one explicit API30+ assumption skip and one expected historical
status failure at ProfilePublishingPreviewTest.kt:207. The original PNG was
individually viewed: settled Edit profile says Profile available on this server
with disabled retry/frozen original edit despite successful empty exact/latest
archive responses. PNG, separate XML/rawlog retained profile-history-red/android10.
Bounded tracer reviews: Spec1/Standards2, overlapping Check no-op observation
gap plus stale-reopen caption discrepancy. Strengthen Check with a positive new
exact-profile query and settled controls, retaining no-TX/status assertions. Split
healthy reopen captions from cache-failure/error captures; no rendering cause
inferred.

Minimal production fix separates durable historical confirmation from this
session's exact availability. Reopening starts unconfirmed; only exact verified
server observation sets current availability. Empty reads retain history and
frozen candidate through navigation. Check profile preserves the historical
flag and exits before authentication/submission when history was confirmed.
Existing durable claim guard still rejects confirmed candidates. Supersession
remains stronger than past confirmation; existing on-disk flag/version/keys and
formats stay unchanged. Local build, focused dual GREEN and bounded committed
review pending; fuller profile recovery/media acceptance still open.
Historical fix local debug/test assembly PASS2m14s, retained container exit0.
Only the subsequent fixture counter tightened to the exact indexed candidate ID;
hosted assembly/runtime will verify that final fixture.

Historical fix494a180 focused dual GREEN: Android10 38100143700 nine applicable
cases PASS plus one explicit API30+ assumption skip, runnerOK10 in38.625s;
Android17 38100145537 all10 PASS92.885s. Source38100140888 PASS core49/debug/
test/release/lint. Four original historical PNGs individually viewed: qualified
previous availability, Check profile and exact frozen bio before/after navigation
on both SDKs. Separate XML/rawlogs retained profile-history-green. Exact
candidate read count and no additional TX asserted; ordinary public cached bio
remains visible through successful empty server history. Bounded review Spec1
remaining status-copy edge, Standards0. Superseded wording claimed current
server availability from a retained barrier alone. Correct resource and the
existing exact positive supersession test literal to qualify newer verified
supersession without claiming current availability. Historical retained images
keep their original wording; no evidence rewrites or rendering-cause inference.
Copy-only committed review/source checks pending; next media tracer will run
existing supersession cases with that corrected wording. #18 recovery/media/full
acceptance still open; no ticket closure or APK.

Bounded494a180...4b33b06 copy/evidence review both axes zero. Both historical
before/after PNGs are byte-identical within each SDK; navigation continuity is
from actual fixture/log assertions, visible qualification/frozen bio from pixels.

Next single native media tracer at the existing Android application/persistence
seams selects red avatar and blue banner through controlled external picker
results, preserves typed Unicode bio, saves draft, deletes both source documents,
then opens a fresh Activity. Require recovered image pixels and bio with no TX.
Only explicit Save profile publishes; inspect actual binary transaction author,
self recipient, full snapshot, unchanged archive/extensions, JPEG pixel colors
and square avatar/3:1 banner geometry. Then require the same public profile pixels
and bio/identity/backend. Owned document cleanup and original wallet restoration
attempt independently and preserve primary failures. Missing selection controls
are the intended next RED; local assembly/native Android10 pending. No new
production media implementation yet.
Media tracer local debug/test assembly PASS1m12s, retained container exit0.

Bounded4b33b06...ea23279 media tracer reviews both zero. Copy-only source run
38100547421 was superseded/cancelled by the next push; do not record it as PASS.
Actual Android10 media RED38100747417 at ea23279: eleven runner cases43.279s,
nine pass/one API30+ assumption skip/one expected missing Change profile picture
control at ProfileMediaPreviewTest.kt:162. Original PNG individually viewed:
Edit profile with exact typed bio and Save profile, no picture/banner controls.
PNG/separate XML/rawlog retained profile-media-red/android10. Existing historical
and supersession cases pass with the qualified newer-profile wording.

First media implementation reuses the bounded provider/raster pipeline, adding
optional square/3:1 centered output crop and preserving default post geometry.
Android ImageDecoder crop operates in target-size coordinates and must be set
inside the header callback ([official reference](https://developer.android.com/reference/android/graphics/ImageDecoder#setCrop(android.graphics.Rect))).
Prepared bytes remain bounded JPEG without original metadata; draft saving
precedes external picker and provider reads. Exact whole-snapshot JSON budget
replaces the selected field with an empty placeholder before normalization,
then rechecks final bytes. Private changes include normalized image/banner
strings and retain the existing encrypted format. Public signed fields expose
safe raster sources; asynchronous profile/editor rendering shares the bounded
loader with generated-avatar fallback. Scrollable editor keeps bio and selection
reachable. Picked-result metadata is session-bound; private bytes stay outside
SavedState. No remove-image UI yet. Local build/core, dual focused GREEN and
actual committed-diff reviews pending. No production publication or APK.
First media core49/debug/test assembly PASS2m37s, retained container exit0.
After that build, picker metadata tightened to carry and compare the full public
wallet/backend/endpoint session plus media kind on restore. Hosted checks will
compile/verify that final source; no private data enters SavedState.

Bounded ea23279...d7e522f first media source review both axes zero. Hosted
source38101283097 PASS49 core/debug/test/release/lint including final restored
picker session restriction. Focused10 38101286465 fails11 cases54.84s (nine
pass/one API30+ skip/one failure);17 38101288539 fails11 cases104.869s (ten
pass/one failure). Both media flows advance through selection, deleted-source
fresh recovery, complete candidate field/JPEG geometry/pixel verification,
one transaction and positive Compose visible available status. Then the exact
UiAutomator By.text availability capture gate fails at222. Original failure
PNGs individually viewed: available status is visible above red avatar/blue
banner while editor is scrolled down; XML omits that status and shows disabled
bio accessibility bounds overlapping its visible region. Separate exact
PNG/XML/rawlogs retained profile-media-accessibility-red. This is not media
acceptance; Back/public-profile checks and independent current-frame verification
were not reached, and actual frame export was after the failed capture.

A bounded source correction raises fixed candidate-status paint order above
the scrollable editor, so its accessibility region is considered above the
expanded bio-field bounds. Keep the same scrolling, exact Compose/status and
UiAutomator gates; no viewport-reset fixture workaround or timeout extension.
This draw-order explanation remains a hypothesis until device verification.
Move public actual-frame export before diagnostic capture, after the same one-TX/
availability assertions, so a future capture failure cannot discard wire proof.
Expand the independent verifier with an explicit selected-media scenario: same
cryptographic/hop/envelope checks, literal bio/unchanged metadata, canonical bounded
JPEG payloads with literal8x8 avatar/12x4 banner headers from12x8 sources. Native
flow separately checks decoded pixel colors. Updated verifier PASS on the prior
actual10 bio frame (SHA5a7028c90d3d544e1be4109c542d141a6ec3c57fa6a78d4c8327a823d84dbb59),
retaining its original scenario; current media frame inputs not yet captured.
Local assembly/new focused dual runtime and committed review pending.

Independent reviewer inspected both originals and the exact matching
[Compose UI 1.11.4 official source archive](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-android/1.11.4/ui-android-1.11.4-sources.jar).
SemanticsOwner.kt275–312 traverses children back to front and subtracts important
nodes' touch bounds; SemanticsNode.kt301–318 obtains z-sorted children. This
supports the bounded zIndex candidate. XML field bounds are Android10 y272..360
versus scroll viewport296..624, Android17 y248..360 versus viewport272..624.
The overlap is observed; attribution of the omission to it remains a hypothesis
until fresh native verification. Retained local debug/test assembly PASS1m55s.

Bounded d7e522f...16f23ec committed source reviews: Standards zero documented
breaches/actionable smells; Spec zero findings. Exact native gates preserved;
selected-media checks fix known dimensions/metadata independently, with native
pixel checks separate. Runtime10 38101974487 and17 38101974419 pending.

Status-layer candidate16f23ec dual focused GREEN: Android10 38101974487
OK11 in44.276s (ten applicable pass/one explicit API30+ assumption skip);
Android17 38101974419 all11 pass65.943s. Source38101971926 PASS49 core,
debug/test/release/lint. All six original media PNGs individually viewed: prepared
red avatar/blue banner, native available status, public media with exact Unicode
bio and owner. Exact PNG/XML/rawlogs/actualframes retained profile-media-green.
The unchanged exact By.text gate now passes on both SDKs; overlap/traversal was
the supported correction hypothesis, not a separate instrumented causal proof.
Both actualframes PASS selected-media pinned WASM/Elliptic/hop/envelope/JPEG
verification, SHA10 e567cf350e1dff90a25a3467c2f8368ba13fa967f57e3de362d9b7ef88ad9516,
SHA17 701c79eed96d2f0e23a821057ab37a12f6b0595d708cd8e7443eb14508b8d414.
Hash-pinned A/B server/browser receive/reconstruction PASS four observations for
each; reports retained. No DOM pixels, live-chain/production-write acceptance.

Integration review afce661...16f23ec: Spec zero new source findings; Standards
one progress-preservation concern. Source interleaving: old non-cancellable
confirmation can finish after a recreated editor loaded an unconfirmed record;
stale mounted saves then replace its new historical flag (or superseding guard).
The shared lock serializes writes but does not preserve previous progress. This
is a source finding, not a reproduced runtime failure. Protect mounted saves
against replacing another candidate, and retain monotonic confirmation/newer
barriers for the same candidate, including deliberate clear semantics.
Removal/reset, discard/new editing, failure/session/migration/process and full
acceptance remain open.

Review-stage progress preservation correction: mounted saves reject a different
durable candidate or altered frozen changes, merge the same candidate's historical
confirmation and newest verified superseding snapshot under the shared lock, and
return that retained progress to the mounted editor. Deliberate clear has an exact
candidate-ID operation which clears only a confirmed, non-superseded record;
a stale clear preserves newer durable guards. Reconciliation stores the returned
newest guard in the public cache. Existing exact-byte/claim gates remain.
No new internal-state test or race reproduction claim; local build, committed
review and native regression pending. Actual recreation/interruption still required
for ticket acceptance.
Local progress-preservation debug/test assembly PASS2m2s, retained exit0.

Bounded16f...b93 review: one Standards residual clear/re-edit edge; Spec review
pending. Once a confirmed candidate is deliberately cleared, an old mounted save
sees transaction=null and could resurrect it over newer plain edits. Source
interleaving only, no runtime claim. Tighten expected-record comparison with a
nonnegative encrypted revision retained through empty clears. Legacy v1/v2
without the optional revision read as0; subsequent saves increment under lock.
Different no-candidate revisions/plain drafts reject stale writes; same frozen
candidate can still merge monotonic confirmation/newer guards. Mounted editor
tracks returned revision, confirmation returns its exact saved record, and clear
compares the mounted expected record before removal. No private SavedState/new
file family/key alias/AAD. Local build, committed review and runtime pending.

Next ONE native tracer at the existing app/persistence seams exercises confirmed
publication → real SAF wallet import → Back cancellation → dismiss wallet → type
a new bio. Require Save profile enabled, no old current-availability claim, one
TX only, then Save draft and fresh Activity recovery of that exact unsent bio,
without public publication. Expected RED is disabled Save after retain cleared
the candidate without resetting live availability. The expected-record revision
fix is included but does not fix that separate live-state bug yet.

Bounded16f...b93 Spec: one actionable live-availability finding, confirmed
publication → import beforeSelection → persist clears candidate → cancel/dismiss
wallet leaves editor mounted. retain cleared candidate/changes but not available,
so editing becomes enabled while Save stays disabled. This is the new native
tracer's expected failure; not an observed runtime result yet.
Expected-record revision plus tracer local debug/test assembly PASS2m1s.

Bounded b93...493 committed review: Spec zero new findings (known live
availability bug remains intentionally unfixed); Standards resolves stale-clear
source protection, one fixture package finding. New cancellation gate accepts
only com.android.documentsui, but retained original17 SAF XML at
profile-cache-failure-red/android17/import-transition-failure-38098588719.xml
identifies com.google.android.documentsui. Accept both observed DocumentsUI
packages before running17, retaining actual external cancellation/no-TX/new-bio
assertions. No runtime scheduler-race proof. Current10 RED run38102826915 pending.

Planned actual-interruption tracer reuses the existing host-separated image
recovery boundary and isolated .dev emulator. Phase1 prepares square avatar and
3:1 banner through controlled picker results, deletes owned sources, changes the
bio, opens the real external picker (saving first), and kills its own PID after
a public-only checkpoint. Phase2 requires cold recovered pixels/newest bio, zero
TX until explicit Save, then retains a signed candidate while Archive returns
empty and kills again. Phase3 serves that exact candidate, requires read-only
reconciliation/current availability/frozen media and zero new TX, then normal
Back and new editing without a pending record. Host validates both dead PIDs
and current phase/signature, force-stops between phases and retains diagnostics.
Use the emulator's current local wallet (assert public identity continuity),
never export its private key or mutate production state; checkpoints contain
only public synthetic signed profile data. Sources must be deleted before
kill because finally blocks cannot run after actual death. No tests/harness
written or executed for this plan yet.

Actual Android10 cancellation RED493948d/38102826915: twelve cases51.01s,
ten applicable pass/one API30+ assumption skip/one expected disabled Save
profile at196 after real SAF cancellation and editing. Original PNG individually
viewed: enabled bio contains My next unsent profile bio plus seedling, Save
profile grey/disabled, no candidate availability label. Exact PNG/XML/rawlog
retained profile-import-cancel-red/android10. Revision protection's existing
flows pass; this is not deterministic scheduler-race proof.
Minimal fix clears live current-availability when retain adopts no candidate,
a different candidate or a superseding record. Historical qualification remains
derived from the durable record. Fixture supports both observed DocumentsUI
packages and bounded Back through a drawer; positively require returned app
window and exact cancellation message before unchanged new-edit/no-TX/recovery
assertions. Local assembly/dual native GREEN/committed review pending.
Source493948d/38102826875 PASS core49/debug/test/release/lint, including revision and cancellation tracer.
Cancellation fix local debug/test assembly PASS1m53s, retained exit0.

Bounded493...eb8646c source reviews both zero. Actual10 eb8646c/38103280834
12 cases74.258s: ten pass/one API30+ skip/one unrelated existing first import
helper failure at ProfileEditPreviewTest330, reference document not displayed.
New real cancellation/new-edit/fresh-draft case passes all assertions. Three
original PNGs individually viewed: new bio/Save enabled before and after fresh
Activity; failed helper capture already shows returned app with correct selected
wallet preview and pending-work warning. Matching XML confirms selected address
and package. Retained profile-import-cancel-first/android10, including rawlog.
Helper checks app return only immediately after file click; when that short wait
expires it never recognizes later success. Bounded fixture correction observes
returned app AND exact expected selected-wallet address at every loop iteration;
keep exact filename/provider/drawer selection and30s deadline. Wait for same
selected address after click, not transient package alone. No production change
or acceptance assertion removed. Corrected10 proof pending;17 original pending.

Android17 original eb8646c/38103280642 GREEN all12/103.519s. Both original
cancellation new-edit/recovered-draft PNGs individually viewed: exact next bio
and enabled Save profile before/after fresh Activity, no old availability label.
Exact PNG/XML/rawlog retained profile-import-cancel-green/android17. This
includes revision/picker packages/current-availability production corrections.
Current picker-helper correction changes tests only and strengthens final return
recognition; corrected outstanding10 pending. No full/process acceptance yet.
Picker return fixture local debug/test assembly PASS1m9s, retained exit0.

Bounded eb8646c...edc39c3 committed review: Standards0/Spec0. Each reviewer
individually inspected all five original images and counts. Failed10 capture
proves the correct preview existed at capture time, not exactly when it appeared
during polling. Source38103756099 PASS core49/debug/test/release/lint.

Next ONE native removal tracer reuses the actual selection/publication flow:
first publish square red avatar/3:1 blue banner with retained literal Unicode bio,
then choose generated picture/remove banner, save without a second publication,
require unchanged public custom images, cold Activity reopen with both removals
retained, and explicitly publish a second full snapshot omitting image/banner.
Retain bio/archive/extension, identity/backend, exact two-publication boundary
and visible generated fallback. External picker results remain the only supplied
UI outcomes. Existing selected-media tracer remains independently named and
its captures are prefixed separately from removal preparation. No production
removal implementation yet; expected RED is missing removal control.
Profile harness now runs its three existing classes separately with unchanged
180s bound per class, checks EVERY class result and retains separate raw logs
plus concatenated legacy log. This avoids accumulating all expanded profile
cases under one deadline; it does not weaken assertions or hide a failed class.
Local tracer assembly and corrected10 runtime pending.

Corrected10 edc39c3/38103756457 still FAIL12/108.84s: nine applicable passes,
one API30+ skip/two older picker helper failures. New cancellation and original
selected-media flows pass. Both original failure PNGs individually inspected,
with exact XML showing returned correct selected wallet preview. They are
captured only AFTER ui.waitForIdle, so cannot prove visibility during the failed
native-only loop. Matching cached official Compose1.11.4 ui-test source:
ComposeUiTest.android.kt waitForIdle runs composition to idle and awaits draw;
its waitUntil advances the test clock, whereas a native UiDevice polling loop
does not. Sharper synchronization hypothesis, not instrumented causal proof.
Helper now checks foreground currentPackageName (rather than any behind-window
app node), synchronizes Compose ONLY after return, then still requires exact
selected description/address. Same provider/file/deadline and downstream
identity/no-publication/backup gates. No production change.
Removal tracer initial local debug/test assembly PASS1m9s, exit0; helper addition
requires fresh assembly before dispatch. This combines ONE new removal tracer
with a repair to an older existing fixture, not another behavior slice.
Fresh helper/removal debug/test assembly PASS1m6s, retained exit0. Four original
corrected10 failure/new-edit/recovered images inspected and retained verbatim
with exact XML/rawlog under profile-picker-return-first/android10.

Bounded edc...3b0b234 Standards0/Spec0; all four originals independently
inspected. Source38104185958 PASS49/debug/test/release/lint.
Interim full afce661...3b0b234 Standards1/Spec1, same newly actionable source
finding: AtomicFile commit can precede directory fsync/reopen failure; mounted
expected revision remains old. Repaired-storage retries then reject the newly
committed plain record (or candidate/clear), although cold recovery retains
work. Reconcile exact attempted outcomes without accepting unrelated stale
writes; next failure slice must include late-commit handling. No runtime
interleaving reproduction. Remaining previously acknowledged scope unchanged.

Actual Android10 3b0b234/38104185964: editor OK6/36.997s (all six including
both repaired import helpers PASS); publisher OK5/32.842s (four PASS/oneAPI30+
assumption skip); media two cases/19.215s, existing selected-media PASS and new
removal expected RED at149, missing Use generated profile picture control.
Original failure PNG individually inspected: unchanged published red picture,
bio retained, empty new draft/disabled Save; reset/remove controls absent.
Exact XML/separate class logs/combined rawlog retained profile-media-removal-red.
Native result supports fixture repair; it is not isolated causal proof of the
prior helper's clock issue.
Minimal removal adds editor null overrides, enabled reset/remove controls only
for existing media and unfrozen editable drafts. Encrypted validation permits
image/banner null removal markers; signed candidate validation requires those
fields OMITTED from its complete snapshot. Existing SaitoProfiles.message
already implements removal. UI respects null rather than falling back to old
public media. Independent removed-media verifier requires literal bio/archive/
extension and exact absence of image/banner, with unchanged crypto/envelope/hop
checks. Local build/current dual-native GREEN pending.
Removal local core/debug/test build PASS2m4s, retained exit0; core49 unchanged.
Independent verifier syntax check PASS; no actual removal frame produced yet.

Removal source bounded3b0b234...412d0e3 Standards0/Spec0. Actual dual GREEN:
Android10 38104649088 editor6/29.809s,publisher5/25.803s (fourpass/oneAPI30+
skip),media2/19.826s. Android17 38104651018 editor6/58.153s,publisher5/29.053s,
media2/18.681s, all13PASS. Six original draft/available/public PNGs individually
inspected: default generated avatar/absent banner/exact retained Unicode bio,
public profile identity and available status. Exact XML/class+combined logs and
actual second native frames retained profile-media-removal-green/android10|17.
Both actual removal frames independently PASS pinned WASM/Elliptic/hop/envelope/
exactliteralbody without image/banner; four A/B browser/server receiver and
reconstruction observations each PASS. SHA10 c710f98f399ab373b17932bbb879b73d07eedebc048b186d4fa3c244dc2b98cf;
SHA17 73010a8e6d6685bfe78ef601e156e9d3abe99d0aa7478e768eda7460e8345eb8.
Source38104649178 PASS49/debug/test/release/lint. These are controlled native
and in-memory receiver proofs, not live-chain, web DOM or deployment evidence.

Next ONE late-save tracer uses the agreed native UI/filesystem recovery seam.
Seed actual encrypted draft/key, type new literal bio, restrict only the owned
isolated emulator noBackup directory to owner write+execute, Save draft and
require truthful visible error/retained editable bio/zeroTX plus changed encrypted
synthetic record digest (not generic pre-write failure). Restore exact original
permissions in primary-preserving finally BEFORE retry/cleanup, then require
same-mounted-editor Save succeeds and fresh Activity recovers exact bio/identity.
No wallet-key reads/exports or process termination while permissions restricted.
[Linux open permissions](https://www.man7.org/linux/man-pages/man2/open.2.html)
and local shared writer order support the inferred fault: AtomicFile finishes
before fresh O_RDONLY directory verification. Disposable ordinary-user host
experiment independently confirmed replacement/known-file read succeeds and
directory O_RDONLY fails; original permissions restored and fixture removed.
Android feasibility and expected stuck retry RED are not observed yet.
Proposed recovery is anchored to the exact mounted expected record and exact
validated previous/intended own write outcomes, preserving a previous committed
witness when a subsequent retry fails pre-commit. Candidate/frozen/newer guards
must remain; readable unsynced bytes are not success. No production retry fix
written yet. Initial single-marker proposal had a repeated-failure gap found by
review; refined bounded previous/intended proposal is still only a plan.
Late-save tracer local debug/test assembly PASS1m8s, retained exit0.

Bounded412d0e3...f3d5136 Spec0 new source issues, one evidence qualification;
Standards0 hard/one diagnostic heuristic. Both independently viewed all six
original removal PNGs and verified counts/frame hashes/reports. Android17
removal-draft XML describes the earlier PUBLIC profile with custom picture/
banner while its original PNG shows the generated-avatar removal EDITOR.
Root separately parsed all six XMLs and confirmed that one discrepancy.
Retain originals unchanged. Cause unproved; this pair is not a matching snapshot.
Native Compose removal assertions, bitmap, exact available/public XML and actual
signed omission/receivers support their separate claims. Future removal-draft
capture now positively gates native Save profile window before PNG/XML capture.
Late-save diagnostic review adds an in-scope primary-preserving wrapper for BOTH
mounted and cold phases, so cold failure is captured before Activity closes.
Successful late-save captures positively gate exact error or recovered bio in
the native window. No acceptance assertion changed; current RED run still at
immutable f3d5136, these fixture fixes not built or dispatched yet.

Actual unchanged f3d5136 Android10 38105211508 unexpectedly PASS: editor7/35.66s,
publisher5 (one API30+ skip), media2. Two original late-save PNGs individually
viewed by root and Spec reviewer, exact XML/class logs retained under
profile-late-save-gate-audit/android10. First shows truthful save error/typed
bio; fresh Activity shows recovered bio. These do NOT prove mounted retry.
Spec review found repaired Save draft success wait also matched the existing
editor title, so failure could satisfy it before Activity closure. No remount
is required to explain the false positive. Same immutable Android17 audit
38105678928 running; no production change. SAME tracer now requires clickable
enabled PUBLIC Edit profile button and absence of editor field/error before
Activity closure; seed gate also tightened. Diagnostic fixes included. Fresh
local build and intended RED pending. No production remediation yet.

Corrected same-tracer local debug/test build PASS1m7s, exit0, retained rawlog.
Both reviewers independently confirmed the title-match gap and inspected both
original audit images/XML/logs. No production change; intended RED pending.

Bounded f3d5136...3c757d6 Standards0/Spec0; both reviewers independently
confirm corrected clickable PUBLIC retry gate and audit qualification. Corrected
Android10 intended RED38105828806 running. Production unchanged. Read-only
design review prefers an internal pre-write callback over duplicate strict UTF8
encoding: only after codec/encryption/file validation, under the existing lock,
install the defensive-copy attempted-outcome witness immediately before write.
This remains a proposal until corrected runtime failure; no implementation.

Old invalid-gate Android17 f3d5136/38105678928 also PASS: editor7/54.491s,
publisher5/27.709s, media2/17.6s, all14 reported cases. Root individually viewed
both late-save originals and retained exact PNG/XML/rawlogs under gate-audit/
android17. Same qualification: truthful error and cold recovered bio, no mounted
retry completion proof. It does not justify production acceptance or remediation.

Corrected tracer 3c757d6 Android10 38105828806 expected RED at99: repaired
Save draft never returns clickable public Edit profile. Editor7/46.297s, exactly
new late-save failure; other six pass. Publisher5/25.868s (fourpass/one API30+ skip), media2/18.568s PASS.
First truthful error/changed synthetic ciphertext/typed editable bio/zeroTX
assertions pass, permissions restored BEFORE second click and failure capture.
Both original PNGs individually inspected and exact XML/class rawlogs retained
profile-late-save-red/android10: same editor/error/bio before and after repaired
retry. Cold phase is unreachable in this RED, no cold recovery claim from it.
Source38105828524 PASS49/debug/test/release/lint.
Minimal remedy now written: per-store defensive-copy attempted outcome with exact
mounted anchor and validated previous/intended records; recognize only those
exact own outcomes through repeated pre/post-write errors. Shared writer internal
pre-write callback installs marker after codec/encryption/file validation, under
existing lock; failed prevalidation preserves previous marker. Verified write
clears marker; failure still throws, readable unsynced bytes are not success.
Same candidate/frozen changes/newer/historical guards remain. Exact own cleared
empty record can be rewritten and verified on retry, including previous outcome
after a later pre-commit failure. Prepared candidate is retained in RAM before
persistence so failure cannot cause new signing; all network use still requires
verified candidate persistence. Local build/review/dual GREEN pending.

Late-retry local core/debug/test build PASS1m57s, exit0, retained rawlog.
Core49 unchanged; corrected native dual GREEN and committed review pending.

Late-retry bounded3c757d6...c8af00f Standards0/Spec0; both independent reviews
address original late-commit source finding without claiming full acceptance.
Actual corrected dual GREEN c8af00f: Android10 38106287107 editor7/36.683s,
publisher5/26.721s (fourpass/oneAPI30+skip), media2/18.209s; Android17
38106288790 editor7/73.061s,publisher5/31.788s,media2/20.704s, all14PASS.
Source38106287358 PASS49/debug/test/release/lint. Four original late-save PNGs
individually inspected; matching XML/class+combined logs retained under
profile-late-save-green/android10|17. First truthful failure and same-mounted
verified retry/public return/no editor/no error and fresh exact Unicode bio
recovery all PASS with zero transactions and unchanged public owner/backend.
Bounded scope audit found a usability gap: retained superseded/never-returning
candidate freezes profile editing indefinitely. Spec does not prescribe a
Discard button but requires profile editing to remain usable. Next ONE native
slice exercises supersession → explicit start-new confirmation/cancel → editable
newest verified baseline → unsent draft save/cold Activity recovery → explicit
new signed update, preserving latest untouched fields and never resending the
retired candidate. Explain retirement cannot undo an update received elsewhere.
Actual process-death proof remains next after this capability, not deferred.
Shared completed#15 provider/size proofs need not be duplicated wholesale.

Next ONE Start-new tracer now written in its own bounded native class. Controlled
first publication is superseded by a signed own snapshot with changed archive/
extension and later timestamp. Require cancel preserves frozen status/oneTX,
confirm returns editable newer bio/no old status/no candidate, unsent new-bio
draft survives fresh Activity with stale responses, then explicit second
publication preserves latest media/archive/extension and has a newer signature/
timestamp with exact twoTX. Capture actual second frame for independent receiver
proof. Harness adds this fourth class with unchanged180s per-class bounds and
checks every class. No retirement production code yet; expected RED is missing
Start new edit control. Local build pending. Actual process-death tracer follows.

Single Start-new tracer local debug/test assembly PASS1m10s, retained exit0.
Harness shell syntax PASS. Expected native RED and bounded review pending.

Bounded c8af00f...82ab54d Spec0/Standards0 hard, one low-priority duplicated
capture-helper heuristic. Actual intended Android10 RED38106837237 at117:
Start new edit absent after truthful supersession/frozen bio/disabled retry/oneTX.
Other classes pass editor7/32.423s,publisher5/24.056s (fourpass/one assumption),
media2/16.905s. New1/3.941s fails; later phases unreachable. Both originals
individually viewed, exact XML/rawlogs retained profile-new-edit-red/android10.

Minimal retirement implementation now written: explicit warning/cancel/confirm;
revision-guarded exact-candidate retirement retaining the newest verified signed
own snapshot privately. Optional v2 baseline is backward compatible, validated,
included in equality/retry provenance and retained through plain saves, preview,
media-budget preparation and fresh merge/signing. New complete signed candidate
drops private baseline. No unconfirmed public-cache insertion; memory changes
reset after verified retirement. Generic saves cannot resurrect this store's
failed retirement/clear, including unchanged disk outcomes; same explicit retry
can repair and verify exact own outcome. New timestamp exceeds retained signed
updates. Local compilation, committed review and dual GREEN pending.

Retirement local debug/test build PASS2m, exit0, retained rawlog. Actual second
frame independent verifier adds literal new-edit scenario for latest bio/archive/
extension/media; no runtime frame or receiver result claimed yet.

Retirement bounded82ab54d...c78ebeb Spec1/Standards1 hard: pending cold preview
can fall back to stale public media after private baseline is dropped into the
full signed candidate; editable private preview also fails to merge newer public.
Signing/retry bytes unaffected. Source-reachable, no runtime proof yet.

Actual c78ebeb new-edit dual GREEN: Android10 38107420462 editor7/36.816s,
publisher5/26.992s (fourpass/oneAPI30+skip),media2/18.153s,new1/11.001s;
Android17 38107422060 editor7/83.711s,publisher5/33.546s,media2/21.458s,
new1/12.216s, all15PASS. Source38107407359 PASS49/debug/test/release/lint.
All twelve originals individually viewed; matching XML/exactrawlogs/actualframes
and independent WASM/Elliptic plus four A/B server/browser receiver reports each
retained profile-new-edit-green/android10|17. Framehashes10
545cd83c81971a0197adafee74ec98ece2feafed1e21a1bd9ad2273e72963cdb;17
0d5ea08d8a401713347eef4dcc4d5477aca1ac354487fb785163d4029d4aa8e7.
Synthetic unavailable HTTPS images do not prove media download. No DOM/live chain
or process-death claim. One new native preview tracer now written: initial public
URLs stay cached, fresh signing baseline omits media, first pending mounted
preview must use generated defaults; cold pending preview must retain exact
omissions despite stale public reads, then exact confirmation must not rebroadcast.
Expected RED is missing generated avatar on cold candidate. Production unchanged.
Local build pending. IO-derived candidate/full editable merge fix remains proposal;
no expensive crypto on typing/main and no stale produceState result may leak.

Preview tracer local debug/test assembly PASS1m5s, exit0, retained rawlog. Two
precompile fixture corrections cancelled and logged, not successful build claims.
Only the final corrected fixture build counts. Native intended RED pending.

Bounded c78ebeb...44b9402 review found disabled field selectors using SetTextAction
can fail before target; same tracer corrected to exact EditableText(bio) plus
disabled assertions in both mounted/cold phases. Old run38107880208 cancelled
while source-build step before device runtime; exact log retained in scratch,
no RED/production failure claim. Corrected local assembly pending.
Both reviewers independently inspecting all twelve originals. Standards notes
Android17 available PNG Check profile versus XML Save profile; root parsed and
confirmed discrepancy. Same bio/status essential state, not atomic matched
control snapshot. Originals retained unchanged; no invented cause.

Corrected same-tracer local debug/test assembly PASS1m4s, exit0, rawlog retained.
Intended native RED pending; production remains c78ebeb unchanged.

Corrected-selector bounded44b9402...ffed71f Spec0/Standards0 residual.
Root then identified image-fixture insensitivity before device runtime: failed
HTTPS ProfileImage falls back to generated picture and unlabelled banner
placeholder, so initial custom-label checks fail and later generated-only checks
could misclassify unavailable images as omitted fields. Cancel38107993254 during
SDK setup; exact log retained, no runtime RED/production finding claimed.
Same ONE tracer now serves signed own old baseline with real red/blue inline PNGs,
waits positive custom picture/banner labels before editing, while fresh signing
snapshot omits both. Exact omitted-field state additionally requires absence of
Use generated profile picture and Remove banner controls, independent of raster
loading and failure placeholders. Disabled exact-bio gates remain. No production
remedy yet. One precommit fixture-strengthening build canceled/logged; corrected
final local assembly running. No success claimed for canceled attempts.

Final inline-image discriminator fixture local debug/test assembly PASS1m6s,
exit0, rawlog retained. Intended native RED pending. No production changes.

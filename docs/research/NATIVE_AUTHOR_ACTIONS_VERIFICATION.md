# Native author actions verification

Active #14, fixed base `6c657d4`; boundaries #1 seams1–4. Primary source contract
NATIVE_EDIT_DELETE_CONTRACT.md. No production writes or node mutation authorized.

## Latest source checkpoint: 3132404

### Hosted execution restored; Android10/17 focused GREEN

On2026-10-10 the owner authorized public repository publication after a tracked
history and issue/comment credential scan. GitHub PUBLIC visibility and anonymous
access were verified; runners execute again without changing billing/spending.

First current Android10 run38051764840 at f8060cb executed all six author UI cases
in22.742s. Five passed, including stale-cache protection, edit/pending-delete/
same-candidate retry, proof propagation and mounted quote deletion. Conflict
failed at a fixture selector requiring SetText on a disabled field, not an
application failure. Inspected screenshot shows retained text/disabled editor
and conflict choices. f177057 selects displayed text instead, retaining exact
text and disabled assertions; the same correction covers cold edit recovery.

Focused Android10 run38052021331 at f177057 passes all six cases24.655s, plus
actual process-death cold recovery for edit2.942s and delete2.835s. Both retain
the wallet and original candidate, open saved work without transmission, then
observe availability on explicit Retry with zero rebroadcast. Inspected conflict
and recovery screenshots support those statuses. Logs and images retained in
`docs/evidence/ticket13/author-android10-green/`; first failure/public captures
in `first-current-android10/`.

The actual native create/edit/delete frames captured by the passing author-flow
case in38051764840 independently verify with pinned WASM0.3.2/Elliptic6.6.1,
including signatures, routing hop, target, author and recipient ordering. Both
unchanged A/B receiver methods apply the native edit, ignore its repeat, preserve
the original signature and remove it on the native delete under an in-memory
Archive adapter. Native-mode results retained in `first-current-android10/`.
Executed network-none/read-only/cap-drop/no-new-privileges/env-i, named public
dependency/script/frame/source mounts. Initial read failed because the pulled
Android frame file was mode0600; a readable copy of that public synthetic frame
input fixed the diagnostic. No signing keys/options/databases were loaded and
no initialized Saito application or production transaction was used.
This establishes native transaction/server-receiver interoperability at the
controlled boundary, not DOM, live deployment or chain inclusion.

Focused Android17 run38052279137 passes all six cases74.175s, edit recovery3.388s
and delete recovery3.322s. Continuity logs and inspected recovery images retained
in `docs/evidence/ticket13/author-android17-green/`.

Full run38052234326 passes core35/debug/test/release/lint and eight browse cases,
then fails three of eight Following cases on Android10. All three time out after
attempting a feed author click. Inspected screenshots show the post body visible
but its author link above the viewport. `waitFor` and `assertPostAbsent` scroll
by body text; a body being visible does not imply that its author link is visible.
The added Saved post changes header reduces the feed viewport. The bounded
fixture correction explicitly scrolls each feed author link into view, asserts
it is displayed, clicks, then observes Profile before unchanged follow assertions.
No timeout extension or application change. Failure log/image retained in
`docs/evidence/ticket13/following-fixture-failure/`. Focused and final full
verification of this correction were pending at dispatch. Focused Following
run38052915840 passes all eight cases22.384s on Android10; run38052917663 passes
eight66.312s on Android17. Logs and unfollowed-empty-feed images retained in
`following-android10-green/` and `following-android17-green/`. Both fixture-review
axes have zero actionable findings. Full38053152722 at f26d3bb passes core35/build/release/lint, then seven of eight
browse cases on Android10. Profile older-likes lookup times out after clicking
Load older. Inspected screenshot shows recent likes, no error and enabled Load
older after the timeout. Profile source publishes cached rows before completing
the new tab's network request and disables pagination during that request. The
fixture waited for cached rows but not enabled pagination, permitting a lost
disabled-button tap. This is a timing hypothesis supported by source and bitmap,
not an independently traced request sequence. The bounded fixture correction
waits for and asserts enabled Load older before both existing paging clicks,
retaining all older reply/like assertions and15s timeouts. Failure log/bitmap:
`profile-paging-fixture-failure/`. Focused browse and final full acceptance of
this correction are pending; public reads had not yet executed. Both review axes previously
confirmed the disabled-field selector fix; application source remains3132404. The current-retweet quote
receiver limitation below remains separately visible. Keep #14 open until
applicable acceptance is satisfied. Earlier billing/unexecuted statements below
are historical checkpoints, superseded by this section.

### Post-control viewport correction

At5d73bf9, focused browse38053551818/38053553901 pass eight cases17.08/37.352s
on Android10/17, including older reply/like pagination. Logs/inspected images
retained in profile-paging-android10-green/profile-paging-android17-green.
Full38053764214 passes core35/build/release/lint and Android10 browse8,
Following8, posting15, queue6, plus posting/queue/social cold recovery. It then
fails two of eight social cases at repost action taps. Both inspected screenshots
show Like visible and Repost below the viewport. Logs and screenshots retained
in `social-viewport-fixture-failure/`.

A shared test-only post-list helper waits for an enabled unique text target,
scrolls that actual target into view, asserts visibility/enabled state, then
clicks. Social action/retry/body/author clicks use it. Auditing analogous quote
and author fixtures found unguarded Quote buttons and feed author links; they
now use the same helper. Existing transaction/count/identity/backend/no-rebroadcast
and displayed-state assertions remain intact. No production source or existing
acceptance timeout changes. Local test compilation passes52s; both supplemental
review axes0 actionable. At16ac9b0, focused social38054544113, quotes38054546080
and author38054547958 were dispatched. Social PASS8 in13.767s plus real cold
recovery2.877s; author PASS6 in27.413s plus edit/delete cold recoveries. Logs and
selected inspected images retained in visible-controls-social-android10-green/
visible-controls-author-android10-green.

Quote38054546080 passes the first four cases, then original-recovery times out
at line251 after the second raw Retry. Its later publication case starts, but
the suite180s timeout interrupts it without a result. No failure bitmap was
captured in that run; no later-hang cause established. The two preview Retry
buttons now use positive visibility/enabled navigation. A source audit also
caught a raw Quote tap in the separate host process-death fixture; it uses the
same helper. Static publication step markers and host screenshot/bounded10s XML
capture improve timeout evidence without extending180s or removing assertions.
Both supplemental review axes0 actionable. Corrected quote runtime and final
full acceptance remain pending. Evidence quote-viewport-fixture-failure/. No #14 closure or new APK publication.

### Recovery-test checkpoint: 1f4703f

Application source remains3132404. Six author Android UI cases now compile,
including a competing-edit flow that exercises both Keep my text and Use latest
text. Each choice must leave transmission unchanged until explicit Save changes;
the emitted signed text and three distinct edit candidates are checked.
Two separate host-driven process-death stages prepare an uncertain EDIT or DELETE,
kill the actual application process, cold-launch with the same wallet and
candidate, open Saved post changes without transmission, then verify the exact
observed candidate on explicit Retry without rebroadcast. Both stages are wired
into focused author checks and final full acceptance. Timeout/failure output is
retained by their host script.

Final test APK assembly passes in1m. Shell syntax, Python parsing and whitespace
checks pass. This establishes compilation only: all six current-source UI cases
and both process-death recovery stages still await Android execution. No runtime
RED/GREEN or recovery success is claimed from their preparation.
Logs: `docs/evidence/ticket13/recovery-source/test-assembly.txt`.

A bounded support agent identified the current-quote receiver target-discovery
gap and reviewed recovery/conflict fixture semantics. The separate
`quote-author-receiver-diagnostic.cjs` executes unchanged, hash-checked A/B
receiver methods with synthetic transaction objects and a stream-aware in-memory
Archive adapter. Under isolated network-disabled/read-only Node24.20.0, both
receivers apply/remove an ordinary RedSquare target but leave a current retweet
target under RedSquareRetweet unchanged while archiving its delete command.
Result: `recovery-source/quote-receiver-diagnostic.json`.
Public action object signer/target fields are adapted for this lookup experiment;
they are not re-signed. This is receiver control-flow reproduction, not signature,
native-frame, deployed-server, DOM or live-write evidence.

The smallest source correction would resolve author targets across both streams,
validate exact target/message/author before mutation, and reuse Archive's
signature-based update/delete while retaining original index fields. Current
quote counters use data.signature rather than older data.embedded.signature;
repeat retweet archival must also be checked for overwritten edit metadata and
deleted-row resurrection. getTweet/profile caches accepting only create tweet
do not normally rescue current retweet targets. A separate server change needs
an isolated controlled implementation, scoped ticket and its own deployment
authorization; none was deployed here. Details in the author-actions contract.

Latest checked hosted run38039739303 was likewise refused before startup for
failed payments/spending limit. No new device dispatch was attempted during
this checkpoint. Billing remains an external prerequisite, not a test failure.

The owner resumed sequential work on 2026-10-10. This is an implementation
checkpoint, not ticket completion. Local core35 (zero failures/errors/skips),
debug APK, test APK, release APK and lint pass in3m55s after the final prefill fix. Logs and counts are
retained in `docs/evidence/ticket13/source-checkpoint/`. The full source review at0129dac and the final prefill fix have zero outstanding
source findings on both axes. Default editor text comes from the reviewed baseline;
explicit drafts remain intact. The chronology below describes earlier checks; their limited Android results do not cover this source.

Verified public per-target/backend author proofs now propagate newer edits and
confirmed deletions across feed, Following, thread, profile, liked-post and quote
cache readers. Original signatures and identity remain stable. Tombstones retain
the independently verified original and authorized delete command separately
from bounded view caches, preventing stale responses from resurrecting a known
deleted post. Unreadable proof records hide the affected content with a cache
warning and are preserved for recovery. Records are not automatically pruned
with individual view caches. They contain public signed envelopes, never wallet
or private Chat data.

The core restore-deletion tracer first failed compilation22s because its API did
not exist, then passed39s. An added restore-edit regression checks newer text,
no injection of unseen history and rejection of unrelated delete proofs. The
rebased-conflict tracer likewise first failed compilation22s for a missing API,
then core35/debug/test assembly passed2m8s. These are API-shape RED stages, not
executed application failures.

Own edit/delete actions share encrypted candidate recovery and explicit retry.
Deletion has an explicit historical-copy confirmation and acknowledges removal
only after observing the exact authorized command plus successful empty target
queries in both `RedSquare` and `RedSquareRetweet`. Target lookups do not filter
moderated content and reject unreadable, unrelated, duplicate or command rows as
absence. Archive command presence alone, disappearance alone and request-handler
acknowledgements cannot confirm deletion. New actions never enter the automatic
post queue. A Saved post changes screen opens retained work without transmitting;
identity/backend changes keep it scoped to its original owner and server.

Conflict choices retain the user's text or explicitly select the latest text for
a new attempt. The original reviewed baseline stays in the encrypted record,
independently bound to its author/target, including across recovery. Public cache
updates cannot silently rebase an existing signed attempt. Legacy candidates
without a baseline conservatively require resolution when another edit is served.
Wallet import now includes pending/unknown native author work in its risk preview.

Mounted quote previews reload when proofs change, retain commentary when the
original becomes unavailable, and use cached proofs even without an active
wallet. An unreadable preview cache does not prevent a bounded healthy-server
lookup. Profile updates atomically merge prior likes as well as prior posts.

Five Android cases are prepared: extended author edit/pending-delete/recovery/
same-candidate retry flow, stale-cache cold launches, rejected encrypted records
and baseline preservation, cross-cache deletion with stale resaves/backend
isolation, and mounted quote edit/deletion/commentary plus keyless proof reading.
They compile but have NOT executed at this checkpoint. The extended deletion
fixture keeps the original in the second archive stream after recording a command;
this is synthetic branch coverage, not evidence that a server migrates posts.
Actual process-kill author recovery and further conflict/failure runtime checks
still need verification. No fresh phone review APK was published.

Native public-frame verification/receiver scripts now support create/edit/delete
frames; syntax checks pass. No public Android frames from the new capture have
been produced, so native-frame interoperability remains pending. Re-executed
reference-only A/B receivers pass under isolated Node24.20.0 with unchanged source
hashes, public previously verified vectors and an in-memory Archive boundary.
Result retained in source-checkpoint/reference-receiver.json. This is not a live
write, DOM or actual Android-frame result.

Hosted execution remains blocked: push38036925480 at92a4a7e was also refused
before starting, with the same failed-payment/spending-limit annotation as
38035438325. The owner was already asked about billing. No spending, billing,
visibility or account settings were changed and no unsupported local-emulator
retry was attempted. Resume focused Android10/17 and final full acceptance after
runner access is restored. Do not close #14 on local assembly alone.

## Known pinned receiver limitation

A/B archive current commentary retweets under `RedSquareRetweet`, while edit
fallbacks and deletion lookups use only `RedSquare`. They can archive a delete
command without removing the target in that second stream. Dual-stream native
lookups prevent false success, but do not repair that receiver behavior.
Archive-only current-format quote actions may remain pending. Native quote
composition emits create tweet with both original references and uses the
working RedSquare archive path; author edits preserve the original quote fields.
Final source specification review corrected an earlier overbroad prerequisite:
this known upstream limitation does not require a node patch to close #14 or
justify excluding native quotes. It does not establish deployed behavior or
authorize production mutation. No successful web-retweet repair is claimed.

## Earlier verification chronology

Independent public vectors use existing pinned WASM0.3.2/Elliptic6.6.1 reference
crypto, unsafe scalar1/2, original signed feed fixture. Named read-only runtime,
dependency, generator and public fixture mounts; network-none/read-only/cap-drop/
no-new-privileges/env-i/512MiB container, no Saito app initialization. Original
identity remains intact in edited metadata. Actor, target and corrupted-signature
negative samples included. Never fund these identities.

First read-edit tracer actually red25s, then green43s after independently
verifying optional update_tx as an authorized edit. Unsigned edit_ts supplies
neither authority nor ordering. Additional actor/target/signature and untrusted
ordering regressions pass in core28/debug/test assembly1m48s. Observable native
own-post edit tracer prepared. Run38033270587 failed at a fixture navigation
race: it scrolled before the saved composer closed. This does not establish the
missing edit control. A bounded New post readiness wait was added in7ce6f6b;
run38033592630 also failed before reaching Edit because New post labels both
composer and feed. Retained failure screenshot confirms composer still visible.
527184d waited for list semantics; run38033860604 then waited for a new post
above the retained list position.1f54664 uses the previously verified quote
fixture pattern: wait for Refresh enabled, then scroll to the post. Run38034150449
actually reaches the own conversation and fails at missing Edit (7.402s).
Retained log and inspected screenshot: ticket13/edit-control-red. This establishes
the intended UI red. Three preceding navigation failures establish no product red.

a2f50e6 adds author-only editing in the conversation, encrypted candidate storage,
active identity/backend checks before transmission, exact nested-edit observation
and original-candidate retries. Newer conflicting edits retain authored text and
show explicit conflict status; resolution controls remain planned. Local core28,
debug/test assembly pass1m48s; focused first-green38034593176 passes6.178s on
Android10. The thread/feed/profile text and original identity checks pass;
inspected profile screenshot shows edited text. Retained ticket13/edit-first-green.
Navigation-save reviewfinding fixed in8a680d9/0650743: saving gates input and
actions, callback itself rejects queued input, immutable snapshot precedes IO.
Both source axes confirm resolved. This firstgreen predates those fixes.
Deletion,
global cache ordering, recovery and final acceptance remain incomplete.

Stale-cache tracer38034937825 actually fails after an older response replaces
newer verified text, while the own-edit tracer still passes (suite8.439s).
322177c applies newest signed-edit merging in feed/thread/profile/cache; local
core29/debug/test assembly pass2m6s. Native edit/delete signatures match the
independent vectors; focused core regression26s. Invalid encrypted author records
and queue/backend isolation regression added, Android execution pending.

First cache-green38035438325 and source run38035437877 were refused before any
runner step: GitHub reports recent payments failed or spending limit needs
increasing. This establishes neither a test failure nor a green. Owner notified
and billing question pending. No billing/spending/repository-visibility change.

Bounded Android10 software-emulator attempt started locally at about07:47UTC:
network-none,1.5CPU/3GiB limits, isolated AVD/dev packages, no concurrent build.
Current /dev/kvm absent, host had4.7GiB available and low load before start.
Unlike the earlier unmeasured speed proposal, this is a temporary verification
fallback after hosted execution became unavailable. Stop at the existing bounded
boot/test limits; no persistent emulator or speed/performance claim. Result
ended with exit1 at instrumentation timeout, about13 minutes after start. Both
APKs installed, but instrumentation produced0 bytes and no test result. Container
removed; no persistent emulator. Logs retained ticket13/local-software-attempt.
This provides no cache/flow green. Do not retry unsupported software emulation
as the ordinary development loop. Hosted Android verification remains blocked.

Standards found one cache read/merge/write race in322177c. Working-tree fix adds
PublicStringStore.update under the shared lock, decoding and merging the latest
committed values before save. Sourcefixreview confirms0 outstandingfindings;
build passed after local emulator ended: core30/debug/test1m52s. Dedicated core
history regression preserves the independently signed newer edit against stale
and damaged/unrelated nested edits. This is core ordering proof, not Android
cache recovery acceptance.
Independent actual-native-frame verifier and pinned-receiver native mode prepared,
unexecuted until public controlled frames are retained; no new frame proof claim.

Deletion reader tracer actually RED27s: valid archived delete commands counted
as unreadable posts (expected0,actual2). Minimal reader separates signed deletion
commands without deleting target posts; GREEN41s. A follow-on original-author/
target pairing API tracer initially fails compilation22s because deletion()
does not exist, not an executed authorization failure. New deletion verifier
independently verifies original and command, requires matching author/target
and delete kind; unrelated/wrong-target/edit-command negatives rejected. Full
core32/debug/test assembly GREEN1m58s. No Android deletion screen, removal,
tombstone or server-reconciliation acceptance yet.

Pinned A/B unmodified edit/delete receiver methods executed offline using the
independently verified public reference vectors and an in-memory Archive boundary
adapter. Both preserve original identity, apply only strictly newer edits, reject
unrelated edits/wrong targets, retain the original on unrelated deletion while
archiving that command, and remove the original on authorized deletion. Source
hashes and results: docs/evidence/ticket13/web-reference/receiver.json. This is
receiver-method compatibility evidence, not deployed-node, DOM or Android-write
proof. Named read-only runtime/script/public fixture/cached source mounts,
empty environment, network-none/read-only/cap-drop/no-new-privileges/512MiB.

Standards preliminary review found one author-store issue: signed binding was
checked on reopen after committing. It is now checked before any write, including
ordinary composer records. This preserves prior work on candidate validation
failure. Final review and Android recovery regression remain pending.

## Own-post conversation layout correction, 2026-10-10

Focused quotes38055428901 at8b3b898 passes its first five cases, including both
original Retry stages. Publication hangs after opening Conversation, at the first
commentary scroll. Inspected host PNG/XML show Back/Reply/Edit/Delete/Refresh,
but no visible title/history/post list. The single header row now contains too
many fixed-width actions alongside a weighted title. The production correction
separates Back/title/Refresh from Reply/Edit/Delete; the test adds displayed
title/list assertions before scrolling. This is an app layout correction, not
another fixture-only change. Prior focused author source remains historical;
new source needs focused and full Android10/17 acceptance. Captures and static
progress retained in ticket13/own-conversation-layout-failure/. No ticket closure.

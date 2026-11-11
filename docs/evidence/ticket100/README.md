# Robust directory display names, issue100

Review base: `1f12812635690547656e6373f0a45a5c85542780`, accepted beta108.
Signed [beta 0.1.109](https://davidengelen.nl/redsquare/) is delivered.
Both complete SDK runs, signed updates, independent reviews and exact public
delivery verification pass; the qualifications below remain explicit.

The owner supplied the same signed conversation on Android and Saitoverse.
`oT3f7KeqeeG6VRBVvWKJpDNtigRUMYXUgtu3ZjNrNUrZ` is reported as
`digirok@saito`; `i6LwT9rFjrsNgExzLeSghGqjHhaNwwjeDrsLr2LkTUk8` as
`CryptoKen@saito`. Fresh public read-only native-protocol probes found empty
reverse maps and unanswered namechecks on the owner node, but both mappings
on saito.io. CryptoKen's current registrar proof verifies; digirok's historical
cached record does not verify, even against its historical signer. No alternate
trust key or weakened ownership verifier is used.

The [pinned official Registry implementation](https://github.com/SaitoTech/saito/blob/e6c937c686495a37d8fc94ecc894189e23a453c3/node/mods/registry/registry.js)
uses reverse maps for browser labels separately from registration proofs.
The app follows that display distinction while preserving original signing keys,
strict registration/availability checks and the verified-name cache. Post text
never supplies an authenticated author name.

The actual Android10 [RED run42987309843](https://github.com/robelene112/redsquare-android/actions/runs/42987309843)
on `0db5ee942ab7d9d1c6dd720337c93c97a8a2af47` shows a genuine signed post but
cannot display its directory name within the original20-second goal. The first
implementation [run42990063478](https://github.com/robelene112/redsquare-android/actions/runs/42990063478)
on `2c87f2ce9` reaches the named profile and full-key identity dialog, then fails
an ambiguous test selector that also matched the background profile. It remains
failed, not accepted. The selector now requires the name beside the exact key.

The actual [recovery RED run42990483914](https://github.com/robelene112/redsquare-android/actions/runs/42990483914)
on `9c8341f81` cannot recover after two directory errors without navigation,
failing the unchanged20-second goal. A core tracer separately proves that an
error object was incorrectly treated as a successful absence. The correction
classifies the read as failed and retries only still-visible identities with
bounded backoff, independently of the selected node's ownership checks.

Large original native artifacts, probes and capture hashes remain outside Git
at `/mnt/storage/redsquare-android-artifacts/identity100`. Final source, complete
Android10/17, signed-update and delivery evidence are recorded here. Parent1/36/55/84/86 remain open; no phone QA or node changes are used.

The [offline RED run42991234195](https://github.com/robelene112/redsquare-android/actions/runs/42991234195)
on `ff45860be` loses the name after recreation and fails its original15-second
retention goal. The [selected-node RED run42992451215](https://github.com/robelene112/redsquare-android/actions/runs/42992451215)
on `543f69f55` fails the original20-second no-navigation recovery goal.
Original instrumentation logs remain in this packet, and complete native artifacts
remain external. Neither failed execution is accepted by a later pass.

On `e7969c227`, the focused [Android17 name run42991535779](https://github.com/robelene112/redsquare-android/actions/runs/42991535779)
and [Android10 recovery run42991531381](https://github.com/robelene112/redsquare-android/actions/runs/42991531381)
pass. They establish their focused goals, not complete final-source acceptance.

The pre-run100 contract retains the actual accepted99 identity lists and archive
paths. In particular, StackPublicReadTest stays under Chat, matching the accepted
raw99 executions and reviewed99 catalog erratum. Seven additive native classes bring
the frozen complete goals to650/651 identities,227 classes,193 archives and246/247
normal completions. All37 deliberate interruptions,36 continuity records, original
deadlines, the inherited Android10 Profile assumption and unavailable Chat archive
remain. The unchanged94 visual/motion/speech validators are reused. Both final100 whole-SDK executions pass
this unchanged complete contract, as recorded below.

Both focused SDK runs on `77adbfb3b` pass all six initial classes, including the
actual public directory comparison and six compact320dp light/dark text-size
cells. The actual official map contains both reported names; the owner map is
empty. These focused passes precede the independent review corrections.

[Initial independent source reviews](source-reviews-first.json) found two Spec
issues and one Standards issue. Selected-node namechecks blocked display maps,
the shared cache namespace coupled selected labels to the optional provider, and
other fixtures did not preserve the new shared cache family. All were accepted.
Two additive native isolation regressions reproduce the first two defects on
`5b9948633` in run42995354366, each failing its original5-second goal. The correction
separates provider stores, multiplexes map reads independently of four bounded
strict proof requests, and restores the shared cache family centrally. Exact
registration verification is unchanged. The subsequent review and final-source
verification are recorded below.

[Independent follow-up source reviews](source-reviews-final.json) on `6acabb4b3`
pass both axes with zero actionable findings. They confirm all three original
findings are resolved; this is source acceptance rather than final execution or
release acceptance. Strict proof requests remain bounded to four, without blocking
map display or the next batch. Provider caches are independent, idle/lifecycle
peers close, and cancellation is checked under the storage lock before commits.

## Actual signed candidate and update gates

Candidate109 is compiled from `de0ae9b60`,42,650,666bytes, SHA256
`6d3a7cb431815c6338f5bd84fca633ccd10f258bcf3653241e74ee0910a5fdb3`.
The immutable downloaded candidate matches. The established certificate, ordinary
package, min29/target36, nondebuggable/nonprofileable manifest and byte-identical
bundled editor license are verified. [Both independent artifact/update reviews](update109-reviews.json)
pass with no findings. Actual signed83-to109 runs42999467936 and42999472049
preserve wallet identity, separate debug identity, exact unsent draft, cached post
and Light preference through update/cold launch, with clean restoration.
At that staged checkpoint ordinary latest108 remained unchanged. Final public
promotion and delivery verification are recorded below.

## Full-run fixture corrections

Initial complete runs42998622485 and42998903591 are **not accepted**. Both were
cancelled after Wallet failures; their failed jobs, originals and distinct reasons
remain under [initial failures](initial-complete-failures). No other partitions
from those executions contribute to acceptance.

Android17's policy observer incorrectly counted the new switch as a third radio.
The correction requires an actual RadioButton indicator while retaining the exact
two-radio assertion, selected states, domain containment, native TalkBack focus/
speech and original5/10-second bounds. Focused runs43003184581 and43003188572 pass.
Android10's process-restoration allowlist omitted a prior snapshotted submission
receipt after preparation removed it from the active directory. The correction
admits only saved names matching the existing exact owner/backend/128hex receipt
pattern. Other allowlists, saved-file/distinct guards, worker cancellation,
identity/backend/digest checks and deadlines remain. Focused agreement runs
43004209675 and43004214747 pass27 identities each. Their fresh focused installations
have no prior submitted receipt, a limitation retained in their summaries; the
initial failing checkpoint and narrow namespace review establish the correction.

[Both independent fixture reviews](fixture-correction-reviews.json) pass, and the
[exact source bridge](fixture-source-equivalence.json) proves that only these two
instrumentation files differ between APK/update source `de0ae9b60` and corrected
full source `759f46f7e`. Product/core/build/workflow/host scripts and the pre-run
contract are unchanged, so the same APK109 and signed-update gates remain valid.
Fresh whole [Android 10 run43005859517](full-final-android10/summary.json) and
[Android 17 run43006404622](full-final-android17/summary.json) both pass all three
jobs at `759f46f7e`. The unchanged frozen verifier accepts 650/651 identities,
227 classes and 193 archives per SDK, 246/247 normal completions, 37 deliberate
interruptions and 36 continuity records. Original inventories retain 931/941 file
hashes. No original goal is removed and no partial SDK assembly is used.

[Both final independent reviews](final-reviews.json) pass with zero actionable
findings. The original Android 10 Profile assumption, unavailable
`chat/chat-image-history-evidence.tar` and four inherited failure-capture
placeholders remain qualified. Fresh focused and complete agreement checkpoints
contain no prior submitted receipt; restoration passes, while the retained-receipt
correction relies on the original failure and reviewed exact-owner namespace.
The original FastNext five-second assertion passes. Later clipped-row/reading-anchor
captures are frame-position evidence, without independent second-label native
ink/timing proof.

## Verified public delivery

The existing immutable APK109 was promoted after both complete runs and reviews
passed. [Public delivery verification](public-delivery109.json) confirms HTTP200
for the page, latest manifest, immutable manifest and actual downloaded APK.
[Latest](public-latest109.json) and [immutable](public-immutable109.json) metadata
match the exact staged notes, established package/certificate and reviewed build.
Downloaded bytes are identical to the original 42,650,666-byte APK, SHA256
`6d3a7cb431815c6338f5bd84fca633ccd10f258bcf3653241e74ee0910a5fdb3`.
No rebuild or additional version reservation was needed for the two test fixes.
[Both independent delivery/documentation reviews](delivery-reviews.json) pass
with zero actionable findings and independently confirm the public bytes.

Issue #100 is complete. Parent #1/#36/#55/#84/#86 remain open. Physical/OEM,
actual provider delivery and Play acceptance remain outside this development
checkpoint. No owner-phone QA, production node changes or live writes occurred.

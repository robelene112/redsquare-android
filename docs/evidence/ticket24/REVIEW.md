# Private Chat review

## First protocol increment c102f89...7dbaba3

### Standards

Confirmed case-sensitive imported passphrase compatibility defect: converting
accepted uppercase saved aes_secret to bytes and back to lowercase changes
CryptoJS KDF input. Preserve the original validated string and add an
independently signed uppercase fixture. A second documented compatibility finding: formatter fields are coerced via
asString, allowing single-element arrays. Require actual string primitives
and verify correctly signed malformed formatter rejection. Zero actionable
Fowler smells; both findings cite CODING_STANDARDS compatibility rule.

### Spec

Two findings against imported history recovery/string passphrase compatibility:
legacy publickey field is ignored despite pinned keychain migration; accepted
uppercase/mixed-case saved aes_secret is normalized. Preserve backup contents
while selecting the compatible field/passphrase and verify independent vectors.
All26 evidence hashes, three reference copies and core77 counts verified.
UI/transport/final recovery acceptance remains pending, not claimed.

## Protocol fixes 7dbaba3...42dfd6f

### Standards

Prior compatibility findings resolved; zero new hard violations. One actionable
Duplicated Code heuristic: identical string-type validation for formatter/body.
Shared private validator now applied, core81/debug/testAPK3m24s passing; review
of that cleanup and new peer discovery still pending. All55 hashes/core81 and
qualified actual Crypto failures verified.

### Spec

Zero findings. Legacy field and exact-case passphrase fixes preserve imported
backup data; Relay original signed bytes authenticate before decrypt. All55
hashes/core81/referencecopies/fourvalid+threeinvalid vectors verified. Native
transport/UI/final recovery remain pending.

## Shared validator and authenticated discovery42dfd6f...0997419

### Standards

Prior duplication resolved; zero hard/actionable heuristics. Bounded strictUTF8
metadata matches pinned service|domain|name ordering, authenticated sameconnection,
no endpoint use. Fixture callback placement/scope mappings and allfive hashes
verify; source evidence correctly distinguishes pending actual runtime.

### Spec

Zero findings. Authentication/disconnect discovery gates and original string
validation preserved; no custom backend/domain following. Existing trailing
Archive callback bindings restored. Allfive hashes/core81/source GREEN and
qualified mixed/cleanRED evidence verified; no UI/full acceptance claim.

## Original Relay receive and socket callbacks0997419...3c35a77

### Standards

Zero hard code violations/actionable heuristics. Bounded explicitly untrusted
framing, original signature before decrypt, authenticated incoming callbacks,
13accept/14reject and existing request behavior verified. All73 hashes and
qualified one-case original evidence agree. One handoff correction: private
contract/reference/generator already committed; fixed all inherited future notes
while preserving group contract protection.

### Spec

Zero findings. Outer Relay provides no identity authority, original inner and
participants authenticate independently, altered inner rejected. Callbacks
preserve request IDs without promising durable/read receipt. Core82/manifests/
referencecopies and qualified original one-case evidence verified. Newtwo-case
result belongs to3c35, retained separately; UI/cache/backend/recovery unfinished.

## First native history UI6df3ef0...7e5811e

### Standards

Zero hard violations or actionable heuristics. Contacts expose only public
metadata, original authentication/dedup and Block semantics preserved. All16
evidence hashes verify; initial UI RED is qualified rather than treated as
passed. Cache, sending and recovery remain unfinished.

### Spec

Zero findings in this bounded increment. Boolean added matches pinned
mods/qrscanner/lib/add-contact.js:15; group entries excluded. Available rows
authenticate/decrypt before display; no cache/send/recovery claim.

## Private cache6f02f73...0620289

### Standards

One hard continuity finding, zero actionable Fowler heuristics. Refresh assigns
cache.load and later cache.save results over current verified memory. After a
failed local save, an unavailable or pruned Archive read can discard a message
while Chat remains open, contradicting CODING_STANDARDS:16–17 and the warning
limited to closing Chat. Merge retained memory, including original ciphertext
envelopes for successful persistence retry. All31 manifest entries verify;
encryption/scoping/refusal to overwrite unreadable history otherwise hold.

### Spec

One same continuity finding against parent#1 keep-reading-through-failures and
#25 available-plus-local history. Add failed-save, offline/empty Refresh and
repair verification. Both RED XMLs/failure original, seven hashes and core82
source GREEN verified. No actual process-death or completed-ticket claim.

## Cache retention fixes e8f6808...43399a7

### Standards

Prior hard finding resolved; zero new hard code violations or actionable
heuristics. Scoped authenticated entries/envelopes merge by original signed ID;
persistence retries retain unsaved entries and warning remains while IDs are
absent from disk. All59 manifest records including parent repetitions verify.
One current UI README drift (recorded RED still pending/open) corrected next.

### Spec

Zero findings, prior loss-on-Refresh finding resolved in source. All43 unique
UI evidence hashes, actual two-case RED and earlier recreation GREEN verify.
Unexecuted repair gates and shared capture filenames correctly qualified.
Unchanged corrective runtime, actual death, send and full acceptance pending.

## Live selected-conversation reception db62258...fed20de

### Standards

One correctness finding, zero actionable Fowler heuristics: an incoming saved
history snapshot can be published after a newer history save and incorrectly
mark already durable entries missing. Keep the warning tied to history load/save
attempts; incoming updates only merge verified entries. All84 manifest records
verify; original failure image and three-case RED match their qualified claims.
Authentication, contact isolation, persistence before ACK and cleanup hold.

### Spec

Zero findings in the bounded reception increment. All71 unique UI evidence
hashes, original failure image/five XMLs, three-case RED and core82/source GREEN
verify. Native corrective result pending; list listener, send, actual death and
whole-ticket acceptance not inferred.

## Incoming warning ordering fed20de...d62a964

### Standards

Prior warning-order finding resolved; zero new hard findings or actionable
heuristics. Incoming updates only merge authenticated entries and cannot change
the history load/save warning. Compilation hash verifies1m25s; runtime source
association remains explicit.

### Spec

Zero findings. Retained envelopes, contact isolation and persistence before ACK
remain intact; no new core/APK/runtime claim from compilation alone.

## First Chat permission integration a3b148a...f2b33cf

### Standards

Zero hard findings or actionable heuristics. One call reuses the optional
permission component without changing shared behavior or requiring alerts for
reading. Fifteen RED hashes and new compilation hash verify; original failure
PNG/six XMLs agree with four-case RED28.341s and unreached Skip/recreation.

### Spec

Zero findings. Contextual first-Chat offer, optional dismissal and unchanged
initial feed match parent scope. All117 UI manifest records verify. Compilation
only, exit143 despite Gradle SUCCESS and unchanged exit0 recheck qualified.
Corrective runtime and remaining DM acceptance pending.

## First private send f072178...e06a22c

### Standards

One recovery-guidance finding, zero actionable heuristics. Initial work.load
failure leaves composer disabled; history Refresh cannot reload unchanged work.
Use an actual reopen action or safe reload trigger. Candidate persistence,
identity/backend guards, cancellation checks and qualified forwarding otherwise
hold. All21 RED hashes/four build records, nine XMLs/failure original verify.
Attached command exit143 distinguished from original container exit0/SUCCESS.

### Spec

Zero bounded findings. Original encrypted candidate saved/validated before any
attempt; shared lock checks wallet/backend/candidate before submission. Original
signed inner relayed; Submitted/Forwarded avoid delivery/read confirmation. All182
UI manifest records, actual missing-composer RED and container success verify.
Actual outgoing recovery/independent client and whole-ticket acceptance were
pending at review time; subsequent native GREEN retained separately.

## Private send guidance/layout and native client check e06a22c...94326b2

### Standards

Prior guidance finding resolved; zero new hard findings or actionable heuristics.
Read errors name actual reopen recovery; later send/save errors do not promise
Refresh retry. Compact layout preserves state logic. All24 GREEN records and
three added build/research records verify. Native envelope matches independent
client check; default reference copies unchanged. Earlier clipped recreated text
and pending corrected runtime are accurately qualified.

### Spec

Zero findings. Actual unsafe2->1 native envelope decrypts through pinned Crypto
wrapper and matches independently reproduced signature. All209 UI evidence
hashes/tenXMLs/three qualified originals verify; default reference copies remain
unchanged. Corrected layout/runtime and actual process death remain pending.

## New-contact metadata review8615857...a26945b

### Standards

Zero hard findings, one Duplicated Code heuristic: contact discovery and contact
addition repeat the group predicate. Extract the same private predicate.

### Spec

One compatibility finding: imported keys:null should initialize an empty keychain
when adding a first contact, matching pinned keychain initialization. Preserve
original options and identity. Prior evidence claims remain qualified.

## Contact-entry correction reviewa26945b...33b755e

### Standards

Zero new hard findings or actionable heuristics; duplicate predicate resolved.
Verified51 new evidence records and core84. Fresh-contact runtime pending.

### Spec

Zero findings. Null imports, original options, same-identity active metadata,
verified persistence and cancellation recovery are consistent. Verified21 protocol
and30 native RED records. No full-ticket completion inferred.

## Public-reader review33b755e...e2eb51d

### Standards

Zero hard findings or actionable heuristics. Explicit scopes, fixed endpoints,
bounded strict replies, ephemeral probes and qualified compilation are consistent.

### Spec

Zero findings. Reads only, no submit/relay/identity mutation. Empty response limited
to unused random pair; actual runtime and nonempty interoperability stillpending.

## Imported-ID core reviewd4d5f01...f2cd911

### Standards

Zero hard findings or actionable heuristics. Original authenticated counterpart
discovery, preserved options and qualified core/public read evidence are consistent.

### Spec

One compatibility finding: valid backups mix DM64hex hashes and generated public-key
group IDs in the same chat.groups list. Throwing on recognized group keys blocks
unrelated private recovery. Skip those group entries and cover modern/legacy lists.

## Imported-ID native reviewf2cd911...655c1e5

### Standards

Zero hard findings or actionable heuristics. Shared original/privategroup verified
loader and protected callback consistent; evidence records53verify, core85.

### Spec

One partial-recovery finding: groupA successful/cache saved, latergroupB failure
prevents publication of A's recovered contact. Isolate failures pergroup and publish
successes with incomplete discovery status. MixedID compatibility finding resolved.

## Partial-recovery fix review37532b6...a6c525c

### Standards

Zero source hard findings or actionable heuristics.58 new evidence records/compile
verify. Stale latest-source and redo-minimum handoff wording corrected.

### Spec

Zero source findings; partial recovery resolved.412 UI hashes verify; chronology
accurate. Same handoff correction recorded separately from source acceptance.

## List-incoming tracer reviewa6c525c...6829cbf

### Standards

Zero source hard findings or actionable heuristics.56 new GREEN and two compile
records verify, boundedredelivery/exactcleanup/qualifiedpendingRED consistent.

### Spec

Zero findings.470 UI hashes/24 XMLs/originals verify; no listener implementation
inferred from tracer, both prior nine-caseGREEN checkpoints correctly qualified.

## List/cache discovery review6829cbf...4734f93

### Standards

One correctness finding: initial retained/portable IO snapshots may replace a
newer live-published contact. Merge into current wallet/backend-scoped contact state.
Zero actionable heuristics.30 RED records/build verify; original failure confirmed.

### Spec

One partial-key recovery finding: one old exchanged-key ciphertext whose secret
is missing from an imported wallet hides the entire cache/contact, including a
readable implicit-key message. Preserve unreadable ciphertext and expose verified
readable history/contact plus partial warning.501 UI hashes verify.

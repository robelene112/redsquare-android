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

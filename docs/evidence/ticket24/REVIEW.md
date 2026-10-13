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

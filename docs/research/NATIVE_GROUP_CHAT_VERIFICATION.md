# Native named-group Chat verification

Active ticket: #26. Review base: bbdd121c0c707ad28ae22a997dd95707dc860f7c.
The visual beta #37 is closed and signed preview 14 is published. Twenty-one of
the original 35 tickets are complete. Group UI, membership recovery and live
write acceptance are not yet proved.

## Independent creation reference

Fetched public SaitoTech/saito `node/mods/chat/chat.js` at immutable revisions:

- A: `9a4ca7e9442eb5e872168ced361ce741d7d139d7`, source SHA256
  `35e372b7d9da0ddfd6286a93059c00a3e34eb494b62350db88a9380c4814b1af`.
- B: `214504ecece76d1cd20a245fe4468eccbfca2951`, source SHA256
  `28a8609343e75dec7982498c0bafd7aa99be0a499156f43c5a543435426209eb`.

Extracted and executed the actual `sendCreateGroupTransaction` method from both
versions against deterministic wallet, network and keychain fixtures. The methods
and signed envelopes match. The observed behavior includes the Unicode/literal
markup name, creator as first recipient, duplicate-self skipping, generated group
key registration, and one broadcast plus one direct relay event.

Signing uses public npm `saito-wasm@0.3.2`, `elliptic@6.6.1` and `base-58@0.0.1`
with Node 24.13.0. The actual WASM hash matches
`f59be69dbb949368ce1fc7ef47d414cbe0281a86a206fe4bfb1128a4d087e01e`.
WASM serialization, preimage/hash, signature and deserialization checks pass.
Elliptic independently verifies signatures. Public unsafe scalar 1/2/3/4 test
identities are used; never fund them. Timestamp and group randomness are fixed
at the system boundary. No Saito app initialization, live wallet file or public
transaction is involved.

The vector is `core/src/test/resources/native-group-chat-creation-reference.json`.
Reproduction tools and dependency integrity are retained under
`docs/evidence/ticket26/reference-tools`. This proves constructor intent and wire
compatibility, not all receiver rules, persistence or live backend admission.

## Reference setup history

An initial attempt reused an old helper's production dependency path. Its named
WASM import failed with MODULE_NOT_FOUND before reference execution. Read-only
WorkingDirectory and named package-directory checks found no usable package at
that location; no node was changed. Switched to isolated public npm packages,
with installation scripts disabled and distinct empty npm configuration files.
Using /dev/null for both user/global config initially failed before installation.
Distinct empty files fixed setup. The generator then passed both pinned versions
in a read-only, network-disabled, non-root Node container.

## Native creation tracer

The pre-agreed protocol/wallet seam in #1 covers this reference test. It compares
the full signed envelope, signature, authenticated author and recipients.

RED: one test failed with UnsupportedOperationException in 0.466 seconds; build
finished in 1 minute 4 seconds. Implemented the creation factory. GREEN: one test
passed; build finished in 1 minute 3 seconds. Original assertions remain intact.
Logs/results are in `ticket26/creation-red` and `ticket26/creation-green`.

The next tracer verifies signed creation membership and rejects a mismatched
claimed admin. Invitation, join, member view, leave, history, persistence and
Android 10/17 acceptance still remain. The preexisting untracked
`NATIVE_GROUP_CHAT_CONTRACT.md` remains untouched.

Membership tracer: RED 2 cases with one stub failure; GREEN 2 PASS, build 1m4s.
Admin must equal the authenticated signer; group ID, first recipient, text and
role map are validated. The immutable role map comes from original recipients.

Invitation discovery tracer: RED 3 cases with one IllegalArgumentException,
build 33s. This exposed incorrectly requiring the reader in original recipients.
The pure creation reader now authenticates without assigning its reader a role;
receipt routing and deliberate joining belong to the later app path. Signer/admin
and all original membership assertions remain. GREEN 3 PASS, build 54s.
No network-wide unique genesis or current membership is inferred from an isolated
creation record. Replay/collision/authority reconciliation remain later work.

Source hashes are now enforced by the oracle before method execution. Repeated
oracle output is byte-identical to the committed vector. All tools/logs above are
public synthetic evidence. No group UI/recovery/final whole-ticket acceptance yet.

Both separate bounded review axes found missing recipient curve validation.
Added an independent WASM/Elliptic signed invalid output-point fixture. Native
signature authentication succeeds before the new rejection check. RED:4 cases,
3 PASS plus expected-rejection failure, build35s. Added validation of every
recipient; GREEN:4 PASS, build57s. The original three assertions remain intact.
Both separate correction reviews have zero remaining findings; no full ticket
acceptance claimed.

## Ordinary invitation reference

Extracted both actual generateChatGroupLink methods and the actual public Crypto
UTF8 Base64 method. Wrapper source SHA256 is
61416839726c1877875062273fd704b37002f8f3a5d299e6c0883f5d24685683; a fresh B fetch
matches. Eight actual creator/member links cover normal and alternate Unicode
name claims. The alternate metadata is unsigned and does not prove a rename.
Original creation and malformed-recipient vectors are byte-identical.

The initial extra '+' fixture assumption failed: the original name's UTF8 byte
alignment did not produce '+'. This was a reference-coverage assertion, not an
app failure. Added alternate Unicode name claims through the actual link method;
some resulting links include '+'. One native parser tracer covers all eight.
Initial stub RED1 UnsupportedOperationException, build54s; implementation GREEN
1 PASS, build56s. Original eight-link/claim assertions remain intact. Parsing returns only unsigned claims, with no wallet/membership or
backend mutation. Group UI and explicit joining remain pending.

Malformed invitation tracer: RED2 cases with one unexpected JsonSyntaxException,
build56s; eight-link positive case still passed. An initial verification-script
assumption expected NullPointerException, so the edit aborted. The shell then
repeated the unchanged RED build17s. Corrected the script after inspecting the
actual failure and used fail-fast shell sequencing. Both RED outputs are retained;
the failed verification attempt does not count as an implemented fix.

The parser now exposes a static Invalid error without retaining an internal
cause or pasted content. GREEN2PASS, build57s, with JSONnull, invalid URI encoding,
malformed UTF8 and duplicate chat_id checks, plus all eight positive links.
This is parser proof, not Android joining/recovery. No owned Gradle remains.

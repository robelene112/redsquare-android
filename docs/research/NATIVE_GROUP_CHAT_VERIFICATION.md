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

## Signed join reference

Executed both actual sendJoinGroupTransaction methods, which match. Their normal
creation stores admin roles, while the join constructor reads group.admin. The
fixture supplies the known derived admin explicitly. Its zero-payment wallet
outputs start with the group and sender, then actual method adds admin/inviter.
Actual WASM/Elliptic serialization and signatures match both source versions.
This proves fields and recipient intent under that documented wallet fixture;
funded wallet selection, live admission and a complete web join session are not
claimed. Native join signature/group-address tracer is running against this corpus.

Native join tracer RED1 UnsupportedOperationException, build57s; implemented
compatible group-first additional recipients through a defaulted createTo
extension. Existing callers use the unchanged empty default. GREEN1PASS, build1m.
Exact independent envelope/signature/author/group-first assertions remain.
A broader core regression is next because existing factories share createTo.
No app joining, authority transition or persistence acceptance yet.

After the defaulted createTo recipient extension, full core regression93PASS,
zero failures/errors/skips, build17s. Previous wallet/serialization/feature vectors
remain valid. Results are in ticket26/core-regression. This is core verification,
not Android group interaction or full feature acceptance.

## Self-leave and retained controls

The actual self-removal constructor matches both pinned source versions. Its
zero-payment group-first wallet adapter is documented as for join. Native tracer
RED1 UnsupportedOperationException/build53s, then GREEN1PASS/build54s. Factory
always removes the signing wallet, with no admin-removal UI introduced.

A control-reader tracer uses original independent creation/join/leave vectors.
RED1 stub/build54s, then GREEN1PASS/build1m: join adds membership, self-leave marks
LEFT, replaying the old join remains LEFT, and original controls/creation are
retained. This proves retained observed sequence and deduplication, not global
chronology, current network membership or a complete authority history.

Next ban tracer is running: an admin removal must remain REMOVED after a member
self-leave, and a new join must be rejected. The current draft receiver mirrors
web self-removal too literally and may allow removal to become LEFT. This is an
unverified concern until the actual negative test result is inspected.

Ban tracer confirmed the draft receiver changed REMOVED to LEFT after self-leave:
RED1, build33s. Preserve existing removal markers through self-leave, including
when delayed. GREEN1PASS, build57s; the subsequent fresh signed join is rejected.
This intentionally closes the inspected web receiver's self-removal loophole
without changing its wire schema. It is not proof of global control ordering.

Saved-original reconstruction tracer: RED1 stub/build54s, then GREEN1PASS/build54s.
Duplicate join after leave stays LEFT; recorded originals are retained once, and
an ordinary member's removal of the creator fails. Reconstruction validates every
original through the same public receiving rules. No stored role map is trusted.
The order is retained observation order, not authenticated global causal order.

The bounded control reviews found Spec0 and Standards0 hard findings, with one
possible duplicate-reference-setup heuristic. Shared setup extracted; independent
offline join and leave regeneration both compare byte-for-byte with retained
corpora. No native change or additional runtime acceptance is claimed.

## Confirmed unsigned invitation origin

First tracer attempt used the wrong corpus property (url instead of link), failing
in setup before the public interface. Retained separately as confirmation-fixture-
error; not a product RED. Corrected fixture then failed UnsupportedOperationException
at confirmInvitation. Minimum implementation GREEN1PASS. Confirmed-link provenance
stays distinct from signed creation; no unsigned sender is automatically a member.
Own signed join and claimed admin anchor the deliberate choice, not network admission.
Restoration retains leave and duplicate-join behavior, and mismatched inviter fails.
App controller must bind the join author to the selected wallet before accepting.

## Plaintext named-group messages

Both actual pinned createChatTransaction methods executed against a two-member
named group with the public zero-payment wallet fixture and fixed timestamp.
Initial whole-method equality failed: B adds private-contact registration only
inside the encrypted DM branch. Both original methods execute unchanged; only
comparison excludes exactly those lines. Named-group signed envelopes/signatures
match byte-for-byte. This proves wire construction, not admission or DM changes.
Native tracer RED1 stub then GREEN1PASS/build57s: exact independent envelopes,
signatures/plaintext Unicode, group/author/time and removed/wrong-group rejection.
Unknown ordinary authors follow existing web membership behavior without admin
grants. Historical display must validate retained messages against their observed
state, rather than erase an earlier accepted message after a later removal.
Native group UI/persistence and full-ticket gates remain incomplete.

Signed admin-snapshot reader tracer RED1 unsupported control/build34s then
GREEN1PASS. Known current admin may rename and update typed role fields. Targeted
non-admin role claims fail; observed departures/removals survive delayed active
snapshots, including reconstruction. No global ordering/current-authority claim.
No outgoing admin-management UI is introduced before #27.

Message timestamp correction: actual unchanged constructors now also produce
delayed body timestamps (1700000123459) with signed header1700000123456. New
reader tracer RED expected1700000123456 but returned1700000123459. Corrected
createdAt to authenticated header time. All13GroupChat tests GREEN/build41s.
No recreation of the web's local arrival-order adjustment is claimed.

Shared active-role predicate removes repeated domain checks after Standards
heuristic. Full core regression101PASS/no failures/errors/skips, build59s;
not Android runtime acceptance. Snapshot role-map size review found an incorrect
255-member cap; independent envelope byte limits suffice, correction pending.

Snapshot-size correction: public fixture with256 valid role entries fails the
incorrect255 cap, RED1/build35s. Removed slip-count borrowing; authenticated
envelope byte limit still bounds input. All14GroupChat cases GREEN. No Android
runtime or arbitrary history completeness claim.

## First native creation flow

Android17 run38513845554 at455d69ba: one test, one missing Groups failure24.909s.
Root inspected original private-Chat PNG/XML; later assertions did not execute.
Source gate38513831069 separately SUCCESS. First creation-only Android store/screen
now implemented, but compile/device GREEN is pending. Direct creation Relay follows
pinned Relay.sendRelayTransaction and Chat.handlePeerTransaction: a wrapped creation
is not handled by Chat's control relay branch. Controlled peer now authenticates
and accepts original creation envelopes as well as existing wrapped private Chat.

Review strengthened exact normalized broadcast/Relay equality and all invitation
claims. Recreate stays in Chat; corrected the previously unreachable fixture step.
New group state is device encrypted and scoped to wallet/backend. Opening does not
autosend. This slice does not yet implement join/leave/history/messages/retry UI or
prove interruption of metadata publication; the creation key must remain recoverable
through that later failure test. No group build published, no complete26 claim.

First native creation/member/invite/reopen GREEN17run38514935237/e30f7d53,
1PASS33.884s; root inspected both original PNG/XML pairs. Exact signed broadcast/
Relay original and four inviteclaims pass, with no reopen transmission. Separate
sourcegate38514890979 SUCCESS. Newer ordered publication fix reviewed0/0.
Pending group-work discovery tracer compiled3m5s; hasWork stub deliberately false
before actual RED. It is not wired into wallet import yet; disclosure finding open.

Work-discovery RED17run38515688441/ce808a84:2cases/30.676s, false-stub work
check fails at112 while creation/member/invite/reopen passes at ordered source.
This verifies the existing UI flow after source fix but not controlled race timing.
Minimum hasWork now includes UNKNOWN/SUBMITTED records, unfinished names and
unreadable matching state; wallet import checks both backend namespaces.
Spec review strengthened the tracer to actually select the other backend before
aggregation; forwarded/unfinished/corrupt negatives added before GREEN. Identity
remains unchanged; only the fixture's random group files and selection are restored.
Compile/native GREEN and actual import-warning UI integration remain pending.

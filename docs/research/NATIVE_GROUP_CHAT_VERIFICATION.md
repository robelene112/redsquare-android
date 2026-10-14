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

Corrected native work/create cases GREEN both at5409d7cd:10run38516631174
2PASS10.066s,17run38516628720 2PASS23.856s. Root original reopened captures/XMLs
inspected. Covers inactive backend warning detection and unfinished/corrupt work.
Actual wallet-import warning UI remains separate verification, not asserted here.
Creation-key pre-metadata recovery tracer now compiled2m55s; nullable key field
currently unpopulated before actual RED. This is public protected-store reopening
without wallet metadata publication, not an actual PID termination experiment.

Pre-metadata key RED17run38517327739/b54a7c87:3cases39.003s,2PASS and missing
key at153. Minimum writes the creation key inside the existing owner/backend
AES-GCM record before wallet metadata/network publication. Save/load revalidate
its public ID against signed group ID; comparisons have static diagnostics. UI
passes generated key during durable preparation. Legacy creation-only records
remain readable with absent optional key. Compile and native GREEN pending.
No actual PID-death/app retry or complete26 acceptance claimed.

Key checkpoint GREENboth ata6a9a772:10run38518366079 3PASS7.377s,
17run38518363805 3PASS37.851s. Covers exact private key/candidate preservation
before wallet metadata, not actualPIDdeath or app-level retry. Next native
invitation-confirmation tracer uses a real pinned member link with raw '+' and
Unicode; requires no transmissions before confirmation, retained selected backend,
compatible signed join, explicit invitation origin and reopening without extra send.
Shared fixture keeps original creation assertions intact. TestAPK compile1m41s;
Join UI is deliberately absent before actual RED.

Invitation UI RED17run38519187380/fbd353ea:4cases28.489s,3PASS plus missing
Join group. Root originalGroupsPNG/XML inspected; later claims/send/reopen unrun.
Minimum adds explicit preview with four claims/unsignednotice/selectedserver/privacy,
then deliberate signedjoin and owner/backend confirmed-invitation cache. No fake
creation, sender auto-membership, server selection from URL or pre-consent sends.
Store binds confirmation author to activewallet, retains own signed join once.
Group watch metadata contains no imported group private key. Compile/GREEN pending.
Existing-group rejoin, app interruption/retry/history/messages/leave remain later
slices within26; neither network admission nor complete26 is claimed.

Refined preview and shared submission sourceee17e184 reviewed Standards0/Spec0;
local debug/testAPK compile PASS3m43s. Claim preview scrolls within a bounded
dialog, and displayed admin/inviter/group-ID assertions scroll before checking.
Four-case native Android10/17 verification is next. Existing key/work/creation
GREEN sets precede this source and are not invitation-flow acceptance.

Invitation four-case native GREEN at44e931af32bf214bee1b66c9e62c4001cd154ed0:
Android10run38521385291 PASS16.510s; Android17run38521387910 PASS46.936s.
Original joined screenshots and creation/join/reopened XML inspected. Reopened
captures show the group list; they do not show selected-detail provenance.
Exact wire, consent, backend and protected key/work checks pass. Records live in
native-join-green-android10/17; this remains focused proof, not whole26 acceptance.

Leave tracer830ed6c2 requires explicit confirmation before a second transaction,
exact [groupID, leavingWallet] recipients, matching normalized broadcast/Relay,
LEFT state and exact protected departure original after reopening without resend.
Initial review found missing exact recipients and retained-original assertions;
both corrected and separately reviewed0/0. TestAPK compile PASS1m33s.
Actual nativeRED17run38538979079 has five cases/64.937s: four PASS plus missing
Leave group control. Root original failure PNG/XML inspected. Consent, departure
and later recovery assertions did not execute. The minimum leave implementation
is now compiling; corrected native GREEN and whole26 acceptance remain pending.

Leave native GREEN atcdbeef729e4d9e6694b713fc213cf38fb1f1fe85: Android10
38540341373 five PASS21.397s; Android17 38540343832 five PASS57.190s.
Root original LEFT/reopened screenshots and XML inspected; exact signed departure,
protected restoration and no resend pass. Retry/message/history/PID/full26 remain.

Retry tracer seeds a protected public-fixture creation checkpoint before metadata.
Independent saved-backup assertions check group key/name/watch flags/private scalar
and conversation ID, alongside whole-wallet equality and unchanged signed bytes.
Initial circular metadata expectation found by Spec review was strengthened;
correction reviews0/0. Refined testAPK compile PASS1m37s. ActualRED17
38541609952 at1d258b1c: six cases/65.228s, five PASS plus missing Retry control.
Root original failure PNG/XML inspected. No-autosend/checkpoint preconditions pass;
later metadata/disconnect/retry assertions did not execute. Minimum Retry source
now retains and sends the exact original with protected-key metadata recovery;
compile/corrected native GREEN pending. No actual PID-death or complete26 claim.

Retry six-case GREEN4743355cc057bb95951183807b71d962c7d749d6: Android10
38543182621 PASS26.252s; Android17 38543185574 PASS65.008s. Original forwarded
retry screenshots and raw summaries retained. Independent protected metadata,
controlled disconnect, exact retry and no-autosend through recreation pass.
This remains seeded preparation/recreation rather than actual process death.

Known-group rejoin tracer uses the protected module seam with seeded forwarded
creation/departure. A new unsigned invitation has conflicting name/admin claims;
the new own join must preserve origin/key/controls/name, restore MEMBER rather
than ADMIN and refuse to replace pending work. Source reviews0/0; testAPK compile
PASS1m40s. Native RED17run38544239476 at362f97c2 completed: seven cases/41.162s,
six PASS plus existing-group rejection at prepareInvitation62/fixture317. Later
retained-origin/key/role/pending assertions were unexecuted. Minimum now applies
the own join to the existing origin, retains key/controls/name and reconstructs
metadata from that retained state. Invite requires a known active admin so an
orphaned group cannot throw while composing its link. Corrected compilation and
native GREEN remain pending. Consent/network admission, actual process death and
complete26 are not claimed by this module tracer.

Review of the first known-rejoin minimum found routing still used the link's admin,
despite retaining local roles. Added core usingKnownGroup to derive retained admin/
name (or original routing anchor for an orphan without granting ADMIN). Genuine
creation/currentadmin1/returningmember2/conflicting4 tracer RED1 stub then GREEN3
invitation cases, builds1m3/1m4. New native eight-case tracer records both original
join outputs and outerRelay targets. CompilePASS3m2; Standards0/coreSpec0, native
Spec2: name could match background list and enabled Join needed a bounded wait.

Actual nativeRED17run38546506531 at95207cf0: eight cases/67.047s, seven PASS plus
missing retained admin in Preview (fixture90). Original PNG/XML show conflicting
name/admin. Unscoped name assertion is not valid preview-name proof. Later consent,
inner/outer routing and recovery assertions were unexecuted. Minimum8d8e9004
resolves known details off Main before confirmation, labels saved provenance and
requires prepared recipients to match retained authority. Fixture now scopes
name/admin/provenance to Dialog and waits for enabled Join before its strict click.
Corrected compile/native GREEN pending; no full26/network admission/PID claim.

Known-routing eight-case native GREEN at0841e381022b0ae2847b0a152b6e1a3c4b9ea674:
Android10run38548067341 eight PASS29.535s; Android17run38548070880 eight
PASS59.308s. Original known-rejoined PNG/XML retained and inspected. Scoped
preview, exact inner/outer routing, retained key/origin/roles and no automatic
resend pass. These remain focused checks, not complete26 acceptance.

Observed-history replay tracer60f5 was RED1 with an unsupported stub;6a493
made all15GroupChat cases GREEN. Earlier authenticated messages remain visible
after removal; later messages from removed authors are rejected. Ordinary
message authors become members, never admins. Originals retain observed order;
display timestamps do not invent a global authority order. Both review axes0.

Original-extraction tracereb27 was RED1 with an unsupported stub. Shared framing
b8cc accepts compatible direct/plain group Chat and extracts inner originals
from unsigned Relay wrappers. The group reader/replay authenticates originals;
wrapper identity is not authority. Full core105 PASS/0failure/error/skip,
build1m7s, including existing private Chat cases. Both review axes0.

Native nine-case message tracer829 adds available history, own send, live
incoming delivery, duplicate handling and cached reopening after the archive
stops serving messages. Reviewf11c strengthens the outgoing protocol reader and
requires an additional positive type13 acknowledgement for duplicate delivery.
Corrected tracer reviews Standards0/Spec0. First nativeREDrun38550903014 at829
is pending. No message UI acceptance, pagination, import discovery, actual
process death or complete26 is claimed by these core/tracer checks.

Actual native message RED17run38550903014 at829: nine cases, eight PASS plus
available-text wait206,91.358s. Original failure PNG/XML show joined group with
an empty conversation area. Sending/live/dedup/cache tail did not execute.
Retained native-message-red-android17 labels those limits explicitly.

Minimum51a7 introduces encrypted observed originals and authenticated available,
own and live group messages. Review corrected independent restore/history errors
and kept live reception/sending usable when Archive is unavailable or malformed.
Correction92b4086c reviewed Standards0/Spec0 for this minimum. Unsent group draft
retention remains an acknowledged review finding and the next slice within26.
Debug/testAPK compile PASS3m14s. Native9 at92b4086c pending:10run38552574915,
17run38552540747. Do not treat compilation or previous eight-case success as
message acceptance. No signed group beta or whole26 completion yet.

Native9 message GREEN: Android17run38552540747 at92b4086c nine PASS82.767s;
Android10run38553319218 at63526b897dd6859b626756ef6f5da2b7a46bcf0f nine
PASS37.241s. Product source unchanged between them. Both unique messages-sent
and messages-cached PNG/XML sets inspected: genuine available, own and live text
persist after Archive stops returning them. Exact outgoing protocol and additional
positive duplicate ACK pass. Retained native-message-green-android10/17.
Prior10run38552574915 at92 had8PASS/immediate live scroll failure230/28.582s;
its original captures show only available/own text. Bounded public scroll/display
wait635 fixed the tracer without weakening assertions. Timing was a hypothesis
in the failed capture; the later successful wait establishes eventual publication.

Draft tracerf767 was strengthened by00297dfc: after immediate Back, require a
positive group-list control before selecting the row; both reopening checkpoints
require exact EditableText in a visible enabled Message field. Corrected reviews
0/0 and testAPK compile PASS1m51s. Actual ten-case RED17run38553804247 at002
pending. Draft minimum is prepared but not applied. No actual PID recovery,
pagination/import discovery/archive-fault or complete26 acceptance claimed.

First native draft attempt38553804247 at002: ten cases/nine PASS72.248s,
sole failure242 Back not displayed after typing. Root unique draft-failure
PNG/XML show keyboard and panned composer, with Back outside the viewport.
This fails before recovery and is not a persistence RED. Retained draft-red
README explicitly qualifies the unexecuted reopening/recreation tail.
Correction6a51 closes the keyboard and positively waits for visible Back before
unchanged navigation/editable/no-send assertions. Both reviews0; testAPKcompile
PASS1m44s. Corrected actualRED17run38554810986 at6a51e6829bf8fb289345f470ee9dfbecc3ee522e
pending. Draft minimum remains unapplied; product source equals message GREEN.

Actual draft-persistence RED17run38554810986 at6a51: ten cases/nine PASS,
95.654s. Positive group-list observation followed by reopening reaches the
exact EditableText wait248/464, which times out. Root unique failure PNG/XML
show all three cached messages and an empty composer. Activity-recreation tail
unexecuted. Retained draft-persistence-red-android17 distinguishes this from
the earlier keyboard-only fixture failure.

Draft minimumc033 stores optional plaintext draft only inside the encrypted,
scoped group record; updates preserve history/key/candidate/status. Send clears
it in the same durable preparation write. Debounced saves remain editable;
explicit navigation freezes edits and awaits the shared save callback. Nonblank
drafts participate in outgoing-wallet work disclosure. Review corrected Refresh
restoration5aed9d08: retry only failed loads and preserve live edited text.
Correction reviews Standards0/Spec0. Local debug/testAPK PASS3m10s. Native10
at5aed pending: Android10run38556274168; Android17run38556277778. Storage-fault
repair, archive-fault regression and whole26 acceptance remain pending.

Draft ten-case native GREEN at5aed9d085ac2f0c35a1fc3b261a86bea7766d45d:
Android10run38556274168 PASS47.308s; Android17run38556277778 PASS83.477s.
Root unique draft-reopened PNG/XML inspected on both. Positive group-list
navigation, exact editable restoration and activity recreation retain unsent text
without extra outgoing transactions. This does not prove actual PID death,
storage-fault Refresh repair or complete26. Retained native-draft-green sets.

Archive-fault tracer ec57/673cf8ff adds fresh malformed group Archive response,
a distinct visible history warning, usable Send, positive live ACK and five cached
messages after reopening with Archive still unavailable. Exact protocol/original
and inner/outer recipients remain checked. Review corrected keyboard accessibility
and strict warning display at both checkpoints; both axes0. TestAPKcompile
PASS1m44s. Actual11-case RED10run38557529120 at673cf8ff1b2a9d99774eeb5702dd5055a80c6170
pending. Source already preserves the live peer during history failure, but the
current generic warning incorrectly blames saved/sent work. Prepared separate
history warning remains unapplied until actual RED is inspected.

Actual archive-warning RED10run38557529120 at673cf8ff: eleven cases/ten PASS,
66.372s. Fresh malformed Archive response observed; missing distinct visible
history warning fails252. Root original archive-failure PNG/XML show generic
saved/sent error and three cached messages. Send/live/five-message recreation
tail unexecuted. Retained native-archive-red-android10.

Warning minimumc3f8a9da separates historyFailed copy from local read/save/send/
draft failures. Reviews caught existing Retry fixture142 still expecting old
generic copy; ad8aa4fc changes only that expected sentence, retaining Submitted,
original and recovery assertions. Correction reviews0/0. Product debug/testAPK
compile PASS3m10s; corrected testAPK PASS1m41s. Native11 atad8aa4fcedf4b1b417df26f2c96bde7bad076f20
pending: Android10run38558779177, Android17run38558782794. Complete26 remains open.

## Foreground mixed receipt RED and minimum, 2026-10-14

Actual Android10 run38609275290 at875cccd70570754439dd51dc860f03fee9359c45
passes action1/10.771s, then mixed1 fails19.349s at the first encrypted private
message positive-ACK wait while Groups is open. Native failure PNG/XML inspected;
private cache/reaction checks, mode switch, group receipt and visible tails did not
execute. Group22 and real PID recovery did not run. Retained native-mixed-red10
README distinguishes missing positive ACK from unrecorded negative ACK.

Minimum e26ed211 routes original authenticated payloads in every foreground Chat
mode: curve public-key group IDs to the existing GroupStore, private encrypted
messages and pair-hash reactions through the existing private validation/stores.
Extraction fallback occurs before authentication/storage, never after a storage
error. All positive ACKs still follow successful retention. Group storage failures
keep the existing explicit warning signal while Groups is open. Bounded Spec review
found the private-screen handlers lacked this signal; a separate warning tracer
is now dispatched before correcting that path. No new outgoing intent or authority.
Actual corrected hints/mixed/PID GREEN and final full acceptance remain pending.

Actual private-screen storage warning RED38610859211 at4d394d68: actions1PASS
10.784s; mixed1FAIL20.04s at88missingwarning. It proves private original and
reaction retention/ACK whileGroups open, then correlated negativeACK for an
authentic group creation under the owned unfinished-file obstruction in private
Chat. Root original PNG/XML inspected; later preservation/recovery/visibility
tails and22/PID did not run. Retained native-mixed-storage-red-android10.
Minimum5d9ba21e adds a stable owner/backend channel collector and shared private
handler wrapper for classified group storage failures only, preserving rethrow
for negativeACK. A static resource warning directs Groups and Refresh. It stays
latched across private navigation/metadata/reloads. Native GREEN pending.

## Focused native acceptance and final full runs

Android10run38611923176 at90e979e85ab462bb36948103b6ea31efb9872c09 SUCCESS:
actions1/13.348s, mixed1/8.196s, groups22/125.4s, actual PID recovery11219 to11515
and exact installation restoration. All failure/recovery tails execute, private
DM/reaction retained inGroups and groups received inprivateChat becomevisible in
correctviews; classifiedstoragefaultnegativeACK+notice/exactsentinel/nooverwrite
thenpositive recovery. Protected group key/origin/control/message/uncertainaction/
draft/wallet/backend remain with no submissions acrossdeath/unavailableArchive.
Root inspected original malformed-hints reopened, private-mode storage warning
and cold recovery PNG/XML. Public captures and raw/checkpoints retained separately.

Android17run38611928760 atsame90e FAILED hostactiondeadline124. Classreports
1PASS54.77s andINSTRUMENTATION_CODE -1, butexit124 failsstrict60sgate. Mixed22/PID
unexecuted. No native17 acceptance or lengtheneddeadline. Failedhostattempt retained.
Finalfull10run38613217864 and17run38613221849 atsamec5193954631f26541c3421d51db23c49bdecd3a9
arequeued withpublic_feed_read=true, unchangedproduction since90e, shared native
harnesshelper reviewed0/0. Existingfullorder runsappearance/group22 beforeactions;
allclassdeadlines/assertions remain. Fullchecks willinclude release/lint/core,
existingprivateprocesshelper regression and allpreviousfeature/wallet/update gates.
Signednextpreview ispreparedbutnotpublished untilfinalverification.

Finalfull17firstattempt38613221849 atc519 FAILED: appearance1PASS44.212s;
group22 21PASS/1FAIL132.256s atimportedguidancevisibility733. Rootnativeoriginal
PNG/XMLshowCheckingavailablegroupinformation anddisabledCreate/Join. Presence
wait doesnotprove displayedtext; sourcewalletmetadata replacement restarts
discovery, only supports a transitionhypothesis. Correctione95df2f4 waitsactual
displaywithsame15sbudget/finalassertion, no productionchange. Other21casespass;
actions/mixed/PID/restfullunexecuted. native-full-attempt1-android17retained.
Full10attempt1failedcurl92publicSDKsetupbeforebuild/device; originalfailure
excerpt andjobstepsretained. Samec519attempt2runtimeactive. Outstanding17will
repeatfull atcorrectedfixture, recordingunchangedproduction perWORKFLOW.

## Final full regression acceptance, October 14

Android10run38613217864attempt2 atc5193954631f26541c3421d51db23c49bdecd3a9 SUCCESS:
302reported301PASS+oneexistingAPI30profileassumption,66completedrecords,20actual
deathstages. Group22/122.025s, actions1/9.991s, mixed1/6.366s, groupPID11704to11990.
Android17run38614860580 at03aacb31848bd724d376df8f8fc7d6f03d1d89a6 SUCCESS:
303PASS/67records/20deathstages; group22/130.524s, actions1/10.468s, mixed1/6.887s,
groupPID5475to5556. Core110/release/lint/source and allpreviousfeature/wallet/update/
privateChatrecovery/publicChat/bothpublicfeeds passboth. Existingprivatehistory24
andactions10 andactualprivatePID/restoration validate sharedhelperregression.

Production/buildconfigidentical on bothsources and preparedsigned15manifest7d695abf;
17onlytestdisplay-readinesscorrection, samedeadlines/predicates, perWORKFLOW.
Final-full sets retain publicoriginals/raws/counts/checkpoints/runmetadata with
650/653hashes and231/233XMLs, checkedsecretmarkers absent. Rootselectedfinal
originals viewed onboth; after.xml showsIdentitycreatedandsignatureverified.
Whole source andfinal10evidence reviews0/0; final17review/publicationpending.
No physicalPixel7/performance/currentphoneupdateacceptanceclaimed, retained35.

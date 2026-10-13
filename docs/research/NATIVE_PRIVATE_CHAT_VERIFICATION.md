# Native private Chat verification

Ticket #25 is active. Review base: c102f891f6041f08199905728d302e19b7588abd.
Protocol contract: NATIVE_PRIVATE_CHAT_CONTRACT.md. Independent reference:
NATIVE_PRIVATE_CHAT_REFERENCE.md and JSON. Evidence: docs/evidence/ticket24/.
Separate axis reports: docs/evidence/ticket24/REVIEW.md.

## Protocol increments

First receive tracer: missing-decoder RED31s, GREEN1/56s. Native send tracer:
missing-create RED28s, GREEN2/58s, matching exact pinned ciphertext, signature,
network and Archive envelope bytes. Initial shared regression core76 and both
APKs passed2m59s. Imported exchanged-key preference/fallback genuine RED3/one
comparison failure55s; minimum implementation passes fullcore77/zero failures,
errors or ignored in1m. Source checkpoint7dbaba3.

First review: Spec2 findings (legacy publickey and passphrase case), Standards2
findings (passphrase case and coercing malformed formatter arrays). No native
transport, UI or recovery completion was claimed.

Relay JSON tracer: missing serialization/read API RED26s, GREEN4/56s against
independent existing-client inner JSON. Legacy field genuine RED5/one failure33s;
alias minimum GREEN5/57s preserves the complete exported backup. Uppercase saved
passphrase independent genuine RED6/one failure34s; exact-string minimum
GREEN6/56s. Correctly signed malformed formatter genuine RED7/one failure40s;
string-primitive guards pass fullcore81/zero failures/errors/ignored in1m.
Source checkpoint42dfd6f. Reference hash886ff62259151851e7a1131d264d805bd4c192f1e312b42376099c2711ee1c06:
four valid vectors plus three malformed; two byte-identical isolated executions.
Actual pinned Crypto returns empty plaintext for ct-array and throws for iv/s
arrays. The initial reference harness assumed an exception for ct; this was
corrected to record its actual result before passing output was generated.

Bounded fixes review: Spec0; Standards0 new hard findings plus one actionable
Duplicated Code heuristic. Required-string validation is now shared between
formatter and decrypted body. Shared validation cleanup remains passing in core81; both APKs build3m24s.

## Peer discovery

New actual local-WebSocket test targets authenticated advertised Archive/Relay
services. The first APK compile failed2m18s with both missing services API and a
fixture constructor regression. Adding a non-function final option broke old
trailing-lambda bindings. A known-invalid subsequent build was stopped143.
The option now precedes the existing final Archive callback; existing assertions
unchanged. Clean RED2m33s contains exactly one missing services compiler error.
Minimum implementation awaits authentication on the same selected connection
before exposing bounded, strict-UTF8 service records. Core81/debug/testAPK source build passes3m24s. Focused emulator socket result
still pending.

These are incremental protocol/source checks. Complete native DM UI, Relay
send/receive, available-history recovery, private cache, Block/Mute semantics,
both public backend contracts, process interruption and final fullSDK acceptance
remain unfinished. No physical Pixel, live Chat publication, positive funding,
closed-app push, permanent history or new phone APK claim.

Authenticated discovery source0997419 both axes0. Actual focusedAndroid10
run38337011297 passes1/0.471s/oneOK/noassumptions/deaths; core81/source gates.
Four retained hashes, no UI bitmaps. Public backend services not yet verified.
Unsigned Relay receive core tracer missingAPI RED31s then GREEN8/1m2s; valid
independent inner is read and altered inner rejected. Socket callback genuine
missingAPI RED2m7s then minimum source core82/debug/testAPK3m12s PASS. New
PrivateChatPeerPreviewTest has two cases; actual two-case runtime next. Original
Chat verification precedes acceptance; full UI/cache/delivery proof pending.

Incoming protocol/socket source0997419...3c35a77 both axes0codefindings.
Standards one handoff correction (private references already committed) fixed.
Actual10run38338221475 at3c35a77 PASS2/0.788s/oneOK/noassumptions/deaths,
core82/source gates; four hashes/no UI captures/durable-cache proof. First
imported-contact native UI tracer compiled1m43; primary-preserving independent
cleanup strengthened and compiled1m26. Missing Chat UI runtime RED next.

Actual first UI RED10run38339184559 at6df3ef0:1failure18.154s/zeroOK/deaths/
assumptions, line66 waitingChat beforecontact/history. Core82/source pass;
five hashes/rootoriginal PNG+XMLconfirmOwner feed/noChat entry. Cleanup restored
opaque wallet without maskingprimaryfailure. Minimum imported-contact/read UI
now source-building. Chat/read rows remain authenticated and personalBlock
filtered; no privatecache/send/liveUI acceptance yet.
Read-only public probe bothbackendTLS/nonceauth/Chat+Archive+Relay/field3 load
accepted0rows; initialdiagnostic and explicitUTF8repeat retained. Pure pinnedWASM
helper now canonicalbuild-support/references/private-chat-public-read.cjs. No
userkeys/privatechat payloads/writes/nodechanges. This is contract research, not
actual nativeChat/publicdelivery or nonemptyhistory proof.

Minimum Chat entry/contactlist/verified availableArchive history source core82/
debug/testAPK3m34PASS. All imported public contacts considered with LazyColumn;
legacyfield and groups excluded through narrow walletmetadata API, secrets not
exposed. Messages authenticate/decrypt/dedup beforedisplay; Block filtersincoming
while Mute doesnot. UI GREEN next; nocache/send/push/fullacceptance claim.

First history UI actual10run38340335366 at7e5811e passes1/4.923s/oneOK/no
assumptions/deaths; core82/source gates. Five hashes, root originalPNG/XML
confirm imported authenticated Unicode history on selected Owner. Both bounded
source reviews6df3ef0...7e5811e report0. Cache/recreation tracer next; no send,
recovery, public nativeChat or whole-ticket completion claim.

Cache tracer compiled1m34s. Actual10run38341603454 at6f02f73 fails1/20.682s,
line82 missing prior message after Activity recreation with unavailable Archive.
ZeroOK/assumptions/actualdeaths; core82/source gates pass. Seven hashes preserve
both originalPNG/XML pairs; root XMLs/failurePNG confirm first history visible,
then error-only reconstructed screen. Minimum encrypted wallet/backend/pair cache
now source-building. This is recreation, not actual process-death proof.

Minimum cache source core82/debug/testAPK passes3m1s. Original encrypted
envelopes are device-encrypted by wallet/backend/pair; cached entries authenticate
and decrypt again, server pages merge/dedup without deleting retained history.
Storage failure is explicit and does not overwrite unreadable history. Actual
reopened UI GREEN and bounded reviews pending; no process-death/delivery claim.

Bounded cache reviews both report one same loss-on-Refresh finding after failed
local saving; separate reports retained. New native UI failure tracer uses an
empty directory at the exact synthetic conversation cache path, preserving
opaque wallet and prior files. Compiles1m33s. Verifies readable memory through
offline Refresh, successful persistence repair on a pruned empty page and
recreation. Actual RED pending before minimal retention fix.

Actual recreation GREEN10run38342240579 at0620289 PASS1/8.768s/oneOK/no
assumptions/actualdeaths; core82/source gates. Seven hashes/root both XMLs and
recovered original confirm message alongside unavailable-history warning. Initial
capture also includes Loading messages; only later screenshot proves completed
unavailable recovery. Failed-save/Refresh actual RED38342655104 ate8f6808 next.

Failed-save/Refresh actualRED10run38342655104 ate8f6808 reports2cases/
1PASS+1failure22.977s; line93 prior message lost after offlineRefresh. NoOK/
assumptions/actualdeaths; core82/source gates. Nine hashes/root allXMLs/failure
original verify local-warning+message then offline error-only. Shared capture
filenames qualified: cached-recreation image belongs to firstpassingcase, not
failedcase. Minimalfix retains authenticated entries/envelopes in memory, merges
cache without replacement and persists all retained ciphertexts on retry. Storage
warning remains while current entries are absent from disk. Source build pending.

Retention fix source core82/debug/testAPK passes3m5s. Actual unchanged two-case
UI GREEN and bounded fix reviews next; no actual process-death or send claim.

Corrective actualGREEN10run38343321818 at43399a7 PASS2/9.458s/oneOK/no
assumptions/actualdeaths; core82/source gates. Eleven hashes/root fourXMLs and
three originals confirm memory retained after savefail+offlineRefresh, successful
empty-page repair and recreated cachedhistory during unavailable Archive. Both
bounded fix reviews0codefindings; stale UI README corrected. Live receive next.

Live-relay UI tracer initially compiled1m39s. Before any runtime dispatch,
strengthened to deliver original unsigned Relay wrapper via actual authenticated
sockets only after empty history finishes; revised APK compiles1m32s. Subsequent
recreation has no redelivery and unavailable Archive. Actual native RED next.
No initial socket/UI result is inferred from either compilation.

Delayed Relay actualRED10run38344010177 atdb62258 reports3cases/2PASS+1
failure26.503s, line96 missing originalmessage after fixture socket offer. NoOK/
assumptions/actualdeaths; core82/source gates. Thirteen hashes/root fiveXMLs and
failure original show completed emptyconversation, no message. Passing-cache
captures separately qualified; fixture send is not confirmed app receipt. Source
has no UI incoming handler and disposes history peer. Minimum now keeps selected
authenticated connection while displayed, routes original verified Relay message
to durable encrypted cache before13ack, sends persisted history to UI via bounded
conflated channel; archive failure does not close healthy Relay session. Source
build and unchanged3caseGREEN pending.

Minimum live-selected-conversation source core82/debug/testAPK passes3m.
Actual unchanged3caseGREEN and bounded source reviews next. No conversation-list
listener, sending, actual process-death or whole-ticket completion inferred.

Live selected-conversation source reviews: Spec0, Standards1 correctness/0
heuristics. Incoming snapshot can publish after a newer history save, falsely
marking newer durable entries missing. Minimum correction removes warning
assignment from incoming updates; only actual history load/save controls it.
Current focused10run38344712980 tests fed20de before this two-line correction;
results must remain associated with that original source. Corrected compilation
and bounded fix reviews pending.

Warning-order correction compiles app Kotlin1m25s. Core unchanged; this local
check is compilation only, no new core/APK/runtime claim. Corrected focused
Android17 and bounded fix reviews next; original focused10 source stays fed20de.

Actual live-selected GREEN10run38344712980 atfed20de PASS3/12.975s/oneOK/
noassumptions/actualdeaths; core82/source gates. Thirteen hashes/root fiveXMLs
and live+recreated originals confirm delayed Relay message,13ACK, then encrypted
local recovery without redelivery/unavailableArchive. Source precedes warning
correction d62a964; corrected17run38344995062 pending. Bounded warning fix
reviews both0. First Chat permission prompt next; send/listener/actualdeath and
full-ticket acceptance remain unfinished.

Corrected selected-live actualGREEN17run38344995062 atd62a964 passes3/
41.36s/oneOK/noassumptions/actualdeaths; core82/source gates. Thirteen hashes,
root fiveXMLs and live/failed-save/recreated originals confirm same observable
reception/cache gates on corrected source. Both warning-fix reviews0. First-Chat
optional permission tracer in progress; no fullSDK/Pixel/whole-ticket claim.

First Chat optional-alerts native tracer compiles1m40s, skips through actual
shared permission UI and verifies no initial feed offer/repeated offer after
recreation. Cleanup restores each preference independently and preserves opaque
wallet. Revised build prints SUCCESS1m31s but command exits143; not claimed as
a successful command, cause uninvestigated. Unchanged recheck exits0/SUCCESS19s,
all50tasks up-to-date. Actual four-case permission RED next; app prompt not wired
into Chat yet.

FirstChat offer actualRED10run38345645504 ata3b148a reports4cases/3PASS+
1failure28.341s,line46 missing Android notification alerts. NoOK/assumptions/
actualdeaths; core82/source gates. Fifteen hashes/root sixXMLs and permission
failure original confirm Chat without offer. Skip/recreation gates unreached.
Minimum integrates existing shared NotificationPermissionPrompt in Chat only;
no first-feed offer or reading dependency. App Kotlin compilation and unchanged
four-caseGREEN next; actual Android system allow/deny already belongs to the
unchanged #24 shared component, not inferred from this new Skip integration.

One-call shared Chat permission integration compiles Kotlin1m23s; core and
shared Android permission component unchanged. Actual four-case focused10/17
and bounded source reviews next. No APK/core/runtime claim from compile alone.

First Chat actualGREEN10run38346218117 atf2b33cf passes4/18.809s; same-source
GREEN17run38346220216 passes4/51.82s. Each oneOK/noassumptions/actualdeaths,
core82/source gates. Each nineteen hashes/root eightXMLs and offer/recreated
originals confirm contextual offer/Skip/no repeated offer plus cache/live gates.
Both bounded integration reviews0. Native outgoing send/Relay tracer next;
no actual system grant/deny or full-ticket result inferred from Skip.

Native send tracer verifies same original signed ciphertext on propagation and
Relay, selected recipient decryption, truthful Forwarded/not confirmed status,
recreated cache and no rebroadcast. Controlled fixture implements pinned Relay
request/JSON wrapper; public unsafe receiver1 only. Shared fixture selects one
explicit history case rather than invalid boolean combinations. First local
command exits143 with incomplete output; Docker event reports build container
exit0 at1791865951, duration110s, with no kill event in inspected15m window.
No cause inferred. Unchanged APK recheck exits0/SUCCESS18s, all50tasks up-to-date.
Actual five-case RED next; native composer/sending not implemented yet.

Actual send RED10run38347083896 atf072178 reports5cases/4PASS+1failure36.397s,
line173 missing composer Message after history. NoOK/assumptions/actualdeaths;
core82/source gates. Twenty-one hashes/root nineXMLs/failureoriginal confirm
no composer; send/Relay/recovery gates unreached. Minimum now adds encrypted
Chat work/draft storage, original signed candidate before sending, unknown state
before an attempt, wallet/backend claim guards, propagation plus pinned Relay
JSON/request, claimed-forwarding status without delivery/read receipt, no send
on load and durable local history. Source build/actual unchanged5caseGREEN and
bounded reviews next; no completed send/recovery claim yet.

Minimum source build command connection exits143 while its isolated container
b781529b7e9c is still running. No source changes or duplicate Gradle launched;
attached to Docker logs/wait. Original container completes exit0, full captured
SUCCESS3m2s/core82/debug/testAPK. Connection termination cause not inferred.
Actual unchanged five-case UI GREEN and bounded source reviews next; outgoing
status/cache recovery, independent browser decoder and interruption not yet proved.

First native submission actualGREEN10run38348001347 ate06a22c passes5/20.851s,
oneOK/noassumptions/actualdeaths/core82/source gates. Twenty-four hashes retain
ten PNG/XML pairs plus unsafe native envelope; root allXMLs and forwarded/
recreated/failed-save originals inspected. Original signature matches propagation
and Relay; received plaintext correct; reopening retains without rebroadcast.
Recreated PNG clips outgoing final line; fulltext is in XML, no full-visibility
claim. Compact composer row is subsequent correction, Kotlincompile1m22s (later
indentation-only change); recovery-guidance correction Kotlincompile1m26s.

Bounded first-send reviews Spec0/Standards1 incorrect Refresh load-recovery advice.
Separated initial-read guidance (reopen) from later send/save failure; neither
promises automatic retry. Corrected source runtime/reviews next. Native send
fixture envelope SHA489f7c17ed504db739a0229fa43e6158106bf8c84ab926045033f903ad9a726e
passes isolated existing-client Crypto-wrapper decryption and independent WASM/
Elliptic signature reproduction. Actual native random salt/message consumed;
no app/network/real wallet initialized. New --check-native-send mode requires
unsafe2->1 and exact fixture plaintext. Original generator default rerun remains
byte-identical SHA886ff622...ee1c06. This is actual captured native lower-boundary
interop, not browser UI/public writes or completed interruption recovery.

Interrupted-send coverage compiles testAPK2m42s. Controlled socket closes after
observing original propagation, before Relay completion. New case requires saved
Submitted/not-confirmed state, no Forwarded claim, decrypted original, recreated
outgoing history and unchanged transmission count. This verifies existing attempt
handling; no RED or new implementation result is invented before execution.
Send/recreated captures now use actual user scrolling to expose the outgoing
paragraph rather than relying on partial visibility. Corrected5case17 source
remains94326b2; actual6case10 coverage next.

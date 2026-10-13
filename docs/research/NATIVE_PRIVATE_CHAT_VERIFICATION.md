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

Corrected actualGREEN17run38348848791 at94326b2 passes5/60.052s/oneOK/no
assumptions/actualdeaths; core82/source gates. Twenty-four records/root tenXMLs
and three originals confirm compact layout and both outgoing lines visible on
recreation; same signed propagation/Relay message, retained history/no resend.
Captured17 envelope also independently passes pinned Crypto/WASM signing check.
Both bounded corrections reviews0. Six-case interrupted10 coverage38349463109
at8615857 pending; actual process death and full-ticket acceptance not inferred.

Interrupted coverage actualGREEN10run38349463109 at8615857 passes6/25.282s,
oneOK/noassumptions/actualdeaths/core82/source gates. Twenty-eight records/root
twelveXMLs and two interrupted originals show Submitted/not-confirmed, generic
send failure, then retained original after reopening/unavailable Archive with
one propagation and zero Relay observations. Existing implementation passes
new coverage directly, no invented RED. Shared native-envelope filename belongs
to later normal send. New-contact compatible-wallet core tracer next.

Private contact metadata API tracer compilerRED26s (missingwithChatContact)
then targetedGREEN54s; immutable portable contact preserves original identity,
existing name/module fields and original export. Native fresh-wallet Open-chat/
send/reopen tracer plus fullcore83/debug/testAPK passes2m45s. Actual seven-case
UI RED next; entry/form/shared-wallet metadata update not yet implemented.

Actual new-contact RED10run38350790362 at a26945b reports7cases/6PASS+1failure
45.202s; root thirteenXMLs/failureoriginal inspected. Fresh-wallet Chat has no
Public key entry atline169. Existing six cases pass; shared captures belong to
later cases. Metadata bounded review861...a269: Standards0hard+1Duplicated Code
heuristic (shared group predicate); Spec1 imported keys:null rejected although
pinned keychain treats it as empty. Fix tracer first had a mistyped method name
(compilefailure26s); corrected behavioral RED pending, not claimed from typo.

Corrected null-keychain tracer actualRED33s/13cases1failure atdiscovery. Minimum
null-as-empty across contact discovery/implicit-secret fallback/addition plus
shared group predicate fixes both bounded findings. Fresh-contact entry now saves
portable contact through protected compare-and-replace and updates shared active
wallet, with durable recovery publication despite cancellation. Fullcore84/0/0/0
plus debug/testAPK passes3m44s. Actual seven-case nativeGREEN/review next.

Fresh-contact correction33b755e both bounded axes0, actual seven-case10run
38351999832 pending. Separate opt-in native public service/history reader uses
ephemeral device-only wallets, authenticates each approved endpoint, requires
Chat/Archive/Relay and an empty Archive response for a never-used random pair.
No submit/relay/live application write; installation wallet export remainsexact.
TestAPK compiles1m49s; real native contract outcome pending, no runtime inferred.
Full runs include this read check only with explicit public-read input enabled.

Actual new-contact GREEN10run38351999832 at33b755e passes7/35.544s, core84/
source gates. Root twelveXMLs/entryandnormalrecreated originalPNG inspected.
Fresh-wallet case includes Open chat, saved compatible contact, same encrypted
propagation/Relay original and unavailable-history recreation/noresend. Shared
captures belong to latercases, no specific fresh-case image attribution.
Public reader e2eb51d both bounded reviewaxes0; actualrun38352335633 pending.
Next imported-ID tracer uses lower-case options.chat.groups, matching pinned
source; no contact key entry or local message metadata. Expected actual UIRED
before private verified Archive discovery, not wallet-ID or permanence inference.

Imported-ID native tracer compiles1m32s, expected actualeightcaseRED pending.
Publicread10run38352335633 at e2eb51d success; raw endpointreport inspectionnext.
No live-send/nativeavailable-nonempty history inferred from unusedpairreads.

Public-read originalJSON/runner inspected:10run38352335633 PASS1/1.649s,
both approved endpoints authenticated Chat/Archive/Relay and0rows forunusedpair.
Private imported-ID core tracer actualcompilerRED27s missingchatHistoryIds/recover;
minimum combines lower-case existing options.chat groupIDs with knowncontacts,
derives a counterpart only from original verified/decrypted private participants,
and rejects mismatched expectedconversation. CoreGREEN pending. Actual8caseUI
RED10run38352677744 atd4d5f01 pending; discovery not implemented prematurely.

Imported-ID core targetedGREEN9/55s validates exact pinned original/decrypted
text/counterparty and expectedgroupbinding without contact keys; originalwallet
export remains unchanged. NativeUI discovery stillpending actualRED evidence.

Actual imported-ID RED10run38352677744 atd4d5f01 reports8cases/7PASS+1failure
49.884s atline181 absentcounterparty. RootthirteenXMLs/failureoriginal inspected.
Boundedcore reviewd4...f2cd: Standards0; Spec1validmixedgroupID rejection.
Pinnednamed group ID isSaito publickey; private candidateIDs64hex shouldskip
recognizedgroupkeys without dependingon#26. Mixed-ID regressionRED next.

Mixed-groupactualcoreRED9cases/1failure34s. Minimum recognizedgroupkey skip
passes modernandlegacy importedlists; fullcore85/0/0/0+debug/testAPK PASS4m12s.
Native list discovery now reads savedIDs, verifies/decrypts expectedprivategroup,
saves originalencryptedcache and publishes portable recoveredcontacts through
same shared active-wallet callback. ActualeightcaseGREEN/reviews next.

Bounded importednative reviewf2...655: Standards0; Spec1partial discovery hides
alreadycached firstconversation afterlatergroupfailure. Added one native regression:
ID-only importcontains availablefirstprivategroup thenunavailable distinctID;
firstcontact/history/recreatedofflinecache mustremainaccessible. Actual9caseRED
pending; no scope relaxation. Actual8caseGREEN38353752949 stillpending.

Actual imported-ID GREEN10run38353752949 at655c1e5 PASS8/44.97s/core85/source
gates. RoottwelveXMLs/twooriginals inspected; sharedcapturesattributedtolatercases.
Imported-ID test verifies contactdiscovery/availableprivatehistory/recreatedcache
withfailedreads. Partialdiscoverytracer compiles1m37s andcommitted37532b6,
actual9caseRED10run38354277929 pending. No actualdeath/newphoneAPK claim.

Actualpartial RED10run38354277929 at37532b6 reports9cases/8PASS+1failure
52.21s atline189 missingfirstrecoveredcontact aftersecondgroupinvalidreply.
RootthirteenXMLs/failureoriginal inspected. Minimum catchpergroup preserves
successfullycached contacts and publishes them via sharedwallet, with discovery
failure flag; cancellationstillrethrows. Source/testAPK build and actualGREEN next.

Partial discovery correction debug/testAPK PASS1m47s (core unchanged85; no core
rerun claim). Actual9case nativeGREEN and both bounded correction reviews next.

Correctedpartial actual9caseGREEN at a6c525c:10run38355056886 PASS9/42.446s;
17run38355059918 PASS9/83.692s, eachcore85/sourcegates. Rooteach twelveXMLs and
critical originalrecreatedsendimages inspected, sharedcapturesqualified. Both
boundedcorrectionaxes0source; handoffstaleproductionHEAD/minimuminstruction fixed.
New list-incoming tracer compiles1m32s, then same-original boundedredelivery
fixture correctioncompiles1m38s. Emptywallet/noimportedcontacts/IDs, Chatlistopen:
fixtureoffers signedoriginal every200ms while authenticating, expects discovered
contact/decryptedmessage/durablepositiveACK and recreationwithoutredelivery.
No listlistener implemented yet; actual10caseRED next.

List-incoming tracer6829cbf bothboundedreviewaxes0. ActualRED10run38355907771
reports10cases/9PASS+1failure54.58s atline196 nodiscoveredcontact. RootthirteenXMLs/
failureoriginal inspected. Minimum list-only incoming authenticatedpeer now caches
verifiedoriginal beforeACK; discoverspubliccounterparty fromowner/backend scoped
device-encrypted originalhistory, includingreopeningbeforeportablepublication.
Selectingnewcachecontact usescompatible sharedwalletmetadata callback before
opening. Selected-conversation listener remainsseparate; otherconversation receive
whilealreadyinsideone is not claimed. APKbuild/actualGREENpending.

List-only incoming/cache-contact minimum debug/testAPK PASS3m20s; core unchanged85
(not rerun locally). Device-encrypted contacts discover only sameowner/backend
originals, including AtomicFilebackup recovery; bad records remain untouched and
reportpartialunreadable, successfulothercontacts remainavailable. Actual10caseGREEN
and boundedreviews next; receivewhileanotherconversationselected stilloutstanding.

Boundedlist/cache review6829...4734: Standards1snapshot overwrite ofnewlivecontact;
minimalcurrent-contactmerge+explicitbackendremember applied, Kotlincompilepending.
Spec1all-or-nothingcachedecryption hidesreadable implicit entries ifolder exchanged
secret missing. Mixed implicit/exchanged cache regression next; originalciphertext
must be preserved and readable entries remainavailable withpartialwarning.

Actual list-incoming GREEN10run38356985068 at4734f93 PASS10/43.21s/core85/source
gates. Root twelveXMLs/liveandnormalrecreated originals inspected; sharedcaptures
qualified. Newlistcase passespositiveACK/discovery/decryptedtext/recreatedcache
withoutredelivery. Snapshot-merge reviewfix KotlincompilePASS1m38s, no newnative
result inferred. Mixed-key cache regression seeds two independently pinned
originals undercompletekeys, thenopens key-only wallet with no contact/IDs and
failedArchive. Readableimplicitmessage/contact+partialwarning+newsend/cache must
survive missing older exchanged secret. Actual11caseRED pending.

Mixed-key cache tracer plusmergefix debug/testAPK PASS1m47s (coreunchanged85).
No partial-key implementation yet; native11caseRED next. Recreated normal-send10
original exposes bothmessageparagraphs/status, but inputfooter is partly clipped:
recorded asviewportlimitation, not professionalpolish/physical acceptance.

Contactmerge+mixedkeytracer89c09bf bothboundedaxes0new; orderingfindingresolved,
mixedkeyfindingstillopen. Stronger fixture now restoresmatching importedsecret
afternewsend/reopening and requirestheoriginal olderplaintext to reappear, with
no extra transmissions. This checks unreadableciphertextpreservation throughsave.
TestAPKcompiles1m39s; initial11caseRED38357934691 stillrunningat89c09bf.
Restored-secret tail isnewcoverage, notan invented RED result.

ActualmixedkeysRED10run38357934691 at89c09bf reports11cases/10PASS+1failure
58.556s line221 missingcachedcontact afterkey-onlyimport. RootthirteenXMLs/
failureoriginal inspected. Earliermergefix passesremainingcases, no invented
deterministicraceproof. Strongerrestored-keytail4f9fb46 notinthisRED. Minimum
cacheRead nowreturnsverifiedreadableentries+partialflag; savepreserves allsigned
originalciphertexts while requiringfullprivateverification/decryption fornewrows.
Cachecontacts skipundecryptablerows, distinguishpartialmessagesfromunavailable
localfiles; selectedwarningpersistsacrossavailablepage+newsave. APKbuildpending.

Partial-key cache correction debug/testAPK PASS3m22s, coreunchanged85(not rerun
locally). Originalsignedmetadata authenticates retainedciphertexts; onlyfull
private/decrypted newmessages enterstore; cachedreadableentries+partialflag are
returned withoutdeletingolderunreadableciphertext. Strongerrestorationtail remains
in11caseGREENnext. Boundedsource review+nativeGREEN pending.

Partial-keysource59ba616 reviewSpec0/Standards1readabilitywarning coupledtosave
success. Newstorage-partial tracer suppliesreadableimplicit+missing-secret exchanged
Archive originals whilelocalcachewritefails, requires partialwarning. Compile1m46s.
Actual11caseattempt38359429556 at59ba616 reports10PASS+onefixturefailure52.032s:
restored-secret tail finds twointentionallysameplaintext independent signedmessages
(line309), notone. Rootthe13XML/failureoriginal inspected. All earlierpartialread/
newsend/retention steps reached; no wholeGREEN claimed. Correctedtest exactlyone
textbeforesecret/exactlytwoafter retainsallrequirements; compiled1m39s withnew
storagewarning tracer. Actual12caseRED next, sourcewarning minimumnotyetapplied.

Actualstorage-partial RED10run38360615490 atecc3d56 reports12cases/11PASS+1fail
70.118s line239 missingpartialhint whenavailablequeryincludesundecryptablerow
andcachefails. RootfourteenXMLs/failureandrestored-key originals inspected.
Correctedpartial-keycase nowPASS, including ciphertextpreservation/restoredtwo
sameplaintext originals; no additionaltransmissions. Minimalwarning appliedbefore
persistencetry, combinescachedpartialacrossRefresh, wallet/backend/conversation
scopedreset; strongeroffline-refreshwarningassertionadded. APKbuildpending.

Independentpartial-warning correction debug/testAPK PASS3m3s, coreunchanged85
(not rerun locally). Actual12caseGREEN10+17 and bothboundedreviews next.
OfflineRefresh assertionstrengthens warningretention; matchingrestoredwallet
resetswarningscope, cacheoriginalpreservation/newfullverification unchanged.

Actualwarning/keyrecovery GREEN10run38362024612 atdf9891d PASS12/55.833s/core85
sourcegates. Root13XML/warningretention+restored-key originalsinspected. Twelve
case17run38362028575 sameSHA truncatedafter11PASS andcase12began, nofinalresult
orassertionfailure. Oldharness90s/exitdiscarded; notclaimedobserved124/appfailure.
Prior9case17took83.692s. Classbudget180+rawexitstatus nowretained, percasewaits/
assertionsunchanged. ShellsyntaxPASS; no productionchangefromharness.
Bothwarningfixreviewaxes0/resolved. Newdifferent-contact-while-open traceruses
publicunsafe3->2 native-signed controlledRelay (independentcrypto alreadycovered);
oneincomingofferafterselectedhistoryready, positiveACK, no bleed intoopencontact,
othercontactdiscovery/readability/recreatedcache with no redelivery. TestAPK
compiles1m47s. Receivingothercontact minimumunimplemented; actual13caseRED next.

Actual different-contact RED at22b07ed: Android10run38363644050 reports13cases/12PASS+1failure61.98s; Android17run38363647971 same13/12PASS+1failure90.368s. Both failatline260 waiting5s forpositiveACK; class180s isnotexhausted. Rawinstrumentationexit0 demonstrateswhyOKresultgate remainsmandatory. RootallXML/failureoriginals inspected; activeconversationcontainsknownavailabletext. Selectedreceiver rejectsothercounterpart, so minimalfix nowfullyverifies/savesotheroriginal intoitsownconversation beforeACK, withoutaddingothertext toopenconversation. APKbuildpending; noGREENclaimed.

Different-contact minimalreceiver debug/testAPK PASS1m51s, coreunchanged85(notrerunlocally). Source256b67f pushed; native13GREEN10run38384045182 and17run38384048900 inprogress. Two independent boundedreviews df9891d...256b67f alsoinprogress; noGREEN/whole-ticketclaim.

Receiver attempt256b67f actual10run38384045182:13cases/12PASS+onefail82.903s atline263 missingothercontactafterBack. Positive13ACK at260 nowpasses, nootherplaintext inselectedconversation; root14XML/listfailureoriginal inspected. Cause observedinsource: contactscanEffect didnotkeylistreturn, sootherdurablecache notrescanned. Minimalfix keys wallet/backend/endpoint/counterparty/refresh andrunsdiscoveryonlyonlist. LocalAPKbuildpending;17attemptstillrunning.

Companion17run38384048900 at256b67f completes13cases/12PASS+one line263failure113.486s, samepositiveACKpassed/missinglistcontact. Root14XML/listfailureoriginal inspected. Neither runhits180sbudget. Listrescanminimum nowkeys selected/list navigation and skipsdiscovery whileconversationopen. Bothbounded priorreceiver reviews0; minimalrescan reviews/nativeGREENnext.

Listrescan correction53fd84b localdebug/testAPK PASS1m44s, coreunchanged85. Bothboundedreviewaxes256b67f...53fd84b0. Native13case10run38384956940/17run38384961151 inprogress; nocompletedGREENclaim.

Listrescanattempt53fd84b:10run38384956940 13/11PASS+twofail89.688s;17run38384961151 same13/11PASS+twofail104.894s. NewothercontactcasePASSboth: positiveACK/no bleed/discovery/read/recreatedcachewithoutredelivery. Local-save/storage-partialcases nowfailline311: stalecontactsFailed survivesselectedRefresh afterstorage repaired. RootallXML/failureoriginals inspected, plus10other-contact-recreatedoriginal. Minimalfix removeslist-onlyearlyreturn, retainingwallet/backend/navigation keys soRefreshcanrecomputeerror. APKbuildpending; no13casewholeGREEN.

SelectedRefresh discoveryfix dd02415 debug/testAPK PASS1m49s, coreunchanged85. Bothboundedreviews53fd84b...dd02415 0. Native13wholechecks10run38385764786/17run38385768655 inprogress. Independentoutside-mention generator modeproducesdeterministic sixvectors withactualCrypto/WASM; repeatbytesmatch56ad3ead...359e5, defaultcorpusunchanged886ff622...ee1c06. Mountsetupfirstfailedbeforeexecution becausecrypto.tsnestedreadonlymountpointmissing; fixedbypublic-only copiedreferencefolder/read-onlynamedmount. NoSaitoinit/wallet/privateconfiguration/network. MentioncoretraceractualREDpending.

Actual13case GREEN atdd02415:10run38385764786 PASS13/61.117s;17run38385768655 PASS13/83.051s, core85 sourcegates. Root15XMLperSDKparsed, othercontactrecreated+storagepartialoriginalsviewed; secondcontactplaintextvisible whileLoading, notcompleteditsArchive. ACK/separation/othercontactdiscovery/recreation/storagerepair allnativepass.
Independentmention CoreRED10/9PASS+one exactrecipientfailure40s, sourceworktreecommitted78020a5. MinimumPrivateChat.read allowsauthenticadditionaloutputs onlywhenactualparticipantspresent, originalsignature/decryption/canonicalpair remainmandatory. Own-authoredrecovery triesaddressedcandidates, acceptsexactlyone successfullydecrypted matchingpair; outside mentions donotbecomemembers. FocusedPrivateChatTest10PASS/build1m2s, fullcore notrerunlocally. Native14tracer nowreadsindependentmentionedincoming Archive+recreatesfailedhistory; encryptedcache exacttwovalidator unchanged until actualUIRED. APKbuildpending.

Mentionprotocol/tracerboundedreview dd02415...5716d23 bothaxes0. Threementionedcorpuscopies56ad3ead...359e5match and originalfourvectors/malformedcases matchbasecorpus. Native14actualcacheRED10run38386689026 at5716d23 inprogress; cacheminimumunchanged. Sourcepush38386688637 fullcore/sourcechecks pending.

Mentionednative14 actualRED10run38386689026 at5716d23:14/13PASS+onefail71.85s line326 missingavailablecachedtext afterrecreation/unavailableArchive. Initialnetworkdecrypt/display passes, cachemetadataexact-two rejects save. Root16XML/failureoriginal inspected; core86sourcegate. Minimalcachemetadata nowrequiresactualprivateparticipants addressed+participantauthor, withoriginalsignature andfullnewdecryption/canonicalgroup verificationunchanged. APKbuildpending; native14GREENnext.

Mentioncache sourcea05c2d6 debug/testAPK PASS2m; bothboundedreviewaxes5716d23...a05c2d6 0. Actual14nativeGREEN10run38387384587 PASS14/65.297s and17run38387388065 PASS14/105.853s; bothcore86/0/0 sourcegate. Root15XMLperSDK/sharedcached-historyoriginalsviewed; filenameslatercases cannotattribute specificallytomentioncase. Newunique-mentioned captures, own-authoredmentionedimport-ID recovery, existingaccountMute/Block-reopen/unblock history testsnowadded asverificationofimplementedpaths; nonewproductsource. TestAPKbuildpending, 17casecheckpointnext. WalletImport.nativeWork currentlydoesnotinspectchatwork family; nativepending-work safeguard verification/correction stillrequired.

Existing-client ownmentioned recovery/nativeMute/Block verification TestAPK PASS1m50s, productionapp/coreunchangeda05c2d6. Added17caseclass withuniqueincoming/ownmentioned/muted captures andblock-hidden/recreated/unblockedretained history. Personalpolicy usesexistingrealdeviceadapter asfixtureprecondition, nativeUI observesbodyvisibility; restorationprefix coversallpublicfixturepolicybytes. Native17checkpointnext, noexecutionresultclaimed.

Actual17case existingbehavior GREEN529aa5b:10run38388552509 PASS17/68.6s;17run38388555732 PASS17/119.614s/core86. Rootall24XMLperSDKparsed+sixuniquecaseoriginalsperSDKviewed. Ownmentionedimport-ID/cachedrecreated, accountMute readable, blockedinitial/recreatedhidden, unblockedretainedwithunavailablehistory allpass. Policyadapterprecondition notnewaccount-managementUIproof; sameplaintextcansharebitmaps, privateoriginalsignatureproofindependentvectors. Bothboundedtest/evidencereviews0.
Next18case unknownoriginalRetrytracer seeds independently generated unsafe2->1 original into real encryptedworkadapter, opensUI withzeroautomaticsends, requires explicitRetry, simulatesonepropagation/disconnectedRelay→Submitted, recreates/noauto, explicitsecondRetrysameoriginal thenForwarded/recreates/noextra. Broadcastnormalizer stripsonefreshroutinghop; everyoriginalsignedbyte equalsindependentnetworkfixture. Initiallyrawwholebroadcastcomparison wouldincludefreshhop, correctedbeforeactualexecution; no invented GREEN. Firstdraft testAPKlogBUILDsuccessful1m40s but shell143/noactivecontainer; finalstrengthenedtracerbuildpending. Productsourceretryminimumunimplemented, actualREDnext.

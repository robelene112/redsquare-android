# Native username verification

Active #19, review base ed5742fd0f57958d284073b7145f1844b72b1dbd.
Source contract: NATIVE_USERNAME_CONTRACT.md. Parent #1 accepted seams1–4:
observable native optional registration against controlled peers, pinned protocol
vectors, persistence/interruption recovery and applicable A/B web interoperability.
No new testing seam or live registration is authorized or performed.

The first tracer resolves exact NativeCase7@saito and public unsafe scalar1
identity from an independently generated registrar statement. The test registrar
is public unsafe scalar2, explicitly supplied at the core interface; it is not
Saito's published registrar and never establishes actual namespace ownership.
The generator checks existing pinned WASM0.3.2/hash and Elliptic6.6.1 metadata,
signs the exact A/B Registry UTF8 preimage and independently verifies both paths.
Network-none/read-only/env-i execution never initializes a Saito application or
reads wallet options. Reference JSON contains only public synthetic information.

Missing reader produced compile RED23s, unresolved SaitoRegistry. Raw stdout is
retained in ignored scratch; compiler stderr appeared in the tool result but was
not included by the initial stdout-only redirection. Do not describe it as a
complete merged stderr log. Later build captures include both streams.
Minimal bounded strict reader validates exact identifier/owner, expected registrar,
block-id/hash proof and signature. An empty array alone means currently available;
unavailable/malformed responses throw. The server lc field is separate unsigned
chain-state metadata, not signed finality. Focused first-case GREEN45s, one test,
zero failures/skips. No Android implementation or native-screen proof yet.

Next one tracer requires exact independently serialized zero-payment registration
bytes, with Registry/register/identifier at top level and published registrar
first, wallet change second. Pure WasmTransaction serialization plus WASM/Elliptic
signature verification generate the reference; this is not a complete initialized
browser wallet or chain-inclusion proof. Native social output order cannot be
assumed to match Registry. Missing registration path compile RED pending.

Earlier read-only live research in the contract observes working Archive/key-map
reads but {err:"no response"} namechecks on both approved endpoints. Do not
interpret that as an available or owned name, claim a connectivity cause, enable
block production, change a node or broadcast a registration. Optional UI must
preserve normal feed/posting/Chat use throughout unresolved registration.

Registration tracer compile RED23s, missing registration path, followed by
focused GREEN47s, two cases. Existing social create retains exact self-first
behavior. The new internal directed constructor puts registrar before self and
shares the existing zero-payment serialization/signing implementation. Registry
registration preserves case, validates1–45 ASCII alphanumerics and adds @saito.
No spend, fee, on-chain confirmation or actual live ownership follows.

Next one tracer restores the independently serialized directed request and
requires its exact original bytes/envelope. Existing restore requires first output
equal input author; the registrar-first reference may expose this assumption.
Native RED pending; do not change restore until observing that test outcome.

Directed restore tracer RED28s: three cases, one IllegalArgumentException at
SaitoTransaction.restore:94, the exact first-output-equals-author requirement.
Merged stdout/stderr and failing XML retained in ignored scratch. Minimal remedy
requires an own output anywhere in the bounded output list instead of first.
The one zero-valued input, all zero-valued slips, normal transaction kind, bounds,
indices and verified signature remain required. No money-transfer support added.
All52 core cases plus debug/test assembly GREEN2m18s, zero failures/skips. Old
independent social/root/reply/hop vectors still pass unchanged. New Registry
request matches independent WASM bytes and its exact original restores. Native
username UI/persistence and final runtime/cross-client acceptance remain unproved.

Core checkpoint2f1ff8e passed both independent review axes with0 actionable
findings. First native tracer5c57cb0 compiles (debug/test assembly1m16s), but no
UI exists yet. Focused Android10 run38113351395 is dispatched for the expected
missing Settings action RED; push source checks38113351503 are separate. The
tracer requires a real authenticated Archive read, exact Registry namecheck,
visible unavailable message, unchanged public wallet and zero broadcasts. Its
PNG/XML failure capture is inside the Activity scope, preserves primary errors
and makes no atomic observation claim. Controlled Registry responses do not
increment Archive-only readiness counters; the new callback precedes the final
Archive callback to preserve old trailing lambdas. New focused10/17 scopes and
full inclusion have independent Standards/Spec reviews with0 actionable findings.

Focused Android10 run38113351395 actual native RED1case/1failure/2.608s at
UsernamePreviewTest.kt:48, missing Saito username action. Build/core/debug/test
assembly1m38s passed; push source38113351503 passed separately. Original failure
PNG individually viewed and XML inspected show Settings/Server/both choices/Done,
with no username action. Raw output/PNG/XML retained verbatim at
docs/evidence/ticket18/first-unavailable-red. Later flow assertions were not reached
and are not claimed as passed. Minimal optional screen now in development;
first local compile was deliberately interrupted after noticing the peer request
interface takes/returns UTF8 bytes, then corrected before restarting compilation.

Minimal screen6bd3cb1 debug/test build2m7s passed. Independent reviews for
5c57cb0...6bd3cb1:0 actionable Standards/0 actionable Spec. First native GREEN
Android10 run38113786054 one case PASS5.465s: real unavailable namecheck,
visible error, enabled New post after Back, same public wallet, zero broadcasts.
Original success PNG individually viewed, separate XML inspected, retained
verbatim with raw log/hashes at docs/evidence/ticket18/first-unavailable-green.
Posting is accessible; this case does not actually send a post or exercise Chat.

Next tracer00bbead requests a registration after a successful empty namecheck,
expects truthful pending state, preserved exact request after Activity recreation,
read-only reopening and identical frame on explicit Retry. Focused10 run38114016651
awaits expected missing Register name RED. Activity recreation is not actual
process death; real interruption remains later acceptance. Writes target only a
controlled loopback peer, never either production backend.

Pending tracer actual RED Android10 run38114016651:2cases, unavailable casePASS,
pending case fails at missing Register name, line89, total8.319s. Original failure
PNG individually viewed and XML inspected show successful availability with no
Register action. Verbatim evidence retained at first-pending-red. Later pending/
recreation/exact-retry assertions were not reached. Minimal request storage/state
now added; debug/test assembly2m6s passed before a two-line synchronous busy guard
addition (the next hosted build verifies that addition). One encrypted immutable
request is validated against author/registrar/module/name and saved/verified
before send. Reopening is read-only; Retry first rechecks ownership and only sends
the original candidate when still available. Native GREEN and review pending.
No actual process-death, identity replacement, proof-cache or name-display claim.

Both review axes for6bd3cb1...11348ee found one fixture selector issue: a disabled
OutlinedTextField exposes its value without SetTextAction. Correction5bc5de4
checks exact EditableText NativeCase7 and disabled state in both phases; reviewed
with0 residual findings. Original run38114449227 on11348ee retained the actual
failure: unavailablePASS, pending reached one emitted request/exact message/
pending status before failing SetTextAction lookup at line102, total2cases,
1failure/8.549s. Original failure PNG individually viewed and XML inspected show
locked NativeCase7, Retry registration and pending message. It did not reach
recreation/retry or frame-report persistence, so those remain unproved. Corrected
focused10 dispatch on5bc5de4 pending; no production change or weakened value gate.

Corrected5bc5de4 focused Android10 run38114695697 PASS2cases/8.858s, source/core/
debug/test build37s. Pending exact locked name survives Activity recreation;
reopening performs no broadcast, explicit Retry emits identical original frame,
same public wallet. Actual captured frame independently verifies with pinned
WASM0.3.2/hash + Elliptic6.6.1: transaction/routing signatures, one zero input,
registrar-first/self-second zero outputs and exact detached envelope/top-level
Registry message. It uses the generated emulator wallet, whose public identity
and signatures are safe to retain. No namespace ownership/live inclusion follows.
Raw log/PNGs/XML/public frame/independent report retained at first-pending-green.
Both pending PNGs are byte-identical320x640, hash46dca2f6...e93014a0. First
pending/full unavailable images individually viewed; reopened-path tool display
was truncated despite byte identity. All three XMLs inspected. File identity
establishes identical original pixels; no renderer/capture-timing cause claimed.

Next tracer5c07260 uses the independent public unsafe1/2 proof to require exact
confirmed name display, then cached-name recovery after unavailable checks with
zero broadcasts. The explicit emulator-only fixture authority is not implemented
yet and never implies actual Saito namespace ownership. Focused10 run38114940866
awaits its native RED. Current production still trusts the published registrar.

Confirmation tracer actual RED38114940866 on5c07260: three cases, existing two
PASS, confirmation times out at owned status line160; total32.8s/one failure.
Original PNG individually viewed/XML inspected show unavailable response. The
published authority correctly rejects the synthetic scalar2 proof; no ownership
or cache-recovery assertion reached. Verbatim log/PNG/XML with hashes retained
at first-confirmation-red. Sensitivity fixac30571 additionally requires a real
namecheck before and after recovery; review reports0 residual findings.

Minimal confirmation implementation now compiles: debug/test assembly2m9s.
Production retains the published registrar. An explicit debug-emulator loopback
fixture can use only the independent public scalar2 authority. Verified own active
proofs are saved encrypted with source/time metadata and revalidated when loaded.
Reopening checks read-only, retains the saved name during unavailable checks and
never describes an unavailable check as current ownership confirmation. Native
GREEN, review and actual-send confirmation remain pending. Other wallet-import,
process-death and name-display acceptance remains incomplete; #19 stays open.

Both independent review axes forac30571...3ae7477 report0 actionable findings.
Focused10 run38115491755 on3ae7477 passes3cases/13.224s, source/core/debug/test
build1m25s. Exact active verified name is displayed; recreation performs a new
actual unavailable Registry read, retains the saved full name without a current
ownership claim, zero broadcasts, same public wallet and enabled New post after
Back. Root individually viewed both confirmation/recovery PNGs and inspected
separate XMLs. Verbatim originals/log/hashes retained at first-confirmation-green.
This is an explicitly bounded synthetic authority, not actual namespace ownership
or process death. #19 remains open.

Next tracercb6ffd5 requires actual compatible registration followed by signed
proof, then an empty read after recreation without resend and own-profile full
name/public-key display. Local debug/test compile1m7s preceded two bounded
fixture corrections: existing Your profile label and explicit zero-publication
assertion before Register tap. Both fixes reviewed with0 residual hard findings;
localized wallet-fixture duplication is a review-stage refactor after GREEN.
Focused10 run38115737996 dispatched; expected absent-profile-name RED pending.

Actual profile-name RED38115737996 oncb6ffd5: four cases, previous threePASS;
new flow confirms the actual unsafe2-directed request, verified ownership, then
new empty read after recreation with saved knowledge/no resend. It times out at
full profile-name line256, total39.573s/one failure. Source/core/debug/test54s.
Root individually viewed registration/failure PNGs and inspected separate XMLs;
profile still shows Anon-jesTu2 and unchanged public key. Raw originals/hashes
retained at first-profile-name-red. No final profile acceptance reached; actual
synthetic request frame was not retained at that source. No independent frame/
receiver acceptance, namespace ownership or actual process-death claim follows.
Minimal off-main-thread revalidated cached profile label is now being compiled.
A common registrar context retains published production authority and propagates
only the already-bounded debug fixture authority. No network lookup delays the
cached identity label; an unreadable/absent cache keeps the existing fallback.

Profile-label debug/test compile2m14s PASS. The new captured actual registration
frame report is written after its in-scope screenshot creates the evidence
folder, so it does not depend on test ordering. Hosted build/runtime will verify
that bounded capture-order correction. Existing profile history and public-key
rendering stay unchanged; only the header uses the verified cached label.

Both review axes for8b18b1f...eecb19a report0 actionable findings. Focused10
run38116194012/eecb19a PASS4cases/17.036s; source/core/debug/test1m29s. Actual
registration receives the independent ownership proof; after recreation a new
empty check retains saved knowledge and cannot resend. Own profile displays the
full identifier and unchanged public key. Root individually viewed both original
registration/profile PNGs and inspected their XMLs. Retained verbatim with raw
log, actual frame and independent/pinned receiver outputs at first-profile-name-green.

Actual unsafe1/2 native registration frame independently verifies transaction
and route with pinned WASM/Elliptic, hashc9f6e6af...52f2d5b1. Explicit verifier
synthetic-registrar mode requires those identities; published-default prior
pending specimen still PASS. Both pinned Registry bodies accept the verified
request under explicit unsafe2 local authority and produce proof matching the
independent vector. Nonzero-conf/relay/wrong-recipient/duplicate gates pass;
constructed namecheck/key-map queries and original keychain display bodies
return the full identifier. TypeScript signatures alone are removed for the
keychain VM. These are in-memory adapters, not a real browser wallet, served DOM,
chain inclusion or captured native read frames. Email hash overwrite is observed
without relaxing proof verification. New helpers/evidence review pending.

Next tracer: native wallet import must warn about the outgoing wallet's verified
name/pending Registry work. Its cache/request cannot describe a replacement
identity. Actual process death, invalid/unavailable responses, broader shared
names, public Android reads and final full10/17 acceptance remain incomplete.

Both review axes for the frame/receiver helpers and retained profile GREEN
report0 actionable findings. Root and both reviewers independently reran the
synthetic frame, published-default pending frame and pinned A/B receiver helper
read-only/network-none/env-i; all PASS. Original PNG/XML/log/hashes match.
Review-stage refactor centralizes identical unsafe-wallet replacement/restoration
for the confirmed fixtures without changing their acceptance assertions.

Next one native tracer previews a real provider wallet import after a signed
namecheck. It requires the outgoing verified full name, incoming public key,
unselected replacement-risk acknowledgement, disabled confirmation and unchanged
outgoing wallet/name after cancelling, with no broadcasts. Its document contains
only public unsafe scalar2 fixture data and is created on the isolated emulator,
never copied from a live wallet. Existing positive drawer-closed/file/returned
preview observations are reused; no ambiguous provider-title selection gate.
Expected absent-native-name warning RED pending.

Import-preview tracer and wallet-fixture refactor debug/test compile1m14s PASS;
actual native RED remains pending. No production implementation changed.

Import tracer run38116827086 on11e92b9 failed during fixture setup, before
Activity launch: the target instrumentation process could not create the
separate test-provider files directory. Four other cases passed; five cases
total in20.776s. No import acceptance assertion or screenshot was reached.
Original raw output/hash is retained at import-fixture-uid-failure. Fixture
provisioning now uses host adb run-as for the provider UID, with only public
unsafe scalar2 data. Both review axes inspected24d5705; Standards found one
cleanup exception path that could replace the primary failure. Correction
a6a4faa wraps the entire shell cleanup and has zero residual findings.
Focused Android10 run38117224099 tests24d5705, so its result does not verify
that later cleanup correction. Intended missing-name RED remains pending.

Corrected import fixture run38117224099 on24d5705 reaches the real provider
selection and verifies the incoming public key, then fails at missing
Saved name: NativeCase7@saito (line298). Four other cases pass, total28.851s.
Root individually viewed the original PNG and inspected its XML; outgoing
scalar1 remains active, with no saved-name warning. Later acknowledgement,
confirmation and cancellation assertions were not reached. Original evidence
is retained at first-import-name-red. Minimal implementation now reads and
revalidates the outgoing native name cache on IO and merges only that name
into existing replacement risks. Unreadable cache keeps unknown-work warning.
Native GREEN and code review remain pending.

Outgoing-name implementation3ab2ee5 compiles debug/test in2m8s and both
review axes report zero actionable findings. Android10 run38117628397
passes the import warning, selected key, unchecked acknowledgement, disabled
confirmation, unchanged wallet and named-profile cancellation assertions.
It fails at the final peer health check because the fixture assumes field4
on a supported profile-history Archive request (ControlledFeedPeer.kt:116).
Five cases, four passes, one failure in29.444s. Root individually viewed both
original PNGs and inspected XMLs. Verbatim evidence is retained at
import-profile-fixture-failure. Bounded fixture correction supplies the
existing explicit empty Archive callback; no app change or whole-case GREEN
claim follows from this failed run. Corrected runtime is pending.

Android10 run38117893858 on89d76a2 passes the complete outgoing-name import
case, including cancellation, original wallet/name, zero broadcasts and peer
health. Root individually viewed its original PNG and inspected XML; public
keys are offscreen in this scrolled capture and proven by runtime assertions.
Individual-case GREEN retained at first-import-name-green. Both axes reviewed
the prior Archive fixture correction with zero actionable findings.

The whole run has five cases, four passes and one unrelated capture failure
in35.446s. Unavailable-case Compose error assertion is reached, but Android
hierarchy capture cannot find it; original PNG/XML show keyboard occupying
the lower screen. The earlier Compose visibility versus later capture cause
is not established. Later wallet/no-broadcast assertions of that case were
not reached. Verbatim evidence retained at unavailable-ime-capture-failure.
The bounded correction scrolls the error into view before asserting display
and capturing. No app change or whole-suite GREEN is claimed.

Review-stage shared SAF helper and unavailable-error scroll fix both pass
independent review axes. New pending/import tracerf9421aa runs actual
Android10 RED38118322693: six cases, five passes and one missing pending-work
warning, total31.191s. It reaches one OWNER request, OFFICIAL empty editable
name/no Retry/no rerouted send and the real different-wallet preview. Later
cancellation/return/retry is not reached. Root individually viewed failure
and corrected unavailable PNGs, inspected XMLs; original evidence retained
at first-pending-import-red. The unavailable case now fully passes. Profile
shared-helper callers remain for applicable regression checks.

Spec review identified unrelated work could satisfy the warning and unchanged
send count alone does not prove reconciliation. Correctionf01211f positively
rules out unrelated public-fixture work, checks persisted backend choices and
an actual official Archive read, and requires an increased matching owner
namecheck before Retry. Residual Spec review is zero findings; those guards
are not verified by the older-source RED run. Minimal app change now reads
Registry requests for both backends on IO, treating unreadable state as unknown
work and a matching verified cached name as previously confirmed work. Native
GREEN, actual process death and remaining ticket acceptance are pending.

Pending-risk implementationdd90e40 passes focused Android10 run38118600472:
all six cases in36.48s, including the newer sensitivity guards, actual official
Archive read, persisted backend selections and new read-only OWNER namecheck.
The private request stays with OWNER; import preview warns while on OFFICIAL;
cancellation keeps the original wallet, and explicit OWNER Retry emits identical
bytes. Root individually viewed the original pending-preview PNG and inspected
XML. Scrolled addresses are established by runtime assertions, not the bitmap.
Verbatim evidence retained at first-pending-import-green. Both code axes have
zero source findings; Standards caught a stale handoff status, corrected in
this checkpoint. Shared username picker/error scroll pass; profile caller
regression, actual replacement/discovery and process death remain incomplete.

Actual-process recovery tracer8eae1ad prepares one controlled request, captures
a public checkpoint, fsyncs it and kills the native PID. Its recovery method
never resets wallet/backend/request, requires a fresh Process.myPid(), a real
read-only namecheck and no automatic send, then an exact explicit Retry.
The host verifies the old PID is absent and the checkpoint remains identical
except the recovery phase/PID. Both axes initially report zero findings; no
actual process-death proof is established by that source review.

The root caught an invalid extra host requirement: the recovered PID was
required to remain running after instrumentation finished. Android10's
[finishInstrumentationLocked implementation](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-10.0.0_r1/services/core/java/com/android/server/am/ActivityManagerService.java)
stops the target on completion. Correctionce9740a keeps the actual native
pre-completion PID assertion/checkpoint, validates its positive distinct ID
and removes post-completion liveness. It also waits for enabled Check name
before typing and preserves primary failures even if diagnostic storage fails.
Current focused10 run38119168208 supersedes38119056454 under the existing
same-scope cancellation policy. Both correction review axes report zero actionable findings. Actual Android10
run38119168208 on ce9740a passes six ordinary cases in44.07s and cold recovery
in7.033s. Host confirms old PID4222 absent; native recovery records PID4324
without resetting wallet/backend/work. It performs a real read-only namecheck
with zero automatic broadcasts, then explicit Retry emits the exact original
frame. Root individually viewed both PNGs and inspected XMLs. Independent
pinned WASM/Elliptic verifies the actual published-registrar frame/routing
signatures. Cold PNG renders inconsistently across root/Spec and Standards views; XML and
runtime assertions support recovery, without a consistent bitmap or proven
rendering-cause claim. Verbatim public evidence is in first-process-recovery-green.
Controlled peer only; no live namespace, power-cut or physical-device proof.

Existing-wallet discovery tracer41f93e7 is actual RED38119739548 at missing
profile name (six previous pass,51.71s); original retained first-discovery-red.
Minimal profile-only lookup e162fce passes its complete discovery journey in
38120023255, but whole seven-case run fails another unavailable native capture
after Compose visibility (six pass,42.417s). IME covers error in captured
PNG/XML. Discovery PNG renders only status strip in root; XML/runtime prove
name/address, no consistent bitmap claim. Retained first-discovery-green.
Both source axes reviewed; Spec identifies verified name lost on cache-save
failure and Standards duplicated namecheck/decoder. Next tracer obstructs
only the public name-cache path and must preserve verified mounted name with
a truthful save warning, then recover after obstruction removal. Bounded
unavailable fixture correction hides only positively observed emulator IME.
Actual-process stage was not reached in these failed ordinary runs.

Local-cache tracer ead23fd runs actual RED38120361760: seven cases pass,
newcase observes real namecheck/obstructed name-cache path then loses verified
label (eight cases,57.54s). Root viewed original anonymous profile PNG/XML,
retained first-name-save-red. Corrected unavailable flow fully passes. Added
repair sensitivity requires recreation with unavailable namecheck and cached
name; not present in older RED. Minimal correction retains verified mounted
label plus generic save warning as one settled result even after cache failure.
Review-stage RegistryLookup shares bounded strict namecheck/decoder between
registration and profile discovery. Source review/native GREEN pending.

Android10 run38120689654 on cf18e3c passes seven cases and reaches the new
verified mounted name/save warning/public key/no-send and repaired fresh lookup
with no warning. After unavailable proof is configured and Activity recreated,
it fails the invalid home-only Your profile gate at line115 (eight cases, one
failure,54.28s). FeedNavigationMemory preserves selected Profile. Later cached
assertions and process stage are not reached; no whole-case GREEN. Root viewed
all three original PNGs/XMLs: error shows named profile/warning/key, repair and
recreation show named selected profile without warning. Durable originals in
name-save-recreation-fixture-failure. Bounded test correction waits for preserved
Profile and retains cached-name/public-key/no-warning/no-send checks. Both prior
source axes pass; corrected runtime acceptance remains pending.

Corrected Android10 run38120998143 on c91877c passes eight ordinary cases in
38.502s and actual cold-process recovery in3.283s. Name-save failure now retains
verified name/generic warning/public key without a transaction; repaired fresh
lookup saves, and recreation with unavailable proof reads restores the cached
name without warning. Final no-send/identity/peer-health assertions pass. Root
individually viewed all three PNG/XML pairs; cached XML has independent profile
loading alongside the restored name. Actual process stage kills PID4339 and
recovers PID4426 with only phase/PID checkpoint changes, original wallet/backend,
read-only namecheck and exact explicit Retry. Independent pinned WASM/Elliptic
verifies the actual published-registrar frame. Raw evidence/derived report are
retained first-name-save-green; current process bitmaps are not claimed here.
Both prior bounded source axes pass, latest evidence audit pending. #19 remains
open for shared names, Settings-first discovery, actual replacement and remaining
negative/persistence/public-readiness/final full acceptance.

Shared-name tracer b744701 uses the independently signed scalar1 post under a
separate scalar2 reader. Android10 run38121392902 passes eight cases and fails
at missing NativeCase7@saito in the feed (line58, total63.65s). Root and Spec
individually inspected original PNG/XML: exact post body and anonymous author
are visible. Later profile/conversation/reply and final identity/no-send gates
are not reached. Originals retained in first-shared-name-red. Subsequent test
corrections bind the label to its exact independent post, scroll from feed
readiness, accept unrelated key requests with empty hints, and scope profile
labels beside full public keys. Those corrections are not in the older RED.

Shared labels now use a wallet/backend/registrar-scoped, cache-first directory
and bounded visible-author query batches. Feed, profile posts, conversations,
reply context and quote originals use the same verified label. Source review
identified duplicate failed-save retries and permanently claimed unavailable
lookups, including key-map timeout terminating the worker. The correction must
retain one in-flight claim, release unsuccessful claims, distinguish request
timeout from parent cancellation and permit a fresh mount to retry. Runtime
acceptance of this shared change remains pending; quotes need their own tracer.

Shared source71bc703 passes its complete feed/profile/conversation/reply journey
in Android10 run38122217674, eight of nine cases pass in43.727s. Correct scalar2
reader, authenticated keys and zero broadcasts pass. Root individually viewed
shared feed/conversation PNG/XML; independent reply loading remains visible.
The cache-save case fails line164 after a fresh namecheck is observed, expecting
its previous warning gone before proof/save settlement. Original failure
PNG/XML retain name/warning/key. Durable shared-name-green-repair-fixture-failure.
Next fixture waits boundedly for warning disappearance and asserts the scoped
name before the unchanged later unavailable-proof recreation/cache/no-send gates.
No app fix or eventual-clear/runtime-cause claim is inferred from this failure.
Process stage was not reached. Settings-first tracer4471601 is reviewed on both
axes but has no runtime result or implementation yet.

Android10 run38122656399 on0f276bf passes nine of ten in76.347s. The corrected
cache-save case fully passes settled repair and unavailable-proof cached
recreation; root viewed repaired/cached PNG/XML individually. Only Settings
tracer fails line106 with the blank optional form, viewed in original PNG/XML.
Durable first-settings-discovery-red. Later Settings proof/no-send/identity
checks and native process stage were not reached. Minimal implementation adds
own-key hint and signed exact ownership discovery only when no existing request
or name cache needs reconciliation. Optional screen waits truthfully; lookup
errors never mean availability. Helpers wait for enabled native actions before
typing. Cold recovery now explicitly requires namecheck, rather than any key-map
request. Build/runtime acceptance remains pending.

Settings correction source review passes both axes. Local Gradle reports
successful debug/test assembly2m15s; wrapper exit143 after completion remains
unexplained, with no compile failure and no surviving build container. Hosted
verification must establish clean build/native acceptance for this candidate.

Settings implementation585074d passes Android10 run38123194656:10 ordinary
cases48.452s plus actual cold recovery1case5.216s, PID4569 to4678, same
wallet/backend, real read-only namecheck, zero automatic publication and exact
explicit Retry. Independent pinned WASM/Elliptic verifies the actual published-
registrar frame. Android17 run38123195572 passes9/10 in88.834s; Settings discovery
passes, shared-name initial scroll fails while feed is Loading posts with disabled
Refresh. Root individually viewed both Settings PNG/XML and failure PNG/XML.
Cold10 PNG only shows status strip; recovery XML/runtime support the claim,
without a consistent bitmap or established capture cause. Durable originals/report:
docs/evidence/ticket18/settings-discovery-green-feed-readiness-failure.
The next bounded fixture waits for enabled Refresh before unchanged independent
post/name assertions; no app change or Android17 GREEN claimed. #19 stays open.

Next replacement tracer extends the verified-name preview/cancel flow with actual
SAF replacement to public scalar2 using a fresh controlled zero-balance snapshot,
explicit skip/acknowledgement/confirmation, clean incoming profile/Settings, new
own-key lookup/handshake, recreated saved identity and zero broadcasts. Only
known fixture identities may be restored in cleanup; all owned cleanup attempts
preserve the primary failure. First debug/test compile passes1m19s before bounded
review cleanup/sensitivity corrections; hosted build/runtime still pending.
No actual replacement or new discovery acceptance is claimed from source alone.

Independent-proof negative regression passes focused core5cases/0failures/0errors
in29s. The new case distinguishes [] from malformed/unavailable/trailing/multiple
records; mutations of the independent identifier/key/bid/bsh/signature/signer
reject, synthetic proofs never validate against production authority. Unsigned
lc0 marks a valid owner inactive, lc2 rejects. Existing parser implementation is
unchanged. This is core protocol evidence, not native UI or live Registry readiness.

Android17 bounded readiness8573f9c run38123932419 PASS10cases77.299s plus
actual cold recovery1case3.601s, PID4945 to5023, unchangedwallet/backend/frame,
real read-only namecheck, zero auto-send and exact explicit Retry. Independent
pinned WASM/Elliptic verifies actual published-registrar frame. Root individually
viewed shared feed/conversation/process PNG/XML. Root cold PNG onlystatusstrip; both reviewers
render fullpending from the same original. XML/runtime establish recovery, no
consistent cross-reviewer bitmap or capture cause claimed. Durable first-android17-username-green.
New actual-replacement and quote-name tracers are absent from this GREEN.
Quote tracer now binds exact name/text to both the original card and the outer
Current commentary retweet post, keeps the outer author's anonymous identity,
actual namecheck/handshakes and zero sends. First compile passes before this
bounded target-scoping correction; corrected hosted build/runtime pending.

Actual replacementfa4e6b0 Android10 run38124110877 PASS10cases53.748s plus
unchanged cold stage3.306s. Real SAF cancel preserves outgoing verified name;
explicit zero-balance skip/acknowledgement/confirmation activates scalar2. New
own-key lookup/handshake, clean anonymous profile/Settings, recreated incoming
wallet and zero sends pass. Root individually viewed profile/recreated-wallet
PNG/XML; Settings/network assertions are runtime evidence. Durable
first-actual-replacement-green. No incoming registered-name restoration or
pending-candidate actual replacement proof yet, and no later quote tracer claim.

A real cached-backup safeguard defect is reproduced: valid45ASCII@saito names
were dropped by the old16-character alias filter, so outgoing replacement could
omit their name warning. Core actual RED11cases/1null-vs-name failure, then
minimal exactcompatible-name acceptance preservesbasename/legacyaliases/key
matching and options. Full core55/0/0/0 GREEN51s, both source reviewaxesclear.
Durable long-cached-name-risk retains RED/GREEN logs/XML/counts. Cached risk is
not ownership; affected native import regressions remain for final acceptance.

Quote sourceac9ce8c Android17 run38124583775 PASS11cases99.966s. Exact outer
commentary/card-bound original name and independent signed text pass, with
anonymous outer author, matching namecheck/readerkeys/zero sends. Verified-name
actual replacement also passes17, clean incoming identity/Settings and recreation.
Root individually viewed quote/profile/recreated-wallet PNG/XML; outer anonymous
label offscreen established by runtime. Durable first-quote-name-green. Unchanged
cold stage4.323s hosted PASS, no new independently audited PID/frame claim.
New pending-replacement tracer compiles2m11s before bounded navigation/cleanup
review fixes. It keeps original-owner/backend exact retry before actual replacement,
then requires incoming clean Settings onbothbackends/recreation, incominghandshakes
and unchanged OWNER2exact/OFFICIAL0frames. Corrected runtime still pending.

Named incoming-wallet tracer now performs actual SAF scalar2→scalar1 replacement
from a wallet-only public fixture without an imported name hint. Feed is disabled
and pre-import Registry queries must be empty. Optional Settings must discover
its own key hint, verify signed ownership, show no Register/Retry, recheck after
recreation, keep incoming wallet and emit zero transactions. Both bounded source
reviews clear; debug/test assembly1m20s passes. Controlled runtime pending.

Pending replacementf800e9f Android10 run38125102237 PASS11cases58.498s.
Original OWNER read-only reconciliation/explicit identicalRetry are preserved,
then actual SAF replacement keeps incoming Settings clean onbothbackends and
afterrecreation, both incominghandshakes/savedwallet pass, OWNER2exactoriginal
frames/OFFICIAL0 remain. Root viewed emptyOFFICIALform PNG/XML; othergates runtime.
Durable first-pending-replacement-green. Coldstage3.794s hostedPASS noextraaudit.
Opt-in native public Registry probe now measures approved endpoints with real
authentication/keymap and known/unknown namechecks, publishedverifier, truthful
unavailable outcomes and no submit/registration/export path. First compile1m27s
before bounded review fixes: report per-backend failures without skipping the
other endpoint; preserve primary errors during diagnostic export. Corrected
hosted build/runtime pending. It measures availability, never infers service
readiness from a successful test or supplies live UI/ownership/chain proof.

Named-import source4ee7718 Android17 run38125286144 PASS12cases121.71s;
actual SAF replacement, no imported hint/pre-import Registry seed, optional
own-key signed discovery, recreation recheck/incoming identity and zero sends.
Root and both reviewers viewed original named-import PNG/XML: owner backend,
NativeCase7@saito and owned status. Four hashed originals/report retained in
first-named-import-green. Unchanged coldstage hosted PASS4.28s, no extraaudit.
Race tracer now checks available→explicit request→different valid owner, disabled
Retry, read-only recreation/no resend and anonymous own profile. Debug/test
assembly PASS1m17s before bounded empty-profile Archive fixture correction;
corrected hosted runtime and final full acceptance remain pending. #19 open.

Native public sourcea46759e Android10 run38125890744 PASS1measurement1.954s,
Android17 run38125892625 PASS1measurement4.093s. Both endpoints authenticate
and answer own-key hints; both known/unknown namechecks give21bytes rejected
by the published signed-record verifier. Unavailable onboth, not namespace-ready.
Five hashed public logs/reports/README in native-public-registry-unavailable.
Raw response bodies are not retained by this probe, so no error-string/cause
claim. No submit/node change. Live registration remains an external blocker.
Storage tracer obstructs the actual request path after availability, asserts
zero send on save error, repairs then sends once, corrupts encrypted persisted
work, fails closed without lookup/send, restores and reconciles read-only, then
retries identical bytes only on explicit action. Debug/test assembly1m17s PASS
before bounded feed-isolation and disabled-field selector corrections. Corrected
runtime and applicable full acceptance remain pending. #19 open.

Cumulative source review finds Settings failed-cache save followed by [] can
forget verified own identity and offer Register again. New isolated native RED
tracer now exercises this exact flow, using username_cache_error after obstruction
created AFTER initial load. No app correction/runtime is claimed yet.
Race17e9ab465 run38126094002 has12/13PASS146.555s; root PNG/XML show request-save
error before taken wait. Fixture writer2 collides with syntheticregistrar2 and
core outputdedup/requeststore2recipient validation. Corrected writer3 is distinct;
no production fix or corrected acceptance inferred. Four hashed failure originals
retained in race-fixture-recipient-collision. A verified conflict also needs an
explicit safe new-name path; cumulative Spec finding remains for the next slice.

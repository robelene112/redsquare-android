# Current implementation handoff

Updated2026-10-17. Owner authorizes continuing remaining issues sequentially.
#42 is CLOSED with complete server-code acceptance. #43 is CLOSED after native acceptance and verified signed26 publication. #44 is CLOSED after complete native acceptance and verified signed27 publication.
#16 is frozen at its accepted native checkpoint,
review base`0e577ab066e9849647dfd53c3766b96c5cb7af7b`. #17 remains open at its
frozen sourcec9b114a8 recovery checkpoint; actual playback is unresolved. #32 staysopen for configured
Google/device acceptance; its native dependency42 is now closed. Its earlier overall review base was dd89bc9108eb002bb260eee709c656cd45348940;
dependency#24 remains closed.
Preserve unrelated untracked `docs/research/NATIVE_GROUP_CHAT_CONTRACT.md`.

## Active native private Chat wake integration (#46)

Sole writer root; #46 assigned, native child33, closed45 prerequisite. Whole review
base4703fabeccb0ba86be0b7711ff546226b0f50a83. Existing whole33/32 Google/config/
deployment/physical blockers remain. Public beta27 unchanged; no node operations.
First scope is gateway-owned persistent signed encrypted-candidate observation,
opaque FCM, recipient-proof original retrieval and real native no-Activity worker/
local decryption/alert/receipts. Preserve private default/opt-in previews and44tap.
Wallet-local background Chat consent joins shared registration demand alongside
independent Social consent; disabling one must not unregister the other. Groups
remain the next34 slice. No live stream promises complete history or Relay-only
coverage. Parent's existing controlled peer/gateway/FCM/native seams apply.

First owned gateway tracer compiled/executed, actual RED1m6s at public HTTP bind:
private-chats category rejected400 instead of201. No observer/FCM/original fetch
assertions reached; Official loop unexecuted. GatewayChatObserver is explicitly a
no-op executable seam at this checkpoint, not implemented observation. Three
hashed raw/source records in docs/evidence/ticket46/gateway-first-red. No Android
source changes. No owned local build. Temporary work stays outside checkout at
/tmp/redsquare-private46-work-20261017. First gateway GREEN1m5s passes both logical backend loops through actual owned
propagation/opaqueFCM/thinrecipient-proofresolve/originalfetch/localfixturedecrypt.
Three raw/source hashes in gateway-first-green. Main --observe is not wired to
this new observer yet; Core client categories and Android remain unchanged.
No retry/second-stream event/process-death/larger-media claim from this first case.
The implementation keeps a bounded persistent owned peer and private ciphertext
cache (24h/128MiB/4096files) separate from the4MiB subscription journal; stream
gaps remain visible. Minimum source review remains pending.
Corrected27fd2916f clamps each new event to its already-retained original expiry
and wires independent per-backend Chat identities into explicit --observe mode.
All observers are signalled before waiting; HTTP-only mode still loads no ADC.
Complete local core/gateway/installDist PASS2m9s: gateway12/0across10suites,
coreUPTODATE114/0/0. Thirteen hashed records in gateway-minimum-full.
First gateway review32696b52e: Spec1retention wording; Standards1samewording
and1heuristic repeatedcipher-cacheIO. Corrected08c92dd79 identifies all eligible
new bindings first, saves the common original once, skips duplicate/irrelevant
state writes and uses an existing-cache fast path. Full cache parsing is reserved
for scheduled cleanup or capacity reclamation. Docs distinguish retrieval expiry
from physical removal after successful cleanup (may lag/fail/stopped). Corrected
local core/gateway/installDist PASS2m8s, gateway12/0in10suites/coreUPTODATE114.
Thirteen records in gateway-reviewed-full; corrected reviews both zero actionable findings.
Added28e1f24af same-stream coverage PASS2/0in6.590s: both logical backends,
first original repeated then a distinct second original over the same authenticated
connection; exactly two opaque requests and exact second resolve/fetch/decrypt.
Three hashed records in gateway-stream. No new RED or encryption interop claim.
No reconnect/media-capacity/process-death claim yet. Native consent tracer added to existing chat-alerts scope. Initial local compile
fixture type error corrected before runtime, not a behavior RED. Actual Android10
CI39057745908 at935c7787d fails first missing Settings Chat alerts when closed
choice after20s; five prior alerts PASS, no union assertions reached. Seven hashed
records in native-consent-red retain raw outcomes/PNG/XML/exact source metadata.
Fixture861a5b219 preserves original failures and guards restoration with an Activity
shutdown barrier; both correction reviews zero remaining findings.
Productionb2600cda6 adds wallet-local defaultoff background consent, new Settings
row/existing skippable Chat prompt callback and shared social/private category
union with a common Core/gateway category validator. Local build PASS5m10s,
core114/0 and gateway13/0across10suites, native/test Kotlin compilation. Thirteen
hashed raw/source records in native-consent-local. Bounded consent source reviews
and runtime GREEN39058689594 pending. No worker/private receipts implemented yet.
Next: complete consent GREEN/review, then native worker/receipt integration; final full SDKs,
both reviews and next signed update are still required before native46 acceptance.
Preserve unrelated untracked docs/research/NATIVE_GROUP_CHAT_CONTRACT.md.

## Accepted gateway session compatibility (#45)

#45 CLOSED, native server child of#32, which retains
its configured Google/device gates. Review base6a9b749ad16f91810334c369bbf29cd647a9cf0c.
No app/build change or production deployment. Public beta27 remains unchanged.
Existing controlled external gateway/peer/FCM seams apply.

First tracer reproduces competing gateway-owned connections under the pinned
Saito public-key API reply routing and peer-ID chain routing. The first handshake
is delayed250ms so the second authenticates first. Local RED49s, onecase/onefailure
7.538s: expected verified reply never reaches FCM within5s; two connections and one
Archive reply positively routed to the other socket. Owner binding only reached;
Official and later delivery/privacy assertions remain unexecuted. Raw log/XML and
source/upstream hashes in docs/evidence/ticket45/routing-red (3hashedrecords).
An earlier fixture compile error is not this behavioral RED; it was corrected
before the recorded run. No node or Google was contacted.
Minimum correction shares the chain peer with Archive for the same endpoint.
Standalone readers and the existing distinct-loopback fixture keep their public
contracts. Closing either shared reader cancels the pass session. Focused local
GREEN1m13s passes the same complete journey on both logical backend bindings.
Three hashed raw/source records in ticket45/routing-green. Source review7f7cefb86
Spec0/Standards1: valid trailing-slash aliases still opened two same-endpoint peers.
Strengthened alias RED1FAIL/4.711s reaches delivery but the final external socket
count expects1/observes2. No actual reply-loss claim from this alias run. Three
hashed records in alias-red. Minimum compares origins after removing their single
accepted trailing slash; all endpoint validation/deadlines remain.
Source CI39054176895 at7f7cefb86 SUCCESS(core/gateway/release/lint). The redundant
local broad run was explicitly stopped143 during lint after the review finding,
not accepted as a full run. Corrected final core/gateway/installDist is running.
Intermediate local core/gateway/installDist atbe8f821c5 SUCCESS2m6s, gateway11/0
in9JUnit suites; coreUPTODATE114/0/0. Twelve hashes in slash-full-green.
Spec0/Standards1 persists for accepted numeric-port aliases. Canonical correction
now compares GatewayPeer.endpoint on both origins, using exactly the same strict
validation/numeric-port serialization as the transport. The same Official journey
uses both leading-zero port and trailing slash; no extra campaign or deadline.
Canonical local core/gateway/installDist at5c5c333db SUCCESS2m8s, gateway11/0
across9suites, coreUPTODATE114/0/0; newtwo-binding journey1PASS/2.846s.
Source CI39055116089 exact5c SUCCESScore/gateway/debug/test/release/lint.
Canonical-full-green retains16hashrecords/allgatewayJUnit/CIcore+lint+buildlogs/
source+unchanged7clientobjects. Both source reviews5c reportStandards0/Spec0.
Final integrated evidence reviews8fe64f3f3 Standards0/Spec0; #45 verified CLOSED.
[Accepted checkpoint](https://github.com/robelene112/redsquare-android/issues/45#issuecomment-6238022008). No owned local build.
No actual Google/deployed-node/Android acceptance.

## Accepted native Chat alert presentation (#44)

GitHub44 CLOSED2026-10-17T17:24:38Z, native child33, related34.
[Accepted checkpoint](https://github.com/robelene112/redsquare-android/issues/44#issuecomment-6237553011).
Whole review base2fff4587ea9fb1261f1c9cdcb478dc68c81199fc stays fixed. Parents33/34
remain OPEN with genuine32 dependencies. Public beta27 is published and verified.
Both complete SDK runs, combined evidence and publication reviews pass.
No Google/physical Pixel claim. No owned local build.

Implemented: authenticated cached private/group original references bound to
wallet/backend; current membership/author/conversation policy; shared social/Chat
route generation and legacy SavedStateHandle key; existing mounted-draft guard;
wallet-local encrypted opt-in private previews with generic default, supported
text/captions, unsupported generic fallback and withdrawal on disabling. Group
content stays generic. Public destination URI/generation survive transient wallet
loading and window restoration, with resolved identity filtering/clearing and
consumption on selected-conversation exit or committed social handoff. Debug
emulator notification launch preserves the already resolved controlled endpoint.
Normal manual Chat recreation behavior is unchanged. Fixture protected restores
require positive Activity shutdown; alert cleanup stays independent.

Canonical packets below are under docs/evidence/ticket44. Each README qualifies
exact source, assertions reached, viewed originals and actual limits. All XMLs
parse and PNG signatures validate; raw originals remain hashed.

| Run / exact source prefix | Packet | Actual outcome |
| --- | --- | --- |
|39034915868 /3dbeefbd1|first-red/android17|FAIL1/1/48.07s, private destination after shade.|
|39035902031 /21f2a340c|private-route-green/android17|PASS1/0/35.712s, UI draft recovery alone not independent durable proof.|
|39036372375 /8df60df48|durable-route-green/android17|PASS1/0/34.179s, exact saved draft before Back, no candidate/statusNONE/automaticfalse.|
|39037078435 /4d04f79c5|preview-red/android17|FAIL2cases/1failure/71.8s, preview toggle absent, later privacy tails unexecuted.|
|39038086362 /505c7fd27|preview-green/android17|PASS2/0/41.822s, choice/recreate/text/caption/unsupported/disable/generic-next; no process death.|
|39038765595 /7b41131a5|group-red/android17|FAIL3cases/1failure/93.615s, exact group destination after shade.|
|39039743573 /a62e147ea|group-green/android17|PASS3/0/77.718s, group target, durable private draft, UI recovery and normal Groups re-entry.|
|39040413257 /442c98500|cold-red/android17|FAIL4cases/1failure/91.514s, Official cold target opens then recreation loses it; initial PI refresh uncontrolled.|
|39041093920 /bc3168854|cold-minimum/android17|FAIL4cases/2failures/95.112s, group return141 and cold first target126; cold cause unknown.|
|39041907742 /6c64ee7b9|cold-diagnosis/android17|FAIL4cases/1failure/87.052s, group fixed; cold pre-tap persistence/binding/policy pass, admission refuses before commit.|
|39042667158 /f4f20e0f8|cold-admission/android17|FAIL4cases/1failure/85.59s, overlapping idle/busy cold entries with no form, diagnostic Retry opens but original failure retained.|
|39043283518 /44f5a34b3|cold-green/android17|PASS4/0/89.02s, first Official cold target/recreate/durable draft/UIreturn/consumed recreation/identity/no outgoing. Reserved limits in README.|
|39044052719 /598dd89ff|group-cold-green/android17|PASS5/0/101.527s, Official group cold503/cache/recreate/privatework, retained stale alert refused after signed removal/Dismiss/consumed recreation.|

Source reviews found/fixed unsupported preview markup, initial group seed reuse,
transient-null wallet reset and missing fixture Activity shutdown barrier. Their
bounded fixes each reached Standards0/Spec0; this is not native acceptance.
Known-bad intermediate39039390627/64038 and39040935051/798 were explicitly
CANCELLED before native acceptance after review findings.

Latest39044052719 exact598dd89ffa4f65a86ef9b5ab9e3e3884d382d94b SUCCESS5/0/101.527s.
All five private/group/preview/no-Activity journeys pass. The final Official group
journey covers controlled503outage with cached target/window/private draft recovery,
then signed membership removal with original still retained, actual stale shade
tap refusal, durable draft and Dismiss recovery, consumed-target recreation,
exact wallet/backend and zero outgoing collections. Earlier private cold first
opening uses no diagnostic Retry. Core114/0/0/debug/test assembly pass.
61hash28XML in group-cold-green/android17; root viewed6group cold originals, other22
retained. Two PNG pairs byte-identical despite renderer differences; title crop
and truthful unavailable history/live status qualified. See canonical README.
Both whole-source reviews FIXED2fff4587e...598dd89ff are Standards0/Spec0.

Production source remains frozen at44f5a34b3. First full pair at9a37c19ba:
Android10 run39044590725 andAndroid17 run39044590715 both FAILED at existing
ChatMutePreviewTest331, waiting for the offscreen Official Settings backend row.
New Chat alert5cases passed on both; early completion16/JUnit56, actual63PASS
and1failure, four intentional process interruptions. Later full gates unexecuted.
Canonical full-first/android10 andandroid17 retain26hashed original records each.
Failure XMLs show Settings/Owner row, Official belowviewport; Android10 PNG viewed.
Fixture-only8113a0386 scrolls the existing backend options before clicking across
all same Settings selectors, preserving assertions and deadlines. Both bounded
source reviews report Standards0/Spec0. Focused chat-mute39046322617 at exact8113
SUCCESS: UI8/0/95.265s, actual policy process recovery1/0/12.708s (PID6796to7265),
restoration1/0/0.081s, core114/0/0. Canonical settings-scroll-green/android10
retains19hashes/inventory38XML38PNG. New alert-specific process death is not proved.
The second full Android10 run39046444313 at4f0 FAILED early at existing
SocialPushProcessRecoveryTest84: actual reply remained after coordinate swipe
and5s wait. Seventeen early passes/one failure; no deliberate death, restoration
1PASS. Full-second/android10 retains13hashrecords. Before/after PNGs are identical;
root viewed failure and parsed the same fully visible card. Cause beyond the
ineffective gesture is unproved. Fixture-onlyf7395a26a uses one UiObject2-bound
swipe, preserving observable assertions/deadlines, without retries or cancelAPI.

Focused social-push-process10 run39047234876 exactf7395a26a SUCCESS:
dismissal/death/recovery1/0/4.178s, restoration1/0/1.711s, PID3294to3636. Existing
autonomous scheduled post-death receipt and restoration1/0/0.765s also pass.
Dismissal-green/android10 retains33hashrecords, five archiveXML/PNG and three
selected pairs without new visual claims. Both bounded source reviews0/0.

Second full17 run39046446190 at4f0 FAILED later at existing private reaction
backend isolation, PrivateChatHistoryFixture478. Three dynamic backend.domain
selectors had been missed by the literal-only search (private reactions, posts,
transfer status). Same offscreen Official Settings choice in the viewed PNG/XML.
160PASS/1FAIL,36JUnit151,10intentional interruptions; later gates unexecuted.
Canonical full-second/android17 retains raw records and the explicit CANCELLED
concurrent10 run39047572445/d68, withdrawn after that fixture defect was found.
No acceptance or completed-count claim from the cancelled run.

Fixture-only16a0c1bc6 adds ordinary scrolling to those three dynamic selectors,
preserving every assertion/deadline. Both bounded source reviews0/0; README and
push configuration docs now describe current native launch/review/presentation
without claiming Google Chat transport. Production/build sources are unchanged.
Final SDK evidence, all at16a0c1bc6636983b806c93c1ed4443dddae3bfc4:
- Focused chat-actions17 run39048726834 SUCCESS10/0/78.258s, core114/0/0;
  dynamic-settings-green/android17 retains14hashrecords and four raw pairs.
  Namespace case uses both logical bindings on one owned peer. No new visual claim.
- Full Android10 run39048729211 SUCCESS409PASS plus1existingProfileassumption,
  96JUnitcompletions410reported/29intentionalinterruptions; core114/0/0, release/
  lint, full device/recovery/publicfeed/Chatreadiness/signed5to8/finaldiagnostic PASS.
  Full-regression/android10 retains245hashrecords,399fileinventory739XML873PNG.
  Root viewed7Chat alert originals; work-retained preview contradicts identical
  decoded composer pixels in positive private/refusal captures (helper retained).
- Full Android17 run39048730979 SUCCESS411PASS,97JUnitcompletions411reported/
  29intentionalinterruptions; same complete core/release/lint/device/public/tail gates.
  Full-regression/android17 retains247hashrecords,405fileinventory740XML877PNG.
  Root viewed7Chat alert originals; decoded restored-editor region matches private/
  refusal captures despite work-retained viewer preview. Existing private recovery
  PID10647to10727 preserves candidate/reaction/incoming/wallet digests and restores.
  Five unavailable optional diagnostics perSDK stay explicit.
Whole fixed2fff...04cda483e source/evidence bookend Standards0/Spec0.
Both full10 packet reviews at505a4a963 are0/0. Final combined evidence review atfc9f905c5 reports Standards0/Spec0.
NATIVE_ACCEPTANCE.md records both complete runs and limits. The full-regression/source-equivalence matrix proves63 matching production/build
objects across7roles,12identical final fixture objects and18fixture-only changed
files since the APK source. Prepared27 still has matching production/build objects.

Signed27/0.1.27 is published, source9a37c19ba. Wrapper0/GradlePASS
4m38s and independent certificate/package/min29/hash checks pass. APK SHA256
8b18899ad022491e5f808a63b0f4413e79098e781c6c018752748724c3c1d159; established
certificate unchanged. Nine hashed prepared records and source/fixture Git object
equality in signed-preview27-prepared. Production equivalence survives fixture-only
correction; do not rebuild/reserve a new version for test-only changes. Artifact
/mnt/storage/redsquare-android-artifacts/chat44-review27-prepared. Direct public
page/latest/immutable27/APK/hash/certificate/package/min29 checks PASS; immutable26
manifest/APK unchanged. Thirteen records in signed-preview27-published.
No owned local build. Same approved seams/180s new alert class/20s observations.
Original33/34 and real32 blockers remain unchanged. No Google or physical/process-
death claim from focused alert journeys. Publication bookend84ac82746 reports Standards0/Spec0;44 is CLOSED.
Continue remaining genuinely available work sequentially.
Temporary work stays outside checkout /tmp/redsquare-chat44-work-20261017; large
original artifacts /mnt/storage/redsquare-android-artifacts. Preserve unrelated
untracked docs/research/NATIVE_GROUP_CHAT_CONTRACT.md.

## Accepted native Chat GIF slice (#43)

Accepted implementation is #43, bounded native child of #31, review base
06e4ef27f55042f59bb79f3db9a97ebafeff4f78. Owner renewed continue-all authorization
after beta25. The accepted native #16 provider/renderer supplies its source
prerequisite; #31 remains OPEN with its #16 dependency and live-key gate intact.
No Android Giphy properties have been supplied. No repeated setup question.

Current production15dbae02405a18bd0e2a1c32938a31d35160d00e provides separate Chat
configuration, shared private/group picker, durable exact URL/query/caption and
credits, canonical img.img-prev HTML, reply-prefix preservation, encrypted work
storage and sent credit retention. Membership, identity/backend and explicit
Send guards remain. Native source and focused acceptance are not whole-child
completion or live provider acceptance.

| Checkpoint | Canonical packet | Actual result |
| --- | --- | --- |
| Initial private provider tracer | `docs/evidence/ticket43/first-red/android17` | 39022059808 at6141f5c2 FAILURE1/1/31.603s, missing Add GIF. |
| Provider failure/retry/cancel/reopen | `docs/evidence/ticket43/recovery-green/android17` | 39022580631 atdc320ec1 SUCCESS1/0/26.011s. |
| Selected-credit tracer | `docs/evidence/ticket43/selection-red/android17` | 39023335733 at04ef0dab FAILURE2cases/1failure/45.506s, missing creator/source. Later tails unexecuted. |
| Private selection/send/interoperability | `docs/evidence/ticket43/private-green/android17` | 39023907089 atc4433ebe SUCCESS2/0/44.372s,27hashes/6pairs. |
| Group selection tracer | `docs/evidence/ticket43/group-red/android17` | 39024586467 atfe8893b1 FAILURE3cases/1failure/47.604s, missing Add GIF. Group tails unexecuted. |
| Group selection/send/interoperability | `docs/evidence/ticket43/group-green/android17` | 39025226571 at15dbae02 SUCCESS3/0/60.283s,19hashes/4pairs. |
| OFFICIAL private queue/cancel/reconnect/Send | `docs/evidence/ticket43/official-private-queue-green/android17` | 39025987948 at7d8c3ac31 SUCCESS4/0/81.897s,25hashes/7pairs. |

Root viewed the named original PNG pairs and parsed their XMLs. Native GIF
motion assertions check two literal colors; credit captures sometimes show
only a thin scrolled preview. Actual private envelopes pass independent WASM
signature, pinned recipient Crypto and both Chat receivers/duplicate checks;
group envelope matches both pinned constructors/signature. A/B sanitization
and offline Chromium preserve exact URL/query/caption and decode owned16x16 GIF.
Reference helper hashes identify each revision. Initial cross-origin canvas
observer failure is retained; corrected GIF observer uses DOM/decode only.
Legacy photo output compatibility remains checked. No live delivery, browser
motion, routing-hop proof or actual process-death claim from these journeys.

Latest native checkpoint: exact91d8fefec Android17 run39027017976 SUCCESS6/0/
103.68s, canonical `docs/evidence/ticket43/queue-and-reply-green/android17`.
Both backend bindings now pass private/group selection/send; OFFICIAL private
and group queue/reopen/cancel/reconnect-silence/explicit Send pass. One private
reply/GIF journey preserves exact quote/reference/caption across freshActivity
and Send. Actual private quote/mentioned key, group constructors and A/B web
DOM/decode independently pass. All11XMLs parsed; root viewed5 named originals,
remaining PNGs retained without a new visual claim. No process-death claim.

Earlier group queue run39026557695 atf55920820 remains FAILURE5cases/4pass/1failure/
96.773s; its18hashes/7pairs stay in official-group-queue-red/android17. It failed
only the unscrolled displayed-image observer after reconnect; later tails were
unexecuted then. The85a69ff00 ordinary-scroll correction keeps all deadlines and
motion/assertions. No production change since15dbae02. Whole production reviews
atf55920820 and bounded tracer/evidence reviews through91d8fefec report0/0.
Shared provider/renderer plus #30 history/recovery need no expanded test matrix.
Local private/group/reply tracer builds PASS2m40s/2m5s/2m3s. No owned local build.

Final full Android10 run39027471407 at91d8fefec FAILED during the old private
process preparation:157PASS/1failure,37completion records,10 prior interrupted
outputs. Chat GIF6PASS56.728s. UiAutomator required-text capture at106/12 did not
find the pending message in the visible history viewport; first cached message
remained onscreen. No private process kill/cold-recovery or later tails executed.
RestorerPASS0.177s. Root2PNGs/2XML;58hashes in full-regression-first-failure/android10.
Fixture-only correction scrolls the exact pending message before both required
pre-death/cold captures, retaining all deadlines/original/PID/zero-write assertions.
New focused chat-history-process scope reuses the existing helper, full unchanged.
Local correction build37521 PASS2m1s; bounded Standards0/Spec0. No owned build.
Android17 full39027473052 also FAILED at the exact same preparation capture:
157PASS/1failure/37records/10earlier interruptions; GIF6PASS64.844s, restorer0.168s.
Both full attempts keep their actual failures,58hashes/2PNG+XML pairs perSDK in
full-regression-first-failure. Root viewed all4 originals/parsed4XML. No private
kill/cold recovery or later full tails are claimed. public_feed_read=true.
Focused correction10run39029516099 and17run39029618443 both SUCCESS at exact
0b43de73b5cdfc4f275a16c80b5fd7e6661a422f. Actual PIDs3341to3769 and3171to4558;
original/candidate/reaction/draft digests preserved with zero writes. Cold recovery
1PASS5.85s/12.591s; restoration1PASS0.342s/0.126s. Core114/0/0/source/release/lint
pass. Canonical process-viewport-green/android10/android17 has21hashes/5XML perSDK;
root viewed two pending-message originals perSDK, remaining3PNGs retained.
Local correction2m1s and bounded reviews0/0. No owned build, app unchanged.

Corrected full10run39029829620 and17run39029992811 both SUCCESS at exact
same e85076aaae54605fcf73ad9f424565a3dcffb46a, public_feed_read=true. Canonical
full-regression/android10/android17:404PASS+one existing Profile assumption /
406PASS;95/96completion records;29 intentional interrupted outputs each. Core114/
0/0, source/release/lint, complete application/recovery/public-read/update gates
pass. Chat GIF6PASS57.281s/58.695s. Private recovery PIDs6166to6529 and10460to10541,
with unchanged protected original/draft/reaction/wallet state and no writes.
235/236 hashed records,711/712 parsed XML entries,845/849 PNG-signature entries;
five optional diagnostics unavailable perSDK, explicitly qualified. Root viewed
three named Chat original PNGs perSDK; other captures retained without new visual
claim. Retain first full failures as failures. Whole-source review at7723a8972
Standards0/Spec0, including new development-preferences standard. Final combined
SDK evidence reviews atbbdb66236 are Standards0/Spec0. Signed26 is published and
independently downloaded/verified; immutable25 unchanged. Publication bookend
2fff4587e Standards0/Spec0, GitHub43 verifiedCLOSED at12:54:43UTC, comment6234126785. No rebuild required. Whole-child review base06e4ef27 remains fixed.
Read-only next-work preparation is in .scratch/chat33-native-preparation.txt;
The next bounded child44 is now assigned, covering private/group notification tap destinations and private preview opt-in,
linked to33/34 without changing their32 blockers or claiming Google transport.
That is original settled scope, not a new product interview.
Signed26 preparation and publication are accepted after full SDK evidence reviews.
Build17593 wrapper0/GradlePASS4m5s; no owned build remains. Independent APK
certificate/package26/0.1.26/min29/hash pass. Prepared sourcee8b337a30 is unchanged
production against both exacte850 full runs. APK SHA256
c7f4c4f00262f35a4a92ea2253b4e78989c0438751fb655df51a66b8627b17a7.
Published at https://davidengelen.nl/redsquare/, publisher0 and independently
downloaded APK verified. Immutable25 manifest/APK unchanged. Thirteen hashed
public records in signed-preview26-published. Public beta26 is current.
No real key, provider query, production writes or
node/account changes. Preserve protected untracked group contract.

## Historical accepted gateway checkpoint

#42servercode is complete: latestimplementationddfd7005, wholebase5a048524,
fullownedgateway10/0/0/coreUPTODATE/installDist, sourceCI39001786605 SUCCESS at
fad81a03. Finalintegratedreviewsb84b5903 Standards0/Spec0. [Canonical acceptance](../evidence/ticket42/FINAL_ACCEPTANCE.md).
Bookendc0f2da7e pushed; GitHub42 verified CLOSED at04:19:43UTC, comment6229558881.
#32 verifiedOPEN/nativeblockers0. #32 configured
SDK/Google/closed-app/Pixel and deployment stayopen. No account/IAM/node/hosting
change, beta24unchanged. No ownedlocalbuild, protectedgroupdocuntouched.
At this historical checkpoint the next app work was #16 below. The #17 native public-read checkpoint is
frozen with a visible provider check and no unchanged retry planned. Other
external inputs remain pending; no repeated owner questions or node changes.

## Frozen YouTube recovery checkpoint

Issue #17 remains open, fixed review base c0f2da7e. App sourcec9b114a8 keeps the
normal101/150 provider surface visible until retry/close, with separate native
Retry/external escape and truthful generic refusal copy. `key(browser)` remounts
the actual replacement on Retry. Source reviews Standards0/Spec0 after that fix.

- Original prearmed public34bcf2f7 run39002714443 FAILED1/1/49.627s, native150,
  no providerError text captured. No cause established by that attempt.
- Controlled REDed0afa19 run39003354073 FAILED4cases/1/68.276s at missing delayed
  explanation; other3pass. Later failed-method tails unexecuted.
- Corrected local c9 build75378 PASS3m31s, coreUPTODATE/debug/test. Earlier b36
  build46456 was deliberately retired143 before compiled-source correction.
- Exact c9 controlled Android10 run39004102308 SUCCESS4/0/22.074s; Android17
  39004103787 SUCCESS4/0/56.491s. Source39004102110 SUCCESS5m43s, JVMFROMCACHE,
  debug/test/release/lint. GREEN49hashes/14XML, rootviewed14PNGs. Android17
  explanation visibly retained; omitted bitmap fields expressly excluded.
- Changed actual public c9 run39004585910 FAILED1/1/56.741s at unchanged actual
  Playing wait81 after readiness+realPlay. Ordinary failurePNG/XML now visibly
  show YouTube's bot-check. CDP produced no providerError record; no underlying
  IP/cookie/attestation cause or physical behavior inferred. Rootviewed3originals,
  parsed2XML,14hashes. Later close/feed/no-write assertions unexecuted.

Canonical controlled packet `docs/evidence/ticket17/error-surface-green/` and
actual public `docs/evidence/ticket17/error-surface-public/android17/`.
No active #17 build/CI. No provider sign-in/header/origin/proxy change, account
request or node operation. No unchanged public retry. #17 actual playback stays
open, beta24 unchanged. Final native-public evidence reviews at0a11559b are Standards0/Spec0. This
source checkpoint is frozen; claim the next sequential source preparation.
## Accepted native Posts Giphy checkpoint (#16)

At the #16 checkpoint: sole writer root, assigned @me, native blockers0. Whole review base
`0e577ab066e9849647dfd53c3766b96c5cb7af7b`. Current app candidate7ad1742e includes
Android10 buffer fixeb840006 and published-credit retention. Both bounded source
reviews Standards0/Spec0. The earlier integrated Spec1 at a275 is resolved.
Corrected focused native flows now pass on Android10 and17 at19e1083d:
[83 hashed originals/outputs](../evidence/ticket16/corrected-green/README.md).
Android10 run39013271535 preview3/0/18.239s and posting6/0/26.229s; Android17
39013273144 preview3/0/49.985s and posting6/0/30.411s. Final full native
regressions now pass on both SDKs; see[the current checkpoint](../evidence/ticket16/NATIVE_REGRESSION_CHECKPOINT.md).

Owned local build59382 completed PASS3m28s: core test task UP-TO-DATE, debug and
test APK assembly. Log `.scratch/gif16-buffer-published-credits-build.log`.
Readiness-fixture test build73696 completed PASS1m49s. No owned local build active.
Signed beta25 at19e is published and independently downloaded/verified. Final
combined evidence reviews at c365a5f4 are Standards0/Spec0.
Its Gradle build completed PASS3m26s and final signature/manifest steps ran, but
outer exec3548 reported143. Independent APK hash/signature/certificate/package/
version/source checks passed; do not call that wrapper exit0. Logs are
`.scratch/gif16-beta25-build.log` and `gif16-beta25-independent-verification.txt`.
APK SHA2562eb5f4ffc021e4fc87672e25f7f5ca207b2d19d49a96453a5a422abe10d536c8,
version25/0.1.25, established certificate unchanged. Beta24 is retained unchanged.
Initial full Android10 39013781414 at19e failed before actual notification
death. Fixture-only5154e89b full Android10 39014622109 is FAILURE later at
the username initial-feed observation, after both social process checks pass.
Focused corrected process checks on Android17 39014623934 SUCCESS
(canonical card-process-green/android17). Full Android17 39013783536 SUCCESS at19e with approved
public feed reads:400PASS/95records/29interrupted outputs, no assumptions. Production is identical; source39013271917 at19e SUCCESS.
The Android-authorized Giphy key remains pending. Do not repeat its question or
borrow backend web keys. [Primary preparation](../research/GIPHY_SOURCE_PREPARATION_2026_10_17.md).
No real provider/key/account/node/live-write change. Beta25 is now current.
Protected untracked group contract stays untouched.

Implemented source: direct native picker with exact debounced searches,24 results,
retry/empty/rate-limit/length states, thumbnails, available credits and provider
branding. Selection saves the unchanged original URL and optional attribution in
an encrypted wallet/backend draft before closing. Four-attachment/message limits
remain. GIF drawables are uncached, byte/dimension/output/concurrency bounded,
lifecycle-owned and clipped. Optional malformed credits do not discard text/images.
Signed Saito payloads retain original URLs, without private draft metadata.

Current corrections:

- Android10 run39011893710 at a275 passed recovery, then crashed during selection.
  [First12 originals](../evidence/ticket16/android10-first-crash/README.md), root
  viewed4 PNGs/parsed4 XMLs. Changed diagnostic39012426099 at3bd also FAILED:
  AnimatedImageTh SIGABRT in ByteArrayStream::read while decoding a GIF frame.
  [Five originals and AOSP inventory](../evidence/ticket16/android10-buffer-red/README.md).
  Representative Android10 source routes heap arrays through JNI reads and
  direct buffers through a retained memory stream. eb840006 supplies direct
  read-only bytes with base Buffer.rewind, preserving limits/lifecycle/assertions.
  This supports the mechanism, not an exact emulator-source pin or runtime fix.
- Published attribution was lost after disposable composer cleanup. Native
  tracer24787e68 catches it:3bd Android17 run39012427386 preview PASS3/0/46.306s,
  posting FAILED6/1/35.72s at missing owner credits185/152; other5 methods pass.
  Later blank-composer/fresh credit tails are unexecuted. [Twelve originals](../evidence/ticket16/published-credits-red/android17/README.md),
  root viewed4 PNGs/parsed4 XMLs; keyboard crop is qualified. 7ad retains selected
  explicitly sent presentation references independently, encrypted per wallet,
  bounded128 references/2MiB. Signed work retains them before transmission;
  shared PostImages resolves known credits across feed/thread/profile/search/
  quote/viewer, with explicit draft credits taking precedence. No API catalogue,
  media cache, extra provider lookup or invented historical credits.
- Search tracer originally failed from an ambiguous text-field selector:
  a275 Android17 run39011891934 FAILED3/1/50.923s at151; prior2 methods pass,
  later search/posting unexecuted. [Five originals](../evidence/ticket16/search-selector-failure/android17/README.md).
  Root viewed PNG/parsed XML. Correction046a892c addresses the visible Search GIFs
  label, retaining values/assertions/deadlines. The subsequent3bd preview PASS
  reaches exact UTF8 query429/retry/empty, local51-character rejection, newer
  result preservation over1.5seconds after releasing the older request, close/
  authored text/no Saito writes. Dispatcher completion is not transport proof.

Earlier canonical checkpoints, never relabeling failed/cancelled runs:

| Checkpoint | Actual result and packet |
| --- | --- |
| Missing picker RED | f3b272d2 /39005341020 FAILED1/1/22.301s, [six originals](../evidence/ticket16/failure-red/android17/README.md). Later tails unexecuted. |
| Recovery GREEN | e5ba44fd /39006305712 SUCCESS1/0/30.684s, [nine originals](../evidence/ticket16/failure-green/android17/README.md). |
| Missing animation RED | f341a30a /39007107484 FAILED2/1/47.974s, [six originals](../evidence/ticket16/animation-red/android17/README.md). Recovery passes, later failed-method tails unexecuted. |
| Selection checkpoint |40588d70 /39007775320 SUCCESS2/0/29.539s, [13 originals](../evidence/ticket16/selection-green/android17/README.md). Animation/URLs pass, visible crop overdraw excludes layout acceptance. |
| Clipping RED |0abb1c29 /39008369694 FAILED2/1/32.83s, [seven originals](../evidence/ticket16/clipping-red/android17/README.md). |
| Clipping GREEN |39e0c245 /39008861337 SUCCESS2/0/49.277s, [12 originals](../evidence/ticket16/clipping-green/android17/README.md). Same-bitmap center/exterior and frames pass, cropToPadding enabled. |
| Selected-credit RED |b377c2e1 /39009395863 FAILED2/1/42.329s, [seven originals](../evidence/ticket16/credits-red/android17/README.md). Later credit/restoration/removal tails unexecuted. |
| Draft-credit GREEN |d423666d /39010198233 SUCCESS2/0/36.558s, [13 originals](../evidence/ticket16/credits-green/android17/README.md). Captures precede credit scrolling, not full visual credit proof. |
| Posting/fresh Activity |92845df9 /39011213336 SUCCESS preview2/0/37.359s and posting6/0/25.056s, [81 originals/outputs](../evidence/ticket16/posting-green/android17/README.md). Fresh VM and visible draft credits pass; this predates the published-credit tracer. |

Root inspected the retained images/XML at each stage. Posting packet: root viewed
all9 preview and3 posting PNGs; Standards reviewer viewed all12 posting PNGs;
all21 XML parse. Indexed owner-post bitmap shows an older card despite paired
XML/native display checks; exclude it from visible indexed-post acceptance.
Available quote captures are partly offscreen. No browser animation/physical proof.
Earlier observer runs39008263619/39008310559 were CANCELLED for coordinate/
positive-center oracle fixes, not app REDs. Obsolete metadata build88421 retired143;
corrected42514 PASS3m46s. Endpoint build30442 PASS1m47s, search build85318 PASS1m41s.

Six actual public test frames from928 pass offline WASM signature/hop/body,
unmodified pinned A/B template and Chromium DOM/decode checks. Twelve source
hashes match accepted ticket14 baseline. Pinned web original quote text is
preserved/authenticated in the model but omitted from their HTML because
embedded.app is absent; identity/images remain. Android preserves original text.
The mistaken HTML oracle failure remains retained, not app RED. Tool source
reviews0/0 at96ed9094; reference-script duplication is acknowledged/nonblocking.

Full Android10 39013781414 failed at the preparatory physical notification
dismissal, before deliberate death; restoration passed. See
[retained full-run failure](../evidence/ticket16/full-regression-red/android10/README.md).
The one-swipe fixture correction5154e89b targets the actual visible reply card,
without changing production or disappearance/manager-zero/silence/death/replay/
fresh-alert assertions. Both reviews0/0; local test build96009 PASS1m47s.
Focused Android17 39014623934 SUCCESS: physical dismissal/actual death/replay/
fresh liveness and autonomous scheduled recovery pass, both restorers pass.
[35 retained originals/outputs](../evidence/ticket16/card-process-green/android17/README.md).
Whole integration reviews at5154e89b Standards0/Spec0; source39014520955 SUCCESS.
Full Android17 39013783536 SUCCESS at19e:400PASS/95records/29interrupted outputs,
no assumptions. Exact profile aggregate is excluded, not counted twice.
[Current full17 evidence](../evidence/ticket16/full-regression/android17/README.md).
Root clarified that published Source text is visible but its clickable parent
is clipped at the viewport bottom; complete target/navigation is not proved.
Corrected full Android10 39014622109 failed later at UsernamePreviewTest443:
owned Archive not observed in5s while feed still loads,15/16class cases pass.
[Second retained failure](../evidence/ticket16/full-regression-red/android10-username/README.md).
Initial feed readiness was corrected in the fixture only; the complete third
Android10 run now succeeds. Beta25 was published after final combined review.

Readiness correction0a6b37bd and corrected metadatae3bf6f4d have both reviews0/0.
Focused Android10 usernames39017639582 SUCCESS exactlye3bf6f4d:
16/0/118.73s, actual process PID5424to5527 with exact request/identity recovery.
Corrected full Android10 39018073768 SUCCESS exactlye3bf6f4d, public reads true:
398PASS plus the existing Profile assumption,94records,29interrupted outputs.
[Full10 originals](../evidence/ticket16/full-regression/android10/README.md).
Source39017616391 at the same candidate SUCCESS release/lint.
Combined reviews at c365a5f4 are Standards0/Spec0. Exact prepared beta25 is
published; downloaded APK/hash/signature/package/SDK and manifest checks pass.
Preserve production19e. Queue-credit and external
source-navigation runtime coverage remain source-only. Fresh Activity is not
process death. Authorized live Giphy acceptance remains external. Whole16 and31
stay open. Focused readiness and corrected complete full10 pass; final combined
evidence reviews are complete. Earlier failures remain retained.
[Publication records](../evidence/ticket16/signed-preview25-published/README.md)
retain public HTTPS observations; immutable24 is unchanged.
[Current GIF data flow](../research/GIPHY_DATA_FLOW_2026_10_17.md) supplements the
dated release inventory without claiming provider/Play/account facts.

## Historical gateway checkpoint records

The remaining gateway sections preserve dated implementation/review stages.
Their active/pending labels are historical, superseded by the accepted entry above
and the canonical FINAL_ACCEPTANCE. Do not treat them as current open work.

## Whole gateway acceptance candidate

Latest code ddfd7005 fullownedbuild28732 PASS1m53/gateway10/0/0/coreUPTODATE/
installDist; parent evidence at48537b27 accepted0/0. Whole42 source base5a048524:
Spec1staleclientdoc corrected48537b27, finalSpec0/Standards0. Stalled unreadable/
unsupportedblock or repeatedArchivebudget limit explicitly retained inPUSH_GATEWAY.
All gateway sources currently committed; no owned localbuild. Candidate
[FINAL_ACCEPTANCE](../evidence/ticket42/FINAL_ACCEPTANCE.md) maps42requirements to
controlledevidence andlimits. Integrated sourceCI39001786605 SUCCESS atfad81a03, retainedfinal-source-ci2hashes;
finalevidence reviewpending, do notclose42yet. #32 actualconfiguredGoogle/device staysopen, no deployment.
NativebindingAndroid10/17 evidence is the earlier pinned17fb7260 ownedcontractpeer
acceptance; no unchangednative rerun claimed or necessary forlatergateway-only
changes. PortableJVMclient usesactualgateway. Parent/sender/runtime/history controlled
protocols pass currentfullsuite. Protectedgroupdoc remainsuntrackeduntouched.

## Active missing-parent availability

History checkpoint1c62eb31 accepted/pushed; sourceCI39001380385 SUCCESS,
retained history-recovery-source-ci(2hashes). NewercandidateCI39001786605 SUCCESS is separate, retainedfinal-source-ci2hashes.
Sole42 remainsopen. Parent tracer182848ae extends the existing owned reply journey:
first withhold exact signed root, require noFCM and retained archive-parent-unavailable
warning, restore root and clear read observations, then preserve the prior fresh
exact-proof→FCM/lostack/restart/retry/optout path for bothselectors.
Owned35676 focusedFAIL37s/1method1failure: noFCMwhileparentwithheldpassed,
thenmissingparentgapassertionfails. Restoration/latertails/OFFICIALunexecuted.
[RED](../evidence/ticket42/parent-red/README.md),3hashes/23sources, tracer0/0.
Currentminimum marks unavailableParent when exactsignedroot isabsent, preserves
independentmentions andrecordsarchive-parent-unavailable.
Owned28732 endedPASS1m53s/gateway10/0/0/reply28.791s/coreUPTODATE/installDist.
[GREEN](../evidence/ticket42/parent-green/README.md),10hashes/23sources; no ownedbuild.
Parent source0/0. Whole42 source audit atddfd7005: Spec1staleclientconfigurationdoc
(nowfixed), Standards0 plus stalled-observation limit requiring explicit disposition.
Finalintegratedevidence/docs/sourceCIreviews remain beforeclosing42.
Focused RED retention must copy only actual GatewayDeliveryTest report (retainer
has label parent-red exception), avoiding older unrelated JUnit. Source list23.
After parent acceptance: whole42 integrated source/protocol/native-evidence review
and finalcheck/operatingfacts, without Google/deployment/#32 completion claims.
Protectedgroupdocuntouched. No code edits while owned build active.

## Active unavailable-history recovery

Runtime checkpoint41cc6baa accepted/pushed; sourceCI39001070761 SUCCESS,
retained runtime-source-ci(2hashes), covers earlier runtime only.
Sole42 remains open. Current history tracer4cadda5c extends the non-social raw
chain/HTTP journey beyond accepted six requests. Seventh Full advertises a
different tip and an unretained2m0/cc ancestor. Expected: explicit ancestry gap,
no new block download, durable null active cursor; fresh-instance eighth SPV
bootstrap of available independently signed full blocks, ninth exact Full cursor.
Owned25077 endedFAIL1m26/gateway10/1 at198 retainedoldcursorobject.
[RED](../evidence/ticket42/history-recovery-red/README.md),10hashes/23sources.
Tracerreviews1/1 explicitJsonNullmismatch; correctedtoabsent-or-JsonNull.
Currentminimum restartObservation atomicallyretainsbindings/dedup, withdraws
uncertainpendingchainwork, clearsactivecursor andrecordsreason, so nextpasscan
bootstraprecentavailableconsecutivesuffix. Missinginteriorheighttailtrimis
source-only, notexecutedfixturecoverage. Owned77177 endedPASS1m50s/gateway10/0/0/chain10.588s/coreUPTODATE.
[GREEN](../evidence/ticket42/history-recovery-green/README.md),10hashes/23sources/source0/0.
No ownedbuild. Final evidence reviews ate13a6a12 Standards0/Spec0.
Bindings/pending withdrawal during this reset are source-reviewed; this tracer
is non-social and does not separately exercise those combinations.
Need preserve subscriptions and withdraw uncertain pending sourced events during
recovery; unavailable old history remains explicit. Parent-proof availability and
whole42 review/verification/operatingdocs remain after this slice. Protectedgroupdocuntouched.

## Active autonomous runtime

Chain-social checkpoint810fa14c accepted/pushed. SourceCI39000330915 SUCCESS;
retained chain-social-source-ci(2hashes), exact810fa14c only. Sole42 staysopen.
Runtime tracerf1539825 actualFAIL1m49s/gateway10/1/new8.592s at193 no automatic
requests after complete manual OWNER recovery; later/OFFICIALunexecuted.
[RED](../evidence/ticket42/runtime-red/README.md),10hashes/22sources. Standards0/
Spec1 evidence-scope: counters alone prove polling. Strengthened source now
requires automatic re-inclusion→samewake secondFCM/200/original-alt-original
blocks, then another Full request. No separate strengthenedRED claim.
Firsteb6982d9 PASS1m48/new7.443/coreUPTODATE/installDist, superseded source lifecycle
findings: eagerADC/closepublication, sequentialobserverstop, earlycleanupfailure,
headpollbypassesunresolvedancestry. [Retained](../evidence/ticket42/runtime-first-minimum/README.md),10hashes/22sources.
Correctionfc97c62f returns chain-reconciled Boolean, signalsallobservers/cancels
senderbeforeawaitingany, sharedcompletecleanup/restoredinterrupt, synchronized
subscriptionclose/statefileclosedguard, lazyADCinsideboundedmetadatafuture.
Owned86941 endedPASS1m49/gateway10/0/0/coreUPTODATE/installDist. [Retained](../evidence/ticket42/runtime-second-minimum/README.md),10hashes/23sources.
Spec0/Standards1: closure wronglypermanentlyrejectsvalidpendingwake. Current
source7394d9f8 makesclosed sender Retry10s; actualexpiry remains410.
Owned97230 endedPASS1m52s/gateway10/0/0/new7.498s/coreUPTODATE/installDist.
[GREEN](../evidence/ticket42/runtime-green/README.md),10hashes/23sources; no ownedbuild.
SourceSpec0/Standards0, one staleatomic-docsentence nowfixed; final reviews atb370a781 Standards0/Spec0.
Periodiccleanup/limits/CLI/shutdowninterleavings source-reviewed, not separately
executedfaultcases. No actualADC/Google/production/Androidchange. Protectedgroupdocuntouched.
NextafterruntimeGREEN: missing parent/history/unknownancestor recovery and whole42
review/verification/operatingdocs beforeclosure. Do not close42/32 prematurely.

## Active chain-social event withdrawal

Accepted gap checkpoint5302304d is pushed. Earlier non-social reorg source
CI38999540554 SUCCESS at e53aef47 is retained reorg-source-ci(2hashes).
#42 remains sole implementation. New tracer5dbfd34d adds an independent pinned
WASM/Elliptic signed reply/full block and fee-only replacement, controlled clock,
owned exact Archive/chain/FCM503 and recipient HTTP resolution across restart.
First owned61690 FAIL42s at compileTestKotlin128 undefinedMockResponse.header;
no gateway JUnit ran/stale reports not used. [Retained](../evidence/ticket42/chain-social-compile-failure/README.md),1hash.
Tracer reviews2/2: hide ordinary Archive head and advance clock beyond retry.
Correcteda8f7dcfc does both and uses addHeader. Actual behavioral RED73580 ended
FAIL1m26s/new2.565s/gateway10/1/coreUPTODATE at179 expected410got200.
Hidden-head chain exact reply/parent→FCM503 and first-instance/restarted200 passed;
later postdeadline/OFFICIAL tails unexecuted. [RED](../evidence/ticket42/chain-social-red/README.md),10hashes/21sources.
Minimumad413963 resets clock per backend and adds event sourceHeight/hash,
withdrawn state and recipient410. Both reviews found split event/cursor commit
could leave an orphan above saved position after interruption. Owned38394 was
retired after verifying its unique workspace/args, actual143, noPASS/JUnitclaim;
[retained](../evidence/ticket42/chain-social-retired-minimum/README.md),1hash.
Correctionaadad53f mutates events+cursor in the same snapshot/sole atomic save,
with shared private enqueue helper. Owned80369 endedPASS1m46s/gateway10/0/0/new6.952s/coreUPTODATE.
Sourceaadad53f Spec0/Standards0. [GREEN](../evidence/ticket42/chain-social-green/README.md),10hashes/21sources.
No ownedbuild. Final evidence reviews atbf1c6655 Standards0/Spec0. Event re-inclusion source-reviewed, not yet exercised.
Default clock remains systemUTC; no Android/core API change. Generator uses only
read-only dependencies/networknone/emptyenv, no Saito app initialization or writes.
Protected group doc untouched; no deployment/cloud/accounts/node changes.

## Active retained coverage warnings

Reorganization checkpoint e53aef47 is accepted/pushed with sourceCI38999540554 SUCCESS. It is separate
from successful38999026430 at earlier aad0e8d1; that earlier source run is now
retained in observer-source-ci (2hashes). #42 remains the sole implementation.
Gap tracer3b37d485 actualFAIL1m17s/new1.934s/9tests1failure at156, missingHTTPgaps.
The preceding latest-warning assertion passed; retained-reason/downloading tail,
later fresh-instance and OFFICIAL were unexecuted. [RED](../evidence/ticket42/gap-history-red/README.md),9hashes/18sources, tracer0/0.
Current minimum2ebd6d2f keeps one recent record per supported reason, preserves
legacy latest-warning shape and adds HTTP gaps, filtering expiry and pruning on
mutation. Owned18561 endedPASS1m41s/gateway9/0/0/new8.157s/coreUPTODATE.
Source2ebd6d2f Standards0/Spec0. [GREEN](../evidence/ticket42/gap-history-green/README.md),9hashes/18sources.
No owned build. Final evidence reviews atce20af14 Standards0/Spec0.
Periodic physical cleanup remains open. No new app/native/node/cloud/deployment.

## Active reorganization recovery

Accepted durable-cursor checkpoint aad0e8d1 is pushed. Source CI38999026430 SUCCESS covers that earlier checkpoint only. Sole issue42 remains open.
Reorg tracer75fa37a7 actualFAIL1m18s, gateway9/1failure/new2.597s at154:
OWNER fifth response observed chain-ancestry-unavailable instead of
chain-reorganization. Replacement cursor/download, later restart and OFFICIAL
were unexecuted. [Retained RED](../evidence/ticket42/reorg-red/README.md),
9hashes/18sources. Tracer Standards0/Spec1: missing positive retained-anchor
precondition. Current minimum3e9a3784 strengthens HTTP history observation and
persists the verified bootstrap predecessor as processed=false, then permits
rewind only to a retained full height/hash, saving before replacement processing.
Owned build43816 endedPASS1m42s, gateway9/0/0/chain8.246s/coreUPTODATE.
Source reviews at3e9a3784 Standards0/Spec0. [Retained GREEN](../evidence/ticket42/reorg-green/README.md),9hashes/18sources.
No owned local build. Final evidence reviews at9f6f8f0f Standards0/Spec0.
This is non-social cursor recovery. Social event provenance/withdrawal, complete
retained gap coverage, chain-triggered social proof and continuous standalone
runtime/cleanup remain unfinished. Do not close42/32. No node/cloud/deployment.
Protected untracked group contract remains untouched.

## Active durable observer processing

Historical cursor sourceb93bda92, accepted/pushedaad0e8d1. Chaincheckpoint319365f8 final0/0,
pushedc35d4fe5; sourceCI38998360212 SUCCESS, retainedchain-source-ci2hashes.
Sole implementation42stillopen. Tracera15df1d6 actualFAIL1m31s/new1.364s at145
missingHTTPcursor, OWNERrawprotocolpassed, restart/OFFICIALunexecuted.
[Retained](../evidence/ticket42/observer-red/README.md),9hashes/18sources.
Standardsoraclegapfixed23b37088: exactsequencebootstrap/Full/observerbootstrap/
restartedFull. Minimumcbc51abd PASS2m28s/gateway9/0/0/new5.766bothselectors,
corefresh aftergenericapplicationMessagesmethod. [Firstminimum](../evidence/ticket42/observer-first-minimum/README.md).
Spec2: per100chunkRegistrycoupling andincorrectsincewhennamegapreplacesotherreason.
Standards0plusduplicatependingdeliveryhelperheuristic.
Correctionb93bda92 collectsALLexactblockevidence beforeANYoptionalnames (inputbound
65_536, actualArchivequerylimitsunchanged), sharesreason-matchedrecordGap and
private deliverPending. Source0/0. Ownedlocal56817 endedPASS1m35s/gateway9/0/0/coreUPTODATE. No ownedbuild.
[Retained GREEN](../evidence/ticket42/observer-green/README.md),9hashes/18sources.
Finaldocs/evidencereview at17ffe38b reportsStandards0/Spec0. Acceptedcursorcheckpoint pushed as aad0e8d1.
Pinnedcursorfixtureblocksnon-social: chain-triggeredsocialeventnotprovenyet.
Next REALsourcework: reorg/ancestryrecovery, unavailablegapcoverage andcontinuous
standaloneruntime/cleanup; finalchain-socialproofstillneeded. Do not close42/32.
No other writers/node/cloud/accountoperations; protectedgroupdocuntouched.

## Active chain range/full-block slice

CurrentHEAD319365f8, remoteaccepted8fc2bd9b. Name checkpoint final0/0 pushed;
sourceCI38997783597 SUCCESS, retainedname-source-ci2hashes. Sole ticket42stillopen.
Chaintracera1ec2bb8 extractsunchangedGatewayPeer andaddsGatewayChainstubs+owned
WS/HTTPjourneywithpinnedindependentfullblocks. ActualRED1m27s/new0.011s at103
expected2/got0, firstOWNER, prior8PASS/coreUPTODATE. Downloads/continuation/
OFFICIALunexecuted. [Retained](../evidence/ticket42/chain-red/README.md),9hashes/
18sourcehashes (wire/fullblock included). Tracerreviews0/0.
Minimum699397f4 adds111bytecorewire request, oneinflightchainfuture/retireuncertain
connection, boundedrangeparser, individualfullblockfetch<=8MiB andexisting
creator/Merkle/referenceidentityverification. Standards0/Spec1: finalreferenceat
tipheightmustalsoequaladvertisedtiphash. Initial699397f4 PASS2m11s/gateway9/0/0/new0.901s bothselectors/coretestfresh;
[retained superseded](../evidence/ticket42/chain-first-minimum/README.md),9hashes/18sources.
Fix319365f8 addsmatchingtipidentity; sourceStandards0/Spec0. Owned51253 endedexit0.
Correctedowned74343 endedPASS1m30s/gateway9/0/0/coreUPTODATE, no ownedbuild.
[Retained GREEN](../evidence/ticket42/chain-green/README.md),9hashes/18sources.
Finalevidence/docsreview5a3c202b reportsStandards0/Spec0 afterone stale push-statusclausefix.
Accepted chain checkpoint ready to push. No durabledatacursor,
chain/eventintegration or whole42acceptance yet. Nextsourceworkmustimplementdurable
observer/gaps/cursorprocessing ratherthanmoreprotocolresearch. Protectedgroupdocuntouched.

## Active name-proof correction

Accepted sourcead8a226a, accepted/pushed8fc2bd9b. #42 remains sole implementation.
Acceptedlost-ack fixtureaea14801/d4342e90 final reviews0/0, included in pushed8fc2bd9b.
Nametracer216882ee RED1m25s at192 missingOWNERwake, old7PASS/coreUPTODATE;
[name-red](../evidence/ticket42/name-red/README.md),8hashes/13sourcehashes.
Orderingoraclegap corrected299817e1. Minimumf6238c51 thenPASS1m30s/gateway8/0/0,
class2/31.088s; [firstminimum](../evidence/ticket42/name-first-minimum/README.md).
Reviews Standards1/Spec1: optionalRegistryfailure or over50names aborts unrelated
replies and retained sends. Strongerordinaryreplytracerf053fae9 adds unavailable
name in owned-parent reply plus unrelated51namepost; existinglostack/restart/
optout assertions stay. NewboundedHTTPobserverstatus must retain latestnamegap
acrossrestart. Actualowned63505 FAIL46s atreply100 IOExceptionmissingRegistry,
reply1.345s; namecasePASS2.646, total7pass/1fail/coreUPTODATE. Laterreplytails and
OFFICIAL replyloopunexecuted. [Retained](../evidence/ticket42/name-availability-red/README.md).
Correctioncfa56b23 isolatesoptionalnames andpersistslatestgap/backend: PASS1m27s/
gateway8/0/0 class2/31.345/coreUPTODATE. [Retained](../evidence/ticket42/name-isolation-first/README.md).
Spec0/Standards1: Registrytransportdisconnect canstillprecedeparent/latercandidate
reads. Two-phasead8a226a completesALLexactArchivecandidate/parent reads beforeANY
optionalRegistrylookup. Transportdisconnectis source-reviewed only, notexecuted.
Source Spec0/Standards0. Owned27444 endedPASS1m27s/gateway8/0/0/coreUPTODATE,
no owned build. [Retained GREEN](../evidence/ticket42/name-green/README.md),
8hashes/13sourcehashes. Final evidence/docsreviews atbc76dd81 report0/0. Accepted name checkpoint pushed at8fc2bd9b; sourceCI38997783597SUCCESS. No new native source, node/cloud/accountoperations.
Protectedgroupcontractuntrackeduntouched. No whole42/32claim.

## Latest owner scope

The owner explicitly asked to stop cybersecurity requests. No Daybreak application
or cybersecurity service/account request was submitted. Stop separate research
and targeted campaigns in that area; continue ordinary app functionality and
correctness. The optional extra test and dedicated dispatch choices were removed
at50ac874d. Subsequent cases cover ordinary app functionality. Keep existing
app fixes.
Do not retry, rename or reroute blocked work. AGENTS.md records this instruction.

The ChatGPT interface displayed a content-blocked notice. Root explained that it
cannot switch that platform notice off or guarantee absence, and pointed to official
feedback guidance. A screenshot does not identify an exact rejected tool action.
Do not invent one. Repository state was verified before ordinary app work resumed.

## Current gateway implementation

#42 is assigned to the sole writer after accepted SDK preparation. Native blocker
count0; #32 is natively blocked by#42. Fixed base5a048524d8681781c1763f850cc3e0773b8e753c.
First vertical slice: public HTTP wallet-signed subscription, retained across a
server-instance restart, then retired using installation authority without the
outgoing wallet secret. Parent controlled protocol/wallet seam, not a new campaign.

Tracer6bb9175e: actual local gateway-only RED43s at firstchallenge expected201/got501,
oneFAIL. Later bind/restart/status/retirement/retry tails unexecuted. Retained at
[subscription RED](../evidence/ticket42/subscription-red/README.md),3 hashes and
four committed source byte hashes. Tracer/RED reviews report Standards0/Spec0.
Core tests were not run by that gateway-only RED task. No process-crash proof.

Minimum source now implements challenge/bind/status/revoke and durable exclusive
file state, idempotent exact retransmission and retirement tombstones. Initial
core/gateway task PASS35s. Bounded executor, canonical origin and constructor-error
cleanup changes PASS39s (exec81310 ended). Final ordinary HTTP/storage error
classification and CI gateway-test/cache wiring PASS34s (exec84118 ended).
Source review atc3abb7a6: Spec0/Standards1, numeric request conversion incorrectly
classified as503. Correction maps invalid request integers to400 while stored
record decoding stays an IO/503 failure. Corrected core/gateway PASS36s, exec48363 ended; no owned local build.
Corrected source41cf0c76 reviews Standards0/Spec0.
[First local GREEN](../evidence/ticket42/subscription-green/README.md) retains
7 hashes and exact source metadata. Final first-slice evidence reviews atc35cb6be report Standards0/Spec0.
Exact c35cb6be source CI38991601083 SUCCESS, including gateway/core, release and lint. [Current contract](../PUSH_GATEWAY.md) describes
the implemented first slice and explicit remaining deployment/runtime limits.

Current renewal tracer adds one ordinary HTTP journey, sourcebcf0ca97. Actual
local gateway-only RED28s:2completed/1PASS(original)/1FAIL(newrenewal), current
signed renewal expected201/got409 atline131. New setup bind passes; later stale
request/currentstatus/idempotency/post-restart renewal tails are unexecuted.
[Retained RED](../evidence/ticket42/renewal-red/README.md),3hashes/7sourcehashes.
Renewal minimum now passes39s, core task up-to-date and gateway2/0/0; exec18711
ended. Same subscription accepts fresh proof to renew FID/backend/categories/expiry
while owner/installation/revocation hash remain fixed. Per-runtime nonce ordering
rejects a delayed older request; a new runtime requires a fresh challenge. Exact
retransmission remains idempotent. Source reviews at1d72826a report Standards0/Spec0. Retained
[renewal GREEN](../evidence/ticket42/renewal-green/README.md), fourhashes and
seven source hashes. Final evidence reviews atcc332826 report Standards0/Spec0. No owned local build.
Standalone tracer83cf14dc, actual gateway-only FAIL51s: old2PASS/new1FAIL at
GatewayProcessTest:89, missing readiness because no-op main exits. New HTTP/bind/
process-death/restart tails unexecuted. [Retained](../evidence/ticket42/process-red/README.md),
fourhashes/ninesourcehashes. Minimum adds application distribution, strict local
command flags, explicit loopback port and shutdown lifecycle. Initial core/gateway/installDist PASS44s, exec33237 ended. Source review found
Standards2/Spec1 fixture issues. Correcteda71e70f5 preserves primary startup error,
forces bounded cleanup after failure and observes child alive before forced stop.
Reviews now Standards0/Spec0. Corrected build PASS33s, gateway3/0/0, coreUPTODATE114,
installDistUPTODATE; exec29335 ended. No owned local build. [Retained GREEN](../evidence/ticket42/process-green/README.md),
sixhashes/ninesourcehashes. Final evidence reviews atc71038b3 report Standards0/Spec0. No deployment.
Next tracer is a portable client against the real owned HTTP gateway: dropped
bind acknowledgement, reconstructed journal, replacement wallet and keyless
retirement/opt-out. It is not Android Keystore or actual Firebase proof.
Client tracer9c77b33a, actualcore/gateway FAIL1m26s: corefresh114/0/0, gatewayold3PASS/
new1FAIL atSubscriptionClientTest:64. Placeholder performed no HTTP, so dropped
ack/recovery tails unexecuted. [Retained RED](../evidence/ticket42/client-red/README.md),
sixhashes/eleven source hashes. Portable minimum now persists installation/retirement
authority before binding, retires old owner/origin before replacement, and checks
fresh demand around binding. First minimum d71defab PASS1m24s, corefresh114/gateway4/0/0; exec64223 ended.
Source review Standards1/Spec2: lost retirement acknowledgement needs durable
retiring intent; fresh null demand must retry instead of silently succeeding; final
opt-out assertion must target newest binding. Strengthened tracer226530ee actual
FAIL35s atSubscriptionClientTest:46 expected201/got410 on same-wallet opt-in after
lost revoke acknowledgement. [Retained](../evidence/ticket42/client-retirement-red/README.md),
fivehashes/eleven source hashes; previous tails through lost revoke ran, later
reenable/newestoptout tails unexecuted. Correction persists retiring phase before
request, completes it before opt-in and signals retry on fresh null demand with
retained authority. Corrected core/gateway PASS1m29s, gateway4/0/0/corefresh114/0/0; exec23936 ended.
Source2a1d774d reviews Standards0/Spec0. [Retained GREEN](../evidence/ticket42/client-green/README.md),
sixhashes/eleven source hashes. Final evidence reviews at724a8455 report Standards0/Spec0. Native adapter tracer8b0f03f9 is now committed, stub no-op plus real-device
PushBindingPreviewTest and first gate in existing social-push/full harness. Local compile19129 PASS3m53s, core/gateway tasks up-to-date, debug/test APKs built;
exec ended. No runtime RED or minimum yet. Tracer reviews Spec0/Standards1 fixed:
fail-fast PUSH_CONFIGURED=false now precedes fixture mutation, so consent writes
cannot schedule real SDK work. The one-line correction awaits actual CI compile.
No owned local build. Client source CI38993005106 at724a8455 SUCCESS, retained
at../evidence/ticket42/client-source-ci (2hashes). Corrected tracer60d00271 now
reports Standards0/Spec0 and is remote main. Source CI38993468005 SUCCESS, exact60d00271. Android10 intended RED38993468083
FAILURE0.205s atPushBindingPreviewTest:103, missing existing-wallet binding.
Resultphase initial-binding/binds0/revocations0/SDK29. Old11UI/worker3 and later
new wallet/backend/FID/opt-out tails unexecuted behind first gate. ADBexit0 is not
JUnitpass. [Retained](../evidence/ticket42/native-binding-red/android10/README.md),
7hashes/22sourcehashes; sourceCI separately retained. Native minimum now implements encrypted device journal, bounded configured HTTPS
adapter/debug-emulator-only local fixture transport, shared fresh push identity
and registration-worker hook. Opt-out attempts keyless gateway retirement before
SDK initialization/outstanding-task drain, then still attempts SDK unregistration;
no early unregistered-SDK success can skip remaining gateway cleanup. First source62e7fe9b reviews Standards0/Spec1: requested categories ignored Android
channel choices. Owned build18265 was stopped at exact owned container after this
concrete finding, actualexit143, not a pass or runtime failure. No other container
touched. Correction derives canonical enabled social categories from actual OS
channels, treats uncreated channels as default enabled, and shares the set across
SDK demand/freshness and gateway binding. Both disabled means no social demand.
Publication's final channel check is unchanged. Corrected source17fb7260 reviews Standards0/Spec0. Exactsource17fb72605cf6e3eb740cbaba73b429a2b5de1bfa sourceCI38994268560 SUCCESS,
release/lint/core/gateway. Native10 run38994269120 SUCCESS: binding1/1.235s,
oldUI11/84.307s, worker3/46.275s. Native17 run38994270968 SUCCESS: binding1/11.810s,
oldUI11/115.412s, worker3/47.324s. [Retained GREEN](../evidence/ticket42/native-binding-green/README.md),
14+15+2+3 hashes,23 source hashes per device. Root parsed2XML/viewed2actual opaque
alert originals; new binding HTTP/storage journey has no screenshot. Core114/0/0
reports; task cache states in actuallogs. Individual channel choices and configured
SDK worker hook are reviewed source, not executed OS toggle/liveSDK proofs.
Corrected duplicate localbuild2522 retired only after all three exact-source hosted
runs completedSUCCESS, actualexit143, never a localpass. No owned local build.
Final evidence reviews report Standards0/Spec0 after the one stale native-GREEN
denial was corrected at9c0e9168. Next slice is remaining gateway observer/events/
resolver/sender; do not close42/32 or repeat unchanged native runs. No configured SDK or Google-delivery claim.
New ordinary API research FCM_GATEWAY_SENDER_2026_10_17.md records FID payload,
ADC scope/permission, error/retry semantics and candidate auth library1.54.0;
Google auth1.54.0 is now resolved only in the JVM gateway; no credential/account operations occurred. Prior native UI11/worker3 assertions unchanged behind new gate.
Earlier standalone CI
38992493829 atc71038b3 SUCCESS, separately retained; no client CI claim.

Android gateway binding is accepted at the controlled HTTP/storage seam above.
Continuous observer, standalone polling, physical retention cleanup and
whole#42 acceptance remain unfinished. Callable durable reply delivery and
recipient resolver are described below. The isolated sender adapter is described below. Existing owner
Firebase setup remains pending without repeating the question. No deployment,
node/account/IAM or live-write operations. Protected untracked group contract stays
untouched. Public signed beta24 unchanged.

## Current bounded Archive read

Tracer0cba443f has actual intended RED1m15s, exit1: gateway old4PASS/new1FAIL
atGatewayArchiveTest:87 (expected3/got0), first OWNER loop. OFFICIAL and later
assertions unexecuted. Core task UP-TO-DATE. [Retained](../evidence/ticket42/archive-red/README.md).
Minimum9967ff40 adds owned-identity authenticated read-only WebSocket requests,
bounded service/response handling, strict UTF8 and signed root/reply/mention
parsing. Its owned local build52476 finished PASS50s, gateway5/0/0 including both
selector loops (new0.888s); core UP-TO-DATE. [Retained](../evidence/ticket42/archive-green/README.md).
Independent source9967ff40 and evidence d6619625 reviews report Standards0/Spec0.
No continuous observer, candidate eligibility, durable event or delivery claim.

## Current external sender adapter

Tracer ee01dd91 actualFAIL1m5s/new0.116s missingAcceptance, old5PASS/coreUPTODATE;
[sender RED](../evidence/ticket42/sender-red/README.md). Firstminimum35e1eb93 FAIL52s/new0.445s,
expectedAccepted/gotRetry10000; lateroutcome/body tailsunexecuted. Standards2/Spec1
findings: exact-length read, status/header preservation on unusable body, and
expiry after credentials refresh. [Retained first failure](../evidence/ticket42/sender-first-minimum/README.md).
Correction663e2f18 source reviews0/0, but its test override failed compilation36s
(nullable Google URI), no gateway test ran. [Retained](../evidence/ticket42/sender-compile-failure/README.md).
Fixture-only2da44ac9 nowPASS41s/gateway6/0/0, new1.897s, coreUPTODATE.
[Retained GREEN](../evidence/ticket42/sender-green/README.md),7hashes/12sourcehashes.
Owned55910endedexit0, no owned local build. Final fixture/evidence reviews at981d1248 report Standards0/Spec0. Pushed;
source CI38996214676 SUCCESS, [retained](../evidence/ticket42/archive-sender-source-ci/README.md).
No actual Google/ADC/cloud/account use. The durable callable event/resolver/send pass is now implemented below;
continuous runtime/chain work remains; current name implementation is described above. Do not close42/32 or claim delivery. Beta24 unchanged.

## Current durable reply pass

Tracercc50e3a6 actualFAIL58s/new3.382s first outgoing request missing, old6PASS/
coreUPTODATE. [Retained RED](../evidence/ticket42/delivery-red/README.md),8hashes.
Spec2 tracer gaps correctedb93f86ab: actual FCM boundary observes exact candidate/
parent reads before first send; distinct new reply exposed after opt-out.
Minimumb3b4b477 PASS1m1s/gateway7/new5.936s bothloops, but Standards1 stale-batch
send demand finding. [Retained firstminimum](../evidence/ticket42/delivery-first-minimum/README.md).
Correction6bc3d712 fresh durable reread per send, current FID/generation and serialized
passes; Standards0, Spec documentation contradiction corrected in current docs.
Owned51625 ended PASS1m1s/gateway7/0/0/coreUPTODATE, no owned local build.
[Retained GREEN](../evidence/ticket42/delivery-green/README.md),8hashes/13sourcehashes.
Final Spec0/Standards0 atf9983893 after the stale preceding next-step sentence was corrected.
The concurrency interleaving, retry branch,
expiry and pubkey mentions are source-reviewed, not exercised by this reply case.
Lost-send recovery is now verified below. Next: verified-name mentions, chain/gaps/cleanup/runtime.
No full42/32, production deployment or Google/Pixel claim. Beta24 unchanged.

## Lost send acknowledgement recovery

Production unchanged; stronger existing journey7facb232 firstPASS1m10s/new27.577s
bothloops. Reviews Standards1/Spec1: early silence assumes restart finishes before
retrydeadline; immediate+latersample does notprovefullschedulerfloor.
[First observation](../evidence/ticket42/delivery-retry-first/README.md),8hashes.
Fixtureaea14801 records actualFCMarrivaltimes, permits legitimate due retry during
restart and checks the observed retry interval>=10s. Source0/0.
Owned80481 nowPASS1m9s/gateway7/0/0/new27.577s/coreUPTODATE, no owned build.
[Corrected observation](../evidence/ticket42/delivery-retry-green/README.md),8hashes/
13sourcehashes. Bothloops losefirstsendack, retainpendingresolver throughnewserver,
hidehead, retrySAMEwake, restorehead→noduplicate, newpostoptout→nosend/410.
Final recovery evidence reviews0/0 atd4342e90. Configuredschedulerfloor/jitter are sourcefacts,
not boundaryproof. No actualGoogle or forcedeventprocessstop. Exactf9983893
sourceCI38996698848 SUCCESS, [retained2hashes](../evidence/ticket42/delivery-source-ci/README.md).
Registrar-backed username mention source/acceptance is now described above. Whole42/32 staysopen.

## Accepted #32 foundation

The accepted scheduled-receipt foundation uses real WorkManager,
selected-Archive retry and an actual Android alert without an Activity.
[Canonical verification](../evidence/ticket32/scheduled-receipt-green/README.md)
records both passing SDKs at exactc69a1896d01b23c6bb41b3f9e248410517017ac7:
- Android10 run38981302625: old11PASS84.608s plus worker1PASS12.005s.
- Android17 run38981305637: old11PASS96.847s plus worker1PASS11.792s.
- Source38981302496 SUCCESS includes release/lint; both native runs core114/0/0.
- Root viewed6new GREEN originals and parsed6XML. Earlier11 screenshots are not
  duplicated; their exact passing raw logs remain retained.

Minimum uses CONNECTED/10s exponential backoff, an execution-time7day processing
expiry and the shared receiver's fresh wallet/backend/policy/signed-Archive/ledger
checks. Clock rollback conservatively retains processing; this does not establish
WorkManager-history deletion after7days. Enqueue returns the actual persisted
unique-work UUID, including pending duplicates. A shared execution mutex is drained
after owned cancellation before fixture files/preferences/wallet/backend restore.
The native journey checks pending duplicate identity, actual recovered alert,
completed duplicate's unchanged OS postTime, no Activity, fullwallet/backend,
zero outgoing writes and scoped restoration.

[First attempt](../evidence/ticket32/scheduled-receipt-group-observation-failure/android10/README.md)
38979224052 remains FAILURE:10/11PASS82.607s, group editor observation before the
worker case. The correction explicitly compares exact full EditableText.text
rather than AnnotatedString metadata, keeping15s/finalassert and persisted
text/photo checks. Installed1.11.4 bytecode confirms those comparisons differ;
the actual old predicate-time cause remains unknown.
[Intended RED](../evidence/ticket32/scheduled-receipt-red/android10/README.md)
38980190191 at ee1bc69b: old11PASS83.64s, worker1FAIL1.88s rejecting the no-op
worker's SUCCEEDED state. Later newcase tails did not execute in that failed run.

Corrected source reviews0/0 at1a62909d; pre-review minimum local core/debug/test
PASS3m20s (exec76439 ended). The simple review fix is compiled by exact-source CI.
No owned local Gradle build remains. Bounded review base8e6498a2; minimum base
ee1bc69b. Final evidence review at2fd7079e found only stale current-handoff wording,
resolved at81624363 with final Standards0/Spec0. This bounded local slice is
accepted; actual timeout/process/external gates below remain.

The additional[unanswered-Archive observation](../evidence/ticket32/scheduled-timeout-green/README.md)
now passes on both SDKs at5463170d091ed58f3ad90686c184ca0875cea08f:
10run38982505303 old11PASS90.863s+worker2PASS35.448s;17run38982507198
old11PASS128.618s+worker2PASS35.981s. Source38982505012 SUCCESS release/lint.
Default-false withholding suppresses only actual Archive response emission after
its normal callback observed/validated the request. The withheld case checks real
≥10s elapsed from submission to retry, then full recovery/duplicate/alert and
restoration assertions; scheduling/authentication are included in that interval.
The peer timer is12s inside15s verification, not proof the outer15s specifically
fired. Same150s/180s class limits, no fake clock or shortened deadline. Production
trees c69→546 exactly equal. Root6timeout originals/12XML; malformed-mode captures
also retained with passing assertions/logs. Source reviews0/0 at0f50c2a4; final
evidence reviews at9663f3a5 report Standards0/Spec0. The bounded timeout
observation is accepted. No owned local Gradle build or SDK/config/live change.

The additional[actual-process observation](../evidence/ticket32/scheduled-process-green/README.md)
now passes on both SDKs. Original17 run38984566019 at57ffd6f8: scheduled PID4482→4741,
restorer0.835s. Corrected10 run38985316045 at47be60d8: PID3804→4054, restorer0.874s.
Both core114/0/0. Original/corrected source38984556683/38985300921 SUCCESS release/lint.
Both also retain the preceding dismissed-replay process journey/restorer. Root
viewed4 new GREEN originals and parsed12 XMLs;41 hashes per SDK plus3 root metadata.

The byte-identical shared test-only peer runs in a bounded host JVM, survives app
death and is reached through an owned reverse mapping. Both prove pending retry,
actual death, distinct PID, post-death available reads and exact visible Android
alert without target instrumentation/Activity or forced job during observation.
Cleanup follows observation, stops the target, cancels/drains owned work and
independently restores device-only originals/full wallet/backend. Source and17
evidence reviews report0/0; combined final reviews atcae9abe5 report Standards0/Spec0.
This bounded autonomous-scheduling observation is accepted.

[Initial10 failure](../evidence/ticket32/scheduled-process-stop-failure/android10/README.md)
38984563727 at57 remains FAILURE at external run-as kill exit1, exact cause unknown
(stderr not retained). New setup reaches pending PID3819, recovery tails unexecuted,
restoration passes. Root viewed1 new failure original/5XML;32 hashes. The marker
correction1738159f/794d145c requests only the waiting fixture's own termination,
matching established tests;10 validates acknowledgement and actual gone PID.17
keeps its actual original external-stop pass, not a claimed later-source run.
Eight production source/build objects57→47 are identical. No additional unchanged
native rerun is needed; final full#32 acceptance remains required.

Local initial build5m32s/corrected2m27s/core114 and isolated host readiness/stop smoke
pass. Later marker source is compiled by exact CI. No owned local build remains.
## Current opaque wake processing

Same-ticket transport step, review base68e420ee. [Intended RED](../evidence/ticket32/opaque-wake-red/android10/README.md)
38986422596 at4f56e70e: existing11PASS87.424s; worker3 reports2PASS/1FAIL37.422s,
new opaque case rejects no-op SUCCEEDED. Existing Archive journeys pass. New
request/auth/recovery/alert/dedup/preservation tails are unexecuted in this RED;
recipient-proof assertions452f4595 were added afterward, not proven by that run.
The21 hashes/7XML are retained; root viewed only the new failure original. Its
PNG IHDR is320x640 and XML records Launcher; preview showed only a thin statusbar,
so no rendering cause is inferred. Source38986388089 SUCCESS release/lint/core114.

Minimum861e795c now resolves the opaque wake over HTTP, using a local recipient
signature over literal domain/full resolver URL/wake. The future gateway must
match signer to wake recipient before returning the strict public reference.
Body remains onlyversion/wake. It retries outages, bounds response1024/HTTP12s,
checks current consent/identity/backend, then reuses the existing signed selected-
Archive verifier and shared publication mutex. Processing expiry7days, fresh
policy/cancellation checks, HTTPS/no redirects, debug+emulator127 fixtures only;
archive overrides are fixture-only. No new user-configurable backend.

Source reviews at861e795c find0 source defects; the handoff correction5aac5d04
now reports Standards0/Spec0. Local minimum core/debug/test build passes3m10s
(log .scratch/push32-opaque-minimum-build.log, exec74616 completed). No owned local
build remains. Exact2685f5fc Android10 run38987459024 SUCCESS: old11PASS86.777s,
worker3PASS46.679s. Android17 run38987461150 SUCCESS: old11PASS124.78s,
worker3PASS47.545s. Both core114/0/0; source38987439730 SUCCESS release/lint.
[Combined evidence](../evidence/ticket32/opaque-wake-green/README.md) retains51
per-device hashes/18XML plus source metadata. Root viewed6 new opaque originals;
12 earlier-mode originals retained without claiming new visual inspection. Full
native assertions and scoped restoration pass. Final combined evidence reviews at22d65f7b report Standards0/Spec0.
The bounded local opaque-wake slice is accepted; next is SDK/client preparation. Tracer local compile failed2m26s at
nullable request body, corrected1m47s pass. Tracer/correction reviews at553df5b0,
dc48e398/452f4595 report0/0. Existing150s worker class retains both Archive journeys.

The[entry-point research](../research/NATIVE_PUSH_TRANSPORT_ENTRY_POINTS_2026_10_17.md)
and[manual setup research](../research/FCM_MANUAL_INITIALIZATION_2026_10_17.md)
prepared the SDK/client work below. Opaque acceptance itself did not add the SDK.
Local scheduling/resolution is not proof of incoming FCM/locked/Doze/Pixel/full#32.

## Current Firebase client preparation

Bounded source base4f7000c29463a044a2060887bfdcd4d28a21cf25. Actual Messaging26.0.0
now added with per-variant public build options, manual default-app initialization,
removed FirebaseInitProvider, explicit FID mode, automatic registration/analytics/
default collection/delegation disabled. All options absent leaves it unconfigured;
partial options or declared package/appId/sender mismatch fail the build.
[Configuration and lifecycle](../PUSH_CLIENT_CONFIGURATION.md) lists the options
and explicitly distinguishes syntax validation from real project association.

The Application supports cold service initialization without wallet creation.
The messaging service accepts only data version/wake, using the configured
resolver and awaiting durable existing wake scheduling. The registration worker
serializes explicit SDK tasks, encrypted installation phase/FID/time, pending
phase before each request, fresh wallet/backend/consent/OS-permission demand,
post-task demand reread, daily refresh and callback-triggered corrective work.
Callbacks do not blindly overwrite the stored phase. Hooks cover committed social
choice, permission result, startup/resume, wallet import/recovery and backend state.
No signing wallet material is retained in installation registration storage.

Initial local build FAIL23s at Gradle java.net name shadowing, corrected explicit
URI import. Corrected build compiled debug/test code, then release lint failed on old
transitive Fragment1.1.0 at existing wallet ActivityResult calls. The remaining
owned build was stopped (exit143), not claimed PASS; log
.scratch/push32-sdk-client-build-corrected.log. Fragment constrained1.8.9 for
compatibility with current Activity/Lifecycle, no lint suppression. Merged debug manifest confirms
service/metadata and removal of FirebaseInitProvider; no runtime result claimed.
Initial source reviews at258ec4f7 found Standards1/Spec2: latest demand blocked
by APPEND retry backoff and waiting timeout releasing an unfinished SDK Task.
Correction uses REPLACE for current demand, matching-callback suppression and a
retained outstanding Task drained under the execution lock before another SDK
operation. Durable phases survive process loss; configured remote behavior remains
unverified. Corrected source reviews at2310e0df report Standards0/Spec0. Local
core/debug/test/release/lint PASS10m15s (exec93880 completed). No owned local build
remains. Exact2310e0df source38989625277 SUCCESS release/lint; Android10
38989625467 SUCCESS11/87.193s+3/46.541s; Android17 38989627211 SUCCESS11/129.468s+
3/47.423s. Both core114/0/0. [Retained source-preparation checkpoint](../evidence/ticket32/firebase-client-preparation/README.md)
contains24 hashes/2XML, two actual alert originals root viewed. Other assertions
pass in retained logs; remaining captures stay raw, not newly claimed viewed.
Merged debug/release manifest and default config checks pass, SDK AAR matches
prior independent binary. Final committed-evidence reviews atbf75f7e1 report Standards0/Spec0;
this bounded SDK source-preparation checkpoint is accepted. This is source
preparation, not a new TDD RED/GREEN or configured Google delivery proof. No new
mock SDK/internal tests were added in place of actual external transport.

Owner Firebase setup question stays pending, do not repeat it. No real project
options/account/IAM/sender credentials or deployed gateway have been supplied.
Signed gateway binding/revocation, observer/events/reconciliation and actual Google
delivery remain unimplemented. Task timeout/rotation/callback/process behavior
still requires configured runtime verification, both backends/fullSDKs/Pixel too.
No node/deployment/account mutation or new beta; signed beta24 unchanged.

Required separate[gateway ticket#42](https://github.com/robelene112/redsquare-android/issues/42)
is now published and linked as a #32 sub-issue. It is ready for the next sole implementation after this accepted SDK checkpoint.
No parallel ticket writer. Source/local verification only;
production deployment keeps its separately authorized boundary. #42 is now the
sole implementation after this checkpoint; the earlier ready status is historical.

The full-harness omission is corrected at1037dc95: full now invokes existing
social UI/work checks then both process helpers. Focused default behavior/app/core
are unchanged. bash -n and source reviews Standards0/Spec0 pass. The new full
invocation is unexecuted; exact2310 native evidence does not claim that wiring.

## Earlier accepted username slice

[Verified username acceptance](../evidence/ticket32/username-mention-green/README.md)
retains intended RED38975655606, first minimum failure38976627865 (username passed,
group observation failed), corrected10 run38977710046/78e4172d11PASS81.609s and17
38976630590/7824504111PASS124.127s. Both notification compatibility runs
38976632886/38976636125 pass32read cases, applicable permission choices and actual
read-history recovery PIDs6113→6203/5326→5398. Production equivalence782→78 and
source release/lint38976622437/38977709969 are retained. Final evidence reviews0/0
at3f3c5bb4. Root10username originals+groupdraft+5permission/recovery originals,
allXML parsed. Loading/failed history and earlier failed-run tails remain qualified.

## Accepted bounded #32 work

These are controlled-transport slices. The scheduled receipt includes actual
process death/autonomous restart, while incoming FCM remains unimplemented.
Every prior failure retains its actual conclusion and qualified limits. Full raw
chronology is in the historical handoff; linked evidence is authoritative.

| Behavior | Canonical evidence |
| --- | --- |
| First reply alert | [Reply GREEN](../evidence/ticket32/reply-alert-green/android10/README.md) |
| Dismissal replay and actual process recovery | [Replay](../evidence/ticket32/dismissed-reply-green/android10/README.md), [process](../evidence/ticket32/dismissed-reply-process-green/android10/README.md) |
| Warm reply tap and post draft/photo | [Tap GREEN](../evidence/ticket32/reply-tap-green/) |
| No-Activity tap/recreation/consumed route | [Cold tap GREEN](../evidence/ticket32/reply-cold-tap-green/) |
| Public-key mentions | [Mention GREEN](../evidence/ticket32/mention-alert-green/) |
| Wallet-scoped alert choice | [Choice GREEN](../evidence/ticket32/alert-choice-green/) |
| Actual Android category shortcut | [Categories GREEN](../evidence/ticket32/android-category-controls-green/) |
| First-use social consent | [Consent GREEN](../evidence/ticket32/social-consent-green/), [compatibility](../evidence/ticket32/social-consent-regression/) |
| Private Chat draft/photo through social tap | [Private acceptance](../evidence/ticket32/private-chat-reopen-green/README.md) |
| Group Chat draft/photo through social tap | [Group acceptance](../evidence/ticket32/group-chat-tap-green/README.md) |

Latest group minimum0b5dc394, exact runtime source4419fd1a: social10 run38973726401
10PASS78.837s, social17 38973729273 10PASS103.434s. Group compatibility
38973732283/38973734828 each26 final completions (actions1/mixed1/group22/recovery1/
restoration1), actual PIDs13930→14322 /5236→5318. Separate source38973706184
SUCCESS release/lint; localminimum3m45s. Root14originals/167XML, both final evidence
reviews0/0 at041de29f, finalizedec025f6b. Loading destinations, delivery-unconfirmed
forwarding and intentionally unavailable-history recovery are qualified. No
unchanged rerun needed. Management dialogs keep their current work deferred.

Private photo failures and follow-ups remain qualified in canonical acceptance.
The earlier Android10 16MiB OOM cause remains unknown; later actual storage process
maxMemory is512MiB. A pass does not establish an earlier failure's cause. Additional
instrumentation status0 diagnostic messages are progress, not extra passing cases.

## Remaining #32

Native-listed mention proof, receipt permission/category controls and
recovery, other mounted forms, registration, real transport and full Android10/17
acceptance remain. Legacy unguarded forms retain work with Retry. Local injection
cannot close#32; the new configured SDK entry has no real cloud runtime evidence.
Firebase project/IAM and gateway setup remain external; the existing owner
question stays pending. Do not repeat it. Production gateway deployment/node
changes require concrete bounded separate authorization, after reviewable work.

Transport references: [local seam](../research/SOCIAL_PUSH_LOCAL_SEAM_2026_10_15.md),
[closed-app requirements](../research/NATIVE_CLOSED_APP_PUSH_REQUIREMENTS_2026_10_15.md),
[observer](../research/SOCIAL_PUSH_GATEWAY_OBSERVER_2026_10_15.md),
[Archive discovery](../research/SOCIAL_PUSH_ARCHIVE_DISCOVERY_2026_10_15.md),
[future Chat transport](../research/CHAT_PUSH_EXISTING_TRANSPORT_2026_10_15.md).
No public/private wallet data or live-node changes are authorized by fixture work.
Later source-only[FCM API observation](../research/FCM_ANDROID_API_2026_10_16.md)
confirms new FID APIs in current Messaging26.0.0 and server fid target, superseding
only the dated token API observation. That research added no SDK/config/service;
current client preparation is described above.

## Current public build

Signed beta26/0.1.26 is published and independently verified at https://davidengelen.nl/redsquare/.
Canonical #43 signed-preview26-published evidence retains thirteen hashed records,
HTTPS manifest/APK identity, package/minimum29 and immutable25 checks. Source
is e8b337a30edd70246404cf91d5ae1ca55d779aee, established certificate unchanged.
APK SHA256 c7f4c4f00262f35a4a92ea2253b4e78989c0438751fb655df51a66b8627b17a7.
Publication bookend2fff4587e has Standards0/Spec0. #43 is closed; #31 remains open
for actual configured-provider acceptance. #44 changes have not been published.
Physical owner installation/continuity remains unobserved. Signing material stays
outside Git. Follow [review-build operations](../REVIEW_BUILDS.md) for the next publication.

## Other remaining issues

| Issue | Current condition / next useful work |
| --- | --- |
| #16, #31 | #16 controlled native work is reviewed and published in25; live authorized key pending, question already asked. #31 still depends on16. |
| #17 | Frozen recovery source above; latest actual public run39004585910 shows provider sign-in check, Playing unproved. No unchanged retry. Reopened after unintended commit auto-close. |
| #19 | Owner Registry name checks time out despite authenticated peer/key mapping. Frozen pending concrete cause/change. Canonical `docs/evidence/ticket19/FINAL_REGRESSION_CHECKPOINT.md`. |
| #32 | Native/client and gateway source checkpoints accepted; configured Google delivery, deployment and physical closed-app checks remain. |
| #33, #34 | Depend on#32. Chat transport source audit above is preparation only. |
| #35 | Integrated acceptance and actual Pixel7 startup/update checks remain. Emulator results do not prove physical targets. |
| #36 | Real publisher/legal/contact/retention/declarations/submission facts remain. Read the Play research documents before release work. |
| #38 | Branding decision, not a release blocker. |

Parent#1 remains open. Original35 tickets:26closed,9open (#16,#17,#19,#31–#36).
Extra#37,#39,#40,#41,#42 are closed. Fetch actual tracker state before changing claims.
Native blocker counts rechecked after publication:16/17/19/32 have0;31/33/34
have1;35 has4;36 has1. Every unblocked original issue is already assigned and
has its specific external acceptance gate above. No next unblocked/unassigned
original implementation ticket exists. The owner renewed continue-all work;
#43 above explicitly separates the already possible native Chat GIF work
from31 live integration acceptance, without removing original blockers. Do not remove dependencies or report
Chat GIF/private/group push source complete to manufacture progress. Resume
from concrete new configuration/provider/device evidence; no repeated setup
questions, unchanged public probes or node changes.

## Evidence and environment traps

Large artifacts go under `/mnt/storage/redsquare-android-artifacts`; verify the
mount/space before large writes. Build instructions are in the operations docs.
No local KVM: follow
[local feasibility](../research/LOCAL_ANDROID_TEST_FEASIBILITY.md), use hosted KVM.
Preserve current pins. Source edits wait until any owned local Gradle build finishes.

Some earlier image previews contradicted the actual PNG bytes. When that happens,
use original hashes, XML and independent read-only pixel decoding. The corrected
[dismissal RED package](../evidence/ticket32/dismissed-reply-red/android10/README.md)
retains decoded observations and the reproducer. No source image was edited.
Do not infer a black UI or an Android rendering cause from a contradictory preview.

For earlier ticket chronology, source provenance, failed attempts or previous
session IDs, read [historical checkpoints](IMPLEMENTATION_HISTORY_2026_10_16.md).
The snapshot before the username minimum preserves the previous handoff verbatim.
Historical active/pending labels are not current authority.

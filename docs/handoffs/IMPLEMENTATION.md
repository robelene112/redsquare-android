# Current implementation handoff

Updated2026-10-20 after the owner's beta42 UI review. The functional checkpoint
is delivered, but the owner explicitly says the UI is still untidy and unfinished.
Current work is [UI55](https://github.com/robelene112/redsquare-android/issues/55)
and its audit-derived children, [Node terminology56](https://github.com/robelene112/redsquare-android/issues/56)
and [repeat likes57](https://github.com/robelene112/redsquare-android/issues/57).
Three independent whole-app audits support concrete native UI work without KVK
or provider credentials. Read the tracker for exact child scope/dependencies. Twelve audit-derived UI
tickets58through69 plus Node56 are linked under55. All three independent auditors
checked the published consolidation and found no missing material coverage.
Release36 has native blockers55 and57 in addition to its unchanged external gates.
Start structural polish with content-first shell58; implement sequentially.
The owner now authorizes implementing this whole backlog without routine stops.
Use a local cap of two new intentional likes per rolling second per active wallet
across post surfaces; this is app policy, not a network rule. It is recorded in57 comment6278005545; ready-for-agent is set. No57 code yet. Ambiguous retry must reconcile/reuse the existing candidate.

Node56 source aa40be525 passed focused Android10/17 feed/theme/public reads and
source/publication review. Ordinary45 remains LIVE;56 stays open through the
larger-font acceptance and signed UI delivery in58.

Shared frame58 remains ACTIVE. Last production change is
9b33d3fefa5d47dbaaf0f05fdfe8735938ffd1e4; subsequent changes through fixture source
cae84e5ad0bd8fc85cef6b4a452a5f0c1ac35f7e are fixtures/harness/evidence only, with
identical app/build/signing inputs. The compact frame, Home-only backup/import
notice, selected primary navigation, guarded drafts, reading-position restoration
and lifecycle-owned actual system-motion scale are implemented.

Accepted focused frame proof:39342318884(Android10)/39342315882(Android17), each
4 frame +1 reading-position +2 native motion methods. All8 actual320dp/412dp,
130%/200%,Light/Dark configurations retain48dp touch/native targets. Narrow200%
captions wrap but remain reachable. Original recordings show normal intermediate
positions and no intermediate zero-scale movement, with Settings/window0->1->0.
This is shared-frame proof, not whole-app visual completion.

Other current focused evidence, retained under../evidence/ticket58/README.md:
- Keyboard39345370402/39345373579:10PASS perSDK, real IME and exact drafts/sends.
- GIF39345377196/39345380431:3 previewPASS perSDK plus posting. Original outside8px
  samples are positioned within the screenshot by12px scrolling.
- Complete agreement39346896065/39346898616:27 accepted38 identities perSDK,
  including actual process death/recovery/restoration with unchanged wallet/draft.
- Cancellation viewport39346900869/39346903466:2PASS perSDK, physical48dp actions.
- Capacity39349185535/39349188302:1PASS perSDK,101-group pause/automatic resume,
  preserved subscription/categories/history/draft and correct node wording.
- Reports39349191022/39349193931:12 identities perSDK (11 UI +1 actual cold-process
  recovery). Original signed report survives and retry remains explicit. Existing
  harness has no separate UI adb-exit record; that absence is retained honestly.
- Social push39350663269/39350665509 at092238769:11 main +1 binding +3 workerPASS
  perSDK. Corrected no-root polling retains the same15second route goal and exact
  conversation/latest draft/photo checks. These scopes exclude process recovery
  and actual Google delivery. A root-gap duration was not measured.
Both review axes have0 residual findings for the reviewed sources/evidence.

Complete acceptance is still pending. Current independently frozen473/474 catalog
and perSDK source/run bindings are in../evidence/ticket58/full-identity-reference.json.
Complete attempts39355334984(Android10)/39355337570(Android17) at077 are both
FAILED.10 and17 Chat hit the same120second host timeout preparing promotion in
GroupChatProcessRecoveryTest, with START only and no completed preparation
checkpoint/native snapshot. Restoration method1PASS is separate from that failed
preparation.17 socialSUCCESS; wallet has all76 accepted38 identities and public
readsPASS, then fails ci-review-update's legacy APK checksum before signed-update
continuity/final diagnostic. Runner bad bytes/headers were not retained. Local
VPS5/8 files and separate HTTP200 downloads match immutable manifests, which does
not establish the runner's failure cause. No retry or relaxed validation.

Diagnostic-only5eb9fb09ecc17b6e9a4e667db23fdcd8d1ac4f04 adds constant stages with
monotonic times and pre-status/history-scroll native/all-root trees to group
recovery. Host retains stages and stops only a timed-out debug emulator process
before existing restoration. Original assertions and120second phase bounds stay.
The download observer records public HTTP/length/actual+expected hash metadata
before the unchanged checksum rejection. Both source review axes0. Focused
group-admin39358679469(10)/39358681910(17) run at5eb to locate the stall. No cause
or corrected runtime is claimed yet. Paired group-stall and17 download-failure
packets retain actual failed metadata; evidence review pending. Complete native
acceptance remains pending; the download-tail exception cannot cover this failed
Chat application gate. All historical failures retain actual outcomes.

Earlier fixturecae84e5ad scopes Add GIF to the existing composition preview and
scrolls only when hidden, then requires the original10second visibility goal.
Reply-only before/after native + all-root full-depth Compose observations on10
show Add GIF445..485 below preview329..442, then402..442 inside after43px scroll.
Every animation/quote/credit/queue/URL/signature assertion and180second class bound
remains. Focused chat-gifs39351423400(10)/39351426372(17) atcae areSUCCESS with all6PASS
perSDK and the same original bounds. Root opened both after-scroll PNGs. Fresh
complete acceptance remains pending;
no new production or signing build is needed solely for these fixture changes.

Fixture077baa5b19b495c8b0cfb3099525e630965b56c4 extends the existing index1
group-history scroll to completed private photo history. Before death, a private
queued photo remains in the composer and keeps its existing pixel assertion.
Focused39354696326(Android10)/39354698834(Android17) bothSUCCESS:4 independent
accepted38 identities,2 deliberate process deaths and2 continuity gates perSDK.
Private PIDs3448->3715/2929->4426 and group4052->4470/4683->4760 differ; original
signed raw/Relay once, photo/caption, cached history, wallet/backend and restoration
checks pass within the same120second phase bounds. Both protocols remain in
images/full scopes. Root opened final private/group PNGs on bothSDKs: red photos
visible. Compose shows the newly visible private photo after target scrolling;
10 final nativeXML agrees. Immediate10 observations and17 nativeXML remain stale
and are not positive photo evidence. Photos are partly clipped in current short
history viewports; this is recovery proof, not62 Chat viewport polish. Both
source review axes0; paired evidence review pending.

Ordinary46 is PREPARED, not published, at9b33. Actual APK SHA256
311d82372566d2eac34e983838c4507b54d4d20ab0740fada1907d58bb95cdb5,
established6ca certificate, nondebuggable/nonprofileable, backupfalse, reviewtrue,
push/PostsGIF/ChatGIF unconfigured. Both preparation review axes0. Actual shared
counter46 is already reserved. Canonical publisher and public before/after checks
remain mandatory after complete native acceptance/review. Ordinary45 stays LIVE.
Source39354689887 at077 passed core/gateway, Debug/tests, Release/Performance
and lint.
No57 code exists yet; its local policy is recorded and ready. Finish58 and signed
UI delivery before claiming another ticket, then proceed57 and59through69.

Read [context](../PROJECT_CONTEXT.md), [workflow](../WORKFLOW.md),
[operations](../OPERATIONS.md) and [OWNER_INPUTS.md](../../OWNER_INPUTS.md).
The actual provider/physical/legal/store gates remain parked under36. Placeholders
never establish real acceptance. Functional tests and old closed37/39 do not mean
app-wide visual completion. UI55 remains open through rendered verification,
signed delivery and the owner's review.

Root is the sole implementation writer. The three independent read-only UI
auditors were `ui_audit_visual`, `ui_audit_journeys` and
`ui_audit_accessibility`; their actionable results are consolidated under55.
Existing two-axis implementation review agents remain available. Preserve unrelated
untracked
`docs/research/NATIVE_GROUP_CHAT_CONTRACT.md` unread and untouched. No separate
cybersecurity campaigns, restricted-access requests, provider safeguard bypasses
or use of historical credentials.

## Current signed delivery

Ordinary45/0.1.45 is LIVE at <https://davidengelen.nl/redsquare/>. Install over the
existing ordinary app; never uninstall or erase the owner's wallet. Package is
`nl.davidengelen.redsquare`, actual APK SHA256
`129217527cb76265669a99ecdea79e009024787beacbbba1bc0f7357336eb740`, source metadata
`aa40be52500889d9e602b99198ba655864799f92`. Actual DEX review=true, Push=false,
separate Posts/Chat GIF keys absent. No configured services or final policies.

Historical timing43/0.1.43 is LIVE at <https://davidengelen.nl/redsquare/performance/>.
Independent package/storage `nl.davidengelen.redsquare.performance`, actual APK
SHA256 `dc409cfed811829738d7dbafc041a074c77574a576936187cf75d3042f260e91`, source
metadata `b5a1569de7a906786fb7518c09595a14eaa0816d`. Actual DEX review=false,
Push=false and GIF keys absent. This is the separate profiling app, never a reason
to import or clear the ordinary wallet. It does not match current45 or coming UI code. Actual Pixel timing remains pending36.

Historical42/43 publications used canonical scripts and independent actual public before/
after checks, all exit0. Actual downloaded APK/metadata/signature/manifest match
prepared artifacts. At42 publication immutable40 and the whole performance41
channel were unchanged; at43 publication ordinary42's whole channel and immutable41
were unchanged. Each publication packet has19 hashes and0/0 bookend reviews.
Prepared42/43 each retain6 hashes. Current45 uses the same canonical scripts:
5 prepared hashes,19 publication hashes, exact public APK/metadata and unchanged
immutable42/performance43 checks, all exit0 and0/0 review. See ticket56 evidence.
Do not rebuild or republish accepted artifacts.

All signed artifacts use established certificate
`6ca1209bcbba661943f7b683d48b2f58493f13851c760c8d80d1521ac0ee79a2`, min29/target36,
nondebuggable, automatic backupfalse. Ordinary is nonprofileable, timing profileable.
Historical source equivalence verifies the same258 production/build objects across accepted
About38 production/full116e/42/43/offline44; object-list SHA256
`1842cb1d3610641cd8a60cd85092296e415d61aa6f3520a9bf2f724b00724421`.

Offline Play44 is prepared only, never published or submitted. Source metadata
`c8d7222d1e8bcd9705c415009c2999b7c2132ce0`, AAB SHA256
`ed81b15ee9afdd21a3ed9edb30ef8dc32e4dd4e6e08ca11b6f83678be8bf5a3b`.
Canonical bundle command exits0; strict JAR/bundletool/local APK/signature/manifest/
DEX/16KiB ZIP and64-bit LOAD checks pass,14 public-safe packet hashes,0/0 review.
Review=false, providers absent and policy/contact readiness draft. It is not ready
for production submission. Renew candidate and relevant checks after real config
changes. Shared counter46 is verified, and46 is already reserved for the prepared beta.
Let the canonical builder allocate any later version; verify the counter before use.

## Complete current-code verification and About38

Native38 is CLOSED afterb1b25ba90. Provisional app name remains Red Square; later
own-name branding stays in OWNER_INPUTS. Productionacb08e820 adds the inline About
card at the bottom of existing Settings: platform, independent Angels’ Foundry
and installed version. No external link, SDK or permission. Real company identity
remains unconfirmed. Fixed review base3c15204aa. Evidence retains actual RED and
initial partial-card focused captures; fixture-only116e scrolls the complete card
before capture without changing production, old assertions or deadlines.

Both complete suites execute exact`116e92935b7287e9157b30c78b8e8f1d4ad7b800`:
- Android10 run39310202005 completedSUCCESS:466 exact identities,465PASS plus
  the sole established API29 Profile assumption,119 normal completions,
  34 deliberate interruptions and33PASS continuity records;470 packet hashes.
- Android17 run39310205648 completedSUCCESS in all3jobs:467PASS,120 normal,
  34 interruptions and33 continuity;500 hashes. Chat183/Social208/Wallet76.
- Independent expected catalogs were committed before execution. Root opened six
  relevant original PNGs per SDK including complete About/three themes. Original
  hashes/PNG decoding/separate XML qualify repeated viewer previews; no image
  edits or inferred app cause. Only five known optional53 observer gaps recur.
- Combined source/full review at00d8e4e09 and publication42 review atb1b25ba90 each
  report Spec0, documented Standards0 and heuristic0.

Fresh matching43 focused runs both completedSUCCESS at exact
`b1b25ba90998858ffc0bc5fa584e40b224cdfe0a`:10 run39321146982,17 run39321151044.
Frozen plan43 predates execution. App/harness diff against accepted116e excluding
documentation is empty;258 production inputs match. Each exactly covers11PASS
native feed/public-read identities plus3PASS signed observer identities, three
normal completions per group. Actual installed35/43 hashes match preparation.
Distinct UIDs/wallets, preserved ordinary35 public address, restoration of both
originally absent packages and zero cleanup errors pass. Both public feeds show
posts. Each validates21PNG/13XML; root opened six originals per SDK. Only four
known absent failure-only wallet captures recur. Packet hashes67/69 respectively.
These are signed35/43 emulator checks, not ordinary42 physical update or timing.
Final combined retained-evidence/status review against78c5d8779 reports Spec0,
documented Standards0 and heuristic0. Both axes independently verified new packet
hashes/original bindings, frozen identity coverage, installed artifacts and tracker.

Canonical evidence: [About38](../evidence/ticket38/README.md),
[timing35](../evidence/ticket35/README.md),
[offline44](../evidence/ticket49/prepared44/README.md) and
[Play preparation](../release/PLAY_RELEASE_PACKAGE.md).
No complete native suite needs repeating without a new change, failure or concern.
Production changes require fresh complete verification on both SDKs. The active58 source changes need fresh verification appropriate to their actual impact.
Latest completed focused39333368227 runsd958685f0 (3PASS). Prior source runs may be cancelled by
newer commits; a cancelled source run is not acceptance.

## Tracker and actual release boundaries

At the prior functional checkpoint, original native tickets2through35 and the
then-existing supplementary work including38 were CLOSED. New UI55 children and
repeat-like57 are OPEN. The earlier only1/36-open statement is superseded.
Native16/17/19/31/32/33/34/35 each had an individual0/0 review and commit,
with exact actual-provider/physical requirements visibly transferred to36 before
closure. Original criteria remain in each ticket. This is development staging
requested by the owner, not a declaration of production readiness.

[Release36 parking matrix](https://github.com/robelene112/redsquare-android/issues/36#owner-directed-parking-of-external-acceptance-2026-10-19)
retains13 unchecked gates: GIPHY_POSTS, GIPHY_CHAT, VIDEO, REGISTRY, SOCIAL_PUSH,
PRIVATE_PUSH, GROUP_PUSH, PIXEL_CONTINUITY, PIXEL_CACHED, PIXEL_FRESH, PUBLISHER,
POLICY_OPERATIONS and FINAL_SUBMISSION. Configured SDK cold startup/register/
unregister/FID/callback rotation/interruption/locked/Doze requirements remain.
Actual embedded video remains unproved and the earlier public run remains FAILURE;
Owner Registry availability is unproved. Neither is silently deferred from scope.

KVK is unfinished. Angels’ Foundry is the selected publisher; no legal identifier,
contact or provider credential is invented. OWNER_INPUTS is the maintained human
checklist. Technical provider verification remains agent work once applicable.
Actual contact/moderation/privacy/deletion/hosting procedures, final public policy
and store/account acceptance remain open. No mid-development setup question needed.

Pixel protocol requires five cached launches each≤2000ms and five fresh isolated
launches each≤5000ms on Pixel7, stable Wi-Fi/normal animations/no debugger, positive
isolated PID absence and candidate/config matching. Never import/clear the ordinary
wallet. See[protocol](../research/FEED_PERFORMANCE_PROTOCOL.md). No physical, ARM64,
configured Google/provider delivery or Play installation result is claimed.

Ordinary signed static APK publication is authorized. Native/placeholder preparation
does not authorize live node/cloud/IAM/provider/public policy/store mutation or
outbound contact. Follow the concrete operations/publication procedure later.

## Environment and preserved history

Netcup152.53.197.181, hostnamev2202609429510531338, is production. Old Hetzner
writers stay stopped. Verify actual mounts before large writes; artifacts belong
under`/mnt/storage/redsquare-android-artifacts`. No local KVM; use hosted KVM and
canonical bounded Docker builders. Preserve pins; known SDK XML/TransformableState
warnings are nonblocking. APK/AAB/private signing material never belongs in Git.

Raw current complete artifacts are`about38-full10-39310202005` and
`about38-full17-39310205648`; focused43 artifacts are
`about38-isolated43-android10-39321146982` and
`about38-isolated43-android17-39321151044`, all under that storage root. Downloads
exit0 before strict retention. View original screenshot bytes; do not edit them.

The prior detailed handoff at`78c5d8779:docs/handoffs/IMPLEMENTATION.md` retains
historical40/41 and54 chronology. Their accepted evidence remains intact. Obsolete
ordinary39 was never published; never publish it. Historical active/pending prose
does not override current tracker/source/evidence. Native UI and repeat-like work
is independently actionable as recorded above;
read current issue bodies/dependencies before resuming. Keep real release gates
separate from functional and visual development acceptance.

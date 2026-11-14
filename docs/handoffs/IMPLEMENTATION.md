# Implementation handoff

## Current planning: combined Vault and Store #105

The owner approved both completed grills and the shared scope on 2026-11-13.
[Spec #105](https://github.com/robelene112/redsquare-android/issues/105) is published and labelled
ready-for-agent. The owner approved the ten-ticket breakdown and requested
the complete build on 2026-11-13. Native sub-issues #106–#115 and all blocking
edges are published and verified. #106–#110 are implemented and verified on both SDKs. #111 is also complete and verified: native
image/file creation, selling, storefront management and recoverable delisting. #112 alternate payments/recovery and #113 seller proceeds/withdrawals are complete. #114 is next. Continue the approved ready frontier
through the combined beta without per-ticket implementation questions.
Source snapshots, publication receipts and working material remain external
under /mnt/storage/redsquare-android-artifacts/vault-store-planning. Use #105 as
the canonical scope. No additional beta has been built or published. The delivered baseline remains
beta 127 with Talk #104 complete.

Vault is first as the file/key/rental foundation, then integrated Store flows,
with one combined completed signed beta. Buying/selling, renting/renting out,
new image/file creation, all offered payment methods, native links and unsent
seller Chat drafts are included. Vault also stands alone with standard/custom/
rental access, Advanced source/witness editing, explicit offline files and
Android Share. Official storage compatibility is accepted, with provider-visible
content and no promise to recall downloaded copies. Reuse the existing native,
pinned-protocol, owned-reference/provider and signed-update testing boundaries;
do not ask the owner to repeat development phone QA. No new production node,
public Saito financial write, community recipient or provider/Play operation is
authorized. Other parent and real-release prerequisites remain open.


## Active integrated rental slice #114

114 is assigned and open; blockers108/113closed. Review base
`e5669b50f55aad4620a4527a5f6304de268843c4`. Current committed source
b6b9c0bdc322d9b35ecca5c5b20b85ac34429a80, with in-progress native/core changes.
Three independent actual-source/WASM rental mint/listing/purchase vectors are
added by the existing reference producers. SaitoImageMint was renamed to
SaitoStoreMint to share exact encrypted original mint recovery with rental stock;
legacy image bytes and journal names remain unchanged. Actual rental restore
RED, listing/delegation RED and order-bound entitlement RED each precede GREEN.
Current full core first run store114-core-first.log passes (218cases/44suites).

Hosted native entry43599459521 atb6 fails the intended missing Create rental
control. Whole original artifacts/captures are retained under
vault-store-planning/store114-native-entry-red10-*. First collector used a wrong
expected source and failed its assertion; corrected collection uses actualb6
above, not guessed identity. Prior dispatch43599207554 at66d was intentionally
cancelled before native acceptance because the core listing tracer was stillRED.

Native entry/creation is now implemented in the working tree: persisted rental
mode/hours, creator-owned master selection, separate cover, immutable disposable
mint and actual deposit, shared PIN/original-reservation recovery. App compiler
store114-rental-entry-build.log is running; no edits to its inputs until complete.
Need inspect actual result and preserve original image labels/assertions. Native
tracer currently covers only entry, not complete mint/listing/renter lifecycle.
Do not claim114complete or build a partial beta.

Important pinned source observations: Store sets expires_at at initial listing,
then preserves it in fulfillment. Show actual end time; do not promise hours
starting at payment. Rental path FIRST undelegated hop determines renter/expiry.
Archive.updateTransaction requires supplied script hash to match current owner.
After initial checkout changes owner from master hash to loan hash, replaying
master checkout cannot succeed. A later rental cannot replace the old loan by
that same existing handler. Creator restoration needs the actual previous loan
path; ordinary master access cannot read the changed archive owner. These are
observed source constraints, not yet a full runtime diagnosis. Execute actual
Vault/Archive/SQLite/WASM provider semantics before choosing a compatible recovery
patch. Similar to112, any required compatibility artifact stays UNDEPLOYED with
STORE_LIVE gates; no node/config/DB/provider activation or public financial writes.
Also inspect multi-unit remainder: official fulfillment appends an undelegated
renter hop to shared metadata, which may retain the prior renter in a later sale.
Do not silently reduce the approved rental/payment scope.

Remaining114: actual offered currency rentals and seller settlement, exact
entitlement/provider checkout/read/expiry/creator/delegation/recovery, direct
Order-to-Vault and native/browser owned proofs, current complete Seller/Purchase/
Vault regressions and both independent review axes. Then115fullpolish/frozen
catalogue/full10+17/motion/keyboard/TalkBack/signed127update/exactpublicbeta.
User approved114then115without more confirmations. Public beta127 unchanged.

## Completed seller currency/proceeds slice #113

Review base4f7b9923ea214b18cb76a9193c238ae232839b84. Final product
9452b92ee152f764c94b28244de8c6320191ade6. Both independent source axes report
zero remaining findings. Core215/43passes. Complete current-source Seller10/17
43597074144/43597074058 each pass18cases/24cells/two3-phase process proofs.
Complete Purchase10/17 43597073961/43597073910 each pass34cases/36cells/both
3-phase process proofs. Focused history10/17 43597074151/43597074071 pass2cases.
Every original digest/member is exact. See ../evidence/ticket113/README.md and
its run ledger for all source-qualified failures, reviews and service limits.

Actual seller currencies, pending/eligible proceeds, signed whole-eligible
quotes, real device authentication and exact manual-withdrawal recovery are
implemented. Recorded request is not actual external payout or SAITO-fee chain
inclusion. Missing external net quote remains explicit. Paged history preserves
legacy100records, uncertain originals and cross-page fee reservations. Actual
513-sale and101history source/SQLite/WASM fixtures cover the scale regressions.
Authenticated orphan-page recovery handles absent/partial index commits without
changing originals or financial authority. Actionable rows stay above remote
balances. Earlier local daemon/client failures are not accepted as passes;
hosted current-source builds compile and execute all tests.

113 development is complete; STORE_LIVE/OWNER_INPUTS retain real manual treasury,
provider/funding/operator/community-runtime/ARM-OEM acceptance. No activation or
public financial write. The combined beta remains115, public beta127 unchanged.
Next ready slice114: integrated Vault/Store rentals and actual access lifecycle.
Continue114then115without another owner permission question.

## Completed alternate-payment slice #112

Review base: `a60cd323cf0c46b965fbbc986394d77a73ada2fb`.
Final product source51bd948439727262ecbd1386cbd8f8881f10fbe4; final observer7a17cf8bcd233bf673c9e40395f509034c10d02e.
Both source-review axes report zero findings. Core209/42 and main/native compilers
pass. Complete10run43582567828 at7a and complete17run43580633625 at51bd each pass
34ordinary cases,36viewport cells and both three-phase actual process proofs.
All99/101ZIP and403captures per OS are exact. Separate7a17probe43582567886 passes
24cells. The reviewed test-only production-object bridge is explicit; it is not a
complete7a17run or assembled prefix. See [the112packet](../evidence/ticket112/README.md).

All seven source methods have native reviewed/PIN/invoice/exact-delivery coverage.
Immutable original orders recover read-only through late/expired/closed invoices,
outages, conflicting replies, remote treasury copies missing and actual process
loss. Unpaid history is distinct from payment/settlement/delivery. Definitely
unsent orders can be discarded. Public SAITO/USDT remainders authenticate without
the prior buyer's private invoice, including an independent different buyer.

Preserve every source-specific original failure. The final native observer enters
text only after actual focus/IME readiness and verifies quantity around dismissal.
The exact prior reset cause is unproven. EncryptedLocalFile now allocates actual
bounded open-file length after an inherited read loop exposed5MiB allocations
under16MiB memory. GCM/AAD/limits/format and all original deadlines remain intact.
Full shared-application regression remains115. No hosted/local run is active.

Provider compatibility artifact is source-only and not deployed. Live activation
requires one ledger writer or equivalent coordination. Actual funding/provider/
community-runtime/OEM gates remain under36; public observation enabled:false.
No public financial write or new beta. Continue113, then114, then115 and the one
combined signed beta. Owner phone review is for that release, not development QA.

For115polish, inspect the quantity IME action/lower purchase controls while the
keyboard stays visible, along with111's image-preview/cost placement and actual
normal/reduced motion. Observers explicitly close IME and do not establish every
control reachable while it remains open. The native drag-handle tooltip is visible
in viewport captures; distinguish real user interaction from test focus effects.

## Completed Store selling #111, next #112

#106–#111 are implemented and verified. See [111 evidence](../evidence/ticket111/README.md).
Review base: `c6157dd47a469923b3c7e9a1511cb27742c87f18`.
Implementation: `696a1a4759ccad3fa0e30248abfd990bb5d6c1f5`.
Both final seller runs `43562045080` / `43562098065` pass nine cases, twelve viewport
cells and all three distinct-PID process phases. Both final catalogue runs
`43562047939` / `43562050409` and earlier wallet regressions pass. All original
artifact/capture bytes are checked. Core 201 cases and both independent reviews
pass. Preserve the ledger's original failures, observer-exit and protocol/runtime
qualifications. No public financial write or beta delivery is inferred.

Continue 112–115 sequentially without another per-ticket confirmation. #112 owns
all actually offered alternate-currency buyer methods and recoverable payment.
The public beta remains 127 until 115's combined signed delivery. For final native
polish in 115, inspect image-creation cost visibility: the large preview can put
the deposit/fee below the first viewport, although the body is scrollable. Final
normal/reduced motion, viewport/keyboard/accessibility and full shared-source
regression/update gates remain required. Ordinary QA stays agent-owned.

## Completed Store purchase slice #110, next #111

#106–#110are implemented and verified. #110is closed;#111is next.
Review base:8810152674202ff0ce09c4ce59926026624b3e87. Purchase source:
c2baf1dd517834fd0a4c52ac96a7a2fcbaeeef24. Final media-test/evidence source:
29fd040163c5e36e407d438a20917f5026fc25b9. Both main production trees are identical.
See [110packet](../evidence/ticket110/README.md) and all original failed sources.

Both purchase runs43547321240(10)/43547323949(17) pass12ordinary native cases,
12actual viewport cells,3separate distinct-PID process phases and187core cases
with0ignored/failures. All75/77ZIP and156captures perOS are byte-exact. Complete
wallet regressions43547485799/43547488665pass; all87/89ZIP and542/652captures are
exact. Complete final catalogue runs43549190857/43549193046pass8classes/13cases/
12cells;84/86ZIP and139captures perOS exact. Both review axes:0remaining findings.
The combined24-cell action matrices and representative financial states/final
red covers are inspected. Viewport proof declares zero recomposer/system motion,
not production clock/OEM acceptance. No successful partial tails are assembled.

Implemented: exact SAITO escrow/funding/fees, deliberate review before PIN/signing,
wallet-wide durable original-order reservations, separate payment/settlement/current
asset observations, read-only recovery/refunds and responsive Orders. An obsolete
reader cannot overwrite newer durable results:context/job/read generation checks
run under SaitoLocalState.lock immediately before persistence. Older-history
continuation is durable/prioritized; unbridged receipts stay historical. Exact
creator/asset/quantity/positions are required. No count-based arbitrary-NFT success.
Store fulfils NFT/price buckets, so native review discloses matching-seller stock.

110is closed with its accepted evidence;verify111native blockers=0and claim it. Continue
111–115sequentially without another owner permission, then one combined beta.
Public beta127 remains;none built/published for Vault/Store. Keep1/36/55/84/86open.
115must append110's3ordinary classes/12methods and all3StorePurchaseProcessRecoveryTest
phase identities to the frozen full catalogue alongside106–109and later slices.
Real funded/provider/full-community-runtime results remain STORE_LIVE. The pinned
refund reference needs its explicit input-copy adapter; do not claim a deployed
refund or activate nodes/providers/spend public funds to replace owned proof.

## Completed Store catalogue slice #109, next #110

#106–#108 are closed and verified. #109 is complete and ready for tracker closure; #110 is next. Review base:
`4c6a2d91a69e14ce4468a15a6ced737945771d7a`. Continue the entire approved
#109–#115 sequence, one issue at a time, then one combined beta without asking
for per-ticket permission. Public beta127 remains unchanged.

Current implementation HEAD: `4b231c758e9f11e0cb20db097990ef5368b35bec`.
Core has179passes/0ignored; main and AndroidTest compile separately. Both review
axes report0remaining findings in the full-base implementation and all later
deltas. Source `8ae4b3c28604f5253c1391bf42e3a21cfd49d2d8` has complete successful
Store runs43534824427(10)/43534821192(17):8classes/13cases,12actual viewport cells,
83/85ZIP and137/137captures all byte-exact. All rendered product viewport
combinations, image/history, private origin, timeout and anchors were inspected.
Both actual anchor receipts are411dp before/after product and module returns.
Stack-link regressions43533770667(10)/43533766215(17)pass on unchanged shared
production source8fc3aa351, exact receipts/captures inspected. Keep every original
failure and its qualification in [the packet](../evidence/ticket109/README.md).

The final detail-sheet source4b231c758 has complete successful native Store runs
43537032310(10)/43537029251(17), each8classes/13cases/12viewport cells and179core
cases with0failures/ignored. All83/85ZIP and139/139captures are byte-exact. Both
actual anchor receipts remain411dp before/after. The final sheet shows selectable
exact references with reachable Done; actual final images are inspected. Both
review axes report0remaining findings after the complete base and final deltas.
See the accepted packet for every original failure and precise qualification.

Close109with its implementation/evidence, verify110native blockers=0, assign110
and implement SAITO buying/orders next. Then111–115, one issue at a time, with no
new owner permission. Ordinary development QA stays agent-owned. Public beta127
remains until the single combined115delivery; no intermediate catalogue beta.

Actual public Store read on2026-11-14 uses a fresh disposable identity, returns
24rows and the exact native core parser accepts24/ignored0; see public-read.json
in the packet. This is read-only transport+JVM parsing, not an Android public-node
journey, unspent custody, fulfilment, payment or full initialized frontend proof.
The key was not stored/logged. Owner-node Store is unavailable; never auto-select
or activate a service. Exact service advertisement is case-sensitive `Store`.

Implementation preserves exact signature targets, original signed asset/terms/
image, selected-node stock report, seller active/sold filters/empty identity,
temporary URL-node context, encrypted unsent seller drafts and immediate nested
origins. Root linked-state holder prunes abandoned scenes to the live predecessor
chain/current Stack origin after identity/node readiness. Released StackLinkEntry
serialized fields are unchanged. Defer catalogue measurement until the real page
exists; internal popped paths are pruned and bounded. Catch nested peer timeouts
then ensureActive on the enclosing coroutine so real cancellation stays obsolete.
The common Chat fixture wraps owned setup and independently restores state despite
assertion failure. Actual FrameIconButton names are semantic text (Back), and the
profile navigation name is Your profile. Use real lazy-list scrolling before
locating lower/higher controls at large font. Heavy local core/main/AndroidTest
compiler phases must run separately; one combined daemon loss is not evidence.

References execute isolated pinned/deployed routes, browser builders/serializers,
and actual WASM0.3.15 listing creation with qualified wallet adapters. Basic listing
has4inputs/4outputs (a normal zero slip as well as the NFT tuple); P2SH is00+hash.
Two signed alternatives at2.5/3.75SAITO share the same owned original input and do
not assert simultaneous unspent listings. The owned transport peer identity is
not the reference Store identity. Financial tickets must bind the actual Store
counterparty and current state; original-term reading is not checkout authority.
Source pin0db0062d873369dc013046ed77c97410b5683b64; all working artifacts external
under vault-store-planning. Preserve provider/device/full-client qualifications.

Ticket115 must append every new106–109and subsequent native class/method/process
identity to its frozen full catalogue before complete final shared-source proof,
actual signed127→new upgrade and exact public APK/manifest/page delivery. Preserve
all inherited deadlines and qualifications; no assembled successful partial tails.
No node/provider/Play activation, public financial writes or community sends.

## Completed upload/key slice #107

#106 and #107 have complete focused Android10/17 acceptance and independent
reviews. See [106](../evidence/ticket106/README.md) and
[107](../evidence/ticket107/README.md). Final107proof source:
`f9225502dab0f2a204534160e8c7282400557ff3`; runs43466897529/43466903331.
Each includes17native cases in12classes,12viewport cells and the three-phase
real-process protocol. All160core tests and affected import/Talk/Chat/Fileshare
regressions pass. Both review axes report0remaining findings. Preserve original
failed runs and their exact qualifications, particularly real Android10 keyguard
handling and retained Offline-filter observations. The native file cap is4MiB;
WASM factory compatibility is not a funded consensus mint or real provider proof.

Continue109–115 sequentially without another owner confirmation. #108 used the
107closure commit as its review base. Ticket115 must append all new106/107/108classes/process gates
to its frozen full catalogue, preserve inherited deadlines/outputs, and perform
complete final shared-source application and signed update/public delivery proofs.
No Vault/Store beta is built or published; public beta127 remains. VAULT_LIVE
stays unchecked in36/OWNER_INPUTS.md. Do not activate providers or spend public
funds to replace controlled verification.

## Completed advanced Vault slice #108, next #109

#108 complete product source:`e7f4a5d93cb8205d658e1d461f25b1a04863c47d`;
review base:`acf61b62e5370afa7afc16bc6e2eeabc82db1422`.
Both complete same-source runs43513916895/43513920607 pass23native classes,
38cases,36advanced dialog cells, inherited106/107read/upload cases and3-phase
actual-process recovery. Distinct PIDs retain the original candidate/file.
All172core cases pass, no ignored tests. Both review axes report0remaining
findings. No partial/tail assembly. See [108packet](../evidence/ticket108/README.md).

Implemented: strict compatible custom source/witness editor and native presets;
whole-key permanent transfer/reservation/PIN/original retry/exact inclusion;
creator-bound rental-master mint/read and signed path/first-renter/expiry/loan
foundation. Independent WASM vectors verify numeric/script commitments and
standard/custom/rental mint and transfer compatibility. Review/native regressions
fix full-size custom candidate recovery, official output numbering0/1/2 for
held tuples3/4/5, lost custom/rental upload-answer reads, duplicate Wallet test
calls, retained title reachability, and cancellation dropping replacement file
discovery. All original failed runs and qualifications remain retained.

Continue109–115 sequentially without another confirmation, then one combined
signed beta. Public beta127 remains; none built/published for Vault/Store yet.
115 must append all106/107/108classes/process methods to its frozen catalogue,
including both rental-master and discovery-restart methods, before complete
shared-source application, signed-update and public delivery verification.
VAULT_LIVE in36/OWNER_INPUTS remains unchecked for actual provider/funded chain,
permanent transfer/rental, ARM/OEM and fully initialized community-client results.
Do not activate nodes/providers or spend public funds. Keep1/36/55/84/86 open.

## Delivered native Saito Talk #104

[Signed beta 0.1.127](https://davidengelen.nl/redsquare/) adds direct audio/video
calls from private Chat and group rooms with invitation links. It supports
three-party media, participant changes, microphone/camera controls, camera
switching and audio output. Active calls have Android return/hang-up controls.
Voice continues in background and with the screen off; camera pauses outside
visibility. Process loss interrupts the call. Wallet, node, drafts, Chat,
Fileshare and native navigation are preserved.

Compiled product/signed APK and update evidence use
`3ad638b33be448a2672f7907590c8971a8ba774c`. Complete verification uses
`96c71daec98336457721a189b3423019d9b74410`. Exactly four test/fixture files
differ; all 223,477
other Git entries, product/resources/build/harness/frozen inputs are identical.
Independent static reviews reproduce the canonical LFdigest. The test correction
positively retires old routes and observes a fresh held handshake while 7 likes
are displayed and 0 frames sent, preserving the 2-second immediate-count and
20-second transmission goals. A held Relay reply also leaves the reader free
for exact ACK8810 within the original 5 seconds, retaining the 15-second gate and
private reaction/forwarded-state assertions. Owned reply workers release and
terminate before local state restoration. All 150 core tests pass. Original
negative results stay retained without reusing successful partial partitions.
Android 10 run 43370655497 passes 687 identities/275 normal
completions; Android 17 run 43370658411 passes 688/276. Each verifies 254 classes,
212 archives, 39 deliberate interruptions and 38 continuity records using the
unchanged frozen collector. Independent Standards/Spec reviews report zero
remaining findings and accept 2082 complete indexed originals plus 96
signed 83-to127 update originals. Inherited API29 Profile assumption, unavailable
Chat archive, four missing failure-only captures and API29 permission-shell
qualification remain explicit. No partial SDK assembly or goal/deadline waiver.

Pinned Saito callback routing now uses one authenticated physical node connection
per wallet/exact endpoint with independent feature lifetimes and process-wide
positive nonwrapping request IDs. Random seeding reduces, without guaranteeing,
cross-process uniqueness. Passive saved-work observations retain unresolved
posting errors until verified durable progress. Native positive error/Retry,
exact original recovery and real before/after receipt process loss pass.
Original failed/cancelled evidence is retained; the earlier blank-wallet cause
remains unproved.

Actual public 91,852,583-byte APK SHA256:
`adb4b1a58c03e399f08107f0a6add82985d23df720a4c7900812417bd4fc1b1b`.
Established signer, optional camera/microphone hardware, exact public latest,
manifest, notes, page and downloaded bytes/signature pass independent delivery
reviews. Candidates 117–126 are superseded; 121 has no package.

Owned peers execute pinned official Saito bodies with explicit adapters and actual
transport/media observations. Real ARM/OEM audio/video quality, selected-node
Relay/Stun, real TURN/NAT, fully initialized community frontends and default
third-party HTTPS association remain OPEN as TALK_LIVE under #36. Provider, Play
and closed-app gates stay open. No node/provider/Play operation, public Saito
write or repeated owner-phone development QA is claimed. Keep #1/#36/#55/#84/#86 open
and stop after #104.

Review base c02fa1e3fb795b7e252f0973235bfc852ec891b4.
Sole writer /mnt/storage/redsquare-android-artifacts/redesign86 on
codex/unified-redesign. Canonical checkout and unrelated work preserved.
Raw originals and helpers are external under talk-native. Implementation and
evidence were pushed to codex/unified-redesign at
`e34f1fb20e9c393ec1f6a3b292d81e21f472f970`. Issue #104 was closed as completed
at 2026-11-13T07:11:25Z after actual public delivery and branch verification.
The final documentation attestation is current HEAD; no product or test inputs
changed during closeout. See [closure receipt](../evidence/ticket104/closure.json).
Parents #1/#36/#55/#84/#86 remain open. Stop after #104.

## Delivered native Fileshare #103

[Signed beta0.1.116](https://davidengelen.nl/redsquare/) is public. Private Chat
supports native compatible live files: system picker, explicit acceptance,
progress, exact Cancel, background/screen-off notification and Save as. Both peers
must be online; no offline delivery or transparent process-death recovery claim.

Review base d46cce88b800639ffffe1ae60af5ae509e121676. Compiled product/source
0e7249cba0209c61a33e91f76245ae45fabac621; verification17 is test-only
f01ae5654e2b01ef40321ab440664c57fe40edda. Exactly PolicyCenter, SocialPushTap
and PostComposerFocus AndroidTest observations differ. Production/build/harness/
other tests are byte-identical, all original assertions/bounds retained. Original
composer transition cause remains unproved; failed/cancelled runs stay retained.

Both complete3-job SDKs and UNCHANGED frozen collector PASS:
10 run43140879123,663identities/239classes/197archives/259normal;
17 run43150151913,664identities/239classes/197archives/260normal.
Both38intentional interruptions/37continuities, with inherited qualifications.
Both independent final acceptance reviews verify1,938 complete originals and96
signed83-to116 update originals. Focused proofs cover all nine file classes,
12viewport cells,4MiB exact unpaced browser bytes, real screen-off and seeded
separate-PID recovery. Browser class bodies/native fixture signer are controlled;
not a fully initialized community webapp or live-peer process-death experiment.

Actual signed updates retain wallet, exact draft/cache/preference and debug
isolation. Candidate116 is91,442,703bytes, SHA256
`3e412f1f4564a4c42b5996d32a44017c34c54c516412bc6f6cdd0d9092077e64`.
Public latest/immutable manifest/notes/page/download match at05:09UTC November12.
Delivery receipt: docs/evidence/ticket103/delivery116/verification.json.
Both postpublication independent delta reviews PASS at05:12UTC, recorded in
delivery116/reviews.json. Issue103 implementation, acceptance and delivery are complete. Never promote
113/114/115. No new candidate build is required for docs/test-only changes.

Sole writer checkout /mnt/storage/redsquare-android-artifacts/redesign86, task
branch codex/unified-redesign. Implementation/evidence and final closeout receipts are on this branch; the
final closeout documentation commit is current HEAD. Raw artifacts stay external under
/mnt/storage/redsquare-android-artifacts/fileshare103. Canonical checkout and
unrelated work preserved. Monitoring sessions are completed, no build/test active.

FILESHARE_LIVE is retained unchecked under36 and OWNER_INPUTS; the exact parent
row is preserved in release36-qualification.json. Issue103 is CLOSED as completed
at05:14UTC November12 with the committed acceptance/delivery proof in its body.
Parents1/36/55/84/86 were verified OPEN after closure. STOP until owner
requests another ticket. No node/provider/Play operation or public Saito write.
Do not ask owner to repeat ordinary phone QA. The maintained data-flow inventory
has a dated Fileshare addendum; final operator/retention/declarations stay under36.

## Delivered native Stack links #102

Signed [beta0.1.112](https://davidengelen.nl/redsquare/) opens approved public
Stack article, author and root links inside the native module. Exact signed
article lookup does not depend on the newest discovery page. The URL source is
visible without overwriting the saved node; Back restores the originating post
and reading state. Unavailable articles retain Retry and an explicit website
Open original. Ordinary external URLs and deliberate publication/access stay
external. Loading-image Retry taps cannot activate the parent card; a loaded
image remains a normal article link.

Complete three-job Android10/17 runs43073588639/43069531194 pass with652/653
identities,229classes,195archives,248/249normal completions,37deliberate
interruptions and36continuity records. Acceptance is explicitly frozen goals
verified with the independently reviewed archive-partition catalog erratum:
the new Stack-link archive was catalogued under Social but the immutable harness
runs it in Chat. Both original verifier failures and frozen files are retained;
all194 inherited paths/goals/deadlines remain. The inherited API29 Profile
assumption, unavailable Chat archive and four failure-capture qualifications remain.

Compiled source0fd2133e7 is also the complete17 source. Complete10 source8612758a2
changes only the synthetic draft cleanup hook to use the existing store lock;
all native test bodies and production/harness are unchanged. The reviewed source
bridge preserves the actual earlier failure and its unproved precise cause.
Actual signed83-to112 updates pass on both SDKs, retaining wallet, exact unsent
draft, offline post, Light preference and distinct debug identity after cold launch.
Both independent final packet reviews pass after verifying1884 complete-run and
146 signed-update originals. Candidate111 is superseded and must never be promoted.

Public latest, immutable manifest, notes, page and actual downloaded42,683,434byte
APK match SHA256`6ba40a6b25f83e204339e570a2149931470183baed5f0f9b1ef675d007b0b4cb`
at19:21UTC November11. The [ticket102 packet](../evidence/ticket102/README.md) records source,
qualified complete results, original failures and delivery receipts. No
owner-phone/OEM, provider, Play, live Saito write or production node change is
claimed. Keep #1/#36/#55/#84/#86 open and stop after102.

## Delivered fast-scroll correction #101

Signed [beta 0.1.110](https://davidengelen.nl/redsquare/) removes a measured
main-thread wait while drawing post actions. The reactive busy-status read now
uses SnapshotStateMap directly instead of waiting on the lock held by background
durable storage. Actual operation claims, identity changes and journal writes
remain serialized. Appearance, animation and transaction formats are preserved.

Actual traces identify a34.4ms busy-read wait behind FileDescriptor.sync. The
controlled native regression fails on old source at8375ms and passes on Android10/
17 at163/434ms while the owned durable transaction remains pending. Three
post-fix scroll rounds contain zero such monitor-contention events, versus4/1/3
before. Cold emulator timing remains mixed, including a worse151.73ms sample.
This establishes removal of one measured stall, not physical smoothness or a
controlled general FPS gain. No profiling SDK is added to the ordinary APK.

Both complete three-part Android10/17 runs43040719802/43040725444 pass at
`75920db2d63eff0d3cc5b2e621609c5bd13349ac`. The unchanged pre-run101 verifier
accepts651/652identities,228classes,194archives,247/248normal completions,
37deliberate interruptions and36continuity records. The inherited API29 Profile
assumption, unavailable Chat archive and four failure-capture qualifications
remain explicit. Actual signed83-to110 updates43038821274/43038826400 preserve
wallet, exact draft, cached post, preference and separate debug identity.

The APK is42,650,666bytes, SHA256
`dd548a3f6d4abb37b94aad25d1073e2be428c30376e79a3fdb0b776201b37414`,
compiled from`64494ef9e`. Reviewed source bridges retain that exact artifact
through CI dependency provisioning and one existing JVM observer correction.
All original failed/cancelled runs retain their outcomes; none contributes partial
acceptance. Both independent final acceptance and delivery reviews pass with zero
findings. Public latest, immutable manifest, notes, page and downloaded bytes match.

The [ticket101 packet](../evidence/ticket101/README.md) records original evidence,
source bridges and limits. No owner-phone QA, live write, node/provider/Play
operation or physical/OEM acceptance is claimed. #101 is complete. Keep
#1/#36/#55/#84/#86 open and stop until another issue is requested. Preserve the
canonical checkout.

## Delivered robust display names #100

Signed [beta 0.1.109](https://davidengelen.nl/redsquare/) resolves available public
author names across feed, conversations, profiles, activity and Chat. The reported
keys resolve to digirok@saito and CryptoKen@saito through the official Saito name
directory. The owner node returned empty reverse maps for both accounts during
actual read-only comparisons.

Directory labels are separate from authenticated registered ownership. The app
retains exact public keys for identity and actions, shows name provenance in
Identity details, and preserves the strict current-registrar verification and
verified-name cache. No historical signer was added to trust. The optional
saito.io directory uses a separate read-only identity and can be disabled under
Settings, Node without changing the selected content/Chat node. Provider caches
are independent; visible names retry after transient errors and survive ordinary
navigation and offline reading. Draft data-flow documentation records the lookup.

Both complete three-part Android 10/17 runs pass on `759f46f7e`: 650/651 frozen
identities, 227 classes and 193 archives per SDK, with all inherited goals and
deadlines retained. Both independent final Spec and Standards reviews pass with
zero actionable findings. Actual signed 83-to-109 updates preserve wallet,
draft, cached content, preference and separate debug identity on both emulators.
The immutable APK is 42,650,666 bytes, SHA256
`6d3a7cb431815c6338f5bd84fca633ccd10f258bcf3653241e74ee0910a5fdb3`,
compiled from `de0ae9b60`. An exact source bridge retains that APK across two
reviewed instrumentation-only corrections. Public latest, immutable manifest,
page, notes and actual downloaded bytes are verified.

The [ticket100 evidence](../evidence/ticket100/README.md) retains original failed executions,
source bridges, complete results and delivery receipts. The inherited Android 10
Profile assumption, unavailable Chat archive and failure-capture qualifications
remain explicit. Fresh agreement checkpoints have no prior receipt; its narrow
restoration correction relies on the original failing checkpoint and source review.
Later isolation captures preserve the reading anchor; the original five-second
assertion establishes the next label, without separate native ink/timing proof.
No physical/OEM/provider/Play acceptance or production node change is claimed.
Issue #100 is complete. Keep #1/#36/#55/#84/#86 open and stop until another issue
is requested. Preserve the canonical checkout.

## Delivered native Stack reading #99

Signed [beta 0.1.108](https://davidengelen.nl/redsquare/releases/108/redsquare.apk) replaces ordinary Stack
sample stories with genuine signed public articles from the explicitly selected
node. Real authors, covers and Markdown images, followed authors, My articles,
bounded history and retained offline reading use the shared wallet and controls.
Local visual/Markdown drafts are durable; review copies Markdown and opens the
actual Stack website with its browser wallet. Native publication, restricted
access, payments and subscriptions remain first-release work under #1.

The owner node currently has no Stack archive; choose saito.io in Settings for
its available public articles. Native read-only checks exercise both approved
backends without substituting data, switching nodes silently or publishing.
Historical design fixtures are debug-only, separate from the ordinary route.

Complete Android 10 [42963703866](https://github.com/robelene112/redsquare-android/actions/runs/42963703866)
and Android 17 [42966576451](https://github.com/robelene112/redsquare-android/actions/runs/42966576451)
pass all three partitions. Frozen goals are verified with an independently
reviewed archive-partition catalog erratum: 642/643 identities, 220 classes,
186 archives, 239/240 normal completions, 37 deliberate interruptions and 36
continuity records. Only the inherited API29 Profile assumption remains.
The inherited unavailable Chat archive and failure-only captures remain qualified.
Both independent final reviews and actual signed 83-to-108 update gates pass.
Public immutable/latest manifests, page and downloaded APK match exactly.

Complete Android 10 source is `f7c92f01961c28be567d07c868625d78d1a3ee89`.
Complete Android 17 and compiled APK source are `706833f8fa332fb56e1d55e8a04839348a4ff2d0`.
The fixture-only rule retains the complete Android 10 run with exact product/core/build/
workflow/harness equality and a fresh whole Android 17 run. One test readiness wait changed
within its original budget. No partitions from different SDK runs are assembled;
the early failed Android 17 run remains failed and its underlying cause is unproved.

APK: 42,581,034 bytes, SHA256
`720b6d129afa1fa1e42a57ce1aeee2377d5c7a22d12f414d531e317f1b19772e`.
The established package/certificate and min29/target36 remain unchanged.
See [original evidence, qualifications and reviews](../evidence/ticket99/README.md).
This completes only #99; stop. Parent #1/#36/#55/#84/#86 stay open.
Physical/OEM/provider/Play acceptance remains under #36 and OWNER_INPUTS.md.
No owner-phone QA, production-node/service changes or owner-wallet operations
were performed. Source checkout remains the isolated codex/unified-redesign
worktree; preserve the canonical checkout.

## Delivered UI fixes #97 and #98

Signed [beta 0.1.107](https://davidengelen.nl/redsquare/) delivers the requested
feed and profile corrections. Refresh completion stays below the tabs at the top
of the visible posts viewport, preserves the reading pixel and lets taps reach
posts underneath. Profile history uses a neutral information icon and "History
may be limited", with accessible Details explaining local/node history and
possibly missing older posts, replies or likes. Real loading, errors and recovery
controls remain separate.

Complete Android 10 [42914306789](https://github.com/robelene112/redsquare-android/actions/runs/42914306789)
and Android 17 [42931114125](https://github.com/robelene112/redsquare-android/actions/runs/42931114125)
pass all three partitions and the frozen verifier: 634/635 identities, 215 classes,
181 archives, 37 deliberate interruptions and 36 continuity records per SDK.
Only the inherited API29 Profile assumption remains; Android 17 has none.
Both independent final reviews, actual signed 83-to-107 updates on both SDKs,
binary verification and exact public APK/page/manifest verification pass.
No partial SDK assembly is used. Earlier unsuccessful runs remain qualified.

The compiled APK and complete Android 10 use source
`8ab10cdd08909f969d485f0a4cb555e438e9bcc0`. Complete Android 17 uses fixture source
`82c01e74da76abd2117bedb3eeed73e60945a8cd`, tag `proof-ui9798-fixture-final`.
Exact production/core/toolchain objects match. The documented fixture-only rule
retains the complete Android 10 run while fresh Android 17 verifies stronger
input/window readiness inside the original budgets. Temporary probes are removed;
later Save/window readiness failure causes remain unproved. See [feed evidence](../evidence/ticket97/README.md) and [profile evidence](../evidence/ticket98/README.md).

APK: 42,388,522 bytes, SHA256
`55dbc679d116079f0e3d47c83db1073ffeeaac82633bd7fccf4e046e94ef41b3`.
The established package/certificate are preserved. This completes only #97 and
#98; stop after recording delivery. Keep #1, #36, #55, #84 and #86 open. Physical,
OEM, provider and Play acceptance remain under #36 and [OWNER_INPUTS.md](../../OWNER_INPUTS.md).
No production node or service changes were made.

## Delivered fix #96

Signed [beta 0.1.105](https://davidengelen.nl/redsquare/) completes
[issue96](https://github.com/robelene112/redsquare-android/issues/96).
In Conversation, the visible Reply action beneath each post now opens a reply
to that exact post, including replies to replies. The redundant header bubble
is removed; Back, history information and post-body reading navigation remain.
Per-parent drafts, exact parent/thread/recipients, recreation and successful-send
return are preserved. Long-parent replies expose the writing field at large
text sizes without forcing focus or the keyboard.

Complete Android 10 [42887785531](https://github.com/robelene112/redsquare-android/actions/runs/42887785531)
and Android 17 [42878955982](https://github.com/robelene112/redsquare-android/actions/runs/42878955982)
pass all three partitions and the pre-run frozen verifier: 634/635 identities,
215 classes, 181 archives, 37 deliberate interruptions and 36 continuity records
per SDK. The single inherited API29 Profile assumption remains; Android 17 has none.
Both independent final reviews, actual signed 83-to-105 update checks, binary gate
and post-promotion public APK/page/manifest verification pass. No partial assembly.

Compiled APK and both complete-run sources are
`c1c1d1066b28fb9eff626bc27908d8c79e409382`. The APK is 42,388,522 bytes, SHA256
`2975aa69337016bbfaa57ba368ff082135bc4dc094deb57c794e83e7c79a3d0a`, using the
established package and certificate. Later commits retain evidence/documentation.
See [ticket96 evidence](../evidence/ticket96/README.md) for originals, reviews and qualifications.
This completes only issue #96; stop. Physical/OEM/provider/Play acceptance remains open
under #36 and [OWNER_INPUTS.md](../../OWNER_INPUTS.md). Keep #1, #36, #55, #84 and #86 open. No node or service changes.

## Delivered fix #95

Issue [95](https://github.com/robelene112/redsquare-android/issues/95) is delivered
as signed beta **0.1.104**: [download or update](https://davidengelen.nl/redsquare/).
After successful post/reply/quote submission, the app returns to its reading
context with brief neutral "Post submitted" feedback. The signed original and
media stay durable for background observation; a delayed node cannot cause a
receipt to be resent or erase the next draft. Actual send/storage failures and
legacy102 attempts remain recoverable using the original signature.

Complete Android 10 run [42845159882](https://github.com/robelene112/redsquare-android/actions/runs/42845159882)
and Android 17 run [42823896331](https://github.com/robelene112/redsquare-android/actions/runs/42823896331)
pass all three partitions and the unchanged pre-run verifier: 634/635 identities,
215 classes, 181 archives, 37 deliberate interruptions and 36 continuity records per
SDK. Android 10 retains the one inherited Profile assumption; Android 17 has none.
Original failed/cancelled runs remain qualified, with no partial assembly.

Compiled source and immutable 104 manifest remain
`fd82ad47754d3e5137503d14331e6a6b2b4be2a1`. Android 10's final harness source is
`5336c0536c47f1d6db5ab347a91f4b802a2d097c`; an exact whole-tree bridge proves only
nine test/harness/documentation paths differ. The existing workflow permits
retaining the complete Android 17 run after a fixture-only correction. Both
independent final reviews pass. Actual signed 83-to-104 update/isolation also passes
on Android 10/17, preserving wallet, unsent draft, cached content and preferences.

APK: 42,372,138 bytes; SHA256
`a21bc86d6ce64c3cec38afdeadf9cfeb1e7b4f3dd143b98b5af324a0dc17c789`.
The ordinary public page/latest/immutable manifest and downloaded APK are verified
after exact promotion. See [ticket95 evidence](../evidence/ticket95/README.md) for originals, reviews and limits.
This delivery completes issue #95; stop after recording it. Physical/OEM, provider and
Play acceptance remain under #36 and [OWNER_INPUTS.md](../../OWNER_INPUTS.md). Keep issues #1, #36, #55, #84 and #86 open.

## Previous delivery #94

Issue #94's prototype-fidelity repair is delivered as signed beta **0.1.102**:
[archived APK](https://davidengelen.nl/redsquare/releases/102/redsquare.apk). The public latest pointer,
actual APK checksum and exact immutable notes are verified after promotion.
Frozen source: `1c7cb53d8c077aa1cd132deec22a1c5a72cc2d3c`, tag
`proof-prototype94-loaded-activity`. Production last changed at be37e8c42; later
changes are tests/evidence only, with an exact whole-tree bridge retained.

Both complete runs pass all three partitions: Android 10 [42452255196](https://github.com/robelene112/redsquare-android/actions/runs/42452255196)
and Android 17 [42451092093](https://github.com/robelene112/redsquare-android/actions/runs/42451092093).
The unchanged frozen Android 17 collector confirms 630 identities and no assumptions. The Android 10 collector
confirms 629 identities: 628 passed and the single inherited
API29 Profile assumption. Both catalogs have no missing or extra identities.
Both independent final packet reviews find no actionable defects. No partial or tail assembly is used.
Original 180-second class bounds, 214 classes and 629/630 expected identities remain frozen.

Actual signed 83-to-102 update/isolation passes Android 10 [42454280787](https://github.com/robelene112/redsquare-android/actions/runs/42454280787)
and Android 17 [42454286099](https://github.com/robelene112/redsquare-android/actions/runs/42454286099).
Independent actual artifact and update-native reviews find no actionable defects.
APK: 42,355,754 bytes; SHA256 `616a151011887ba2f19be9cba8fb23d69f33d4b4da15d02bebcc142d1ec14fb7`.
Signing certificate, package, minimum API29 and target API36 and vendored license are verified.
Never promote superseded candidates 93 through 101.

Prototype authority: `c95ecaa01abb71c576f052c7cc2303356416091c`,
`design-3d989e9e8406`, plus owner amendments: rounded named module selector,
real identity/data, separated profile history, compact repeatable heart, exact-post
Activity grouping and collapsing reading header. Do not copy sample identities or
artwork as live data. Stack remains explicitly a local design preview; actual
network/publication/payment/subscription work stays under issue #1.

Current originals, frozen collector results and qualified independent reviews are
in [ticket94 evidence](../evidence/ticket94/README.md). Historical failed executions
remain failed. Known baseline missing failure-only captures are recorded by the
frozen collector; they are not missing application identities or waived goals.
Physical Pixel performance, actual provider delivery, business/policy setup and
Play acceptance remain pending under #36 and [OWNER_INPUTS.md](../../OWNER_INPUTS.md).
Do not automatically close issues #1/#36/#55/#84/#86. Canonical checkout, other work,
production nodes and services remain untouched. Issue #94 is the previous accepted checkpoint; the later95 delivery is recorded above.

## Working paths and resume boundary

- Sole implementation checkout: `/mnt/storage/redsquare-android-artifacts/redesign86`,
  branch `codex/unified-redesign`. Preserve canonical `/home/david/projects/redsquare-android`.
- Public immutable candidate108 is the delivered ordinary beta. Artifact:
  `.scratch/review-builds/108`; exact notes: external `stack99-release-notes.txt`.
  Do not reserve, rebuild or promote another version for completed99.
- Compiled APK source/tag: `706833f8fa332fb56e1d55e8a04839348a4ff2d0`, `proof-stack99-feed-ready`.
  Later commits retain evidence/documentation only. Product/core/build/workflow/
  harness objects match both complete-run sources under the reviewed fixture rule.
- Accepted whole-SDK manifest: external `stack99-accepted-full-runs.json`.
  Complete Android 10: `42963703866` at `f7c92f01961c28be567d07c868625d78d1a3ee89`,
  originals `stack99-acceptance-raw/full-android10`.
  Complete Android 17: `42966576451` at `706833f8fa332fb56e1d55e8a04839348a4ff2d0`,
  originals `stack99-fixture-full17-raw/full-android17`.
  No partitions from separate dispatches are assembled.
- Frozen contract/verifier stay unchanged. Use the explicitly reviewed
  `docs/evidence/ticket99/verify-archive-partition-erratum.py` for its one-path
  expected catalog correction, and preserve the original collector failures.
  Report frozen goals verified with archive-partition catalog erratum, never an
  unchanged-verifier PASS. All identities/recovery/archives/inherited validators
  still pass. See the exact summaries and reviews in99's evidence packet.
- Actual signed 83-to-108 updates: external `stack99-update-runs.json`,
  runs 42969207984/42969210675. Originals `stack99-update-raw/candidate-update`
  and `stack99-update-raw/candidate-update-android17`. Both reviews pass.
- Exact immutable/latest manifests, public page and downloaded APK are verified.
  The original failed full17 source f7 run42963706966 remains failed; its passing
  Social/Wallet partitions are not reused. Fixture source 706 has its own complete17 run.
- This completes99 only. Stop. Parent issues #1/#36/#55/#84/#86 and physical/provider/Play gates
  remain open. Native publication/access/payment/subscriptions remain under #1.
  No further owner QA or node/service operation is needed for this reading slice.

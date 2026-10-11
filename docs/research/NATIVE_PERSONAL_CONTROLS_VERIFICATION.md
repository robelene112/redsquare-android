# Personal account controls verification

Active ticket21, parent specification1, review base
99e58c6a49f5a10ee630f948ad3a18c9f5fb5592. The owner authorized sequential
implementation of35tickets and seams1–4; no additional grill is required.

Source contracts: NATIVE_DISCOVERY_MODERATION_CONTRACT.md and
NATIVE_MODERATION_CONTRACTS.md. Personal policy is distinct from backend
moderation. Mute hides authored posts and social notifications; block also
suppresses incoming Chat. Conversation mute remains separate. Policies belong to
the active public identity and apply across both approved backends.

First tracer: native Profile Mute confirmation explains effects, feed hides only
that author's posts, the same policy holds on the other approved backend and
fresh offline Activity. Settings management Unmute restores retained cached
history. Independently signed existing following/profile fixtures are the truth
source; real local binary controlled peers authenticate the actual wallet. Verify
same wallet/all client identities and zero transactions. Full-list absence checks
must search the complete list rather than pass because a post is offscreen.

TDD status: test and focused controls harness added first. Final isolated emulator
guard uses the maintained HARDWARE check in setup and cleanup; compilation PASS
1m20s. Native RED pending. No product implementation or acceptance claim yet. Tests run only
in the .dev isolated emulator. Native screenshots and hierarchies are separate
observations; preserve originals and primary failure diagnostics.

Implementation direction: separate encrypted identity-scoped local preferences,
without signing-wallet mutation. Preserve unknown imported wallet options; do not
reinterpret foreign moderator lists. Wallet-key import alone cannot recover absent
local preferences. Filter display without deleting verified cached history, cover
feed/thread/profile/search and quoted originals, retain innocent commentary and
access to account management. Load policy before displaying authored content.
Failed saves preserve previous choices; durable recovery must cover late failed
commits, not just pre-write obstruction.

Later vertical journeys follow the first result: independently saved Block/Mute,
mounted views and quoted-original privacy, real failed-save repair and cold
retention. Chat and notifications use this shared policy in their later tickets;
this ticket must not claim those currently absent features are implemented.
The tracker explicitly makes #21 a prerequisite for #24 notifications and #25
private Chat; each later ticket requires actual personal-control integration.
Finish with both source review axes, relevant complete Android10/17 acceptance,
release/lint and retained evidence before closure. No live moderation writes,
node operation, physical timing or new phone APK claimed here.

First native RED38149454797 at056d9a3:1FAIL29.531s at waitFor Mute account,
line70; source/core/debug/test PASS. Root views original blank PNG and parses XML
without app nodes: capture happened after ActivityScenario closed. End safeguard
assertions not reached. Durableticket20/first-red keeps raw log/originals/4hashes.
Fixture now captures inside each live Activity and waits for Profile first; repeat
corrected focused RED before adding product code. No visible-Profile/GREEN claim.

Capture-scoped fixture compilation PASS1m16s. No product source added. Corrected
focused Android17 RED dispatch next; successful first source push38149448583 also
passes core/debug/test/release/lint.

Corrected native RED38150093643 atd4bf054:1FAIL31.11s at missing Mute account.
Root views original native Profile bitmap and separately parses the hierarchy:
actual author/reference post/Follow/Refresh present, Mute absent. Durable
 ticket20/captured-red retains raw log/originals/4hashes. Both initial fixture
review axes0 actionable findings. Later assertions remain unreached.
First product tracer now implements only saved Mute, profile confirmation,
feed filtering, backend-independent scope and Settings Unmute. Block, other-view
filtering and durable late-save recovery follow further vertical native tests.

First Mute implementation compile PASS2m14s. Focused native GREEN attempt next;
no runtime acceptance asserted. No wallet options or transaction path modified.

First-product bounded source reviewd4bf054...0503aee: Spec0 new actionable
findings for the first tracer; Standards1 actionable documented-boundary finding.
Null personal scope hides all cached rows and indefinitely reports loading during
wallet recovery failure, conflicting with preserved anonymous public reading.
After the first GREEN, add a native recovery journey: without prior personal
records, unavailable wallet still permits public cached reading; with prior
personal records and unknown owner, withhold content with explicit wallet recovery
copy rather than risk exposing hidden authors. Prove through the UI using the
existing controlled filesystem-sync failure, not guessed owner/plaintext reads.
No closure claim; later slices and final two-axis/full acceptance remain required.
Focused first GREEN attempt38150876485 at0503aee is active.

First native GREEN38150876485 at0503aee:1PASS32.955s, all first-tracer
wallet/peer/zero-TX assertions reached. Root views3originalPNG/separate3XML;
 ticket20/first-green retains8hashes. No final #21 acceptance claim.
Next native tracer added test-first for the actionable anonymous-reading/recovery
boundary: actual wallet OS-sync failure with intact wallet bytes, public cached
reading without personal records, then saved-Mute owner unavailable gate and
same-identity recovery retaining the choice. No production recovery fix yet.

Recovery tracer compile PASS1m19s; focused Android17 RED dispatch next.

Recovery fixture/evidence review0503aee...560424e: Spec0 actionable,
Standards0 new hard violations/heuristic smells. Both independently verify the
first-green8hashes and original observations. The initial Standards source
finding remains open until recovery RED/fix/GREEN. Recovery RED38151508176
at560424e is active; no production fix added ahead of observed failure.

Recovery RED38151508176 at560424e:2reported/1PASS1FAIL30.592s; missing cached
root atline149 in empty list after positive wallet failure. Root inspects original
failurePNG and separateXML; ticket20/recovery-red retains4hashes. Later repair,
owner-gate and end identity/no-TX checks unreached. Minimal fix now loads explicit
anonymous policy only when no personal record exists; otherwise explains owner
recovery and withholds the content list. Known-owner policy loading stays gated.
No signing identity is guessed or changed. Compile/GREEN pending.

Minimal anonymous/owner recovery fix compile PASS2m23s; unchanged two-test focused
Android17 GREEN attempt next. No runtime recovery acceptance claimed yet.

Recovery source review560424e...7f5d796: Spec0 actionable; Standards prior
anonymous-reading finding resolved,0 residual hard violations/heuristic smells.
Both verify4REDhashes/original observations. Native unchanged two-test GREEN
38152142197 at7f5d796 is active. Source review is not runtime acceptance.

Recovery GREEN38152142197 at7f5d796:2PASS45.744s, both unchanged journeys/end
gates pass. Root views5originalPNG/separate5XML; recovery-green12hashes. Native
anonymous reading, saved-policy owner gate and same-wallet repair/mute retention
now proved on Android17 only. Next SINGLE Block tracer added before product code:
explained Block after intentional Mute, approved backend switch, offline Unblock
retaining Mute, fresh offline reopening and explicit Unmute restoring cached post.
Both approved sessions intentionally share one controlled binary peer; different
server histories were independently verified in the first Mute tracer. Native
Block RED/compile pending; no Block implementation or final acceptance claimed.

Block tracer first draft compilePASS1m26s. Before dispatch, strengthen the same
journey to temporarily Unmute while Block stays active: full-list author absence
must still hold, Settings shows Block without a saved Mute, then explicitly
re-Mute before switching backend/offline Unblock. This prevents Mute alone from
masking absent Block filtering. Recompile before the first native Block RED.

Strengthened Block tracer compilation PASS1m27s; first native Block RED next.

Block fixture/recovery-evidence review7f5d796...f1100cf: both axes0 new
actionable findings. Both independently verify12recovery-GREENhashes/originals.
The test expresses Block-only/independent-Mute assertions; actual Block behavior
is not proved before implementation/GREEN. Native first Block RED38152934786
atf1100cf is active. Product source unchanged since7f5d796.

First Block RED38152934786 atf1100cf:3reported/2PASS1FAIL71.307s, line227
missing Block account after saved Mute. Root views native original/separateXML;
block-red4hashes retain log and qualified failure. Existing two journeys pass;
new Block end safeguards and later action assertions unreached.
Minimal Block implementation now adds independent encrypted sets, immutable
PersonalPolicy semantics (either flag hides social content, only Block hides
incoming Chat), shared Profile/Settings explained controls and feed filtering.
Older native Mute-only records load with no Block; imported wallet options remain
untouched. Later actual Chat/notification integration is still required in its
own dependent tickets. Compile and unchanged three-test GREEN pending.

Block implementation compile PASS2m16s; unchanged three-test native GREEN next.

Block source reviewf1100cf...fe555b9: both axes0 new actionable findings.
Block GREEN38153595298 atfe555b9:3PASS42.425s, all three journeys/endgates pass.
Root views8originalPNG/separate8XML; block-green18hashes. No final acceptance.
Next SINGLE cross-view tracer added before its source filters: independently
signed root/replies/embedded quote, positively observed quoted original before
Mute, Profile full-list absence, retained innocent thread reply without muted
root/nested reply, innocent quote commentary with hidden original, Search posts
and people management access, fresh offline quote hide and Unmute restoration.
Setup also clears isolated public author proofs/quoted-original caches so earlier
unrelated fixtures cannot supply stale deletions/history. First compilePASS1m14s;
root author-link selector now explicitly chooses the first chronological card
and positively checks exact Profile key before Mute. Recompile before native RED.
No cross-view filter source added yet.

Final cross-view fixture compilation PASS1m16s; first native cross-view RED next.

Cross-view RED38154431362 at3aec9fa:4reported/3PASS1FAIL72.367s. New journey
fails atline354 full-list Profile absence after positive exact-author saved Mute;
root views actual native original still visible and separately parses XML.
Before-Mute quote original positively observed. Later cross-view/end gates
unreached; views-red retains6hashes. Fixture Spec0, Standards0 hard/one possible
repeated Mute-action helper reserved for review after GREEN.
Minimal display filters now cover Profile, Conversation, Search posts and the
shared quoted-original renderer. Verified caches remain intact; innocent replies,
quote commentary and people/account-management access remain available. Known
policy must be ready before authored content appears. Filtered Profile/root/quote
copy explains why content is absent. No outgoing transaction path changed.
Compile PASS2m22s; unchanged four-journey Android17 GREEN attempt next.

Cross-view source review3aec9fa...c89a099: Spec0 actionable; Standards0 new
hard/heuristic findings, prior Mute-helper judgment reserved for postGREEN.
Both independently verify6REDhashes/originals. Unchanged four-test GREEN
38155113079 atc89a099 is active. Source review is not runtime acceptance.

First views GREEN attempt38155113079 atc89a099:4reported/3PASS1FAIL75.649s.
Product gates pass through Profile/thread/quote/Search-post filtering, then exact
person navigation fails atline388 because long editable query also scrolls.
Root views5originalPNG/separate5XML; views-selector-failure12hashes. The actual
person result is present. Exclude editable fields from that list selector;
positive exact-person/display/navigation/end assertions remain unchanged.
Offline/end gates unreached. Separatepush38155113301 source/release/lintPASS.
No production source change; corrected fixture compile and fulljourney GREEN next.

Corrected exact-person selector compilation PASS1m14s; unchanged4journey GREEN next.

Selector/evidence reviewc89a099...17104b8: both axes0 new actionable findings,
12retainedhashes independentlyverified. Corrected4journey GREEN38155702778
at17104b8 active; pendinglatergates are not claimed passed.

Corrected cross-view GREEN38155702778 at17104b8:4PASS61.642s, all endgates.
Rootviews14originalPNG/separate14XML; views-green30hashes. No actual processdeath
or finalacceptance claimed. Review-stage repeated explained-Mute UI steps now
extracted into one narrow helper, preserving positive copy/confirm/active gates.
Next SINGLE native failed-save tracer added before rollback product code: save
Mute/filteredfeed, inject only active encrypted-write OS directorysync after
AtomicFilefinishWrite, explicitUnmuteerror/oldUI, freshnormalActivity filtering
and savedMute, successfulexplicitUnmute/cache restoration, samewallet/noTX.
EncryptedLocalFile now accepts the real OSsync seam; DEBUG intent drives only
personalwrites, wallet/read behavior unchanged. No recovery anchor implemented
before observed RED. Compile/focused5journey RED next.

Late-save tracer and narrow Mute-helper refactor compilePASS2m7s; native5caseRED next.

Late-save fixture/OSseam/refactor/evidence review17104b8...9611408: both axes0
new actionable findings; prior repeatedMute judgment resolved. Both verify30
views-GREENhashes/originalobservations. First5caseRED38156328999 at9611408
active. No rollback product implementation before actualfailure.

Late-save RED38156328999 at9611408:5reported/4PASS1FAIL83.454s. Mountedfailed
Unmuteerror/oldMute passes, freshnormalActivity showsrootandfailsfull-listabsence
atline452. Rootviews2originalPNG/separate2XML; save-red6hashes. Newjourney
repair/endgatesunreached. ExistingfourjourneysPASS; sourcepush38156329246PASS.
Minimal AndroidPersonalControls adapter now validates independentsets/owner,
loads/recoversexistingencryptedprevious-policyanchor beforeactivecontent, compares
expectedoldpolicy under sharedlock, verifiesoldanchor before activecommit and
removes/syncsanchor onlyafterverification. Failedwrites/removalsrestoreoldpolicy
usingnormalOSsync; failedrestorationwithholdsreadycontent and retainsrecovery
instructionwhenpossible. Emptyoldpolicy isexplicitanchor; Mute-onlylegacyrecord
stillloads. Policyfilenamefamilyincludesanchors, preserving unknown-ownergate.
No signingwallet/options mutation; unchanged5caseGREEN/compile next.

Verified-anchor adapter compilation PASS2m3s; unchanged5case focusedGREEN next.

Durability source review9611408...2309d4d: Spec0 new actionable; Standards1
residual removal-failure sequencing concern. If anchor deletion succeeds but its
syncfails andrecreationfailsbeforewrite, activeold restoration is skipped; a
freshload couldacceptupdatedpolicy afterfailedchange. This is source-supported,
not runtimeproved. Do notclaimfailedremovalfullycovered. Currentunchanged5case
GREEN38157057749 at2309d4d active. Afterresult, addone nativefilesystemtracer
forremoved-anchor recreation obstruction/repair/coldold-policy beforefix. Later
identity/corruption/actualprocess/finalpairedacceptance remainpending.

Lateactive-save GREEN38157057749 at2309d4d:5PASS88.55s/allendgates. Rootviews
3new originalPNG/separate3XML; save-green8hashes retains reached saveproofonly.
Residual Standardsremoval/recreationfinding staysopen. NextSINGLE nativetracer
beforefix: post-anchor-deletion OSsync callback createsnonemptyownedrecoverypath
obstruction thenfails; anchorrecreationmustfailbeforewrite. UIrecoveryerror and
withheldauthoredcontent required; repairONLY controlledobstruction thenfresh
normalActivity mustretainfilter/Mute beforeexplicitUnmute/samewallet/noTX.
Guardedcleanup preservesprimary andrefusesunexpecteddirectorychildren. RealOS
callbackdefaults unchanged; DEBUGfixture only personalrecoveryremoval. Noactive
restoration-sequencingfix ahead of observedRED. Compile6caseRED next.

Removed-anchor recreation-obstruction tracer compilation PASS2m25s; first6caseRED next.

Removalfixture review2309d4d...b8bf2cd: Spec1 actionable preliminaryguard mismatch:
management removesallaccountactions whenreadyfalse, so disabledUnmuteassertion
wouldstop before intendedcoldcheck. Confirmedactualdialogsource; firstattempt
38157802202 cancellationrequested while sourcebuildactive, not a runtimeRED.
Correctfixture to requireUnmuteabsence+positiveRetry alongsideexistingexplicit
recoverycopy/noauthoredlist; allrepairedfreshMute/filter/endgates unchanged.
RecompileanddispatchcorrectedfirstRED beforeproductfix. No appsource change.

38157802202 confirmedcancelled; bothSDKruntime stepsskipped. Correctedmissing
Unmute/positiveRetry fixture compilePASS1m16s. Firstactual6caseRED next.

Correctedfixture reviewb8bf2cd...09f3f4f: both axes0 residualfixturefindings.
Actualfirst6caseRED38158038045 at09f3f4f active. ProductrestorationfindingOPEN.
Read-only Standardsaudit recommends independentlyattemptingnormalactiveold
restoration evenifanchorrecreationfails, plus proportionateowner-bound encrypted
old-policy guard ifretainingpost-deletion syncprotocol. Guard isverifiedbefore
activechange, honoredonload, retainedthrougheveryfalliblerecovery/removal, then
removedonlyasfinalpublication withno subsequentfalliblework. This follows the
conservativepublicationbarrierconvention; physicalpowerloss/guardreappearance
isnotclaimedproved. ApplyonlyafterobservedRED; lateractualprocesskill mustprobe
pendingold-policy guard recovery, not merelyordinaryActivity reopening.

Corrected removed-anchor RED38158038045 at09f3f4f:6reported/5PASS1FAIL88.615s.
Mounted uncertain-policy checks pass; repaired freshActivity exposes failedUnmute,
full-listabsencefails526. Rootviews2originalPNG/separate2XML; removal-red6hashes.
ExplicitUnmute/endgates in newjourneyunreached. Product now keeps owner-bound
encryptedold-policyguard across allfallibleactive/recovery writes andremoval-sync,
honors guard firstonload, restores guard/anchor/active independently preserving
secondaryerrors, deletesguardasfinalpublication. NormalOSsyncforrestoration;
corruptguardfailclosed. Physicalpowerlossnotclaimed. Compile/same6caseGREENnext.

Guard/independentrestorationfix compilationPASS2m5s. Same6caseGREENnext.

Guardfix boundedreviews09f3f4f...2473000: Spec0, Standards0source residual,
1stalehandoffdocumentationfinding correctedhistorical/currentwording. Actual
6caseGREEN38158906379 runtimeactive. Separateactualprocessverification now
compilesPASS2m17s: nativeUI savesdistinctMute/Block, checkpointpublicowner/accounts/
backend/PID, DEBUGactivepost-writeOSsynckillsprocess witholdguardpending; host
requiresoldPIDgoneandpositiveguardmetadata beforecoldnormalnewprocess; UIboth
choices retained, explicitUnblockkeepsMute/filter, explicitUnmuterestorespost,
samewallet/backend/authenticatedpeer/zeroTX. Recoverydoesnotresetchoicesorwallet.
No walletcontentreadintoartifacts. This isverificationofimplementedrecovery, not
manufacturedRED; nativeprobe notyetdispatched/passed. Same6caseGREENfirst.

Removed-anchor GREEN38158906379 at2473000:6PASS95.631s/allendgates including
samewallet/authpeer/zeroTX. Rootviews2new originalPNG/separate2XML; removal-green
6hashes. The unchangednewjourney nowretainsMute/filter afterrepairedfreshActivity,
explicitUnmuterestorescachedroot. This remainsActivityreopeningnotprocessdeath.
Sourcepush38158898547 at2473000 SUCCESS, verified actualrunlist.
Separateactualprocessprobe38159358286 atdd40a2c active. BoundedStandardsprocess
review0sourcefindings/stalehandofffixed; Specreviewpending. Lateridentity/corrupt/
whole-review/finalpairedacceptancepending; #21ACTIVE/noAPK.

Processprobe boundedreviews2473000...dd40a2c: both axes0 findings.
Corruptactivepolicy/retryprobe is nextverificationofexistinggate; noforcedRED.

Corruptpolicyprobe compilePASS1m16s. Sevenordinarycases nowinclude actualencrypted
active-record authentication damage afterUI-savedMute; fresh knownowner UIerror,
no authoredlist/content, boundedoriginalciphertext repairthenRetry, retainedMute/
filter, explicitUnmute/samewallet/controlledkeys/noTX. Bytesstayboundedmemoryand
cleared; cleanuprestoresonlyownedrecord/preservesprimary. Productunchanged;
verificationprobe notforcedRED/no runtimeclaimyet. Processprobe38159358286atdd40
stillruntimeactive; awaitbeforeexpandedfocuseddispatch. Identityroundtripnext.

ActualprocessGREEN38159358286 atdd40a2c: sixordinaryPASS; interruptedactualdeath
PID4992 absent/survivingencryptedguardmetadata, independentcold1PASS7.083s PID5082.
Samewallet/backend/oldMute+distinctBlock, UnblockretainsMute/filter, Unmuterestores
root/authpeer/zeroTX. Rootviewsall4originalPNG/separate4XML; process-green16hashes.
No recoveryreset. Onlinecoldpeermeansofflineproofremainsearlierjourneys; nopower
loss/finalSDKclaim. Corruptionprobe source8d45dfc both boundedaxes0; runtimepending.
Identityroundtripbuild49sfailedincorrectAndroidWalletStoreimport; removedimport
foractualsamepackageclass and recompiling. No nativerecovery/productdefect inferred.

Identityroundtrip compilePASS1m27s afterimportcorrection. Eightordinarycases now
verify actual outgoingwallet vsdistinct publicunsafeincoming selectedfromtwo
hostprovider-UIDfixtures, freshcandidate-boundauthenticatedSAFbackup/PIN,
unacknowledgedreplacementdisabled, boundedfulloptionsbackupequality noJSONlogs,
explicitdifferentimport/opaqueextensionretention/rootvisible/emptychoices,
freshincomingActivity, actualSAF outgoingrestore+explicitzero-balanceSkip/ack,
originalfulloptions/Mute/filter, furtherfreshActivitysamewallet/filter, samebackend/
actualbothpeerkeys/noTX. Independent emergencycleanup onlyknownidentity; PIN/
providerfiles/servercleanup preservesprimary andrefusesunknownwallet fallback.
Privateoutgoingbackup remainsprovideronly anddeletedneverartifact/log/tar.
IndividualUIdeadlinesunchanged; ordinaryharness240s foraddedcorruption+realPIN/SAF
roundtrip versusmeasured6case95.631. No nativeidentity/corruptionclaimyet;
focused8caseplusprocess next. Whole-ticketreview/fullpaired remainpending.

Expanded38160012441 at13943cc:8reported/7PASS1ERROR101.282s. Identitytestfirst
onfreshfocusedinstallation triesloadExistingbeforeactualappwalletcreation642;
noidentityActivity/capture/backup/import/endassertionsreached. Fixturecorrectedto
actualfirstlaunchWalletUIdisplayedidentitythenexistingoptionsread, nodirectwallet
creation/reset. Other7ordinary includingnewcorruption passallgates. Rootviews
2originalcorruptPNG/separate2XML; identity-bootstrap-failure6hashes. Separate
actualprocessstageunreachedinthisrun; priorprocessproofremains. Sourcepush
38159998964SUCCESS core/debug/test/release/lint. Recompileunchangedidentity
assertions/retryfocusednext; whole-ticket priorbothaxes0source, finalpairedpending.

Actualfirstlaunchidentityfixture correction compilePASS1m16s; expanded8case retry next.

Corrected8case38160579644 at584:7PASS1cleanupERROR124.105s. Identitymainbody
reachesallbackup/import/opaqueoptions/fulloriginaloptions/emptyincoming/coldincoming/
actualSAForiginalrestore/Mute/filter/furtherfreshwallet/backend/bothpeerkeys/noTX
assertions. CleanupthrowsliteralPINclearoutputcheck792 withprimarybodyerrornull;
exactcommandoutputnotcaptured, soactualclear/wordingunproved. Independentother
cleanupstillruns. Rootviews4originalidentityPNG/separate4XML; identity-cleanup-
failure10hashes. Notpassingidentitycase/finalacceptance. Processnotreached.
Fixturecorrection usesactualKeyguardManager.isDeviceSecure false wait aftersame
clearcommand, no weakenedprivacy/recoveryassertion. Compile/retrynext.

ActualPINclearstate fixture compilePASS1m12s; commentdirectioncorrectedafterbuild, nosemanticchange. Eightcaseplusprocessretry next.

CompletefocusedGREEN38161237509 ataa91ff2:8ordinaryPASS111.163s, actualSAF/PIN/
options/walletisolation/restore/cold/filter/bothpeerkeys/zeroTX/actualcredentialclear
allgates. ActualpendingdeathPID5044gone+guardmetadata, cold1PASS6.427PID5131same
wallet/backend/MuteBlock/explicitindependentremovals/noTX. Rootviews6newidentity/
corruptoriginalPNG+4processoriginalPNG/separate10XML; identity-green28hashes.
Bothcleanupfixboundedaxes0 findings. Previouswhole99...13943cc bothaxes0;
584/aa fixturecorrectionsclear. Finalpairedfull10/17same-source next withopted-in
publicreads. No finalacceptance/physicalpowerloss/newAPK/ticketclosureclaimyet.

Finalpairedsame-source acceptance dispatch: fullAndroid10 38161791197 and
fullAndroid17 38161793299 at781bd1af8e4b8bea82d3b414c6ecb6dd9948c1c2, both
public_feed_read=true. Fullscope/source/core/release/lint/device/signing/cold /
publicreadsrequired. Runtimepending; collectactualcounts/rawdeathlogs/newpersonal
nativeoriginalsandseparateXML onbothSDKs beforeclosure. No production source
changedafteraa91ff2completefocusedproof. Evidence-only781 boundedreviewsactive.

Final Android10 run38161791197 at781bd1a stops in existing browse verification:
eight cases, seven PASS and one fresh offline profile timeout at300 in35.513s.
Root viewed both original failure screenshots and separately parsed both XMLs;
full10-browse-failure retains six hashes. The in-scope profile header fills the
small screen; its post is awaited before scrolling the lazy list. Fixture-only
correction scrolls first and retains the exact displayed-post assertion. No
production source or timeout change. Remaining full stages were not reached.
Android17run38161793299 is still active. Compile/review and Android10 retry are
next; #21 remains open.

Bounded781bd1a...ce1acdc Spec review has zero findings. Standards identified
one source-supported fixture readiness risk: Posts appears before asynchronous
cached history is loaded, so immediate lazy-list search can run too early.
Correction waits bounded15s for the existing enabled Profile Refresh after
Posts, then scrolls and asserts the exact reference post. That Refresh is
disabled during history loading and enabled after cached history is published.
No production change or weakened assertion. Intermediate full10run38162365495
atce1acdc was explicitly cancelled during setup to avoid testing the superseded
fixture. Source push38162360677 is separate. Recompile/review then final full10
retry are next. Original full17run38161793299 remains independent.

Readiness correction local native-test compile PASS1m13s. Bounded fix reviews
and the corrected full Android10 retry remain next. No final acceptance yet.

Full Android10 retry38162586525 ataa2cf36 reports8browse cases,7PASS/1failure
in34.331s. Cached root scroll/display and its positive capture pass. The next
old pre-scroll wait fails at307 on Replies. Root viewed3originals and separately
parsed3XML; full10-replies-failure retains8hashes. Failure header fills viewport;
outer launcher is after Activity closes, not app evidence. Offscreen reply/cache
existence is not independently proved. Fixture now consistently waits for
enabled Profile Refresh, scrolls lazy list, asserts exact Posts/Replies/Likes
content; bounded15s unchanged/no production mutation. Recompile/review/full10
retry remain required; original full17run38161793299 remains active. Source push
38162581617 passes; #21 remains open.

Consistent offline-tab readiness/scroll correction compiles PASS1m12s.
Both bounded reviews and corrected full Android10 retry remain next.

Full Android17run38161793299 at781bd1a PASS:179allPASS/46completion records/
15actual interrupted stages; core55/0/0, release/lint, signed5→8/cold continuity
and both public reads. Personal8PASS82.867s; actual pending-save death10423→10513
and independent cold1PASS6.65s. Root viewed all25ordinary+4processoriginalPNGs
and separately parsed29XML; both public screen originals viewed with current
notification-history post. Publicowner1PASS8.524s/official1PASS7.055s; ordinary
100rows/91posts/0ignored, quotes4rows/0posts/0ignored onboth. Durable final-full/
android17 retains162hashes/allraw countable completion/death logs, recomputed
identical179/46/15. Evidence reviews and final Android10 acceptance remain.

Android10full38163120112 atf0c6191 passes browse/posting/queue/social/quotes then
author-post has8reported/7PASS1failure58.215s. Image-author deletion waits at151
for a saved attempt after a possibly disabled Confirm delete tap; source renders
the control before asynchronous durable draft load settles. Root originalPNG
shows confirmation/no saved status, with no legacy XML; exact tap enabledness/
scheduling cause unproved. full10-delete-confirmation-failure retains3hashes.
Fixture correction waits bounded20s for enabled Confirm delete, asserts shown/
enabled, captures separate PNG/XML then taps once. All later acceptance gates
remain; case-specific in-scope failure PNG/XML preserves primary exceptions.
No production/cache/peer change, timeout extension or final acceptance claim.

Author confirmation fixture compiles PASS1m10s. Both bounded review axes
and focused Android10 author verification are next; then rerun outstanding
Android10 full scope. Android17 acceptance is preserved at unchanged product
source, with its durable evidence review also next. #21 remains open.

Bounded f0c6191...732e67f reviews clear both axes: zero residual source/fixture/
evidence findings. Both verify162final17hashes/3failurehashes, independent
179/46/15 recount and qualified originals/XML. Spec initially reported a
differing reading of official public screenshot; a fresh single original-detail
view of the exact verified22fa30a69c02762af911c04fc9c0c70b73e8b1e8d7f71ee5dcb43ef79b593a31
file confirms Server:saito.io and withdraws the proposed caption finding. Root
fresh single view agrees; no cause inferred for the earlier differing reading.
Focused Android10 author38164253681/controls38164297170 at732e67f are active;
passing focused and outstanding full Android10 remain required.

Focused Android10 author38164253681 at732e67f PASSES; ordinary8PASS46.026s and separate actual process/recovery logs remain to inspect. Controls38164297170 ordinary8PASS74.394s, including actual wallet/PIN roundtrip, but its separate process stage stops at host `run-as test -s` metadata check before recovery. Native phase reports Process crashed and public checkpoint validated, but discarded command output prevents proving cause/guard absence. Root original pre-death PNG and separate XML show both saved choices. Durable controls10-metadata-failure retains5hashes. Harness uses established stat positive-size gate and retains metadata only; product/acceptance assertions unchanged. Focused retry required before outstanding full10.

Portable metadata fixture fe710dd compiles with Python; bounded 732e67f...fe710dd reviews: Spec0, Standards0. Both independently verify5failure hashes and qualify pre-death original/XML; unchanged positive-size/PID/identity/policy assertions permit retaining passed full17 under WORKFLOW. Focused controls29retry38164957454 atfe710dd active; sourcepush38164949469 active. Root author29recount10completed/3records/2actualdeathstages, individually views image/native positive readiness originals and separately parses2XML(enabled Confirm delete); author10-ready-green retains13hashes and raw continuity logs. No timing-cause inference.

Focused controls10retry38164957454 atfe710dd PASSES:8ordinary73.893s plusactualdeath4897→5093/cold1case5.679s;9completed/2records/1death/noassumptions. Portable guard metadata returns0/213bytes; no ciphertext read. Sameowner/backend/independentchoices/actualpeerkeys/zeroTX assertions pass. Root individually views4originalprocessPNG and separately parses4XML. controls10-metadata-green retains18hashes including rawlogs/checkpoints/counts and metadata. Full10run38165304273 atfe710dd withpublicreads active; sourcepush38164949469PASS. Author13hashes and this bounded evidence need review; final full10 remainsrequired.

Focused evidence reviews fe710dd...97df2ee with indexed-pathfixa43e046: Spec0residual, Standards0residual. Both verify author13 and controls18hashes, originalPNG/separateXML and actual10/3/2 versus9/2/1. One reproduced evidence-index finding resolved by retaining matching process subfolder; root+both axes reproduce exact originalcountrecords/deathlogs from durable proof. Full10run38165304273 remainsactive; no21closure claim. Handoff now consolidates current acceptance rather than repeating superseded pending states.

Final full10run38165304273 atfe710dd PASSES with both public reads:179reported/178PASS+existingAPI30+profileassumption,46records/15actualdeaths; core55/0/0 and debug/test/release/lint/signed5→8/cold continuity pass. Personal8PASS68.771s, actualdeath21951→22148/cold1PASS5.708s; survivingguardstat0/213bytes; samewallet/backend/independentchoices/peerkeys/zeroTX. Author8PASS43.911s and currentpositive native/imageConfirm captures qualify fixture readiness. Publicowner1PASS8.2s/official1PASS4.011s; ordinary100rows/92posts/0ignored,quotes4rows/0posts/0ignored onboth. No counts explanation/exhaustivehistory/chain/Registry claim. Root all29original personalPNG/separate29XML,2author originals/separate2XML and each2public original individually inspected. Anonymousreading bitmap header fillsviewport, so cachedroot visibility is runtime/XML proof there; ordinaryUnmute bitmap showsProfile20, processUnmute literalroot. Durable final-full/android10 retains168hashes/all countable rawlogs/checkpoints/source/core; root exactrecount179/46/15+assumption identicaloriginal. Product/core/reference/workflowdiff781...fe empty; existingprofile/authorfixture+hostmetadataonly changes preserveacceptancepredicates. Passedfull17retained perWORKFLOW; final boundedpair evidence review required before21closure.

Final evidence caption review finds2documentation errors, now corrected: fresh single original-detail view of exact retained anonymous-cached-reading.png visibly shows Anon-jesTu2 and literal reference root below the warning; root and both reviewers agree on verified111754b65e23d129955935c87ad0ec625acbceef9a2a26005c2863ee0a970080 original. Earlier batch-reading caption excluding visible root was inaccurate; no differing-view cause inferred. The current full10 public originals show a signed post byAnon-osa8eP; earlier notification-history topic attribution is removed. Root had independently viewed both correct backend originals; no topic claim is needed. Both discrepancies affect captions only, not runtime/semantic/count or acceptance gates. README corrected and168hashmanifestregenerated; finalfixreview remainsrequired.

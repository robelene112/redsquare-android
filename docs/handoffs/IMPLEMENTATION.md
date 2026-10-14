# Sequential implementation handoff

Updated 2026-10-14. The owner authorized implementing all 35 proposed
tickets sequentially, committing after each, and reviewing the app at the end.
Continue without routine permission requests or a grill per ticket. Ask only
one question at a time when a material choice really needs the owner.

## Current work

**ACTIVE, owner resumed2026-10-14:** continue sequentially until all remaining
implementation issues are complete. This explicit resume supersedes the storage
migration pause recorded below. Commit/push each ticket and keep signed reviews
available. Do not pause after the next ticket or ask routine approvals.

Twenty-two of the original35 tickets are complete (#2 through#15,#18,#20 through#26).
Current issue39 is the bounded group Chat visual polish explicitly requested after
#26 in docs/PROJECT_CONTEXT.md. Review base8528893a9e659e0556581d08f547246d01b7d09b.
New group message bubbles/identity rows share existing private Chat presentation;
privacy/status, quote/copy/reaction actions and all protocol/persistence behavior
stay intact. First local compile6924 failed due to a wrapper-closing brace during
extraction; corrected compile46724 PASS2m57, .scratch/group-polish39-build-corrected.log.
Source95e089699c6192abaea0d9dbacb7dbf4af04e69e pushed; both source reviews0.
Full10run38633250917 at95e FAILED group22 21PASS1/116.296 at735 guidance
display after prior displaywaitpassed; rootcaptureCheckingstate, transitionhypothesis
only. Full17run38633255144 at95e active, public_feed_read=true. Test-only7b49825e
waits automatic protected public watch identifier, Activityrecreate/reentry andsame
strict15s guidance checks. Both correctionreviews0. Corrected full10run38635529637 active atdfa360534ce992216f911f0120cfaf0fa7618e9e, bothpublicfeeds.
Productsourceequal95e/16; full17old95e remainsactive. Correctedruntimepending.
Signing8792PASS3m9, signed16/0.1.16 PREPAREDNOTPUBLISHED, source95e,
APKsha0b34d6e6537ebc130c841cb884a7a9253c0b2a181ed6dc37cb4511e500721163,
certsame15/appID/min29verified. No ownedlocalbuildactive. New large CI downloads use the verified
user-owned /mnt/storage/redsquare-android-artifacts directory. Use existing group/private
native seams and full10/17 for acceptance; no implementation-mirroring visual tests.
Only protected preexisting untracked docs/research/NATIVE_GROUP_CHAT_CONTRACT.md
stays untouched. Issue39 tracker https://github.com/robelene112/redsquare-android/issues/39.

After39 resume27 and remaining original16/17/19/28 through36 sequentially from
native dependency state. #38 is a normal-priority branding follow-up, with final
product name not chosen and explicitly not a release blocker. Current signed15
remains live until the next verified review publication. Physical Pixel evidence
and Play account/submission prerequisites remain real acceptance requirements,
not waived by autonomous implementation authorization.

Storage migration observed Docker data now bind-mounted from /mnt/storage/docker;
repo/.scratch remains on the system disk,63GiB free versus158GiB onadditional disk.
Read /home/david/agent-skills/environments/vps/STORAGE.md for large future artifacts.
Proactive maintenance check: apt metadata refreshedtoday and no pending packages;
BC1.86 matches current official stable download listing. Keep deliberate build pins
unless a verified relevant maintenance change is separately implemented and tested.

**#37 Early visual beta is COMPLETE and published.** The owner requested this
slice before Group Chat. It adds coherent native feed/navigation/type/avatars,
system/light/dark appearance and styled private Chat while preserving existing
wallet/backend, safeguards, drafts/cache and signing/transport behavior.
Product source4c8f14ca34c2726881e074dab6de73cbce6f5e77; starting reviewbaseca2ade6a.
Whole/refinement/fixture/source/final evidence reviews havezero remaining findings
on both separate axes. Canonical chronology: docs/evidence/ticket37/README.md;
separate reports REVIEW.md. Priorselectorfailures and cancelled40m workflows stay
qualified;60m joballowance changes no individualtestdeadline/acceptancepredicate.

Final full10run38501541774 and17run38501544092 SUCCESS at exact
190690150569af28e3eeeb706fa38ca9c82d7881, public_feed_read=true.10 276reported/
275PASS+oneexistingAPI30profileassumption/61records;17 277PASS/62records;19actual
processdeathstages each. Core86, release/lint, every existing feature/recovery/
wallet/update gate and final diagnostic pass. Duration34m19s/35m25s. Final evidence
contains1,115verifiedhashes/382parsedXMLs, no secretmarkers. Rootandreviewagents
inspected selected final originals; applied32/16 andexact10no/17custom_bedtime
restoration retained. Configuredcontrast audit is not exhaustiveaccessibility.

**Historical #37 phone build was signed14/0.1.14**, superseded by15 below.
Publicpage/manifestHTTP200 and downloadedAPK hash/signature/package/versionverified.
Source4c product-equivalent tofull19069015. APKsha
`dffba7dbdddaa18b090d61fcbb69366f4084289b424c7d37788b956f48a58364`;
cert`6ca1209bcbba661943f7b683d48b2f58493f13851c760c8d80d1521ac0ee79a2`.
Package/signing unchanged; ordinaryupdateinstallovercurrent withoutuninstall.
Local .scratch/review-builds/14; publicrecords docs/evidence/ticket37/signed-preview14-prepared.
No store/node/service/ingresschange. Current14physicalPixel/continuity/performance
remain35. Preview13superseded;12neverpublished.

**#26 COMPLETE, HISTORICAL PAUSE BOUNDARY, 2026-10-14 19:13 UTC:** implementation, full10/17,
separate source/evidence reviews, signed review publication, commit/push and
tracker closure all complete. Issue26 CLOSED at2026-10-14T19:13:21Z, completion
commit e8a815ef37b7fe3372b70d2e3e889e50ae590568 pushed to main.
The owner subsequently resumed; the ACTIVE instruction above is current.
Protected untracked docs/research/NATIVE_GROUP_CHAT_CONTRACT.md remains untouched.

**Current phone build is signed15/0.1.15**, https://davidengelen.nl/redsquare/.
Manifest source7d695abf9e00df57106ab2b167d63b05b66c9a25, production/build config
identical to final full10c5193954631f26541c3421d51db23c49bdecd3a9 and full17
03aacb31848bd724d376df8f8fc7d6f03d1d89a6. APKsha
21917bb6d3b611c99fd6789ceb8de64f4c15976c95815918e26ae738e15eb241;
cert6ca1209bcbba661943f7b683d48b2f58493f13851c760c8d80d1521ac0ee79a2.
Public page/manifest/APKHTTP200, exact downloaded hash and direct signing/package/
version/min29 checksPASS. Signed14 immutable artifact preserved; updateinplace,
no uninstall. Local .scratch/review-builds/15, publicrecords signed-preview15-published.
No node/service/ingress/store changes. Physical Pixel/update/performance remain35.
Signing/review state outsideGit under ~/.local/share/redsquare-android/signing and
review, canonical management docs/REVIEW_BUILDS.md. No owned local builds active.

Final10run38613217864 ATTEMPT2 SUCCESS atc519, public_feed_read=true:
302reported301PASS+oneexistingAPI30profileassumption,66records20actualdeathstages,
core110/release/lint/source/fullruntime. Group22/122.025s,actions1/9.991,mixed1/6.366,
groupPID11704to11990/exactrestoration; existingprivate2/24/10/PIDrestore+allprevious
feature/wallet/signedupdate/publicChat/bothfeed gatespass. Final17run38614860580
SUCCESS03aacb31,public_feed_read=true:303PASS67records20deathsnoassumptions,
core110/allgatespass; group22/130.524,actions1/10.468,mixed1/6.887,PID5475to5556.
Productionequal,17onlyreviewedtestdisplay-readinessfixsame15s/finalassertion,
perWORKFLOW. Final-full evidence650/655hashes231/233XMLallverified/secretmarkers
absent. Rootandreviewersselectedoriginalsviewed; bothfinalevidenceaxes0/0.
Whole source bbdd121c...90e+sharedharnesscorrection f259 also0/0.

Earlier actual REDs and failed attempts remain qualified. Full10attempt1SDKcurl92
prebuild/device originalexcerpt/jobsteps retained; attempt2 unchangedsourcepass.
Full17firstc51921PASS1guidancevisibilityfailure, source supports transition
hypothesis only; e95visiblewaitsame15s finalassert, no productchange. Priorfocused17
actionsclassOK54.77 buthost124 FAILED, no latercoverage. Freshfinalfull17passesall.
Focused10 hadall24groupcases+actualPID/recovery; finalfulls supersedeacceptance.
Handoff chronology/research docs retain exact earlier sources andunexecutedtails.

#26 includes namedcreate/invite/join/memberview/leave, compatible signed/explicit
unsigned origins, incomingcontrols, immutableguardedRetry, plaintextmessages,
reactions/replies/copy/selfmentions, drafts/cache/archive/discovery and failure
recovery, private/group reception independentofscreen. Accurateunencryptedprivacy,
truthfulunknown/forwarded status, owner/backend isolation andno automaticresend.
Outgoingadmin controls27, offlinequeue28,media/push andphysical35 remainlater.
Do not claim complete35-ticket app orPixelperformance. Historical pause ended on owner resume.

Earlier chronology below is historical, not unfinished current26 work.

Imported-hints/discovery and all21 existing named-group cases GREEN at
f25c90059e1488e50e5a129c1967f3e1e1d1009f: Android10run38598680108/118.174s,
Android17run38598684948/132.816s. Original hints/reopened/storage captures inspected,
qualified native-imported-green both SDK retained. Earlier imported RED and failing
attempts retained with precise coverage. Bounded Spec/Standards reviews0.
Public IDs grant no authority; only available authentic creator-origin recovery.
Watch metadata preserves wallet key/options and only candidate-null RECEIVED records
update passively. Outgoing work retains pre-Retry wallet. Warning persists across
metadata store changes until corresponding successful explicit Refresh; each token
consumed before async attempt, never reused after failure/cancellation. These fixes
and guarded current-member routing/reactions passed all21 both SDK.

New actions fixture36c11a23 compiled2m, reviews0. First attempt38599898201/9fcc
ran old21 first and failed draft Refresh final visible message1004 (20PASS/1,
108.148s); action class did NOT run. PNG/XML shows restored draft enlarging composer
and clipped last message author. Correction08f45 waits exact restored draft before
message scrolling and runs focused action class first. Assertions/deadlines remain.
Qualified native-actions-prerequisite-attempt10 retained, both reviews0.

ACTUAL action RED38601182527 at08f45d12a6d0684eeec44784fc62ecccfd19a79b:
1case FAIL19.322s missing enabled Copy66. Original PNG/XML shows cached message and
Like but no Copy/Reply. Clipboard/composition/wire/recreation tails unexecuted;
21/PID not run. native-actions-red10 retained. Minimum454e340e now shares
chatReplyPrefix with existing private API, adds guarded group Reply/Remove reply,
body-only composer/quote preview/mentioned quote author, shared sensitive body-only
Copy and existing renderer's cached quote scrolling. Durable preparation/exact
original routing unchanged. Both bounded reviews ACTIVE against7c11...HEAD.
Root self-audit: member role lower-case text should become equivalent resources
once owned build ends. No action GREEN yet.

PID tracer7c11b99f APPLIED/COMMITTED, test APK compilePASS2m3, both source reviews0.
New GroupChatProcessRecoveryTest uses existing independent creation/message vectors,
protected public-fixture creation key, authenticated removal, actual typed draft,
UNKNOWN reaction preserving draft, explicit list Refresh to publish seeded status,
strict restored draft before message visibility, unavailable Archive, exact digests/
roles/wallet/backend/no sends. Host verifies actual old PID gone with checked probe,
fresh different PID, exact checkpoint phase/PID-only delta and restoration.
No process recovery runtime yet. Shared ProcessFixtureFiles and process_check_utils
remove real snapshot/driver duplication with private fixture, preserve per-fixture
allowlists and assertions, validate saved filenames before deletion, keep encrypted
snapshots device-only and preserve primary cleanup failure. Group driver runs after
successful UI gates in focused/full scopes. Python compile/bash syntax pass.
Scratch PID/actions files are older prepared snapshots: DO NOT copy over applied
sources. All previously listed apply scripts are obsolete after application.

NEXT: inspect current build/reviews, fix any actionable finding after build ends,
add translation-ready equivalent member role labels, push exact verified source,
run focused group-ui10/17 including action1 + group21 + actual group PID/restoration.
Inspect raw originals and retain qualified actual proof. Then fresh full10/17 same
commit with public_feed_read=true, core/release/lint and private PID harness regression,
whole26 two-axis review frombbdd121c/fixes, signed next phone beta, accepted closure.
STOP after26. No production node/service/chain mutation or Play submission.
Root sole writer; reviewers read-only. Global skills applied: no authored em dashes,
ordinary chat questions only, explicit gh -R robelene112/redsquare-android.
Latest tracker progress: issue26 comment6173373505 records imported21 both GREEN.

**Historical #26 checkpoint, 2026-10-14 10:14UTC:** known-admin native RED38546506531/95207cf0
8cases67.047s has7PASS plus missingretainedadmin fixture90. RootPNG/XMLshowsbad
name/admin; unscopedname notproof, laterwire/recoveryassertsunexecuted. Corrected
source8d8e9004 resolvesknown details offMain beforeconsent; storeindependently
validatescanonicalname/admin/exactrecips. Strongerclaims/provenance assertions
areDialogscoped and Join readinessbounded. Correctionreview95207...ef3647ae
Standards0/Spec0. Localdebug/testAPKcompilePASS3m28s. No ownedGradle.
Next push/dispatch eightcasegroup-ui10/17, inspectactualallcases/outertargets/caps,
then message/incoming/history/import/isolation/actualPID/full26. Donot reapply
known-routing script now: already applied.21originalclosed/#26open/beta14unchanged.

**Prior #26 checkpoint, 2026-10-14 10:03 UTC:**21 original tickets complete;
#26 still open. Stable signed14/0.1.14 remains live with37 visuals, no group APK.
Native creation/invite/member/key/work/leave and exact Retry verified10/17.
Latest Retry six-case GREEN4743355c:10run38543182621 PASS26.252s,
17run38543185574 PASS65.008s. Independent saved metadata, controlled disconnect,
identical original retry and noautosend through recreation pass. Root forwarded
PNGs/actualXML/raw summaries/hashes retained native-retry-green-android10/17.
This is seeded preparation/recreation, not real PID death or network admission.

Known-group rejoin module tracer a51d9202 reviewed0/0, compiled1m40; actual RED17
38544239476 at362f97c2 seven cases/41.162s,6PASS plus duplicate-group rejection
in store62/fixture317. Minimum81af8bb5/cf8e6897 preserves existing origin/key/
controls/name, allows own rejoin without restoring former ADMIN, rejects pending
replacement. Metadata uses retained name/key. Invite is guarded by known active
admin to avoid fromGroup failure for orphaned groups. Local debug/testAPK compile
PASS3m15; Standards0 but Spec found routing still trusted the pasted admin.
That minimum has NOT been declared native GREEN.

Known-routing core helper usingKnownGroup now chooses retained current admin/name,
keeps inviter without granting role, uses original anchor only if no active admin
is known (does not restore ADMIN). Core tracer uses genuine public creation with
returning member2/currentadmin1/conflicting4: RED1 stub then GREEN3 invitation
cases, build1m3/1m4. Globalcore103 has not yet been counted by root.

Current HEAD/remote95207cf0e682ee2eec0a2973494d2509faa5e3a7 adds native seeded
confirmed-group wallet4/admin1 leave/rejoin with conflicting pasted adminself/name.
Checks saved preview, signedinner recipient sequence, outerRelay targetobservations,
roles/origin/name/metadata/noresend. Existing module tracer now uses resolved invite.
ControlledFeedPeer records actual outer to-list. Standards0, coreSpec0; native
Spec2 requires modal-scoped name/admin and bounded enabled Join readiness.
Both corrections are prepared, not yet applied or counted as proved. TestAPK
compilePASS3m2. Active eight-case RED17run38546506531; source38546507443.
No owned local Gradle. Do not redispatch/cancel without a new concern.

NEXT: inspect actual RED/raw/PNG/XML, then apply
.scratch/apply-group26-known-routing-ui.py. It includes native Preview resolution
from known groups off Main, saved-details notice, exact retained-admin recipient
validation in prepareInvitation, and scoped display/readiness fixture corrections.
Compile/review fixes and pass eight-case native10/17. Original unscoped RED must
remain qualified; later strengthened assertions unexecuted until GREEN.
Then original group message/incoming/history UI/cache, imported metadata/history
limits, owner/backend isolation, actual PID recovery, final full10/17/release/lint,
whole-ticket reviews and signed next APK. Queue/media/mute/push are later tickets.
Protected untracked group contract unchanged. Old scratch create/join/leave/retry/
rejoin/tracer apply scripts are historical after application; do not reapply over
current code. Follow-on notes retain pinned source/history facts.

**Owner priority, 2026-10-14:** after #26, inspect and implement a bounded UI
polish pass for showing the beta to friends, especially new group screens and
first-use presentation, before later feature tickets. Existing #37 is complete;
this request does not reopen it or reduce accepted feature scope.

**Next active ticket: #26 Create, invite, join and leave unencrypted group Chat.**
Implement sequentially after37 closure; no new broadgrill. Allblockersalreadyclosed.
Read#26/#1, current nativeprivateChat, pinnedprimarywebgroupconstructors/receivers,
and protected untracked docs/research/NATIVE_GROUP_CHAT_CONTRACT.md. This research
file predates theactivework and mustnotbestaged/deleted/edited indiscriminately.
Rootfollow-onnotes .scratch/group26-follow-on-notes.md are preparation.
#26 nowclaimed; reviewbasebbdd121c. Independent public pinnedwebcreation corpus
verified viaWASM/Elliptic, native creation firsttracerstub/coretest added. CreationtracerRED1UnsupportedOperationException thenGREEN1PASS; builds1m4/1m3.
MembershipRED2(onefailure)→GREEN2; discoveryRED3(onefailure)→GREEN3.
Pure reader accepts authentic creation for later joiners without granting roles.
Signer/admin and signed first-recipient validation remain. All source still
protocol-only; no groupUI/Android/recovery acceptance. No ownedGradle remains.
Both bounded reviewaxes found invalid recipient point couldenterroles. Genuine
WASM/Elliptic bad-pointfixture reproducedRED4(3PASS+1failure); receiver now checks
all recipientcurvekeys, GREEN4PASS/build57s. Bothboundedfixreviews0, previousfindingresolved. No ownedGradle.
Ordinarylinkoracle now8actual creator/member links with rawBase64plus/Unicode,
actualCrypto wrappermatchesA/B. InviteparsertracerRED1stub/build54s. Parser
implementationGREEN1PASS/build56s. NoownedGradle remains. Next: malformedlink/recovery checks,
explicit join trust provenance, groupUI anddurablelifecycle. No26wholeacceptance.
Invitation errors RED2(oneJsonSyntaxException) thenGREEN2PASS/build57s. Parser
normalizes errors without exposing pasted content/cause; malformedURI/UTF8/null/
duplicatequery cases and eightvalidlinks pass. A failed verifier assumption caused
one unchanged RED repeat17s, retained honestly. NoownedGradle remains.
Creationsourcepush38507535486 at058460d3 SUCCESS; doesnot include laterinvitation
source or AndroidgroupUI acceptance. Both invitation-source review axes0 at058460d3...b75e4592.
Next:
signed join/leave/message controls, durable owner/backend groupstate and nativeUI. No membership/UI/recovery implementation yet.
Chronology: docs/research/NATIVE_GROUP_CHAT_VERIFICATION.md. Preserveunsignedweb
invites with honest authority/privacy provenance, existingmembership rules and
recovery. Do not treatparticipantprocessedhistory asoriginalsignedmessageproof,
or grantadmin authorityfromnon-admin targeted snapshots. No productionwrites.
One ticketwriter;reviewsubagents permitted. Original21of35completed count unchanged
by37 (childof35). #16Giphy/#17YouTube/#19Registry external gaps remainopen.

**Completed private Chat proof:** Android17 full38484424347 at4e3a6ea7 passes
273 cases/58completion records/19actualdeaths/no assumptions. Corrected Android10
full38487263465 at90c85603 passes272reported/271PASS/one existing API30 profile
assumption/57records/19actualdeaths. Both pass core86, source/debug/test/release,
lint and every current feature/recovery/wallet/update gate. Both final evidence
review axes0; checksums/XMLs/checkpoints independently verified. Production is
equivalent;10-only scoped picker correction preserves all predicates and the17
companion qualifies under WORKFLOW. Failed originals/diagnostics remain retained.
See `docs/evidence/ticket24/final-full/android{10,17}`, REVIEW.md, public-feed and
public-read. Private Crypto/constructors/receivers use independent pinned Saito
references. Public reads and controlled writes remain distinct.

Earlier preview13 is superseded by published14 above. Signed5-to8 automated
continuity does not establish current14 physical Pixel acceptance.

Group Chat, Chat media, offline Chat queues and closed-app push remain in their
original tickets.37 is a visual beta slice;35 remains the integrated final scope
and physical acceptance. After37, resume26 and the rest sequentially.

No owned Gradle remains active; final37 full runtime runs are active as above. Protected untracked
`docs/research/NATIVE_GROUP_CHAT_CONTRACT.md` belongs to26; never stage/delete it.
No production node/configuration/private-wallet/database writes or store
submission are authorized. Signed review-site publication is authorized.

## Historical checkpoints

The following records are historical evidence. Current status is the section
above; old statements about active runs or unimplemented behavior are superseded.


#25 is active;20of35 tickets are closed (#2–#15,#18,#20–#24).
Stable phone preview0.1.11/sourcea36972f remains https://davidengelen.nl/redsquare/.
Owner's looks-good does not establish Pixel timing or update continuity.

Accepted product2fff210a includes Copy, older-history cursor fixes, native Reply
composition/removal/raw encrypted draft retention/body-only editor, encrypted
reply-author auto-mention, and available-local quote navigation. Reply production
has no accepted native GREEN yet. Minimum local core86/debug/test APK build
passed3m28s. An earlier exact owned build was deliberately stopped to stabilize
callback identity and escape literal quote markup before runtime dispatch.

Corrected Reply26GREEN source9fe61503/product2fff210a:
Android10run38455813479 passes26/116.507s/core86;17run38455816701 passes26/
172.881s/core86.45XML/6unique normal+zero draft/forwarded/cached originals each
root inspected. Both captures perSDK independently PASS pinnedCrypto/WASM/exact
body/auto-mention/actualChat receiver+duplicatesuppression. Actualprocess:
10cold3.961s/restore.132s/PID8160→8427;17cold4.928s/restore.155s/PID5278→5355.
3XML/3originals/checkpointdelta each verified; installation snapshots remain
deviceonly andrestore. Quote navigation now directlyverified by subsequent3ba3df98 runs below.

Prior26RED38454040603 at67fbbb00/product4d8ba673:25PASS115.77s/core86,
onlyzero quote-onlyguard failed383;43XML/root original inspected, zerotail and
process skipped. Source/review correctionsd973+2fff alignproducer/parser shared
0..JSsafe policy andguardunsupported targets. Minimum4m15s/core86/APKs and
sharedrange maincompile3m16s PASS; bothfollowup reviewaxes0.

Executed pure reactionreference123986f0 supports pinnedChat construction/toggle,
realTransaction JSON and independentWASM/elliptic with namedfee/network adapters.
Repeated corpus identical; bothreviewaxes0. No appreaction implementation yet.

NativeReply firstRED38452059233/source53c7bd90:25cases24PASS113.541s/core86,
missing Reply at377,40validXML/root original inspected. New draft/send/metadata/
cache tail and processcheck were unexecuted. Earlier bounded minimum reviews:
Standards0,Spec1(timestamp bounds); corrected in d9737678, pending nativeGREEN. No whole#25 acceptance.

Copy is accepted at unchanged product439fe52e: corrected10run38450331371 at
72ef47cf passes24/114.206s/core86,39validXML; actual cold4.738s/restore.171s,
PID7546→7806. Earlier17run38448319576 at439 passes24/156.175s/core86,
cold5.318s/restore.151s,PID5190→5275. Root original/clipboard/cold captures and
checkpoint records inspected. The17 companion did not execute the new public-
list scroll. Virtualization remains a hypothesis for earlier10body failures.

Older-history24GREEN source85c8f1a3:10run38445452902 100.762s and17run38445457782
125.107s/core86. Correct signed-page cursor advances through50unreadable recent
messages, retains ancient cached history, fetches readable older messages with
exact cutoff/exhaustion/cache/no-send checks. Actual process recovery is accepted
on bothSDKs with exact wallet/backend/cache/draft/UNKNOWN original and no sends.
Device-only encrypted originals restore; artifacts contain only public fixtures.
Native public17 both service/read contracts38442247790 PASS1/4.702s, no sends.

Next: resolve Reply timestamp regression, qualify bothSDK Reply+independent
metadata/receiver checks, compatible chat-like actions/state/retry, actual wallet/
backend isolation, final whole reviews/fullSDK/release/lint/signed update/public
reads. No Pixel/fullticket claim. Protected untracked
`docs/research/NATIVE_GROUP_CHAT_CONTRACT.md` belongs#26; never stage/delete.
No production node/private-wallet/database writes or store submission authorized.
Git history and the current section above supersede this historical checkpoint.

#24, "Show in-app social notifications", is complete after both full emulator
runs and both final evidence reviews. Twenty of35 tickets are closed
(#2–#15,#18,#20–#24). No new phone APK; stable previewv10/source5225d06.

Final notifications source68633d976a5ef6dfa4ce38d982af46ea2f24edb1;
whole source reviews d1e65f5...0798b55 both axes0 after self-like warning and
remaining-parent discovery fixes. Full10run38330887204 atb80b878 passes236
reported/235PASS+one existing API30profileassumption/54completed/18actualdeaths.
Full17run38328912121 ata5648ee passes237PASS/55completed/18actualdeaths.
Each passes core74/release/lint/debug/test/signed5->8/cold wallet/cache and both
separately enabled public reads. Production equivalent; reviewed test scrolling
and diagnostics-only changes qualify under WORKFLOW companion rule. Final
full10 evidence review982c502...9befdf8 both axes0; full17 Spec0/Standards0source
findings and one corrected caption. Durable evidence: docs/evidence/ticket23/
final-full/android10 and android17; canonical chronology and separate reports
remain NATIVE_NOTIFICATION_VERIFICATION.md and docs/evidence/ticket23/REVIEW.md.
Failed intermediate runs remain retained without invented causes. Earlier
accidental auto-close was reopened; close only after final acceptance.

#25, "Exchange encrypted private Chat messages with existing clients", is assigned
and active. Whole review basec102f891f6041f08199905728d302e19b7588abd.
Authority parent#1/issue25 and docs/research/NATIVE_PRIVATE_CHAT_CONTRACT.md.
Canonical chronology NATIVE_PRIVATE_CHAT_VERIFICATION.md; separate reports
in docs/evidence/ticket24/REVIEW.md. Accepted seams1–4 authorize controlled
native/crypto/recovery/isolation verification. One ticket; no extra grill needed.

Implemented: compatible encrypted send/read, original propagation/Relay and
truthful Submitted/Forwarded-not-confirmed state; private encrypted history and
work stores; optional first-Chat permission; new-contact entry/shared portable
wallet metadata; modern/legacy imported-ID recovery; partial discovery/history
preserving readable messages and opaque old ciphertext; selected/list live
receive; cached contact discovery. Matching imported exchanged keys recover old
ciphertext after newer saving. Missing-key warning survives storagefailure/Refresh.

Latestproduction dd02415 retains fullyverified other-contact messages beforeACK,
rescans contacts on navigation and recomputes discoveryerror duringselectedRefresh.
Localdebug/testAPK PASS1m49s; both boundedreviews53fd84b...dd02415 0. Core85
lastfull655c1e5/subsequentCI sourcegates; newer localapp builds leavecoreunchanged.
No whole#25acceptance. Read gitlog for HEAD/currentuncommittedwork.

Actual13case sequence:
- 22b07ed RED10run38363644050/17run38363647971 12PASS+1line260ACKfailure,
  61.98s/90.368s. Source256b67f fixesothercontactsave; reviewsboth0.
- 256b67f attempts10run38384045182/17run38384048900 12PASS+1line263contact
  failure82.903s/113.486s; ACKpasses. Source53fd84b keysnavigation, reviewsboth0.
- 53fd84b attempts10run38384956940/17run38384961151 11PASS+2line311storage
  recoveryfailures89.688s/104.894s. OthercontactcasePASSboth; staleloadbanner.
  Source dd02415 removesselectedscanearlyreturn, reviewsboth0.
- Current13case native10run38385764786 PASS13/61.117s;17run38385768655 PASS13/83.051s atdd02415. Root15XMLeach plusotherrecreated/storagewarningoriginalsviewed. Fullticketpending.
All failed rawlogs/exit/source/PNG/XML retained. RootallXML/failureoriginals
inspected; 10secondcontactrecreatedimage showscachedplaintext whileLoading,
notcompletedArchive. Rawinstrumentationexit0 despitefailures; mandatoryOKgate
rejects. Class180s withindividual5/15swaitsunchanged. Avoidsame-scopeconcurrency.

EarliercompleteUI10 checkpointdf9891d/run38362024612 PASS12/55.833s.
SameSHA17run38362028575 truncatedafter11PASS underold90sclassbudget/noexit;
noobserved124/appfailurecauseclaimed. Earliercomplete17a6c525c/run38355059918
PASS9/83.692s. Mixed-key/restored-key andstoragewarningchronologyiscanonicaldocs.
Public native10read38352335633 ate2eb51d PASS1/1.649s verifiesbothfixedbackend
Chat/Archive/Relayservices/zero rowsforrandomunusedpair. No livemessageclaim.
Actual unsafe native10/17 encrypted send independentlyverifies pinnedCrypto/WASM;
defaultreferencecorpus886ff622...ee1c06 remainsunchanged.

Nextcompatibilityslice: pinnedDMmentionsaddthirdrecipient whileencryption/pair
IDremainprivate. Currentexact-two validator/outgoing.single/cachemetadata reject
validmentionedDMs. Readonlyreviewconfirmedbothpins/popupmentionpath. Optional
--mentioned generator producesindependentbothdirectionextraoutputfixtures,
repeatbytes56ad3ead...359e5; defaultunchanged. Coretracer78020a5 actualRED10/9PASS+one exactrecipientfail40s. Minimalcoreparticipant/counterpart correctionfocused10PASS/build1m2s. Native14tracerlocalAPKsPASS3m2s. ActualRED10run38386689026 at5716d23 14/13PASS+onefail71.85s line326 cachedtextmissingafterrecreation, root16XML/failureoriginalinspected/core86. Minimalcachemetadata a05c2d6 localAPKsPASS2m/reviewsboth0; native14GREEN10run38387384587 PASS14/65.297s/17run38387388065 PASS14/105.853s, core86. Root15XMLperSDK/sharedoriginalsqualified. Test-only17cases529aa5b pass10run38388552509 17/68.6s and17run38388555732 17/119.614s/core86. Root24XMLperSDK/sixuniqueoriginalsviewed; bothboundedreviews0. Ownmentionedimport-ID/Mute/Block/unblockverified. New18caseunknownoriginalRetrytracer(localfinalbuildrunning) hasreal encryptedadapterindependentcandidate/noauto/UIexplicitretry, interruptedSubmitted/recreate/secondexplicitretry/exactoriginalbytes/Forwardedrecreate. Final18tracerTestAPKPASS1m40s/reviewsboth0. Actual18RED10run38389762051 at976158b 18/17PASS+missingRetrynode76.122s/core86; root26XML/failureoriginalviewed/noautosendpreconditionpasses. Submittedsecondretrytailnotexecutedpreminimum. Retryminimumapplied: originalcandidateforUnknown/Submittedemptydraft, prepareRetry bindswallet/backend/id anddurableUnknown beforeenqueue. OriginalRetrysourcee6cd339 localAPKsPASS3m1s/coreunchanged86, bothboundedreviews0; Native18SDK17GREENrun38390512523 PASS18/96.785s/core86/exactoriginaltworetries/noautosends;10run38390509276 17PASS+one line322secondRetrydisabledfail78.969s. RootXML/failure17finaloriginalsviewed. Positive15senabledwaitsPASSlocalTestAPK1m47/reviewsboth0; native10run38392865924 atc9465f8 PASS18/78.064s/core86/exactoriginalretries/noauto. SDK17e6sameproduct/olderassertsqualified. New19SAFpendingChat-risktracerlocalTestAPKPASS1m45/shellPASS; controlledbalanceport9/publicuniquefixturename, noactualswap ornetworksend. ActualREDnext, WalletImportchatworkinspectionminimumunimplemented. XMLrecount26corrected. Draftnavigation/erasure preservation andwalletimportchatworkrisks stillpending. Bothbounded dd02415...5716d23reviewaxes0.
Generator/runtimemounts publicsource/dependencies only/networknone/env-i; noappinit.
Ordinarywebtyping isinnerText; replyprefixcanbe sanitizedblockquote. Need narrow
recognizedreplydisplay preservingordinaryliteralanglebrackets/signedoriginals.
Detailedsourcefacts/hashes/snippets camefromchat_spec_review; durableauditreport.

Remaining: mentioninterop protocol/cache/native; readable
webreplyinterop; older-historypaging; nativeBlockhidesincoming/MutekeepsChat;
uncertainattempt/draftrecovery; WalletImportpendingrisksmustinspectchatwork(currentlyomitted); actualChatprocessdeath; wallet/backendisolation;
bothfinalfullSDK/release/lint/signedupdate/opt-in publicreads. ActivityScenariorecreate
isnotprocessdeath. PhysicalPixel#35/polish stillpending. No newphoneAPK; stable
previewv10/source5225d06. No realfunded/onchain/privatemessage/nodewrites orstore
submission. Protecteduntracked NATIVE_GROUP_CHAT_CONTRACT.md belongs#26, never
stage/deletehere. Firebase/Giphy optionalquestionsunanswered; continueindependentwork.

## Earlier completed ticket checkpoints

Profile #18 is closed, final evidence commit ed5742f pushed. Nineteen of35 tickets
are complete (#2–#15, #18, #20, #21, #22 and #23). Parked #19, "Register an optional Saito username",
parked on the external Registry prerequisite, review base ed5742fd0f57958d284073b7145f1844b72b1dbd. #16 Giphy still awaits
Android-authorized credentials; #17 actual YouTube provider playback remains
unproved. Do not close those from controlled source/layout successes.

Closed #18 reference: NATIVE_PROFILE_EDIT_VERIFICATION.md and
 ticket17/REVIEW.md/final-full/README.md. Both source axes clear throughb0e941b.
Final full10run38110790124 and17run38110321794 have145reported cases,
41completion records/13actualprocessdeaths each;10 has144PASS+oneAPI30+
assumption,17all145PASS. Core49, release/lint, affected regressions, publicfeeds
and signed5→8 update continuity pass. Production/build/reference/scripts identical
between b0e941b/aea25ca; only test/evidence/docs differ. No physical-device/current
DOM/chain proof. Do not repeat unaffected checks for historical #18 acceptance.

#19 native checkpoint10c59c5 now passes final full10run38130613902 and
17run38130615551. Each161reportedcases/43records/14actualprocessdeaths;10 has
160PASS+oneexistingAPI30+profileassumption,17all161PASS. Core55/0/0,
release/lint, both opted-in publicfeeds and existing signed5→8/cold continuity
pass. Username15PASS79.348s/89.192s plus actualcold1PASS3.476s/4.193s,
PID19067→19169/9653→9734 exactsamewallet/backend/sig/frame/envelope;
no auto send, explicit exact Retry. Both current nativeframes independently
pass pinnedWASM/Elliptic. Authenticated retirement corruption/repair/newrequest,
own-name cache warning retention/repair and wallet safeguards pass.

Implementation/evidence chronology: NATIVE_USERNAME_CONTRACT.md,
NATIVE_USERNAME_VERIFICATION.md; ticket18/final-full and other retained RED/GREEN
folders. Root inspects16criticalusernamePNG+XML and4publicfeeds. Both final
review axes complete:0 native/source findings,1 external Spec acceptance gap. No new APK/Pixel/chain/live registration.

#19 stays OPEN for demonstrated external Registry readiness: native10/17 public
known/unknown namechecks unusable21bytes; fresh host09:53:48Z onboth returns
{"err":"no response"} despite authenticated archive/key-map reads. Host helper
filtererror is its own summary defect, not server key-map failure. Source/host/
native observations remain distinct. No node changes/trust fallback/livewrites.
#20, "Search posts and people in available backend history", is CLOSED.
Final evidence commit99e58c6 is pushed; closure comment6110277037 records proof. Review base is
3be555be85586b68c0d05c2c8e69d3976bbffe89. Accepted seams 1–4 apply.
No additional owner grill or intermediate phone gate is required.

Implementation includes signed posts/replies/quotes on the selected backend,
independent bounded paging and partial retry, available offline cached history,
profile/thread/back/recreation navigation, canonical exact keys, verified exact
usernames and prefixes of known accounts. Historical names receive provenance.
Name-check failures are independent of post history. Post keywords and prefixes
are matched locally; an explicit exact-username check sends that public identifier
to the selected Registry service. Save warnings and retained author edit/deletion proofs are
integrated. No exhaustive chain history or permanent archive is promised.

Whole-ticket source review through fd35b02: Spec 0 actionable findings;
Standards 0 residual hard violations or actionable heuristic smells. Subsequent
existing posting import fixture corrections also clear both axes. Contracts and
chronology: NATIVE_DISCOVERY_MODERATION_CONTRACT.md, NATIVE_SEARCH_REFERENCE
(.md/.json), NATIVE_SEARCH_VERIFICATION.md and ticket19/REVIEW.md.

Final full acceptance results, each opted into both public backend reads:
- Android 17 run38145373388 at6c489bf: 170 reported/all PASS, 44 completion
  records/14 actual process-death stages. Search9PASS58.789s.
- Android 10 run38147131217 at7b4dd14: 170 reported/169PASS plus one existing
  API30+ profile assumption, 44 records/14 actual process-death stages.
  Search9PASS54.238s; corrected posting18PASS100.284s.
- Both: core55/0/0, source/debug/test/release/lint, signed5-to-8 updates and
  actual cold wallet/cache continuity PASS. Public owner/official each1PASS:
  17 takes9.678s/7.672s; 10 takes10.869s/14.748s. Each Search ordinary stream
  returns100rows/90posts/0ignored; quotes4rows/0commentaryposts/0ignored.
  Counts do not establish exhaustive history, explain row/post differences,
  chain inclusion or external Registry readiness.

Root individually viewed all15 Search originals and separately parsed15XMLs on
EACH SDK, plus both fresh public feed originals on each. Durable
 ticket19/final-full/android10 and android17 retain119hashes each and all raw
countable completion/death logs. Root recomputed durable counts equal to the
original artifacts. Android17 bounded evidence reviews: both axes0 findings.
Android10/final evidence review also complete: both axes0 findings or closure
blockers. Both independently verify119hashes, counts, originals and paired source
equivalence. No need to repeat accepted #20 verification.

Two earlier full10 failures remain in full10-first-failure and
full10-second-failure. The first stopped at an old wallet-picker selector; exact
scheduling cause remains unproved. The next failed an introduced direct File
metadata guard before the maintained chooser ran. Fixture-only correction uses
fixed run-as stat under the provider identity and maintained native drawer/preview
synchronization. Final full10 passes the unchanged safeguards. Production/core/
build/reference/scripts/workflow and Search/public acceptance assertions are
identical between successful full17 and full10, as allowed by WORKFLOW.md.

No new phone APK, actual Search process-death, Pixel performance or live write
claim. Stable review remains version10/source5225d06. #19 remains externally
parked. #21, "Mute and block accounts across backend sessions", is CLOSED (closure comment6112726266).
All blockers complete (closed#8); review base99e58c6a49f5a10ee630f948ad3a18c9f5fb5592.
Implementation and focused verification are complete: independent encrypted
wallet-scoped Mute/Block across both backends; filtering in Feed, Profile,
Conversation, Search and quoted originals; management and truthful save/recovery
errors. Chat/notifications are dependent tickets, not delivery claimed here.

Focused run38161237509 ataa91ff2 passes8ordinary cases111.163s, including actual
SAF/authenticated wallet roundtrip/full opaque options, corruption repair and
actual PIN cleanup. Separate pending-save process death5044→5131 passes one
independent cold case6.427s without identity/backend/policy reset. The durable
previous-policy guard and independent rollback resolve the demonstrated late
save/removal/recreation defect. Root inspected the final six identity/corruption
and four process original PNGs, then separately parsed all ten XMLs. See
NATIVE_PERSONAL_CONTROLS_VERIFICATION.md and ticket20/identity-green/28hashes
for the full qualified chronology. Whole source and bounded focused reviews are
clear. Both final full Android17 and Android10 acceptance passed, as recorded below.

Completed final acceptance runs and earlier fixture interruptions are below. Preserve the
remaining group-Chat research file; continue one ticket at a time. No new phone APK.

Protected future untracked: docs/research/NATIVE_GROUP_CHAT_CONTRACT.md for#26.
Private Chat and notification references/contracts are already committed.
Include only the relevant research with its ticket. Review agents inspect only;
never run parallel implementation/CI dispatch or claim another ticket.

## Completed foundation references

#5 reviewed source177a647, final full37868642713 and focused public reads
37870028403/37870030262. Fixed backend selection, checked persistence and
uncertainty fail closed. Report NATIVE_BACKEND_SELECTION.md; ticket04/REVIEW.md.

#6 reviewed source7a2ab0b, full37892360326 passes Android10/17/core14,29 normal
cases per SDK plus actual export process-kill recovery, assembly/lint and signed
updates. Real PIN/SAF, provider failures/deadline and new authenticated retry.
Report NATIVE_WALLET_EXPORT_VERIFICATION.md; ticket05/final-full and REVIEW.md.

#7 reviewed source5225d06, closure44cae5d, full37921133133 passes both SDKs:
core15,39 completed instrument cases each plus2 actual process-kill stages,
release/debug/test assembly/lint, signed5-to-8 updates, both public feeds and
cold-cache/wallet recovery. Seven import UI flows, strict byte rejection,
same-wallet vs replacement, fresh balance-dependent outgoing backup gates,
verified old-state rollback and actual pre-commit cold recovery. Both axes
zero actionable findings. Detailed history and safeguards remain authoritative
in NATIVE_WALLET_IMPORT_VERIFICATION.md and ticket06/REVIEW.md/final-full,
and older handoff checkpoints are preserved in Git at44cae5d.

Initial wallet/live-feed milestone is published as stable signed version10
(source5225d06). No physical Pixel/update8-to-10/power-cut claim. Prior official
Compose bitmaps had inconsistent labels; use each report's stated evidence
limits. #9 also has official bitmap inconsistencies; no invented cause.

Historical after-wallet milestone note: continue #23 sequentially. It is now closed; active work is #24. Owner reviews the
completed app; no intermediate phone-review gate is required.

## Verification already inspected

Ticket #2: hosted run 37834551583 passed Android 10/17 independent signature
and visible diagnostic checks. See [native proof](../research/NATIVE_PROOF_VERIFICATION.md).

Ticket #3: run 37849279965 at 35dff32 passed both SDKs: eleven instrumentation
tests per SDK, cold-process identity preservation and actual signed 5-to-8
updates. Final two-axis reviews passed through 1b50e2f; a1973c2 is its final
status/evidence commit. Recovery fails closed for missing/corrupt published
wallets. Controlled interrupted-initialization fixtures model crash states;
they do not prove arbitrary physical power-cut behavior. Directory/file sync,
reopening verification and a nonsecret publication marker prevent Retry from
silently replacing an exposed identity. Detailed failures and fixes are in
[wallet verification](../research/WALLET_PREVIEW_VERIFICATION.md), with durable
public evidence under docs/evidence/ticket02.

Ticket #4: full run 37857285246 at 4fd87bd passed both SDKs, including separate
fresh native owner reads, cached cold launches, wallet recovery and signed
updates. Downloaded outputs/screenshots were inspected; public feed evidence
is retained under docs/evidence/ticket03/first-full. This predates later fixes.
Focused run 37858491264 at c17e0b5 passed reconnection and corrected refresh/
recreation position checks. It exposed a missing cached unreadable warning
(now fixed) and a storage fixture error: `.new` is not Android 10's write path.
The corrected fixture obstructs the actual public cache base and restores its
preserved contents in finally, without changing wallet storage. All four final
controlled feed cases passed on both SDKs in 37859209011, before its later live
Android 17 failure. See [feed verification](../research/NATIVE_FEED_VERIFICATION.md).

## Build and review operations

Repo: /home/david/projects/redsquare-android, public GitHub robelene112/redsquare-android,
main branch. Kotlin/JVM core plus Compose Android app, Android 10+; Docker
supplies Java 17, Gradle and SDK. Run scripts/android-build for local checks.
Hosted KVM provides actual Android 10/17 runtime verification. Intermediate
focused checks do not replace final full verification. Capture failures and
separate functional failures from fixture/build/emulator setup errors.

Stable review: https://davidengelen.nl/redsquare/. Versions5,6,8,10 published;
2,3,4,7,9 remain unpublished. Version10 source is5225d06. Signing state
is private under the operator's local share directory, outside Git/public
hosting; never print or copy its contents. Public certificate fingerprint and
publication/update checks are documented in [review operations](../REVIEW_BUILDS.md).
Review publication touches only the app's existing static directory.

Use project-local implement/tdd/two-axis code-review and applicable Saito
skills. Node access follows [operations](../OPERATIONS.md) and maintained Snake
routing. Production configuration, block production, on-chain actions and store
submission require their own explicit authorization. No production writer was
changed for the Android app. Read-only public integration is separate from
ordinary controlled tests; no production transaction was broadcast.

## Completed personal-controls acceptance checkpoint

#21 is CLOSED, comment6112726266;17/35 closed. Final product checkpoint aa91ff2,
whole-ticket review base99e58c6. Subsequent changes correct existing fixtures
and retain evidence; production/core/build/reference source is unchanged.

- Full Android17 run38161793299 at781bd1a PASSES:179cases/46records/15actual
  process deaths; core55/release/lint/signed5→8/cold continuity and both public
  reads pass. Personal8PASS82.867s, death10423→10513/cold1PASS6.65s. Root all29
  original personal PNG/separate29XML plus2public originals inspected. Durable
  final-full/android17 retains162hashes; both evidence review axes0final findings.
- Android10 full38163120112 atf0c6191 stops in the existing author delete
  fixture. Corrected offline profile tests pass. Fixture732e67f waits for
  displayed/enabled Confirm delete and captures live PNG/XML before one click,
  keeping all subsequent assertions. Focused author38164253681 PASSES
  ordinary8cases46.026s plus2independent cold cases/2actual deaths:10/3/2.
  Root2readiness originals/separate2XML; author10-ready-green retains13hashes.
- Focused controls38164297170 at732e67f passes all8ordinary cases74.394s but
  host metadata check stops before cold recovery. Discarded command output
  prevents proving cause or guard absence. Corrected host fixturefe710dd uses
  established stat positive-size gate and retains metadata only. Retry
  38164957454 PASSES8ordinary73.893s and actualdeath4897→5093/cold1case5.679s:
  9/2/1, guard return0/213bytes. Same wallet/backend, independent choices,
  actual peer keys and zero transactions pass. Root4process originals and
  separate4XML inspected. controls10-metadata-green retains18hashes.
- Both bounded focused review axes clear througha43e046. One evidence-index
  finding fixed by retaining process logs under their counts.json paths; root
  and reviewers reproduce exact durable9/2/1 and all18hashes. Sourcepush
  38164949469 passes. Python harness compile passes. No product/acceptance
  predicate changes; WORKFLOW permits retaining full17 after fixture correction.
- **Full Android10 run38165304273 atfe710dd PASSES**, opted into public
  reads:179reported/178PASS+existingAPI30+profileassumption,46records/15deaths,
  core55/release/lint/signed5→8/cold continuity. Personal8PASS68.771s and
  death21951→22148/cold1PASS5.708s, guardstat0/213bytes. Root all29original
  personalPNG/separate29XML,2author readiness originals/separate2XML and both
  public originals inspected. Durable final-full/android10 retains168hashes;
  exact root recount179/46/15+assumption equals original. Both public feeds
  pass8.2s/4.011s; ordinary100rows/92posts/0ignored andquotes4rows/0posts/0ignored.
  Final bounded pair evidence review through7f54d98 clears both axes after
  correcting2Android10captions. Acceptance complete; GitHub closure recorded2026-10-11T19:17:39Z.

Failure chronology and qualified retained captures: NATIVE_PERSONAL_CONTROLS_VERIFICATION.md
and ticket20/REVIEW.md. Earlier full10 stopped at existing lazy-profile
pre-scroll/readiness fixtures; finalf0c6191 corrects all3tabs. No offscreen
cache/deletion/timing cause was inferred. Preserve already accepted full17.

## Current reporting acceptance checkpoint

#22 is CLOSED2026-10-11T23:23:14Z, comment6115065628, final acceptance
commita797276 pushed;18/35complete. Whole-ticket review base9ecb97b. Product source complete
atac808f9; whole Standards0/Spec0. Reports name the selected destination before
consent, use compatible signed type12/self outputs/no type4, and distinguish
strict handler acknowledgement from moderator review/removal/chain inclusion.
Wire14/application errors remain delivery-uncertain and persist through reopening;
missing/malformed replies remain unknown. Exact signed intent is saved before
possible transmission; explicit Retry preserves candidate/wallet/backend. Noauto
resend, rerouting or removal. Newer genuine receipts are adopted atomically rather
than overwritten or resent. Accepted parent seams1–4 already authorized.

Final full acceptance PASSES on both SDKs, separately opted into public reads:
- Android10run38180155391 at709b58d:191reported/190PASS+existingAPI30+profile
  assumption,48records/16actual interrupted stages; report11PASS92.703s,
  actualdeath23785→23879/cold1PASS4.34s/635-byte record.
- Android17run38181575469 at92d80f3:191/allPASS/no assumptions,48records/16
  actual interrupted stages; report11PASS108.155s, actualdeath11147→11225/
  cold1PASS5.35s/635-byte record.
- Both core55/0/0, release/lint/debug/test, signed5→8/cold wallet/cache continuity
  and both approved public reads pass. Native reports preserve originalidentity/
  backend/candidate, noauto/exact explicitretry/no type4. Each public metadata
  read returns100rows92posts0ignored/4quote rows0commentaryposts0ignored;
  ordinary owner/official timings10:9.48s/6.512s,17:8.055s/7.132s.

Root individually viewed41ordinary+3process originals/separate44XML and2public
originals perSDK. Qualified ACK strip byte-equivalence and public header-only
observations are in eachREADME; retained Conversation still Loading replies,
not completed thread-loading proof. Runtime candidate/report count/authentication/
full opaque wallet restoration gates are separate from bitmap claims. Each durable
final-full SDK folder retains202hashes/allcountable logs, exact original/durable
191/48/16 recount (with10's sole documented assumption). Both final10 bounded
evidence axes0. Final17 paired evidence review through6d3888a also clears both
axes:0findings/no acceptance gap, independently verified202hashes/rawcounts/
originals/continuity/source-equivalence. Sourcepush38183784351 PASSES.
Ticket22 acceptance is complete and its GitHub closure is recorded above.

Full17 includes a reviewed existing-image fixture correction after prior setup
FileNotFoundException; product/core/reference/build/harness/workflow unchanged
709→92. Acceptance predicates retained; recognizable oversized source has a new
literal positive-size setup guard. Both source/docfix axes0 through92, focused
images17run38181457132 passes20/5/3 with13hashes; both evidence axes0 ate8b45e5.
WORKFLOW companion rule permits retaining full10. No successful rejection PNG/XML
or exact provider failure cause claimed. Earlier browse/posting viewport failures,
restored-receipt RED, timeout/save/failure/isolation chronology and all qualified
native/independent proof live in NATIVE_REPORT_VERIFICATION.md andticket21/REVIEW.md.
Do not reconstruct or repeat accepted checks from this handoff's old chronology.

Historical next-ready note after #22 closure: #23 balances/receiving/authenticated
SAITO transfers. It is now claimed and active as described under Current work. Read-only preparation .scratch/ticket23-readonly-plan.md records
public parameter observations/pure reference gaps; it is not implementation or
acceptance. Protected future files remain untouched. No newphone APK/Pixel/live
report/moderator outcome/permanent history/original scheduler reproduction claim.

## Completed authenticated-transfer acceptance checkpoint

#23 CLOSED2026-10-12T15:18:29Z, comment6127183552, finalacceptance commitd1e65f5
pushed. Whole-ticket base2fe1fa51ed8d0c0e250d77e7c2f4198809418fdd; app/protocol
sourcefcd9771e232007e3c4c6702bb3c135661639b654. Both fullSDKs pass that same source:
- Android10run38265052461:202reported/201PASS+one existingAPI30profileassumption,
  51records/17actualdeaths; core66/release/lint/signed5->8/coldcontinuity/publicreads.
  Transfers9PASS87.886s; actual25060->25226/cold5.976s/cleanup.08s.
- Android17run38220054361:202allPASS/noassumptions,51records/17actualdeaths;
  samegates. Transfers9PASS98.055s; actual11646->11742/cold9.168s/cleanup.043s.

Selectedbalance/fullreceivingkey/copy/QR, compatiblefundedpayment/exactreview/
realdeviceauth/cancel/freshretry, durable wallet-wide reservations, verifiedfull-
block/linkedancestry inclusion/Unknown/Reorganization, noautoqueue/rerouting,
funding/storage recovery andrealbackend/SAFwalletisolation verified. Separate
publicwallet journeys read actualapprovedHTTPSfreshunfunded0balance/funding/fullkey/
QR/returnfeed on bothbackends withnoauth/review/send. Notpositivefunding/admission/
consensus/livepayment proof. IndependentpinnedWASM/Elliptic/fullblock/oddMerkle/
wire compatibility remains qualifiedsynthetic evidence.

Finaltwo-axis whole/source/bounded/evidence reviews0residualhard/heuristics/Spec.
200/201finalSDKhashes/exactrawcounts/checkpoints verified. Root33/34nativeoriginal
PNG+35XML each,6publicoriginalPNG+4XML each; IMEfooters/clipboard/black-or-refused
systemPIN/runtimeauthority/SAF/duplicatebytes qualified in final-full READMEs.
No newphoneAPK/physicalPixel/independentscanner/arbitrarypowercut/Home-policy/node
change. Stablephonepreview remainsv10/source5225d06.
Canonicalchronology: docs/research/NATIVE_TRANSFER_VERIFICATION.md.
Separate reports: docs/evidence/ticket22/REVIEW.md.

## Outstanding external acceptance

Owner requested physical app review at completion. Pixel 7 Android 17 checks
are tracked in #35. Emulator results do not establish physical timing, updates
or push behavior. Use [the timing protocol](../research/FEED_PERFORMANCE_PROTOCOL.md).

Closed-app notifications require Firebase access and a gateway. A prior optional
one-question prompt asked dedicated versus existing Firebase project; no answer
has arrived. Continue independent work and never invent credentials or upload
wallet private keys. Source research is documented, not implemented delivery.

Giphy Android credentials are also unresolved. An optional single question on
2026-10-10 asked whether an existing Android-authorized key is available and
where configured; no response yet. Do not ask again routinely, invent a key or
assume a backend's browser key is authorized for Android. Source contract is in
NATIVE_GIF_VIDEO_CONTRACT.md. Continue independent tickets and controlled
implementation while external configuration remains pending.

Read-only research notes for later tickets were prepared during hosted checks:
NATIVE_QUOTES_CONTRACT, NATIVE_EDIT_DELETE_CONTRACT, NATIVE_IMAGES_CONTRACT,
NATIVE_GIF_VIDEO_CONTRACT, NATIVE_PROFILE_EDIT_CONTRACT, NATIVE_USERNAME_CONTRACT,
NATIVE_NOTIFICATIONS_CONTRACT,
NATIVE_SAITO_TRANSFER_CONTRACT, NATIVE_PRIVATE_CHAT_CONTRACT and
NATIVE_GROUP_CHAT_CONTRACT (all .md under docs/research). Only the group-Chat contract remains uncommitted for#26; other listed contracts
are committed.
They establish source contracts, not runtime or feature completion.


## Latest private Chat checkpoint

The 19-case SAF pending-work tracer at fd6a382 failed on Android 10 run 38395668530: 18 passes, one missing specific pending-work disclosure, 97.738 seconds. Generic acknowledgement is present; do not describe all warnings as absent. Core 86 passes. Failure evidence is in ticket24/ui/pending-chat-import-red-android10. Later cancellation assertions were not reached. Both bounded reviews completed: Standards 0; Spec requested a durable work reload after cancellation. That assertion and the minimal WalletImport ChatWorkStore inspector are now implemented locally; compilation and native GREEN are pending. No ticket closure, actual process-death proof or new phone APK. Preserve the untracked NATIVE_GROUP_CHAT_CONTRACT.md.

Pending Chat work inspector local test APK compilation passes in 2m51s, command exit 0. Native GREEN and code review are pending. Core unchanged; no full-ticket acceptance.


## Current review delivery and Chat checkpoint

Owner asked when a first version could be reviewed. Early signed preview0.1.11/sourcea36972f is now live at https://davidengelen.nl/redsquare/ (direct releases/11/redsquare.apk). It replaces stablev10 as current preview. Same certificate/package, version increase, full hosted APK checksum verified. Explicitly unfinished; no physical installation/update or whole-app acceptance claim. Delivery provenance in docs/evidence/previews/11 includes wrapper143 and exact owned-container successful completion/resumed signing.

Latest corrected-source native19 checks both GREEN at a36972f: Android10run38397754389 19PASS89.353s; Android17run38425643202 19PASS142.351s; core86. Root28XML+unique wallet-warning original per run inspected. Specific outgoing Chat work warning, disabled confirmation, cancellation preserving exact durable original/wallet/backend and no sends pass. Both code-review axes0 after8a85351 unfinished-file correction. Earlier483 source runs also pass19; do not attribute .new runtime coverage. Draft navigation/erasure, actual process death, older paging, readable existing-client reply, native isolation, both public contracts and final full suites remain. #25 still active;20of35 closed. Protected group contract untouched.


## Active unsent Chat draft correction

After publishing earlypreview11, new20case draft tracer0447c37 actualRED10run38427322282 reports19passes+lost-draftfailure84.364s/core86. ImmediateBack/reopen yields emptyEditableText atline319; later erasure/recreation assertions unexecuted. Root original/XML inspected. Minimal composer persist hook and awaited header/systemBack correction is locally implemented; APK build is running. No native GREEN, background/process-death or whole#25 acceptance. Stablepreview11/sourcea369 does not yet contain this fix. Two tracerreviewaxes0, nextsource review pending. Protected futuregroupfile untouched.


Latest draft attempts: sourceab18713,10run38428616491 and17run38428621143 each20cases19PASS+line324contactwaittimeout119.358/119.542s/core86. Failure shows feed after secondBack; prior nonempty assertion did execute but first reopening was ambiguously matched by current conversation header. No erasure/recreated tail acceptance. Test-only positive list Public key readiness waits added afterbothBack; compiler running. Product remainsb2bc017; no GREEN. Preview11 staysa369.


Latest20case sourcea306f3f: Android17run38430016172 GREEN20/136.043s/core86. Android10run38430012756 draftcase passes but separateRetry initialbodyassertline372 fails93.832s/19PASS. Root30/28XML and unique draft/failure originals inspected. Add test-only positive outgoingbody wait before strict scroll/assert; build running, no productchange. Next10 only needed; retain17 unchanged-production companion (does not execute newwait). Actual process death/older paging/reply display/isolation/public17/fullticket remain. Preview11sourcea369 still current; no newerAPK.

Retry-body readinesseb0e892 localTestAPK PASS1m33s/exit0 andbothreviewaxes0. Nextdispatch10native20 only;17a30620GREENsameproductb2companion. Current phonepreview11a369 unchanged andverifieddownloadable. #25notclosed; no actualprocessdeath/whole-ticketacceptance.

Current outstanding native20 run38431315237 at1a78f69 is in progress (Android10 chat-ui). Inspect its actual result before further changes; do not redispatch the same scope while running. Android17 companion38430016172 remains20GREEN/sameproductb2. Owner received the published preview11 direct download link. All owned code is committed; protected group contract remains untracked.


Latest draft/retry checkpoint: native10run38431315237 at1a78f69 GREEN20/87.403s/core86. Keep17companion38430016172 a306GREEN20/136.043s/sameproductb2/olderbodyassert. Root30XML/two unique draft originals each inspected. Both bounded code-review axes0; no actual process death/fullticket.

Next older-history21tracer locally added and compiling. Controlled50-row initial page plus one older page, canonicalgroup/50/oldestcutoff, older visible and retained after failedArchive recreation, no sends/full identity. Product older-fetch UI unimplemented until actualRED. Public unsafe native-generated pagination rows are not independent crypto vectors. Preview11sourcea369 remainscurrent. Protected group contract untouched.


Older-history21tracer01a56a6 actualRED10run38432238667:20PASS+missingLoadoldercontrolline33795.186s/core86. Root31XML/failureoriginal inspected, lateroldercachetailunexecuted. Minimum olderpage control/effect/authenticatedcache retention implemented locally; buildrunning. Positiveenabledwait15s precedesstrictnewcontrolassert becauseinitialloadmustfinish. No nativeGREEN/actualdeath/whole#25. Preview11 unchanged.


Older UI43c4dad compiles3m10s. Both reviews completed: Spec1 cache/pagecursor gap (ancient cached message skips available intermediate history); Standards1 duplication heuristic. Same21tracer strengthened with real encrypted ancientT-100 fixture plus recentT and availableT-1; wrongservercutoff naturallyreturns[]. Test compiling; no strengthened nativeRED/cursorfix yet. Product43 unchanged whileownedGradleactive. Both findings mustbe corrected/reviewed beforeacceptance. Preview11 remainsa369.


AncientcachegapRED10run38434215697 atdd8ef695:21cases20PASS+intermediatebodyline346timeout109.476s/core86. Root31XML/failureoriginal inspected; exactfailedrequesttimestampnotretained, latercachetailunexecuted. Separatearchivepagecursor andshared retainHistory helpercorrect bothreviewfindings locally; buildrunning andfollowupreview/nativeGREEN next. Product43c4dad no longercurrentworking source; preview11 unchanged.


Latest Copy acceptance: Android10run38450331371 at72ef47cf GREEN24/114.206s,
core86, actual cold4.738s/restore.171s/PID7546→7806; all39UIXML and3processXML
root inspected plus copied/cold originals. Earlier17run38448319576 at439 is
24GREEN156.175s/cold5.318s/restore.151s and sameproduct, olderfixturestep.
No demonstrated cause claimed for earlier10body failures. NativeReply tracer
adds25th case at approved UI/controlled-peer/cached-recovery seams, using actual
pinned sanitizer source-guided excerpt oracle. NoReply product minimum yet.
Wrong local Gradle task failed14s beforecompilation; corrected testAPKbuilding.
Preview11sourcea369 unchanged; #25open, reactions/isolation/fullgatespending.


Current NativeReply tracer53c7bd90: corrected localTestAPKPASS1m28s, two
followupreviewaxes0 resolving preview/mention gaps. Native10chat-ui run38452059233
actuallydispatchedmain; inspecthead53c7bd90/result beforeminimum. EarlierSHAref
HTTP422 launchedno devicecheck. Independentrequired --check-native-reply-send
accepts onlycaptured unsafeoriginal matchingcompositionmetadata; priornonreply
capture explicitlyrejectedexit1. Ordinarychecker priorresult remainsidentical.
NoReplyproductcode/capture/GREEN/wholeacceptance yet. OwnedGradlecompleted.


ReplyRED10run38452059233 at53c7bd90:25cases24PASS/113.541s/core86,
missingReplyline377. Root40XML/failureoriginal inspected; remainingnewcase
quote/draft/send/metadata/cache tail and process fixture unexecuted. Minimum
Reply source uses existing encrypted raw-prefix draft, literal editable suffix,
explicit remove/send, auto-mention in encrypted body, and available-local quote
navigation. First ownedminimum Gradle d76d21b10b29 was deliberately stopped
before completion to stabilize remembered callback identity and escape literal
quote markup; no runtime/minimumbuild acceptance claimed. Correctedbuildnext.


Latest boundsminimum locally permits timestamp0 in replyparser andreturnsnull
fromproduceroutsideJSsafe range, disablesunsupported rowReply anddoesnotapply
such prefixes. Normal capturedreply independentcheckerPASS in isolatedruntime;
zeroUI actualRED tail notaccepted. Buildrunning; review/native26GREENpending.


Boundsminimumd973 local core86/debug/testAPKPASS4m15s/exit0. BoundedSpec0;
Standards1maintenanceheuristic duplicatepolicyrange. Private sharedrange now
corrects that heuristic; narrow mainKotlincompilation and followupreviews next.
No26GREEN/zero metadata acceptance yet; ordinary nativeReply/receiverPASS retained.


Current correctedReply source9fe61503 native26runs:10=38455813479,
17=38455816701, bothinprogress. Sharedrange mainKotlincompilePASS3m16s/exit0.
Pure reaction source-reference script independently executes pinnedChat create/
receive and Transaction JSON methods with namedwalletfee/signing/network adapters;
publictwo-operation corpus togglesoneoriginal on/off. No appreaction source yet,
no dispatchdedup/native acceptance. Missinginput/BigInt generator attempts fixed
before exit0; preservedboundaries described inNATIVE_PRIVATE_CHAT_ACTIONS.md.


Currenttest-only split8289886d + walletgatefixbb31c329 preserves24coreUI cases
and2replyaction cases via sharedpublicUI fixture, independent180s caps/reports.
Actualprior17run172.881s justifiesboundedfamily split beforegrowth. Bothgatefix
reviews0; localsplitTestAPKPASS1m47s. StrongquoteNav tracer now adds authenticated
publicnative-generated24line spacer between original/reply andasserts original
notdisplayed beforetap/strict originaldisplayaftertap, then returns toreplybody.
Crypto/metadata baseline unchanged. Compilation/review/newsplitruntimepending.


Latest bounded UI/quote navigation GREEN source3ba3df98 (product2fffunchanged):
10run38458495453 coreUI24PASS111.95s/actions2PASS14.294s/core86;
17run38458499644 coreUI24PASS158.218s/actions2PASS17.348s/core86.
Root49XML and4before/afterquote originals eachinspected; genuineoriginaloffscreen
beforetap thenexactoriginaldisplay, scrollback/cachedreply/body/identity/nosend.
Bothnormal+zero captures perSDK independentlyPASS pinnedCrypto/WASM/mentions/
actualChat receiver/dedup. Actualprocess10cold4.541/restore.162/PID8168→8417;
17cold5.082/restore.159/PID5330→5409,3XML/3originals eachverified.

Active firstLikeRED10run38459575841 at8d4d17ea: coreUI24 +actions3 (newLIKEcase),
productLike absent. Like UI/signature/pair/corpusbody/propagate+Relayoriginal/
truthfulstatus/cache/identity/noauto tracer reviewedboth0, localTestAPKPASS1m35s.
Inspectactualresult beforeLike minimum; no likecapture/GREEN yet. No ownedGradle.
Onlyprotectedgroupcontract remainsunrelated; no node/privatewallet/statewrites.


FirstLikeRED10run38459575841 at8d4d17ea: coreUI24PASS100.919s/actions3two
PASS+missingLike atFixture277/13.277s/core86. Root50XML/sole failureoriginal
inspected; Like body/transport/status/cache/identity tail+processunexecuted.
Minimum firstLike source now locally adds authenticatedpublicChatLike factory/
read, encryptedreaction originals+attemptstate scopedwallet/backend/privatepair,
explicit optimisticcount/Like/Unlike, guardedprepare-before-submit/truthfulstatus/
retainedcache/noautoloadbroadcast. Reactionnamespace included in fixtureopaque
cleanup and outgoingwalletworkwarning. No live receipt/Unlike verification/retry/
archive-reaction recovery/whole#25 yet. Reviews/build/nativeminimum pending.


LatestLike product3526dd06 (firstminimume684 + sharedguardedtransport) reviewed
bothfollowupaxes0. Localsourcee407 core86/debug/testAPKPASS4m32s/exit0.
Native24coreUI+3actions10run38461533102/17run38461536720 at e407 active; inspect
resultbeforemoreappchanges/redispatch. No ownedGradle. Referencechecker079a4eb4
usesactualpinnedconstructor/independentWASM original/receiver; defaultcorpusrepeat
unchanged and ordinaryencryptedreply rejected1. Bothcheckerreviewaxes0, noLike
capture/checkerPASS yet. Incoming/archive/duplicate/exactRetry/Unlike/isolation/
finalgates stillpending. Stablephonepreview11 unchanged;#25open20of35closed.


2026-10-13 first outgoing Like GREEN10/17 (38461533102/38461536720, e407); both independent captured-Like checks exit0. Evidence retained under ticket24/ui/private-like-android{10,17}-green, native-process/android{10,17}-like-green and native-web-like-receiver/android{10,17}. Unlike/incoming/dedup/retry/archive/isolation/final#25 still pending. Continue sequentially;20of35 closed.


2026-10-13 explicitLike/Unlike GREEN10=38464119759,17=38464123469/source7de8/core86; retained UI/nativeprocess/independentfouroriginals. Incoming corpus/tracer7f53 reviewedboth0; localcompile1m28 predates tinyreadiness additions. Correct UI RED run38465042619 active (job started20:43:22, no reportedsteps20:46); earlier scopechat38464504279 protocol-only2PASS, notUI. No ownedGradle. Current#25: incoming/dedup/retry/archive/isolation/fullgates stillpending. Protected groupresearch untouched.

2026-10-13 incoming reaction minimum b77dda0c followed by ordered-publication/
bounded-signal fix2a0c626d and concurrency tracerda786327. Both fix reviews0;
localcore86/debug/testAPK initial3m46s and corrected3m4s PASS. Native focused
5action cases active10run38467247996/17run38467252208 atc1969126. Wait/inspect
actual logs/artifacts before asserting GREEN; GitHub jobs can report empty
steps while real test work has run. Prior38465042619 is actualincoming UI RED
(history24PASS101.155s +actions4 threePASS/one missingcount33.729s); focused
38465896724 same missingcount4cases/36.716s. Both failure originals inspected,
retained, and no infrastructure cause claimed. No ownedGradle. SourceGatepush
is separate from device acceptance. Stablephonepreview11 unchanged,#25open.
Continue with original reaction Retry/process/isolation/available source handling,
whole-ticket reviews and final fullSDK/release/lint/update/public-read gates.

2026-10-13 incoming/concurrent/outgoing/Reply action foundation now five-case
GREEN bothSDK:17run38467252208/c196 PASS58.295s and corrected10run38468474482/
b499 PASS30.339s, same product. Quote readiness correction07e094af reviewed0/0,
compile1m17. ReactionRetrytracer2b8443fc reviewed0/0, compile1m19; actualRED17
38468903248/2a90 sixcases fivePASS+missingRetryatFixture301/80.505s/core86,
originalfailurePNG/XML inspected; laterretrytailunexecuted. Minimum33674419
reviewed0/0 and localcore86/debug/testAPK PASS3m4; retainedRED/review5053fec7
pushed. Activecorrected6case runs10=38470363216/17=38470366652 at5053fec7.
No ownedGradle. Waitinspectresults beforeRetry acceptance; then reaction actual
PID recovery, backend/wallet isolation and available-history/invalid-receipt
coverage as appropriate, whole#25reviews/fullSDK/release/lint/update/publicreads.
Stablepreview11 unchanged,#25open20of35closed. Protectedgroupresearch untouched.

2026-10-13 exactreactionRetry sixactions GREEN10=38470363216/40.583s,
17=38470366652/61.425s at5053fec7/core86; root26XML+threeRetryPNGs each.
Retried originalcaptures identical; independentconstructor/WASM/receiverexit0,
SHAac031ff325c8a68e02a917bf18c9cf37d0c1eba353f0d428fe93aa4ac1beab84.
Retained3e156a6c, notyetpushed. Two17 immediateunlike XMLs containstalecounts;
pairedPNGs/strictassertions/reopenedXMLs correct, qualificationretainednocause.
ActualreactionPID tracer48858fed reviewedboth0, compile1m21; broadnative
ChatUI24history+6actions+actualPID active10=38471567896/17=38471571748 at9111.
No ownedGradle. Inspectraw reports/PIDs/newreactioncheckpoint/originalPNG/XML
before claimingnewprocesscoverage. Nextbackend/walletreactionisolation, invalid/
available-sourcehandling asappropriate, whole#25reviews/fullSDK/release/lint/
signedupdate/publicreads. Do notclose25yet. Protectedgroupresearchuntouched.

2026-10-13 21:54UTC:17broadChatUI38471571748/9111 GREEN24history+6actions+realreactionPID5422to5506, originalUI/process captures inspected/retained8bb4. Corrected10broadrun38473067796/12e fails3of24history146.011s/core86; actions/process notexecuted. Shared failureimage overwrittenbylatercases; lastrootseenfeedstillopen. InitialChattext wait can precedeenabledwallet;be946226 addsenabledgate+uniqueper-casecaptures, bothreviews0/0, compileactive94884. PARTIAL_KEYS retainedoutgoingafterrecreatetimeout also needsnewdistinctdiagnostics, notexplainedbyinitialreadinesshypothesis. Backendisolationtracer2c0a16d1 bothreviews0/0, compile1m19, focused7action runs10=38473908900/17=38473912585 active at2c0. Bothlogicalbackends useonecontrolledpeer; no deployedcontractclaim. Nextinspectthose, dispatchcorrectedbroad10 aftercompile, then walletreactionisolation/available-source handling and whole25gates. Stablephone11unchanged;protectedgroupresearchuntouched.

2026-10-13 22:07UTC:focusedBackendisolationGREENboth2c0:10=38473908900 7actions47.182s/core86,17=38473912585 7actions79.856s/core86; fouruniqueoriginalbackendPNG/XMLpairs each rootseen (10all30XML). Retainedc299. Walletreplacementtracer962e bothreviews0/0 compile1m22. Available-Likehistorytracer7fd4 compile1m23 reviewedStandards0/Spec1false-successRefresh; f748fix newexternalread+publicerrorabsence reviewed0/0 compile1m30. New9actionsRED17run38474963986/sourcec299active. Broad10retry38474160633/945:23/24historyPASS127.451/core86, solePARTIAL_KEYS outgoingwaitafterfirstscrollincoming. UniquePNG/XMLrootseen incoming/error/incomplete/Fwdfooter; outgoingoffscreen. be289 fixes publicLazyListscrolltoexactnoneditableoutgoing beforestrictdisplay, reviews0/0 compile1m26. Retainedede68da4/pushed. NoownedGradle. Whole25source reviewc102...ede Standards0/Specnoadditionaldefects, knownArchiveplaintextLikehandlingpartial. WaitactualRED17; inspectwalletcaseandmissing1Likefailure beforeminimumArchivehandling thenfinalbothSDK(fullgatesincludehistory24,newreactionactualPID), release/lint/signedupdate/publicreadonly. NoAPKpublished,preview11unchanged. Protectedgroupresearchuntouched.

2026-10-13 22:30UTC:actual10regressionRED38476508566/e7ed3e5a has9/10actionsPASS67.854s/core86, soleARCHIVED_LIKE_STORAGE commonDMwaitFixture314 failure (originalPNG/XMLnoDM+falsebackenderror+genericreactionerrorseen). Available-reactionhistorycase+walletreplacementnow10PASS (17walletpriorPASS); root3available+3walletoriginalpairsseen. Retaineddocs32/newcommit. e8203a21 fixesindependentreactionpersistencewarning/readableDMmerge, separateRestoreFailed/SendFailed, badrestore disablesediting. Bothreviewaxesconfirmabortfixbutflagemptyreactionpageearlyreturn skippinglocalreloadonRefreshafterrepair, leavingLike/Retrydisabled. CurrentfullGradle16739ACTIVE:core86up-to-date, debug/testAPKs built, Releasebuilt, lintanalysispending; DO NOTmodifycodeuntilcompletion. Preparedextendedmessage-onlyregression `.scratch/private-reaction-message-only-tracer.kt` fromcurrentfixture, notyetapplied: afterfirstrepair count1, keepopaqueencryptedbytesdeviceonly, obstructfile/recreatewithArchiveDMsonly ->genericrestoreerror/Like disabled,restoreexactbytes+explicitRefresh->newread/errorgone/count1/actionenabled/nooutgoing/fullwallet/backend. Nextapply/committracer, compile+actualRED iffollowingstrictTDD, thenremoveemptyreactionpageearlyreturn, reviewfix and runfinalwhole25gates bothSDK includinghistory24/fixedoutgoingLazyvisibility/newactualreactionPID, Source release/lint, signedreviewupdate/publicreadonly. Whole25sourceReviewc102...ede0Standards/noadditionalSpecexceptnowaddressedArchivepartial. NoownedGitHubproofactive; mainremotee7ed, localHEADnewer(includesretained10RED). Protectedgroupresearchuntouched; noAPKpublished11unchanged;20/35closed25stillactive.

2026-10-13 ownersteering: after25 prioritizevisualUIbeta forfriends before26. Created #37, childof35/dependenton25; ownerprioritycommenton35. PROJECT_CONTEXTupdated. Scope feed/nav/type/spacing/Chat+existingimplemented screens, familiarRedSquare/accessibility/safeguards, signedshareablebeta withremainingfeature notes. Original35ticketscope and35physical/finalgates retained. Do notstart37until25done; then37nextbeforeotherfeatures. Currente820fullchecks PASS9m19; extended3dea testcompile PASS1m35; docs c76pushed. Actualmessage-onlyRED17run38478679915/c76active; bothpublicfreshreadruns10=38478905098 PASS1.677s/core86/rootreadiness+rawinspected,17=38478907763active. PublicreadclassunchangedbyfutureUIhelperfix. NoownedGradle. Currentsource stillknownempty-reactionearlyreturn; waitactualREDthenremove line+reviews+finalbothSDK/gates. Protectedgroupresearchuntouched;phone11unchanged.

2026-10-13 23:05UTC:finalproduct7363 localcontainer526 actualexit0/fullbuild7m1 (wrapper14250exit143didnotstopbuild), SourceGate38479942248/05e SUCCESS. Signedpreview12 prepared/unpublishedsource05e, certsame6ca..., APKsha45313bb70a2f3ee2854c7a133423c9c19d41a9b40e8a7a098debc36fa202c42b; productdiff05e->a473 empty. Bothfullnativeattempts05e failEARLYbrowse, notChat:10=38479941677 3of8PASS31.217s/5navigationfail;17=38479944678 7of8PASS37.201s/cachedprofileauthorentryfail301. Core86passes. Rootfour10feed-still-openPNG/profileXML+17cachedprofilepairseen. ReadonlySpecsupport confirmspositivefeeddestination failure/offscreen-tap hypothesis (48dpviewport), no parser/revision/policydefectestablished. Testfix98ae1aca scroll/assertVisible/click/exactConversationorProfiletitle beforeunchangedReady/reply/tamper/cache/identity gates, reviews0/0compile1m29. Retaineda47370b5/pushed. Current4native runsat a473:full10=38481762136,full17=38481765239,chat-ui10=38481767940,chat-ui17=38481770606 ACTIVE. NoownedGradle. Do notredispatch/cancel withoutresults/newconcern. InspectChat24+10actions+actualnewreactionPID andfulltails separately. Stablephone11unchanged, no12publishuntilacceptance. #25active20of35closed; nextOWNERPRIORITY#37earlyvisualbeta(child35/dependson25), thenresume26etc. Protectedgroupresearchuntouched.

2026-10-13 23:12UTC:focused final ChatUI GREEN both at a473:10=38481767940/history24 104.921s/actions10 52.406s/actualPID10825to11121;17=38481770606/history24 154.875s/actions10 58.770s/actualPID5356to5438. Core86 each. Raw reports/checkpoints/zero-transmission continuity and original reaction recovery screenshots inspected, all79XML each parsed and recovery semantics checked. Retained ui/private-chat-final-android{10,17}-green + native-process/android{10,17}-final-green. Includes final message-only Archive reaction repair regression. Full10=38481762136/full17=38481765239 still active, so25 not closed and signed12 still unpublished. No ownedGradle. Nextinspect fulltails, retain acceptance, publish12/close25 then OWNERPRIORITY37 visualbeta. Protectedgroupresearch untouched.

2026-10-13 23:17UTC:full a473 attempts10=38481762136/17=38481765239 bothfailFollowingfirstcase begins/noresult beforehost180s, missingcapturefolder. Browse8/privatepeer2/history24/actions10/actualChatPIDcoldrestorepass BOTH beforestall; laterappfulltailnotexecuted. Retained full-regression/android{10,17}-following-stall. Zero-heightFollowing/outage viewport is source-supportedhypothesis, notproven. f85d2cdc diagnostics stagefile+hostactualexit/externalboundedPNG/XML, no assertions/timeout/productchange. ReviewsStandards0/Spec0; localTestAPKcompile PASS1m24. NextfocusedFollowing10 diagnostic thenboundedcorrectionbasedoncapture/fullbothgates. Signed12stillheld;25open;37nextafter25. NoownedGradle/proofsactive exceptnewsourcepush. Protectedgroupresearchuntouched.

2026-10-13 23:23UTC:Following diagnostic10 run38483374543 at97b661a8 ACTIVE (do notredispatch). SourceGate38483369384 atsamehead SUCCESS. Compiler1460closedPASS1m24. Bothfullsecondattempts showFollowingfirstmethod/noend; stagecaptureunavailablethere. Minimaldiagnostic reviewboth0 andunchangedassertions/180s. WaitnewPNG/XML+stage/exitbeforeproductcorrection. Stable11/held12unchanged,25active,37nextpriority. Workspaceonlyprotectedgroupresearch untracked; noownedGradle.

2026-10-13 23:28UTC:minimumfeednavigation b7541e6a bothreviews0/0, debug+testAPKbuild71009 PASS2m40. ActualdiagnosticRED10=38483374543/97 host124 progressProfile newerpost20scroll, originalPNG/XMLrootseen showsentire640pxheight consumedbackup/nav/following/outage, novisiblefeed. Retainedfull-regression/android10-following-zero-viewport. Minimumonlygroupsfourexistingbuttonsintotworows/saves96dp/sameguards/routes. Newproductneedsfreshacceptance; full10=38484421113/full17=38484424347 ACTIVEat4e3a6ea7. Do notredispatch/cancelwithoutresults. Signingreview-build13639 ACTIVE preparingnextversion; no source/testeditswhileownGradle. Previous12superseded/unpublished; currentpublic11. Waitfulltails, correctedsource/runtime/release/lint/thennew signedreviewpublish/25close andOWNERPRIORITY37visualbeta. Protectedgroupresearchuntouched.

Active26 update: join oracle verifies both actual web methods with explicit
known derived admin and zero-payment group-first wallet fixture. Native tracer
RED1 UnsupportedOperationException/build57s, then GREEN1PASS/build1m. createTo
has defaulted additionalRecipients; empty defaults preserve existing callers.
No ownedGradle currently. Broadercore93PASS/0failure/error/skip,17s; noownedGradle. Next signedmembership/leave/
message controls and durable nativeUI. No group app/final acceptance yet.

Latest26: leaveRED1→GREEN1 (53s/54s); controlsequenceRED1→GREEN1 (54s/1m).
DraftapplyControl nowverifiedjoins/removes, immutablemaps/controloriginals and
signaturededup; LEFT/REMOVED rolesadded. No globalchronology/currentnetworkclaims.
Ban-bypass tracer owned64021 running .scratch/group26-ban-red.log. Do notedit
app/core/tests whileGradleactive. Next inspectRED thenkeepREMOVEDthroughselfleave;
reviewleave/controls together afterGREEN. Original untrackedgroupcontract protected.

Follow-on app design remains implementation work, not acceptance: retain trusted
origin (signedcreation vs explicitlyconfirmedunsignedinvite), owner/backend-bound
encrypted originalcontrols/history, and durable work without autosigning/resending.
Non-admin snapshots must not grantroles. Display available/partial history honestly.
Unsigned link origin must not selectbackend. Grouptext and controls should keep
privateChat untouched and inherit published14 styling. Media/offlinequeue/push
stay in later tickets. #26 is stillopen; no groupUI/build published.

26 latest: banRED1 confirmed REMOVED→LEFT via self-leave; correctedstickyREMOVED,
GREEN1PASS57s, fresh join rejected. No ownedGradle. Next reconstructsavedcreation+
originalcontrols, authoritynegativecases and reviews before native persistence/UI.
Source control modelstillbounded observed history, not globalcurrentmembership.

26 current: restore-originalsRED1→GREEN1(54s/54s), verifiesretainedLEFTafterduplicate
join and rejectsnon-admin removal. NoownedGradle. Controls/leave/restore source
reviewpending. Next explicitconfirmedinvite trust origin (no fakesignedcreation),
plaintextgroupmessages, targetedadminsnapshot authority and encryptedgroupapp/UI.
Do not claim full26 or actualprocess recovery fromcore reconstruction alone.

2026-10-14 active26 checkpoint: known-routing native8 GREEN at0841e381 on both
SDKs (10run38548067341/29.535s;17run38548070880/59.308s). Core replay plus
shared original extraction b8cc now full105PASS, both review axes0. Current
HEADf11c corrects native9 message tracer protocol and positive duplicate-ACK
oracles; correction reviews0/0. Local testAPK session53289 running, log
.scratch/group26-message-tracer-review-build.log. DO NOTedit app/core/tests
until ownedGradle finishes. Actual first messageRED17run38550903014 at829 is
active. After inspecting actual failure artifacts, apply prepared
.scratch/apply-group26-message-store.py and
.scratch/apply-group26-message-ui.py (NEITHER applied at this checkpoint), then
review minimum and verify native9. Store adds encrypted observed originals;
UI adds available/local messages, deliberate sending and live authenticated
receipt. These are a bounded first message slice, not all26. Pagination,
recovery/import discovery, blocked-account behavior, process interruption and
whole final gates remain. Protected untracked NATIVE_GROUP_CHAT_CONTRACT.md
untouched. Public signed preview14 unchanged;21originaltickets closed. Owner
requests continue until all issues complete, with further UI after26.

2026-10-14 10:55UTC supersedes preceding26checkpoint: actual messageRED17
38550903014 at829 has9cases8PASS/missing available text206/91.358s. Original
failure PNG/XML inspected and retained f4c813b4 with unexecuted-tail limits.
Store/messageUI scripts APPLIED (now obsolete), minimum51a7 + review fix92b4086c
committed/pushed. Separate local/history flags; Archive failure no longer closes
livepeer or disables otherwise safe Send. Both correction reviews0 for minimum,
known unsent-draft finding retained. Local17763 COMPLETE0 debug/testAPK3m14s,
no ownedGradle. Active native9 same92b4086c:10run38552574915,17run38552540747;
sourcepush38552539385. Do not redispatch before results. Inspect actual reports
and original messages-sent/messages-cached PNG/XML before acceptance.
Prepared .scratch/apply-group26-draft-tracer.py NOTAPPLIED: adds10thcase type,
immediateBack/reopen/recreation/exactunsenttext/noextratransmissions. Draftminimum
notes .scratch/group26-draft-minimum-notes.txt, implementation NOTWRITTEN. Add
that next after currentGREEN; preserve scoped encrypteddraft via existinggroup
record and LocalPostNavigation save-before-leave. Next fault/history/pagination/
import discovery/blocked/actualPID/fullgates still26. UIafter26 ownerpriority.
Protected untracked groupcontract untouched; publicpreview14 unchanged, no26close.
Ownerrequests continue until ALLissuesdone; don't stop at thischeckpoint.

2026-10-14 11:06UTC active26: message9GREEN both SDKs retained59deb185/437979a1
originalmessages-sent/cached PNG/XMLseen, 17run38552540747/92 ninePASS82.767s;
10run38553319218/63526b897dd6859b626756ef6f5da2b7a46bcf0f ninePASS37.241s.
Productsourceequals92. Prior10 immediate-scroll230 RED8of9/28.582s retained635;
fixture publicscroll/display wait15s passed withoutweakeroracles.
Drafttracer APPLIED f767 then corrected00297dfc (positive Create group list,
exactEditableText both reopens, reviews0/0), testAPKcompile1m51 COMPLETE0 session
86142, noownedGradle. ActualdraftRED17run38553804247 at00297dfc480195112aa6c9e1e971f92adbded427 ACTIVE.
Do not redispatch. CurrentHEADDOCnewerthanremote002. AfteractualREDinspect
uniquedraft-failure PNG/XML thenapply `.scratch/apply-group26-draft-minimum.py`
(NOTAPPLIED), compile/reviews/correctnative10. It adds Entry encrypteddraft,
load/save/clear-with-preparation, hasWorkdraftdisclosure, save-before-navigation
viaLocalPostNavigation; notes .scratch/group26-draft-minimum-notes.txt.
Previous message scripts applied/obsolete. Protectedgroupcontractuntracked
untouched. Publicbeta14 unchanged. Archive-fault/pagination/importdiscovery/
blockedaccount/actualPID/wholefullgates remain26; UIafter26 ownerpriority.
Continueuntilallissuesdone, no partialfinalstopping.

2026-10-14 11:16UTC active26 supersedingdraftstate: actualfirstdraft17
38553804247/002 ten9PASS72.248s fails242 Back offscreen fromkeyboardpan.
OriginaluniquePNG/XMLseen, retained6a51 asfixture navigation failure BEFORE
persistence checks, no draftREDproved. Correction6a51e6829bf8fb289345f470ee9dfbecc3ee522e
closeskeyboard +boundedBackvisible; reviews0/0/testAPK1m44 COMPLETE0 session4506,
noownedGradle. CorrectedRED17run38554810986 ACTIVE, sourceproof38554810639.
Do notredispatchbeforeoutcome. `.scratch/apply-group26-draft-minimum.py` STILL
NOTAPPLIED; dryrun preview only `.scratch/group26-draft-preview` is old initial
script snapshot (do notcopy). ScriptrefinedsavingForNavigation tofreezeediting
onlywhileexplicitleave, notduringbackgroundautosave. Actualcurrentapp stillNO
draft persistence. AfteractualREDinspectthenapplycanonicalpreparedscript,
compile/reviews/nativeGREEN. Detailed remaining26 checklist local
.scratch/group26-remaining-acceptance.txt (nottracker): archivefault/pagination,
incomingaddressedsignedcreation/importhints/blocked/actualPID/fullgates.
SourceChat-B receiveCreateGroupTransaction1050ff confirmsaddressed signedcreation
isordinaryincominggroupdiscovery, notonlylinkjoin. Sourceopsunchanged.
OwnerUIafter26 includes reviewing keyboard/header viewport. Preview14unchanged,
protecteduntrackedgroupcontractuntouched. Continueallissues, no partialfinal.

2026-10-14 11:27UTC active26: correctedactualdraftRED17 38554810986/6a51
10cases9PASS95.654s, positivegroupList/reopen thenEditableTextwait248/464,
emptycomposeruniquePNG/XMLrootseen, retainedf1e744d7. Draftminimum APPLIED c033
plusRefreshfix5aed9d08: encrypteddraft/default/load/save/candidateprepclear/
hasWork; navawaitsaves/freezesediting, autosaveeditable; effectRefresh retries
onlyfailedloadwithoutclobberingeditedtext. Reviews0/0minimum+fix. Local14280
COMPLETE0 debug/testAPK3m10s, noownedGradle. CurrentHEAD5aed/sourcepushedsame;
ACTIVEnative10checks10=38556274168,17=38556277778. Do not redispatch.
AfteractualGREENinspect draft-reopened PNG/XML+literalEditableText raw, retain.
Nextprepared .scratch/apply-group26-archive-fault-tracer.py NOTAPPLIED: new11case
validmessageflow ->invalidArchive ->Send/livepositiveACK/cache/recreate/noextras.
Uses samepublicreader/wire checks; productalreadyfixesarchivefault basedreview,
so this is regressionproof, notnewREDclaim. Mainremaining26 checklist local
.scratch/group26-remaining-acceptance.txt; storage-fault/repairedRefresh still
unverified andneeded. OlddraftscriptsAPPLIED/obsolete; dryrunpreview stale DO
NOTcopy. Protectedgroupcontractuntrackeduntouched. Preview14unchanged;21of35
closed26open. OwnerUIafter26, thenremainingsequentialissues. Continueallissues.

2026-10-14 11:36UTC latest26: draft10GREENboth at5aed9d085ac2f0c35a1fc3b261a86bea7766d45d,
10=38556274168/47.308s,17=38556277778/83.477s. Unique draft-reopenedPNGXMLseen,
exactEditableText/noextras; retained3df3fb78/673cf8ff. Currentnativefixture11
APPLIED ec57+673cf8ff, truthfulhistorywarningliteralabsent =>expectedRED. Adds
fresh malformedArchive request, reachableRefresh/strictvisiblewarning,
compatibleSend/+liveACK/5cachedtexts/recreate/3txnoextras; reviews0/0.
Local27033 COMPLETE0 testAPK1m44s, noownedGradle. ACTIVE11REDAndroid10run
38557529120 at673cf8ff1b2a9d99774eeb5702dd5055a80c6170; sourcepush38557534693.
Do notredispatch. AfteractualREDinspectunique archive-failure PNG/XML thenapply
`.scratch/apply-group26-history-warning.py` (NOTAPPLIED) separating historyFailed
from genericread/save/senderror, translation-readywarning. Thencompile/reviews/
actualnative11GREENboth. Oldarchivefaulttracerscript APPLIED/obsolete.
Draftsave/Refreshsourcefixed, butstorage-fault/repairedRefresh stillunverified;
remaining26 historypagination/incomingaddressedcreation/importhints/blocked/
actualPID/fullgates listed .scratch/group26-remaining-acceptance.txt. No26close,
noAPKpublish; preview14unchanged. OwnerUIafter26 thenotherissues. Protected
untrackedgroupcontractuntouched. ContinueALLissues, no partialfinalstop.

2026-10-14 11:49UTC latest26: actual11RED10=38557529120/673 tenPASS66.372s,
freshbadArchive+distinctwarningmissing252; uniquePNG/XMLgeneric saved/senterror
+3cachetextsseen, tailSend/live/5cacherecreateunrun. Retained518eddbf. Minimum
historywarningscript APPLIED c3f8a9da (obsolete): separatehistoryFailedresource
fromgenericread/save/send/draft. ReviewsfoundRetryfixture142oldliteralmismatch;
ad8aa4fc fixedonlyexpectedcopy, 0/0reviews. Local51520 COMPLETE0 testAPK1m41,
product52644 COMPLETE0 debug/testAPK3m10. NoownedGradle. Active11GREENattempts
samead8aa4fcedf4b1b417df26f2c96bde7bad076f20:10=38558779177,17=38558782794;
sourcepush38558780269. Do notredispatchbeforeoutcome. Afteractualresultsinspect
archive-unavailable-live/cached PNGXML and rawtail, retainqualifiedcoverage.
Nextprepared .scratch/apply-group26-pagination-tracer.py NOTAPPLIED:12thcase
50distinctsignedrecentmessages (falsifiedunsignedcreated_at42) +olderpage;
exactauthenticatedcursor/oneexplicitoldread/51cachecount/oldfirstlastdisplay/
prunedrecreate/noextras. Productpaginationabsent; actualRED mayalsoexposecurrent
per-recordreplaycost. No speculativebatchfixbeforeevidence. Remaining26
incomingaddressedcreation/importhints/blocked/storagefaultRefresh/actualPID/full
gates; localchecklist .scratch/group26-remaining-acceptance.txt. IncomingwebHTML
shouldusestablishedChatmessage renderer ratherthanliteralmarkup beforepolish.
Protectedgroupcontractuntouched; preview14unchanged;21/35closed26open. Owner
UIafter26. Continueallissueswithoutpartialfinal. Scratchretainhelpernowverifies
ActionsheadSHA/completion and rejects existingtargets; neutralcoverageREADME
requirescase-specificnotesbeforecommit. NeverguessfullcommitSHA.

# Native conversation mute verification

Issue29, review base40f28872cb9360d36c834fa632fd463dad3be72c. Parent1 approved
native UI/controlled peer and protected persistence/failure/identity seams apply.
Blockers25/26 are closed. Implement sequentially after closed28 and published18.

Pinned Chat saves group.muted in local conversation storage and suppresses
notification/chime while keeping history, independently of account mute/block.
See NATIVE_MODERATION_CONTRACTS.md and NATIVE_NOTIFICATIONS_FEASIBILITY.md.
Native conversation choices remain local to the wallet and shared by both fixed
backends, preserving current account policy. Importing keys on another device
does not recover absent local preferences. Closed-app push remains32–34.

First tracer: actual private conversation mute control/clear distinction, existing
unsent text retained, incoming independently signed compatible private message
through authenticated controlled Relay remains readable, mute survives recreation,
and unmute retains cached history without outgoing messages. Uses existing
independent web private reference, not production writes. Actual RED pending.

First tracer40f28872...1a08d695 reviewed Spec0/Standards0. Actual focusedAndroid10
run38689427606 owns exact1a08d6950f0c0e7028efd76e9127d25374ae399e, ACTIVE. Local55693
compileDebugAndroidTestKotlin stillactive; no app/test edits until complete. No
actualRED/GREEN claimed yet. No duplicated native dispatch.

Local55693 compileDebugAndroidTestKotlin PASS1m31s exact1a08d695. No ownedlocalbuild.
Focused10run38689427606 stillactive, no duplicate. ActualRED stillpending.

ActualfirstRED10run38689427606 exact1a08d695:1failure26.538s, missingMutecontrol74
onreadyprivateconversationwith exactdraft. RootoriginalPNG/separateXMLseen;
retainedprivate-control-red-android10(6hash1XML). Dialog/incoming/recreate/unmute/
no-wiretailunexecuted. Privatecontrolminimum nowaddsheaderbell/dialog, guarded
wallet-local mutedConversations policy field(defaultemptyforlegacyrecords), exact
commit/recoveryreuse andsavedchoicepublicationonlyaftercommit. Groupcontrols and
actualnotificationfilteringstillnotimplemented. Compile/reviews/GREENpending.

Private-control minimumafab1530 reviewed Spec0/Standards0 (one stale handoff
status corrected). LocalcompileDebugKotlin+compileDebugAndroidTestKotlin PASS3m32s.
FocusedAndroid10GREENattempt38690473150 exactafab15302d3cfaad137db95496754ae59f7cfa1f
ACTIVE. Actualincoming/recreate/unmute result pending. No ownedlocalbuild.

First minimum Android10run38690473150/afab FAILED1case7.720s at exact draft
afterrecreate89: emptyfield whileLoadingmessages. Prior muted live incoming/ACK/
exactdraft and recreatedsavedmute/cache text pass. Retainedprivate-control-draft-
attempt-android10(8hash2XML); unmute/no-wiretailunexecuted. Fixture now waits20s
for unchanged exactEditableText+enabled before asserting. Source supports a
transition hypothesis only; no product draft fix/notification acceptance claimed.

Reopened exact-draft wait4f9cb3a1: localtestcompilePASS1m45s, Standards0/Spec
no regression. Spec identified existing lifecycle exposure: remember-only text,
300ms autosave, no lifecycle flush. Current failure composer was enabled; Archive
Loadingmessages does not prove draft initialization unfinished. Actual cause
stillunproved. Focused10run38691358098 exact4f9cb3a1a24caf6e3846a4914f084c06fa623156
ACTIVE. If exactwaitfails, compare protected durable draft before/after recreate
while retaining exact final UI criterion. No duplicate dispatch/localbuildactive.

Boundedexactwaitrun38691358098/4f9cb3a1 FAILED1case25.604s, emptyenabled
composer afterrecreation through20s. Retainedprivate-control-draft-red-android10
(8hash2XML), originalfailurePNGseen/XMLparsed. NoArchiveLoadingwarningatfailure;
exactorderingunproved. Minimum applies existingpost-composer retained ViewModel
pattern, keyedpublicowner/backend/contact, updatesalluseredits/sendclear/cancel/
reply, prefersmemoryonlyforunfrozenwork. Encrypted persistenceunchanged; no
plaintextBundle/SavedState. Compile/reviews/newnativeproofpending.

Memoryminimumaa1f9a84 Standards0; Specfound1cancellation publication gap.
Correction keeps IOcommit andMain saved/memory update inside one outer
NonCancellable segment underexistingmutex, for bothCancel andSendprepare/clear.
No UI-thread filewrites. Localcompile84903 wrapperended143 but ownedcontainer
20bc1778f1ce continued; dockerwait93966 returnedactualexit0. No duplicatebuild.
Correction compilation will be owned by CI rather than repeat the localbuild.

Correction113ef323 Standards0, Specone remaining overlap: recreation had a new
mutex and could load between old durablecommit andMainmemorypublication. Retain
the existingwrite mutex in the conversation ViewModel and take it for initial
load+memoryselection too. This serializes existingwriters/acrossrecreation without
a newbackgroundwriter or plaintextpersistence. Owned38692323675 superseded before
acceptance by this sourcecorrection; no runtimepass claimed.

PrivatecontrolGREEN38692452298 exactcb7a1b98:1PASS7.189s,3originalPNGseen/3XML
exactdraft/mutedreopen/unmute retained(10hashes). Both latestsourcecorrections
reviewedSpec0/Standards0; no process/notification/group/full acceptance inferred.

Next tracer adds an actual positive generic Android private notification, system
shade observation and private category, alongside zero alerts for muted readable
incoming. Existing sharedprivateflow keeps its exactdraft/recreation/no-wire
assertions. Host grants POST_NOTIFICATIONS only on33+ outsideinstrumentation
and restores prior grant state after fixture completion, including testfailure.
Fixture preserves existing alerts and cancels only newly posted fixture alerts.
ActualREDpending; no productionnotificationpathyet. Android primary guidance:
https://developer.android.com/develop/ui/compose/notifications/create-notification
https://developer.android.com/develop/ui/compose/notifications/notification-permission
https://developer.android.com/reference/android/app/NotificationManager#getActiveNotifications()

Alerttracercc74746c review: Spec1(singleimmediateabsence couldmiss delayedOS
publication), Standards1possibleduplicatedpostconditions. Correctedtwo-second
pollingzero-alertobservation afterACK andaftercachedunmute, plusreopenedabsence;
sharedidentity/backend/no-wire/peerhealthpostconditionhelper. PositiveOSalert
assertionsunchanged. OwnedfirstRED38693284699 exactcc74746c remainsactive; its
baselinealertcriterionunchanged, no duplicate dispatch. No productionnotifpathyet.

ActualnotificationRED10run38693284699 exactcc74746c:2cases1PASS/1FAIL30.514s,
positiveincoming/ACK thenmissingactualalert82/20s; shade/privacy/categorytail
unexecuted. Retainedprivate-alert-red-android10(12hash4XML), originalfailureseen.
Reviewed84f8e98a silenceobservationfixSpec0/Standards0. Minimumliveprivatealert
nowretainsbeforeoptionalnotification, authenticatesseenoriginalIDsincluding
unreadableoldciphertext, checksfreshpublicidentity/backend anddurablepolicy
underlock, skipsmuted/block/self/permissionfailures. GenericNewmessage/category
privatechats, nativecontentIntent, no secretpayload. Archive/reactionsremain
silent; notificationerrors donotnegativeACK savedmessages. No group/closed-app
notificationacceptance. Sourcecompile/reviews/correctednativeGREENpending.

ActualprivatealertsGREEN10run38693924118 exact0bf65264:2PASS15.647s, native
genericnotification/privatecategory/contentIntent/systemshadepositive; muted
2sOSsilence+reopen+cachedunmute/exactdraft/no-wire pass. Retainedprivate-alert-
green-android10(12hash4XML), originalsystemshadePNGseen/allXMLparsed. Source
reviewedSpec0/Standards0. Focused17run38694546565 sameexact0bf ACTIVE.

Next singletracer groupmutecontrol, exactdraft, independentweb-signedincoming
groupmessage/ACK, recreation/unmute/cache/no-wire; controlledsignedcreation
preparesknownmembership, no networkwrites. FirstactualREDpending. Groupbutton/
publickeyconversationIDvalidation notimplemented yet. No groupnotifacceptance.

Grouptracer4aa36fb1 review: Spec1recreatedChatlist has no clickableChat nav;
fixedGroups-or-Chat readinessbeforegroupentry, preservingexactdraft andremaining
assertions. Standards1possibleduplicatedfixturelifecycle; extracted scoped
files/preferences snapshot/prepare/restore helper with explicitprefixes/owner,
independentcleanupactions retained. FirstgroupRED10run38694777442 exact4aa
ACTIVEandstillvalidformissingcontrol; no duplicate. Private17run38694546565
exact0bf ACTIVE. Noownedlocalbuild, no productiongroupminimum yet.

Privatefocused17run38694546565 exact0bf65264 GREEN2PASS52.994s, actualgeneric
OSshade/privatecategory/intent + muted2ssilence/readable/exactdraft/reopen/unmute/
no-wire. Retainedprivate-alert-green-android17(12hash4XML), originalshadeseen/
XMLparsed. Hostgrant/revoke commandsPASS afterfixture; noaftergrantqueryclaimed.

ActualgroupRED10run38694777442 exact4aa36fb1:3cases2PASS1FAIL36.041s, missing
Mutecontrol209 aftercorrectgroup/Archive/enableddraft. Groupincoming/recreate/
unmutetailunexecuted; sameprivate2PASS. Retainedgroup-control-red-android10
(14hash5XML), grouporiginalfailureseen. Minimum adds sharedgroupheaderbutton
and validSaitopublickey conversationIDs; no groupnotifications yet. Corrected
fixturee2727e09 reviewedSpec0/Standards0. Newcompile/reviews/nativeGREENpending.

Groupminimum3749a543 reviewedSpec0/Standards0. Actualnative10run38695426962
3cases2PASS1FAIL38.852s: groupmute/liveincoming/ACK/exactdraft and recreatedmute/
cachepass, exactdraftempty20s204. Retainedgroup-control-draft-red-android10
(16hash6XML), originalfailureseen/allXMLparsed; unmute/no-wiretailunexecuted.
Groupminimum nowreuses ChatComposerMemory underseparategroupowner/backend/id
keys, sharedmutexinitialload/transitions, latestedits/replies/sendclear/cancel
memorypublication withdurablependingauthority andNonCancellablecommit+Mainstate.
No newplaintextstorage/protocol/groupnotifications. Sourcecompile/reviews and
correctednativeproofpending. No ownedlocalbuild or othernativerunactive.

GroupcontrolGREEN38696233581 exact4a8db3f2:3PASS20.001s, mutedindependent
incoming/ACK/exactdraft, recreationmute/cache/exactdraft, unmute/history/no-wire;
private2alsoPASS. Retainedgroup-control-green-android10(18hash7XML), allthree
grouporiginalPNGseen/allXMLparsed. GroupdraftsourceSpec0/Standards0.

Nextsingletracer fourthcase actualgenericOSgroupalert/category/intent/systemshade
positive, plusgroupmuted2ssilence/reopen/cachedunmute. Sharedactualnotification
observation/cleanup andidentity/no-wirehelpers usedbyprivate+group, preserving
all existingcriteria andstrengtheningtitle toexactRedSquare. FirstactualRED
pending; no productiongroupnotification pathyet.

Groupalerttracer5f390291 source/evidenceSpec0/Standards0. ActualRED10
38697022653 exact5f390291:4cases3PASS1FAIL43.877s, compatiblegroupincoming/ACK
positive thenmissingactualOSalert224/20s; notificationtailunexecuted. Retained
group-alert-red-android10(20hash8XML), rootoriginalfailureseen/XMLparsed.
Minimum nowposts newauthenticatedretainedgroupmessages onlywhileownmembership
active, sharesgenericnotification/selection/policy/permissionroute withprivate,
keepscontrols/reactions/Archive/duplicates/selfsilent. GroupcategoryGroupchats.
Headernamebounded2lines+ellipsis/fulltextsemantics afteractualnewbuttoncaused
three-lineheaderinretainedcaptures. No closed-app push orwhole29acceptance.
Sourcecompile/review/native4GREENpending; no ownedlocalbuild.

Groupalertminimumdc86984f Spec0/Standards0; cross-axis concern: ownremoval could
commitbetweenretentionlockrelease andnotificationlockacquire. Correctednotifier
rechecks currentprotectedgroupmembership underpublicationidentitylock, alongside
freshpolicy/selection, so lateownremoval silencesalert. Ownedfirst4-caseGREEN
attempt38697852024 exactdc remainsactive, no duplicate; doesnotexercise this
concurrentguard oracceptwhole29. Correctionreview/compilepending.

Firstprivate+groupnativealertsGREEN38697852024 exactdc86984f:4PASS24.863s, actual
generic/private+groupcategories/intent/systemshades andmuted2ssilence/readable/
exactdraft/reopen/cachedunmute/no-wire. Retainedgroup-alert-green-android10
(20hash8XML), originalgroupshade/reopenedPNGseen/allXMLparsed. Latermembership
publicationguardf518be53 bothreviews0/0, notcoveredbyolderdcsourceproof.

Nextregression strengthensalreadyimplementedoriginaldedup throughactualnativeUI/
peer/OS: clearonlyfixturealert, redeliverexactsameindependentsignedpacket with
newRPCid778, requirepositiveACK/exactonecachedmessage/noalert2s. Appliedboth
private/group positivecases andafterunmutingmutedcachedoriginals, preservingall
identity/no-wireassertions. No fabricatedREDclaim foralreadyimplementeddedup;
actual4-case regressionpending atsourcewithfreshmembershipguard.

DedupregressionGREEN at53adc844 bothSDKs:10run38698778675 4PASS34.884s;17run
38698781938 4PASS88.570s. Bothactualcategories/systemshades, mutedreadable/exact
draft/recreate/unmute/no-wire, exactsignedredeliveryRPC778+positiveACK+oneUI
message+2ssilence bothbeforemute andaftercachedunmute. Each28hash12XMLretained
dedup-green-android10/17, rootallfournewduplicatecapturesseen/XMLparsed. Source
includesfreshmembershipguard but no actualconcurrentraceclaim; bothreviews0/0.

Nextregression adds private/group latefailedUnmute through existingdebugOSsync
faultpersonalCommitSyncUnavailable. ActualUIinitialmute, attemptUnmute fails,
requiresgeneric saveerror+retainedmute, newauthenticateddistinctincoming stays
readable/ACKed/silent, recreate retainschoice/cache/exactdraft, removefault then
explicitUnmute succeeds witholdoriginalstilldedup-silent. Independentcaptures
prefixedperfaultcase; boundedshowMessage scroll+display preservesassertions when
secondcachedmessage isoffscreen. Existingrollbackmachinery isalreadyimplemented,
so no fabricatedREDclaim. Actual6-case proofpending; no productionchanges.

FailedUnmuteregression26a79e3b reviewedSpec0/Standards0, actual10run38699942542
6PASS67.603s. BothformsactualOSsyncsaveerror/retainedmute, freshsignedRPC779
ACK/readable/silent, recreatechoice/cache/exactdraft, removefault/explicitrepair/
oldoriginaldedup andprior4allPASS. Retainedfailed-unmute-green-android10
(52hash24XML), rootallfournewfault/reopenPNGseen/allXMLparsed.

Nextsinglecommonpolicyfaultregression: positiveprivateOSalert first, then scoped
fixture-only badpolicyfile, freshauthenticatedmessage retained/ACK779/readable
withoutalert; deleteonlycreatedbadfile restoring priorabsentrecord, sameoriginal
redeliveryACK780/oneUI/silent, newfreshACK781+actualgenericOSalertafterrepair.
Existingfailclosedroutealreadyimplemented, no falseREDclaim. Hostclassbudget
300sfor7cases basedpriorSDK17fourcase88.570s andSDK10sixcase67.603s; individual
20sUI/RPC assertionsunchanged. No duplicate/native/localbuildactive.

Unreadablepolicytracer9fc36231 Standards0, Spec1helpercleanup couldmaskprimary
assertionifbadfiledeletionfailed. Correctedhelper uses existingcleanupAfterTest
withretainedprimary, matchingouterindependentfixturecleanup. Noactualcriterion/
productionchange. Ownedfirst7-case38700873867 exact9fc ACTIVE; no duplicate.

UnreadablepolicyGREEN10run38700873867 exact9fc36231:7PASS74.080s. Positive
OSalert, scopedbadpolicy freshACK779/readable/silent, repair originalACK780/
oneUI/silent, freshACK781/actualgenericOSalert andprior6PASS. Retained
unreadable-policy-green-android10(62hash29XML), root3neworiginalsseen/allXMLparsed.
Later12af76e9 cleanup-primary correction bothreviews0/0, notexecutedbyolderrun.

Nextsingle combinedbackend/identity regression uses actualUI mutes forprivate
andgroup onOWNER, actualSettings switchtoOFFICIAL withbothchoicesretained;
protectedfixturewalletswitch tosecondidentity withsamegroupID defaultunmuted,
positivegenericprivateOSalert, independentsecondwalletmute; restorefirstwallet,
itsownmute/readablefreshincoming/silence andsamegroupchoice remain. Scopedtwo-
walletsnapshot/independentcleanup, fullwallet/backend/no-wire assertions. Reuses
approvedprotectedidentityseam withoutrepeatingSAF workflows. No prodchange or
falseREDclaim. Native8case proofpending; processinterruption stillremaining.

Combinedbackend/identity sourceace5b807 reviewed Spec0/Standards0; ownedfocused10
run38702369456 exactace ACTIVE, no duplicate. Newsequentialprocessregression
usesrealUI savedprivate/groupmutes andexactdrafts, cachesbeforedeath, existing
DEBUG personalCommitTerminate onlywhenexplicitUnmute commits. Hostmustobserve
crash+originalPIDgone; separatecoldprocess requiresbothchoices, exactdrafts,
cache+freshsignedRPC950/951 ACK/readable/2sOSsilence, fullwallet/backend/no-wire.
Device-only explicitfileallowlist snapshots include originalencryptedwallet and
scopedpolicy/history/draft/guardrecords. Independentfile/preference restoration;
publiccheckpoint containsdigests/publicidentity only. Existingchat-mute scopes
nowrunprocessproofaftereightcases beforehostpermissionrestore; individualUI/RPC
boundsunchanged, no new CI scope. Compile/review/actualproofpending.

Process972870cb Speconeproofgap: pendingcheckpoint+crashalone couldprecede the
commit. Hostnowrequires nonemptyencryptedpendingguard bystat-onlymetadata,
matchingexistingaccountpolicyprocessproof, beforecoldrestart. Boundedactual
scroll/display addedforsecondcachedincoming, retaining20sdeadline+literaltext.
No protectedcontentsleave device, no production/acceptanceweakening.

Whole29sourcereview40f28872...3a964944 Spec0/Standards0, sourceonly.
Firstprocessattempt38703034326/3a FAILEDcompileDebugAndroidTestKotlin: fixture
usedMockWebServer whereControlledFeedPeer ownsRPC/observations. No actualprocess
or8case runtime executed. Correctedretainpeer separatelyfromstartedserver,
no production/assertionchange. Prior8case38702369456/ace remainsGREEN100.478s,
retainedbackend-identity-green-android10(74hash35XML), root6neworiginalsseen.
Signed19buildisolatedsource3a active; test-onlycompilefixdoesnotchangeAPK.

Corrected56b3c17e process source Spec0/Standards0. Actualfocused10run38703489486
SUCCESS:8PASS95.831s plus realprocess6413gone→6890, pendingguard259bytes/stat0,
bothsavedmutes/exactdrafts/cache/freshsignedACK950/951/readable/2ssilence,
fullwallet/backend/no-wire, recovery+restorationeach1PASS. Retainedprocess-
regression-green-android10(74hash35XML) andprocess-recovery-green-android10
(16hash3XML); rootall3processoriginalsseen/allXMLparsed. No secretsnapshotpulled.
Finalfull10run38704251435 and17run38704254979 dispatchedsameexact56b3c17e with
public_feed_read=true, ACTIVE. No duplicatefull orlocalbuild. Signed19prepared
fromproduct-identical3a PASS4m36s, cert/package/min29checked, SHA82ed82795eda62dd4
c064603cea022869063184175e12c4147cc4e5d031a2de4. Public18unchanged pendingfulls.

Finalfull10run38704251435 exact56b3c17e SUCCESS:345reported/344PASS+existing
API30profileassumption,79records25actualdeaths/core114/release/lint/allpriorgates/
bothpublicfeeds. Mute8PASS98.636s; realmutePID6522gone→6921 bothchoices/drafts/
cache/freshACK/silence/restoration. Queue13PASS250.602s andbothautomaticprocesses,
bothadminprocesses, coldwallet2+immutable5→8four nativeobservers andfinaldiagnostic
allpass. Retainedfinal-full/android10(893hash329XML), root10selectedoriginals
seen/allXMLparsed. Final17same56b stillACTIVE. No full17/publication claimyet.

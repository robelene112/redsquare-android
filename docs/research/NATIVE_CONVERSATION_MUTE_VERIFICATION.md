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

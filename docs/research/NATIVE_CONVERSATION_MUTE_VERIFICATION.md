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

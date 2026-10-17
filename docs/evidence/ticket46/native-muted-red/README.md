# Existing ordinary mute and preference-repair regression RED

Actual Android10 run39062885447 atd81516f485d97d26e90f0cdfd8a98b2e68afa0fd executes the unchanged ChatMutePreviewTest: eight cases in82.164s, five pass and three fail. Both normal and previously failed unmute paths retain readable messages/drafts but redelivery after unmute violates continuous alert silence at line130. The unreadable-preference case retains its message, then redelivery after repair violates silence at line429. Group and ordinary generic-private cases pass. The later process-recovery harness is not reached after class failure.

Eight hashed records retain exact raw instrumentation/exit/run, immutable existing test-source hash and two failure PNG/XML pairs. The assertions identify unwanted active notifications; the failure screenshots do not independently prove how the user reached that state.

Minimum correctionb1e2d3330 distinguishes ordinary notifier failure from background retry and completes eligibility after deliberate suppression. Source Standards0/Spec0; exact mute39063261318 and chat-alerts regression39063263234 pass, as retained in [the separate GREEN](../native-muted-green/README.md). Bookkeeping errors remain recoverable. Whole#46 remains open.

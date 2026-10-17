# Corrected notification-card process checks, Android17

Exact5154e89b4747cd30ff1421543485650c45b856f2,
run39014623934 SUCCESS, scope social-push-process-android17.
Separate source39014520955 SUCCESS includes core, release assembly and lint.
Fixture-only change from19e; production source is identical.
35 original/output files are hashed, including5 PNG/XML pairs.

The reply is physically dismissed with one near-full-card swipe before actual
process death. Checkpoints retain original PID2920 and recovered PID4231.
Cold duplicate replay remains silent, while a distinct signed reply produces
a visible alert. Recovered case1/0/6.648s, independent restoration1/0/2.241s.
The first interrupted result deliberately reports Process crashed; checkpoint
phase and PID checks distinguish intentional fixture termination from success
or an unexplained application crash. All original assertions remain.

The separate scheduled recovery kills verified live PID4529; recovered PID4775
reads the now-available owned Archive without launching target instrumentation,
an Activity or forced jobs during the observation. Peer available reads0to2,
no outgoing writes. Exact alert observation and independent restoration pass,
restorer1/0/0.916s. This is local WorkManager/owned transport, not FCM or Google
closed-app delivery. Owned work is cancelled and original installation restored.

Root viewed all5 PNGs and parsed all5 XMLs. Published and fresh replies are
visible; dismissed/cold duplicate captures omit the fixture alert. The scheduled
XML also contains the exact reply at[88,281][288,301]. An initial image preview
was visually ambiguous, so independent read-only Java ImageIO decoded ARGB
comparison confirms that entire4000pixel text rectangle exactly matches the
visibly populated fresh-reply capture. No original image was changed. The
retained decoded-reply-rectangle.txt records that limited result, not whole-screen
or atomic screenshot/hierarchy equivalence. XML and PNG are separate observations.

The original Android10 full failure remains FAILURE in full-regression-red.
Corrected full Android10 39014622109 and original full Android17 39013783536
remain running. No complete full-suite acceptance or new beta publication.

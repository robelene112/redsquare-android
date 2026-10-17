# Full Android10 regression interrupted at physical dismissal

Run39013781414 at19e1083dbe38aa0bd2209e535df58a764d3ff654 is FAILURE.
Appearance1/0/10.429s, push binding1/0/0.981s, social receipt/tap11/0/83.148s,
and worker3/0/46.777s completed. The process-recovery preparatory case then
failed1/1/7.559s at SocialPushProcessRecoveryTest.kt77: the reply text did not
disappear within5seconds after a physical horizontal swipe. No deliberate
process death, cold replay, scheduled recovery or later full-suite class ran.
Independent installation restoration passed1/0/0.638s.

Root viewed the failure PNG and parsed its XML. The notification remains
visible and fully expanded; its text bounds are[20,246][300,265] on a320x640
screen. The existing gesture starts at one quarter of the screen and ends at
width minus2. The earlier social class used the same gesture successfully.
These observations do not establish an application dismissal defect or the
precise gesture failure cause. No application assertion is waived.

Both PNG/XML pairs are retained as separate observations.13 files are hashed;
README is explanatory. Android17 full39013783536 is separately in progress.
Beta25 stays unpublished pending complete regression acceptance.

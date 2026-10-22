# Original frame viewport goal passes after current-instance recreation

Android10run39723214114 executes exact44601552d50de619def7c5b90de27af985a9cb66.
Whole/job SUCCESS. The original AppFramePreviewTest method
`largerTextKeepsFrameControlsAndBackupGuidanceReachableAtBothViewportSizes`
passes in27.885s/180 with its original15s readiness bound, all eight320/412dp,
130%/200% Light/Dark cells, every original native48dp target and backup guidance.
No dropped assertion, identity change or deadline extension. It restores font,
size, density and appearance after closing its Activity.

The fixture calls the shared current-instance recreation operator previously
used by PostViewportFixture. Root inspected320/font130/Light,412/font200/Dark
and its full backup-guidance captures. The earlier failure did not trace callback
ordering, so this success supports the correction without proving that historical
ordering. The rejected78bd dispatch remains CANCELLED/ChatFAILURE separately.

The packet retains the original archive's138 members plus goal/configuration/
restoration/terminal metadata,149 hashes. Native functional reachability does
not establish final app-wide visual acceptance: the existing Following word wrap
at200% is still visible and remains open under70. Wallet layout polish is67.
Full current Android10/17 and final signed publication remain pending.

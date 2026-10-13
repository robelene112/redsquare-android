# Contextual permission fixture RED

Android17 run38326680659 ataf49e30: main31 notifications pass173.964s, then
separate allowingAlertsKeepsInAppReadingOptional fails1/16.864s/zeroOK on the
15s initial waitFor(Notifications), NotificationPermissionPreviewTest.kt:49.
Root parsed the XML and viewed original failure PNG: Owner header and optional
wallet-backup reminder, no FeedScreen or Notifications entry. feedDisabled=true
skipped that feature; f4cce67 already removes the flag, preserving controlled
loopback URLs. Three hashes retain original log/PNG/XML. No OS permission dialog
or denial failure occurred; this is fixture setup, not a product-permission defect.
Corrective10run38326981751 and17run38327322258 pending.

# Original notification class passes, cold identity navigation rejected

Run39780917627 at exact507d35d8192c2d8cd536010be79c06d2a0295e3a is FAILURE.
All32 original NotificationsPreviewTest methods pass147.866s/240; the first
permission method passes7.95s. The following cold-recovery method fails5.142s
because it still asserts the full actor key directly on the profile main
screen. Root viewed the original failure showing the matching compact identity
and Identity details control. The correction uses the same full-key dialog
inspection as the regular routes, preserving actor identity, read state and
recovery assertions. Subsequent permission cases were not executed.

Source inspection also found four identical old raw-key assumptions in Search
profile routes and three old existence waits for lazy profile posts in Quote,
Social and Author fixtures. They now navigate the same exact identity/content
through existing helpers; the original20s content budget and all original
expectations remain. None of these changes claims Search UI65 implementation.

Verified all111 SHA256SUMS entries; this later README is excluded.
Original members and bindings are retained. Public63 remains current.

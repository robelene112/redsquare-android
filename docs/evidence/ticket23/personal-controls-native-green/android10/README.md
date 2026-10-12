# Personal notification controls first GREEN

Android10 run38320137452 at08a05c3 passes24 cases in80.211s, one completed
OK runner, zero deaths/assumptions, core73/debug/test. Local original fixture
APK1m39s passes. Actual Profile Mute/Block removes rows/actor/unread, survives
changed failed reads and Activity recreation, and actual management removal
restores one cached like/actor/1unread. All23 previous cases and opaque restore/
no-write/authenticated-owner assertions pass.

Root parsed28XML and viewed original mute, block and restored PNGs: selected
owner, failed-refresh warning, no unread/no rows while controlled, and1unread/
actor2 own-like restored after removal.58hashes. Other PNGs retained without
root visual claims. This run uses original fixture, not corrected cache-warming
waits in c0a71b9. Corrected waits still require runtime verification. Activity
recreation is not process death; no final SDK/whole-ticket acceptance claim.

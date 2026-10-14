# Incoming group UI attempt, Android 10

Run [38573990110](https://github.com/robelene112/redsquare-android/actions/runs/38573990110) at `955f4bbfc407bb3e732013092f336ecaad10d3ba`: 16 tests, 15 passed and one failed, 80.732 seconds.

The new incoming UI case passed its creation acknowledgement, list discovery, received status, absent Retry, message acknowledgement/display, exact draft persistence through Back and Activity recreation, protected original/cache checks, owner backend and zero outgoing raw/Relay assertions. It then failed at the final controlled-peer health assertion: the default feed archive handler accessed absent field1 for a group field3 request. This is not a successful run. The later strict archive callback correction has separate source and execution evidence.

Root inspected the original incoming-ui-failure PNG and parsed XML: received group, live history and exact draft are visible. These captures do not prove the new storage-error/reconnect case, real process death, physical performance or complete issue26 acceptance. Unsafe public fixture identities only. Raw output and retained captures are hashed below.

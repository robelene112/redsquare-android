# Username classifier notification compatibility: android17

Run[38976636125](https://github.com/robelene112/redsquare-android/actions/runs/38976636125)
SUCCESS at78245041c64c2fddaaa039cf7d076c6e477c5802. All32 reading cases,
2 applicable permission case(s) and one actual read-history process recovery pass.
Times: 175.378s reading,6.363s allow,6.2s deny,4.247s recovery. Core114/0/0 and selected SDK build/runtime passed.
The other SDK was deliberately skipped in this focused run.

Recovery checkpoint PIDs change from5326 to5398; continuity retains the same
wallet/backend and read activity under failed controlled-server reads, actor
navigation and zero transactions. This fixture never replaces the installation
wallet. Its interrupted phase is deliberate and is not counted as a passing test;
there is no separate restoration JUnit phase.

Root parsed all retained XML and viewed the permission reading and/or OS prompt
and cold recovered reading originals. Cold recovery explicitly shows the failed
refresh message alongside retained read activity and No unread activity. It does
not prove successful online history retrieval. Android10 has no runtime notification
permission dialog. Android17 proves allow and deny while in-app reading remains
available. These cases do not prove social receipt suppression after denial,
FCM wakeup, locked-device delivery or physical Pixel performance.

Raw output, checkpoints, screenshots/XML and exact run metadata are retained.
No production wallet snapshot was pulled. SHA256SUMS covers every retained file
except the manifest itself.

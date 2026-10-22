# Exact-source compilation after rejected build attempts

Local compileDebugAndroidTestKotlin succeeds at exact
507d35d8192c2d8cd536010be79c06d2a0295e3a, Gradle5m35s, actual child exit0.
Tracked source stayed unchanged throughout the invocation. The retained
command receipt records12:38:32 through12:44:11UTC on2026-10-22. This verifies
compilation, not native execution.

The three retained cd06 runs39779361041/39779372323/39779383033 all failed in
Build and verify native sources: the fixture referenced unavailable
performScrollBy. No device checks ran. The supported SemanticsActions.ScrollBy
replacement retains the same offset and assertions. Automatic e07 source run
39779931077 was superseded by the corrected push and cancelled, not accepted.

SHA256SUMS covers the original compiler receipt/log and three failed-run
metadata/log pairs. This later README is excluded. Corrected focused native
runs are pending. Public63 remains unchanged.

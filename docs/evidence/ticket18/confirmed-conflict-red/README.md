# Confirmed conflict cannot open a new-name form

Android 17 run38127516540 at b3f268368617f731dea9a5ca043030d59379a55f:
14 of 15 tests pass in106.204s. The lost-race test fails at line623 because
Choose another name does not exist. Its earlier assertions verify a registrar-
signed conflict, disabled registration retry, read-only recreation with no
resend, and an anonymous profile for the losing wallet. The original failure
PNG/XML show frozen NativeCase7, grey Retry and the taken message. Root and
both review agents individually inspected these originals.

The Settings unsaved-own-name regression now passes its full warning-retention,
empty-check rejection and repaired-cache assertions. The storage obstruction,
corruption, repair and exact explicit registration retry also pass. This failed
run does not reach process recovery or establish full acceptance, live Registry
readiness or any successful retirement. The retirement implementation comes
later; source review identifies and corrects its stale-saver permission gap.

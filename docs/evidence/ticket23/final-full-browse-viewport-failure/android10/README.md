# Full Android10 existing browse viewport failure

Final full run38328911797 at a5648ee stops in first browse class:8reported/
7PASS/one existing recovery-test failure20.442s/zeroOK/deaths/assumptions.
ThreadProfilePreviewTest.kt:443 finds the cached root's text in semantics but
assertIsDisplayed fails before opening Conversation. Root viewed original inner
failure PNG: selected Owner, optional backup reminder/server-unavailable banner/
feed controls/Notifications/new saved-changes controls, cached author at bottom;
post body falls below viewport. No recovery XML was captured by this legacy
helper. Three hashes retain original runner and two original failure PNGs.

Outer failure capture is blank after Activity closes; it does not establish a
blank product window. Correction scrolls to the existing cached post before the
unchanged displayed assertion/click/thread/reply/offline acceptance assertions.
Production/source/reference/harness remain unchanged. SDK17 original full run
38328912121 continues; reviewed test-only correction may keep its full proof if
all gates pass, per WORKFLOW companion-run rule. No later-suite/full acceptance
claim from failed Android10 run. Build core74/release/lint/debug/test pass.

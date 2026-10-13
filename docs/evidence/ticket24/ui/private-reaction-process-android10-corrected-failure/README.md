# Android 10 broad Chat UI failure, run 38473067796

Source 12e1b760. History 24 cases: 21 passed and 3 failed, 146.011s; core86 passed. Actions and actual reaction process recovery were not executed. The earlier strict offscreen check was corrected before this run; this result does not establish its cause.

Failures: key-only imported wallet timed out waiting for its outgoing retained message at fixture line820; blocked-history and other-contact cases timed out waiting for the initial contact at line255. The single shared failure PNG/XML was overwritten by successive failures, so it cannot represent all three states. Root inspected the retained final original pair: feed is visible with Chat; no conversation or wallet material is displayed.

Initial fixture launch waited for Chat text, but the public control is initially disabled until wallet initialization finishes. An explicit enabled readiness gate and per-case failure captures are the next bounded diagnostic correction. This is a source-supported hypothesis for the initial-navigation failures, not a proven explanation of all failures. No product change or history loss established.

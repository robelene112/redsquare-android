# First social alert offer RED, Android 10

Run [38960006823](https://github.com/robelene112/redsquare-android/actions/runs/38960006823)
at `f5e824dcc06ecdf02a53387bb4d784f6f98613b8` completed FAILURE: eight ordinary
cases, seven PASS and one FAIL in 68.437 seconds. The new onboarding journey
times out after 15 seconds waiting for Android notification alerts at line 139,
after positively observing the displayed Notifications screen. The global
Android prompt is already marked offered, while wallet social consent is absent.

Root viewed the original failure PNG and parsed all 29 XML files. Image and
hierarchy agree on the actual Notifications screen with Back, Refresh and no
alert offer. This is the intended missing-behavior RED. Seven established cases
pass, including actual Android categories and saved-work/navigation assertions.
The new case does not reach Enable, OS page, consent persistence, recreation,
reply alert/tap or its final preservation invariants. Those tails are unexecuted;
guarded fixture cleanup still runs.

62 hashes retain original PNG/XML pairs, raw runner output, failed metadata
and core report. adbexit0 is preserved while the harness correctly fails; no
scope.txt was generated after failure. Core 114/debug/test build passed. Focused
scope omits release/lint/full suites and Android 17. No actual Firebase/public
delivery or whole issue completion is established. Public beta 24 is unchanged.

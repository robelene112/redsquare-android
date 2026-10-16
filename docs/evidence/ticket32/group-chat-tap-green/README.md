# Group Chat draft preservation on a social tap

The new group guard at0b5dc394 is verified at exact4419fd1a2ac07bbd084f95578a136bf38ec590eb. The [intended RED](../group-chat-tap-red/android10/README.md) remains a separate failure. Existing test fixture, core and group store are unchanged, as recorded in [source-equivalence.json](source-equivalence.json).

- [Android10 social](android10/README.md), run38973726401: all10 cases PASS,78.837s.
- [Android17 social](android17/README.md), run38973729273: all10 cases PASS,103.434s.
- [Android10 group compatibility](../group-chat-tap-regression/android10/README.md), run38973732283: actions1, mixed1, group22, actual recovery1 and restoration1 PASS; PID13930 to14322.
- [Android17 group compatibility](../group-chat-tap-regression/android17/README.md), run38973734828: same26 reported completions PASS; PID5236 to5318.
- Separate push38973706184 SUCCESS includes core/debug/test/release assembly and lint at the same source. Local minimum core/debug/test compilation passed3m45s. Source/RED-evidence reviews report0/0 at0b5dc394; final GREEN evidence review is subsequent.

Root individually viewed14 selected originals across these four packages (four new social captures and three group regression captures perSDK), and parsed all167 XMLs. Exact notification destinations appear while Loading replies remains visible; this is exact-target proof, not exhaustive history. The restored group draft text/photo and unencrypted-group notice are visible. Existing forwarded replies remain delivery-unconfirmed; deliberately unavailable-history/process cases retain their qualified recovery states.

This accepts the bounded group-draft navigation behavior only. Local injection and instrumentation do not prove actual FCM, gateway or closed-process push. Group management dialogs remain deferred instead of discarding their unsaved input. Full#32 and physical Pixel acceptance are outstanding; public beta24 stays unchanged.

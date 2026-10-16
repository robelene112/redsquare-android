# Private Chat tap acceptance slice

The private Chat guard passes actual social notification navigation on both tested Android versions with unchanged production since 84eb9704. This is one slice of open #32.

- Android 10 social run 38968037239: nine cases PASS, 72.148 s. See [earlier GREEN](../private-chat-tap-green/android10/README.md).
- Android 17 social run 38971338531: nine cases PASS, 112.585 s. See [latest GREEN](android17/README.md), including positive edited text and before-Back draft checks.
- Android 10 photo compatibility run 38968045554: ten UI cases and both actual private/group process recovery phases pass; subsequent storage cases fail with the retained 16 MiB OOM. The unchanged storage cases later pass in 38970020926 and 38971341246. See [failure](../private-chat-photo-regression-failure/android10/README.md), [first follow-up](../private-chat-photo-regression-followup/android10-storage/README.md) and [effective memory observation](../private-chat-memory-observation/android10/README.md). The last run records actual maxMemory 512 MiB; it does not establish the old 16 MiB limit cause.
- Android 17 photo compatibility run 38970017629: ten UI cases, both actual process recovery phases and both storage cases pass. See [follow-up](../private-chat-photo-regression-followup/android17/README.md). Earlier timeout remains a failure with unproved cause.
- Separate exact-source push 38971334056 passes core, debug/test/release assembly and lint. Focused native scopes omit release/lint/full acceptance.

[source-equivalence.json](source-equivalence.json) establishes unchanged production trees across these sources. Fixture corrections preserve acceptance assertions and deadlines. All failures remain in their own packages. Local injection does not prove FCM, public gateway or closed-process push; private draft navigation does not complete #32. Public beta 24 stays unchanged.

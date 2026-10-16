# First social alert choice, Android 10

Run [38962211627](https://github.com/robelene112/redsquare-android/actions/runs/38962211627)
at `495fb72cd066aef4fd0f154bcb52779d79a906c9` succeeds: eight ordinary cases
in 68.043 seconds. The new case starts with the general Android offer already
handled and no wallet social consent. Android permission is pregranted on API
33+ only. Notifications still offers an explicit wallet-scoped social choice.

Enable saves consent. Android 10 opens the actual app-specific OS page; Android
17's already-granted permission needs no further OS request. Screen recreation
retains the choice and no repeated offer is observed for two seconds. Settings
shows the Replies and mentions switch On. An actual signed reply alert and
system tap then open the exact target, preserving the latest unsent text/photo,
unsigned/unqueued draft, full wallet/backend and zero outgoing writes.

Root viewed all new-case original PNGs, six on Android 10 and five on Android
17, parsed all 67 XML files across both SDKs, and independently checked the
exact social-switch label's checkable parent is checked. The Android 10 Conversation capture still shows Loading replies.
The result proves the exact destination and available content, not permanent
or exhaustive history. 73 hashes retain 34 PNG/XML pairs, raw runner output,
source metadata and core report. Seven established cases also pass.

Core 114/debug/test pass; focused scope omits release/lint/full acceptance. The
separate same-source push check covers release/lint, and the existing notification
regression scopes cover actual permission choices and read-history recovery.
This is local injected transport with instrumentation alive, not real Firebase
or public closed-app delivery. Skip persistence failure and enable-error recovery
are source-reviewed, not demonstrated by this positive journey. The intended
RED remains FAILURE. Public beta 24 is unchanged and #32 stays open.

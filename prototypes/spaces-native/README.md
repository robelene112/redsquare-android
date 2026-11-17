# Spaces native UI prototype

Throwaway design study for [specification134](https://github.com/robelene112/redsquare-android/issues/134).
Question: do the agreed community/channel hierarchy, reading density, controls and difficult states feel right on a phone?

This is one coherent direction based on the agreed grill, rather than three competing layouts. It renders native Compose using the existing app's actual theme/typography source and vector resources. The independent Android package is `nl.davidengelen.redsquare.spaces.prototype`, labelled **Spaces Prototype**. It has no Internet, camera or microphone permission, wallet, push registration or payment integration. It does not replace the ordinary beta. State is synthetic and in memory; restart resets it. Scenario controls are always reachable from the header.

Included: community overview, discovery and activity; Space/channel drawer; chat, reply threads, reactions and editing; announcements; articles/Markdown/review; shared files; prejoin, simulated call and compact call bar; member roles/moderation; invitations/rules; information/settings/storage; archive, transfer and recovery dialogs. Owner/admin/member/visitor, normal/loading/offline/denied/timeout/full/expired states and appearance can change without rebuilding. System font size and reduced-motion settings also apply.

Prototype limitations: roles, storage, reports, ownership, publication, encryption and calls are simulated. The visual editor and reader reuse the existing vendored Stack editor, with editable Markdown and publication review; publication remains simulated. Some detailed operations deliberately open an explanatory design dialog instead of pretending to perform real work. Synthetic file rows do not represent downloaded bytes. No design approval, real data delivery or bug132/133 fix is implied.

From the repository root, `prototypes/spaces-native/build-prototype` compiles and signs the isolated release using the existing protected signing identity. Output is outside Git in the named external artifact cache. `./gradlew -p prototypes/spaces-native :assembleDebug :assembleDebugAndroidTest` builds the native QA variant. The branch-only Spaces prototype workflow runs actual Activity journeys on Android10, Android17 and a 320dp/200percent-font Android17 configuration and retains screenshots and terminal results. This focused prototype verification is not the full functional quality plan.

User review: install alongside the normal beta, open **Spaces Prototype**, explore the communities, then use **Scenarios** to change role/state or jump to a flow. Feedback should identify the screen, action and desired behavior. Keep ordinary app testing agent-owned.

Delivered prototype1: https://davidengelen.nl/redsquare/spaces-prototype/ . See [the acceptance packet](../../docs/evidence/spaces-prototype1/README.md) for exact source,36 native outcomes, public APK verification and phase limits. Owner UI feedback remains pending.

# Spaces prototype 2: fixed left-hand channel navigation

The owner approved the broader prototype direction but found the drawer trigger unintuitive. The approved revision places a labelled **Channels** button on the left in a fixed Space bar. The current Space appears alongside it, or below it with large text. The global module picker stays separate. This revision is delivered for another owner UI look, not functional Spaces acceptance.

- [Download page](https://davidengelen.nl/redsquare/spaces-prototype/)
- [Direct prototype 2 APK](https://davidengelen.nl/redsquare/spaces-prototype/releases/2/spaces-prototype.apk)
- [Specification #134](https://github.com/robelene112/redsquare-android/issues/134)
- [Complete native run 44202131823](https://github.com/robelene112/redsquare-android/actions/runs/44202131823)
- Exact signed/tested source: `1c070633c507c8076109344d1f1cf5fc426124e5`, maintained on `codex/spaces-native-prototype`.

## Verification

All 12 expected native methods pass on each of Android 10, Android 17 and compact 320dp / 200% font: 36 terminal passes and 93 screenshot hashes. The updated journey verifies the menu is visible, remains at the exact same screen bounds after scrolling, sits on the left and opens the drawer. Existing route/state/role/queue/article/call journeys remain included. Normal and large-text chat images were inspected before publication. Debug and signed release lint pass. These are native prototype checks, not real backend, encryption, physical/OEM or full ordinary-app release acceptance.

Independent Standards review: 0 documented-standard or actionable smell findings. Independent Spec review: 0 findings. Both reviewed `e99725752...1c070633c`, including the build/publisher changes, before publication.

The signed nondebuggable APK keeps package `nl.davidengelen.redsquare.spaces.prototype`, advances versionCode to 2 and retains the established signing certificate. It can update prototype 1. Public full-download bytes, checksum, manifest and certificate match the signed artifact. The public page links release 2. Ordinary beta 139 page/metadata and immutable prototype 1 files are byte-identical before/after publication. No device/network permissions were introduced.

The small JSON receipts preserve exact identities and hashes. Raw APKs, screenshots, logs and report data remain outside Git at `/home/david/.cache/redsquare-android-artifacts/spaces-prototype2`. Prototype 1's earlier observations and qualifications remain in [its packet](../spaces-prototype1/README.md); they are not reused to pass this revision.

Owner feedback on the relocated control remains pending. All content and operations are synthetic/in-memory. No functional Spaces, real Chat E2E, node/provider operation or public financial/community write is delivered. Bugs #132/#133/#135 remain open prerequisites before functional integration. The ordinary-app beta notice polish #136 remains separately queued for the next ordinary beta.

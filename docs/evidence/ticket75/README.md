# Issue 75: Profile actions hierarchy

## Delivered

The Profile actions dialog now groups public identity, connection state, and
privacy and safety actions into clearly labeled sections. It explains whether
the account is followed and what Follow changes, and describes Mute, Block,
and reporting before the user activates them. Active Mute and Block states are
shown explicitly. Follow, Mute, Block, and Report are omitted from the owner's
own profile. Existing confirmation and dismissal behavior remains in place.

Implementation and instrumentation source: `3205a535a270ebe6ae4eb976b5c569d81588df05`.

## Verification

The focused `ProfileHierarchyPreviewTest` passed on both Android emulator
versions:

- Android 10 / API 29: [run 41623639977](https://github.com/robelene112/redsquare-android/actions/runs/41623639977), 1 test passed.
- Android 17 / API 37: [run 41624266779](https://github.com/robelene112/redsquare-android/actions/runs/41624266779), 1 test passed.

Each test exercises light and dark themes at 100%, 130%, and 200% text size,
including the longer scrollable dialog, identity details, the Follow state,
Mute confirmation cancellation, and the safety actions. Screenshots and
semantics captures are retained as downloadable artifacts in each CI run.
Independent Standards and Spec reviews found no documented violations or
acceptance gaps.

The first final Android 10/17 attempt exposed an offscreen assertion in the
test after the Mute confirmation closed at 200% text. The production rows were
present and scrollable. The test now scrolls each section into view before
asserting it; the subsequent runs above pass. These are focused emulator checks,
not a physical Pixel 7 walkthrough or a complete app suite.

## Signed beta

Beta `0.1.80` is published as an in-place update at
https://davidengelen.nl/redsquare/ . It was built from the verified source
commit above and retains the established signing identity.

- APK: https://davidengelen.nl/redsquare/releases/80/redsquare.apk
- SHA-256: `4f1d7ee1fbf1a6cae8fa24adb1391e2e8a2de2aae17b33a2768590c56f57ebe0`
- Signing certificate SHA-256: `6ca1209bcbba661943f7b683d48b2f58493f13851c760c8d80d1521ac0ee79a2`

The public latest manifest identifies version `0.1.80` and the source commit
above. Downloading the published HTTPS APK produced the same SHA-256 as the
verified signed build artifact.

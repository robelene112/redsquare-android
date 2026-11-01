# Issue 74: conversation display names

## Delivered

Private and group conversation rows now show the selected Saito backend's
verified display name when available. Missing, stale, or unavailable names keep
a readable anonymous identity. A displayed name opens identity details with
the exact public key, so the name is not presented as the key itself.

Cached names are accepted only for the selected backend and for 24 hours after
verification. Online responses still pass the backend's Registry verification
before they replace the fallback.

Implementation and final instrumentation source: `553630f73622842e00d67b9c763d61439fed56da`.

## Verification

The dedicated `ChatIdentityPreviewTest` suite passed all three tests on each
emulator version:

- Android 10 / API 29: [run 41341824488](https://github.com/robelene112/redsquare-android/actions/runs/41341824488), 3/3 passed.
- Android 17 / API 37: [run 41341824356](https://github.com/robelene112/redsquare-android/actions/runs/41341824356), 3/3 passed.

The suite covers verified long names and exact-key details in private chat,
cached group history while offline, and cache freshness/backend matching. The
Android 10 proof also confirms the cache UI settles after reopening the
conversation. Release and Android test Kotlin compilation succeeded locally.

These are focused emulator checks, not a full-app suite or a physical Pixel 7
walkthrough. The CI evidence artifacts are retained with the linked runs.

## Signed beta

Beta `0.1.78` is published as an in-place update at
https://davidengelen.nl/redsquare/ . It was built from the verified source
commit above, uses the established signing certificate, and has application ID
`nl.davidengelen.redsquare` with minimum Android API 29.

- APK: https://davidengelen.nl/redsquare/releases/78/redsquare.apk
- SHA-256: `56c9e9695be2a34c4a8a4a9e8ae119b2ee27a5dd3bd558cdbabdfc9ec692dd5b`
- Signing certificate SHA-256:
  `6ca1209bcbba661943f7b683d48b2f58493f13851c760c8d80d1521ac0ee79a2`

The public latest manifest identifies version `0.1.78` and source commit
`553630f73622842e00d67b9c763d61439fed56da`. Downloading the published HTTPS
APK produced the same SHA-256 as the verified build artifact.

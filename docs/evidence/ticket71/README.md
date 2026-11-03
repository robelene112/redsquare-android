# Issue 71: immediate Like feedback and receipt checks

## Delivered

The Like button no longer shows Material's oversized press ripple. It keeps
the icon-only accessible action and 48 dp touch target. The filled heart now
has a clear, interruptible scale pulse. The count changes immediately and
briefly pulses as a single readable value, without sliding two counts over one
another. Android's existing window motion scale applies to both animations,
including when system animations are disabled.

Each deliberate tap still submits its own signed Like. Receipt verification
continues asynchronously against the exact Like signature in the selected
backend's `RedSquareLike` archive. The UI does not wait for the archive or show
Like pending/success/failure status.

Implementation commit: `61187f51ee6b14addeb2bc8e2671567fe5e286af`.

## Verification

The controlled social suite passed on both supported test OS versions:

- Android 10 / API 29: [run 41636305374](https://github.com/robelene112/redsquare-android/actions/runs/41636305374), 6 tests passed.
- Android 17 / API 37: [run 41636310201](https://github.com/robelene112/redsquare-android/actions/runs/41636310201), 6 tests passed.

The Like test uses an actual Compose touch, captures the animation at 72 ms,
and asserts that `5 likes` is already visible. It then sends two additional
Likes, verifies three distinct signed transactions, exact-signature archive
readback, and the reconciled count. The `android-native-proof` artifact for
each run contains `social-evidence/like-feedback-during-tap.png` and its UI
hierarchy. Both captures show one count and a filled pink heart without the
oversized ripple or overlapping text.

Standards and specification reviews found no remaining actionable findings.
The only CI failure during iteration was a controlled peer fixture that did
not accept the valid exact-signature Like archive query on the second backend;
the fixture now validates its signature shape and routes that query through
the injected archive response. No Likes were sent to public nodes.

These are controlled emulator checks. They do not claim a live Like receipt on
`saito.io` or a physical Pixel 7 performance review.

## Signed beta

Signed review beta `0.1.83` is published as an in-place update at
<https://davidengelen.nl/redsquare/>.

- APK: <https://davidengelen.nl/redsquare/releases/83/redsquare.apk>
- SHA-256: `47c5f0582db9eed3451438d2ab3f639a656bd925950139ec06dfb1105f55fb23`
- Signing certificate SHA-256: `6ca1209bcbba661943f7b683d48b2f58493f13851c760c8d80d1521ac0ee79a2`
- Source commit: `61187f51ee6b14addeb2bc8e2671567fe5e286af`

The public `latest.json` and downloaded APK were checked against the signed
build manifest; all APK hashes match.

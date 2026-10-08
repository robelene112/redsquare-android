# Persistent wallet and review delivery verification

Ticket #3, 2026-10-08. Implementation/review base: `34c3704`. Candidate source:
`35dff32`; wallet changes reviewed through `7e24a03`. This is the wallet
foundation, not the live-feed/import milestone or completed app.

## Current result

Hosted [run 37849279965](https://github.com/robelene112/redsquare-android/actions/runs/37849279965)
passed the source build, nine core tests, lint and both Android runtime targets.
Downloaded evidence was inspected: each SDK passed eleven instrumentation tests,
the cold-process observer and actual signed 5-to-8 update. These include the
recovery scenarios, public-address UI,
cold-process continuity, update notice/browser intent and actual signed 5-to-8
APK update. Retained [Android 10 evidence](../evidence/ticket02/android10-runtime.txt)
and [Android 17 evidence](../evidence/ticket02/android17-runtime.txt) include the
public continuity results. These are emulator correctness checks, not Pixel
performance proof.

## Wallet boundary

Wallet creation uses real Saito-compatible identity generation, Android Keystore
AES-256-GCM and private no-backup ciphertext. No private wallet data is sent to
the website, nodes or GitHub. The UI receives an address only after the saved
identity is reopened and file/directory durability checks succeed.

A nonsecret initialization marker is synced before generating an unused key.
It remains until the saved identity is valid and its publication barrier is
durable. An interrupted valid save resumes the same identity. Incomplete data
may be discarded only while that marker proves the identity was unpublished.
Temporary I/O/provider failures preserve pending data. An established wallet
with missing/corrupt ciphertext remains fail closed and shows recovery guidance.

Controlled app checks cover failed first saving and Retry, partial first-write
state, valid saved ciphertext before publication, temporarily unreadable pending
data, failed directory sync after marker removal, missing/damaged established
storage and recovery after restoration. Crash-state fixtures model interruption;
they do not reproduce arbitrary physical power cuts. Cold-process checks use
actual force-stop/relaunch. Fixtures/reset commands are confined to `.dev` on
an isolated emulator; ordinary signed preview state is preserved.

## Review distribution

The [review page](https://davidengelen.nl/redsquare/) serves build notes and a
verified immutable APK. Public builds 5, 6 and 8 use application ID
`nl.davidengelen.redsquare` and signing certificate SHA-256
`6ca1209bcbba661943f7b683d48b2f58493f13851c760c8d80d1521ac0ee79a2`.
Versions 3, 4 and 7 were not published. Version gaps are valid.

The publisher verifies actual APK identity/version/signature and checksum before
writing only this app's static directory. HTTPS metadata and downloaded build 8
match the verified signing artifact. A 390-by-844 Chromium check verifies the
visible latest version, build notes and correct download link; see
[the retained screenshot](../evidence/ticket02/review-page.png).

The signed preview checks for a newer build on resume and links to the fixed
review page. Ordinary debug tests use a controlled loopback manifest and stub
the browser boundary. Public artifact downloads are explicitly enabled only
for hosted integration checks. See [review operations](../REVIEW_BUILDS.md)
for signing continuity and the final physical Pixel procedure tracked in #35.

## Regression and environment evidence

- Run 37840610980 exposed creation of a replacement identity after missing
  saved ciphertext; the recovery guard fixes this.
- Run 37843638678 reached the intended missing-update-notice assertion before
  the notice implementation. Local socket permission failures were setup
  failures, not evidence of that missing behavior.
- Run 37846931132 (`add4096`) reached the intended failed Retry assertion after
  first-save storage became writable again. Initialization recovery fixes this.
- AndroidX Test 1.7.0/JUnit 1.3.0/Espresso 3.7.0 fix the documented Android 17
  removed-InputManager reflection failure. Earlier failure was before test body.
- Run 37845030873's Android 17 cold observer was blocked by the exact System UI
  ANR, confirmed by retained XML/screenshot. The observer dismisses only that
  system dialog and records it; an app ANR still fails.
- Run 37846484687 passed the real signed 5-to-6 update on Android 10. Android 17
  later timed out reopening the wallet; missing/damaged-storage cases passed.
  That timeout lacked a screenshot, so its cause remains unknown. Future waits
  retain a public-screen failure image. Two-core/2 GiB KVM allocation is a test
  resource adjustment, not an established explanation of earlier failures.

Standards review: zero remaining findings through `7e24a03`. Spec review: zero
remaining findings through that commit. Physical Pixel install/update and launch
timings remain outstanding in #35, consistent with the owner's final app review.

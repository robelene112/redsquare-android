# Android version support assessment

Official Android and Rust documentation checked on 2026-10-08. No Android
application, selected dependency set or native Saito prototype exists yet.

- Current AndroidX libraries default to minimum API 23 (Android 6). Individual
  libraries can require a higher version. This makes Android 8/API 26 and
  Android 9/API 28 plausible candidates, not demonstrated supported platforms.
- Device authentication needs version-aware handling: the biometric/device-
  credential combinations described by Android are unsupported on Android 10
  and lower, with Keyguard-based credential handling needed for older platforms.
  An Android 10 minimum would not remove all authentication fallback work.
- Rust officially supports Android cross-compilation with the current LTS NDK.
  This establishes a toolchain route, not compatibility of Saito's particular
  dependencies or a completed Kotlin/native integration.
- Native library minimums must match the app's actual support floor. Merely
  lowering the manifest minimum cannot establish working compatibility.
- Supporting more versions adds correctness testing and version-aware fallback
  work. The exact effort depends on the selected libraries and integration path.
  No time estimate or population coverage percentage has been established.
- The accepted startup performance targets are for the owner's Pixel 7; wider
  OS compatibility does not imply those timings on every older phone.

Sources:

- [AndroidX minimum versions](https://developer.android.com/jetpack/androidx/versions)
- [Authentication compatibility](https://developer.android.com/identity/sign-in/biometric-auth)
- [Rust Android support](https://doc.rust-lang.org/rustc/platform-support/android.html)
- [Native SDK version requirements](https://developer.android.com/ndk/guides/sdk-versions)

The owner chose Android 10 and newer after considering broader compatibility,
and reported Android 17 on the primary Pixel 7. Verify minimum-version behavior
in the initial milestone. Android 8/9 support is outside the chosen release range.

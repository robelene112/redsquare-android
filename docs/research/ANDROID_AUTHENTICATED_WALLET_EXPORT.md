# Authenticated wallet export on Android

Primary-source research for #6, 2026-10-09. Source and public documentation only;
no implementation, device/account setup, private wallet reads or runtime
verification. Current source uses `ComponentActivity`, minSdk 29, compile/target
36, Kotlin 2.1.20, AGP 8.10.1 and Compose BOM 2026.06.01. Android 17/API 37
and the physical Pixel 7 remain separate runtime acceptance boundaries.
[Build](../../app/build.gradle.kts),
[host](../../app/src/main/kotlin/nl/davidengelen/redsquare/MainActivity.kt).

## Authentication options

| Choice | Compatibility and constraints |
| --- | --- |
| Platform `android.hardware.biometrics.BiometricPrompt` | Available from API 28 and callable directly from the current activity. Declare normal permission `USE_BIOMETRIC`; there is no runtime permission dialog for it. API 30+ has `setAllowedAuthenticators`; API 29 has `setDeviceCredentialAllowed(true)`. The latter must not be combined with a custom negative button. Use the non-crypto authenticate overload for an application-level export gate. |
| `androidx.biometric:biometric:1.1.0` | Latest stable in both the official release table and Google Maven metadata. Its constructors require `FragmentActivity` or `Fragment`; current `ComponentActivity` alone is insufficient. It handles prompt lifecycle and device compatibility, but introducing it means adding a fragment host. On API 29, `STRONG | DEVICE_CREDENTIAL` and credential-only masks are unsupported and throw at prompt construction; `WEAK | DEVICE_CREDENTIAL` is supported. |
| `KeyguardManager.createConfirmDeviceCredentialIntent` | Public credential-confirmation fallback available from API 21, deprecated from 29 but still callable. Requires secure-lock-screen support, returns null without a secure device lock, and succeeds only with system `RESULT_OK`. Use Activity Result registration rather than treating return to the app as success. |

Sources: [platform builder](https://developer.android.com/reference/android/hardware/biometrics/BiometricPrompt.Builder),
[platform prompt](https://developer.android.com/reference/android/hardware/biometrics/BiometricPrompt),
[AndroidX releases](https://developer.android.com/jetpack/androidx/releases/biometric),
[Google Maven versions](https://dl.google.com/dl/android/maven2/androidx/biometric/biometric/maven-metadata.xml),
[credential confirmation](https://developer.android.com/reference/android/app/KeyguardManager#createConfirmDeviceCredentialIntent(java.lang.CharSequence,java.lang.CharSequence)).

The 1.1.0 published AAR declares minSdk 14 and no elevated minCompileSdk or AGP
constraint. Its Java source introduces no Kotlin compiler/metadata dependency.
Its POM requests Fragment 1.2.5, Activity 1.1.0, AppCompat 1.2.0, Core 1.3.2
and Lifecycle 2.2.0. These are dependency requests, not a verified resolved
graph with this app. Existing newer Activity/Lifecycle versions can be selected
by Gradle; compile/runtime verification would still be needed.
[Published POM](https://dl.google.com/dl/android/maven2/androidx/biometric/biometric/1.1.0/biometric-1.1.0.pom),
[AAR](https://dl.google.com/dl/android/maven2/androidx/biometric/biometric/1.1.0/biometric-1.1.0.aar),
[exact 1.1.0 source JAR](https://dl.google.com/dl/android/maven2/androidx/biometric/biometric/1.1.0/biometric-1.1.0-sources.jar).

**Practical implementation option:** platform prompt preserves the current
host and requires no new library. API-gate its builders: API 30+ can explicitly
request the chosen biometric class plus device credential; API 29's legacy
credential flag does not express a Class 3-only combined mask. Do not claim
that changing the mask on Android 10 silently preserves a stronger policy.
The approved scope accepts supported biometrics or device PIN; this note does
not choose which biometric class to allow. If Class 3-only authentication is
required, an API 29 credential path must be designed separately and verified.
[Android 10 builder source](https://android.googlesource.com/platform/frameworks/base/+/android-10.0.0_r1/core/java/android/hardware/biometrics/BiometricPrompt.java),
[authenticator restrictions](https://developer.android.com/identity/sign-in/biometric-auth#declare-supported-authentication-types).

## No false authentication success

Check `isDeviceSecure()`, not `isKeyguardSecure()` or whether the screen is
currently unlocked: the latter checks can include a SIM lock or describe only
visibility. Availability/preflight is not authentication. No enrolled biometric
does not exclude PIN fallback. No secure lock, unavailable hardware, cancellation,
lockout, negative-button events and authentication errors authorize no export.
`onAuthenticationFailed` is a nonmatching attempt, not success; only the success
callback or a bound system credential `RESULT_OK` can grant the operation.
[Secure-device semantics](https://developer.android.com/reference/android/app/KeyguardManager#isDeviceSecure()),
[callback contract](https://developer.android.com/reference/android/hardware/biometrics/BiometricPrompt.AuthenticationCallback).

Platform cancellation uses `CancellationSignal`. Starting another prompt
cancels the prior client. AndroidX 1.1.0 instead retains its prompt across
configuration changes and requires recreating the callback early in the new
host, without launching a duplicate prompt. Either approach needs application
operation IDs to reject delayed callbacks from a canceled/superseded export.
For platform use with the current host, cancel/invalidate on destruction and
require a new explicit authentication rather than persisting an authenticated
Boolean. Authentication does not survive process death. Activity Result APIs
can redeliver results after recreation: register launchers unconditionally in
the same order, but accept results only for a still-valid operation. Retain
public operation context separately from secret JSON and authentication grants.
[Platform lifecycle](https://developer.android.com/reference/android/hardware/biometrics/BiometricPrompt#authenticate(android.os.CancellationSignal,java.util.concurrent.Executor,android.hardware.biometrics.BiometricPrompt.AuthenticationCallback)),
[Activity Results](https://developer.android.com/training/basics/intents/result).

Current encrypted storage's AES key is not user-authentication-bound. A prompt
therefore gates this application operation; it does not automatically make
all wallet decryption cryptographically contingent on authentication. Changing
that key would affect ordinary feed/signing behavior and is not required by
this research. Never add a `CryptoObject` expecting PIN fallback to work on
API 29: AndroidX's crypto authentication forbids that combination.
[Current store](../../app/src/main/kotlin/nl/davidengelen/redsquare/AndroidWalletStore.kt),
[crypto API](https://developer.android.com/reference/androidx/biometric/BiometricPrompt#authenticate(androidx.biometric.BiometricPrompt.PromptInfo,androidx.biometric.BiometricPrompt.CryptoObject)).

## Save a real document and prove its contents

Use SAF `ACTION_CREATE_DOCUMENT`, `CATEGORY_OPENABLE`, MIME
`application/json`, and a suggested `.json` name, or the existing Activity
library's `CreateDocument("application/json")` contract. The user-selected
document grants access without broad storage permissions. A returned URI only
creates/selects the destination; it does not mean any wallet bytes were saved.
Cancellation/null URI is no export. Name collisions create a separate document.
[SAF guide](https://developer.android.com/training/data-storage/shared/documents-files#create-file),
[contract](https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.CreateDocument).

Recommended operation requirements, derived from the approved safeguards:

1. Bind one export attempt to the outgoing active public key, wallet revision,
   operation nonce and, when applicable, the selected import candidate's exact
   fingerprint. Do not reuse an earlier export completion for another candidate.
2. Authenticate that attempt and generate its bounded UTF-8 backup from the
   current wallet only after success. A picker-before-authentication sequence
   avoids keeping a grant alive while the user browses destinations. Whichever
   sequence is implemented, check binding again immediately before writing.
3. Open a non-null provider stream/descriptor, write all intended bytes, flush
   and close successfully. Propagate write/close failures. Mode `w` is
   provider-defined and may not truncate; request a truncating mode when
   appropriate and verify exact length afterward. Do not assume every SAF
   descriptor is a seekable local file or can be universally `fsync`ed.
4. Reopen the returned URI for bounded readback through EOF. Require exact
   byte equality and successful `SaitoWallet.fromBackup()` validation with the
   intended public key. Recheck active operation, identity/revision and candidate
   binding before issuing its completion receipt.
   Missing/revoked access, timeout, corruption, an empty/partial file or a
   failed close/readback must never satisfy an import backup prerequisite.
5. Invalidate the attempt on cancellation, identity/candidate changes or
   supersession. After process loss, require a new authentication/verified
   completion; never infer success merely from an existing URI/file. Best-effort
   cleanup of an incomplete newly created document is separate from success.

These checks are application requirements, not guarantees made by SAF.
Provider writes may use pipes or remote storage; immediate readback establishes
returned contents, not durable cloud replication or future retention.
`ParcelFileDescriptor.checkError()` can detect remote errors for supporting
providers, but silently returns when that capability is absent.
[Provider modes/null/error behavior](https://developer.android.com/reference/android/content/ContentResolver),
[descriptor close/error contract](https://developer.android.com/reference/android/os/ParcelFileDescriptor),
[automatic descriptor closure](https://developer.android.com/reference/android/os/ParcelFileDescriptor.AutoCloseOutputStream).

The current native format preserves imported options, inserts the verified
public/private key pair, and limits backup input to **5 MiB**. Export JSON
contains private material and must remain off logs, issue evidence, previews,
SavedState and network diagnostics. Clearing temporary byte buffers helps but
does not erase immutable Strings already produced by `exportJson()`.
[Wallet format](../../core/src/main/kotlin/nl/davidengelen/redsquare/saito/SaitoWallet.kt).

## Meaningful emulator verification

On isolated API 29 and 37 test emulators, configure a known test-only screen
PIN and demonstrate the **real system challenge**: wrong PIN must not proceed;
correct PIN must lead to a real SAF save and independently verified compatible
readback. Test Back/cancellation at both boundaries, no secure lock, rotation,
process loss and provider open/write/close/read failures. UI Automator can
operate system surfaces outside the app. Stubbing an authentication result or
asserting only that the picker intent was sent does not verify these boundaries.
[Cross-app automation](https://developer.android.com/training/testing/other-components/ui-automator),
[AOSP test PIN commands](https://android.googlesource.com/platform/frameworks/base/+/android-10.0.0_r1/services/core/java/com/android/server/locksettings/LockSettingsShellCommand.java).

The shell's `locksettings verify` only checks emulator credential setup; it
does not prove that the app requires authentication. Emulator fingerprint
enrollment plus `finger touch` can exercise the biometric callback, but cannot
prove physical Pixel 7 sensor behavior. No PIN was configured, authentication
performed or export verified by this research.
[Emulator fingerprint simulation](https://developer.android.com/studio/run/emulator-console#fingerprint-simulation-and-validation).

# Real Android wallet export test harness

Primary-source research, 2026-10-09, for ticket #6. Read current app dependencies
and [authentication/export requirements](ANDROID_AUTHENTICATED_WALLET_EXPORT.md).
No dependency, app, test, device or credential changes; no emulator execution.
Recommendations below need verification on both existing images:
`android-29;default;x86_64` and `android-37.0;google_apis;x86_64`.
Only isolated development application `nl.davidengelen.redsquare.dev` is in
scope. Source/resource evidence is not a claim about those images' actual UI.
[Build](../../app/build.gradle.kts), [image selection](../../scripts/ci-android-device).

## Verified dependency choice

Use test-only `androidx.test.uiautomator:uiautomator:2.4.0`. Official release
table and Google Maven metadata agree this is the latest stable, released
2026-07-01. Its published AAR declares minSdk **23**, minCompileSdk **34**,
minAGP **8.1.1**, no core-library-desugaring requirement, and Kotlin module
metadata **2.1.0**. POM requests Kotlin stdlib **2.1.20**, Test core **1.7.0**,
JUnit **4.13.2**, annotation **1.8.1** and tracing **1.1.0**. These requirements
fit current minSdk29/compile36/AGP8.10.1/Kotlin2.1.20/JDK17 and Test runner1.7.0.
This is published compatibility evidence, not a resolved app build or runtime
pass. Existing graph already selects newer stdlib2.2.21.
[Release table](https://developer.android.com/jetpack/androidx/releases/test-uiautomator#2.4.0),
[Maven versions](https://dl.google.com/dl/android/maven2/androidx/test/uiautomator/uiautomator/maven-metadata.xml),
[POM](https://dl.google.com/dl/android/maven2/androidx/test/uiautomator/uiautomator/2.4.0/uiautomator-2.4.0.pom),
[inspected AAR](https://dl.google.com/dl/android/maven2/androidx/test/uiautomator/uiautomator/2.4.0/uiautomator-2.4.0.aar).

Required transitive `uiautomator-shell:2.4.0` redirects Android Gradle variants
to `uiautomator-shell-android:2.4.0`. That AAR has the same SDK/AGP/Kotlin
metadata bounds and requests Test core1.7.0/stdlib2.1.20. Its manifest includes
INTERNET for local Unix-socket shell communication; that dependency declaration
is not permission to send backups over a network. No incompatible transitive
requirement was found in these published artifacts. Version2.3.0 also fits
the SDK but is older; no downgrade is necessary for these constraints.
[Gradle variant metadata](https://dl.google.com/dl/android/maven2/androidx/test/uiautomator/uiautomator-shell/2.4.0/uiautomator-shell-2.4.0.module),
[Android shell AAR](https://dl.google.com/dl/android/maven2/androidx/test/uiautomator/uiautomator-shell-android/2.4.0/uiautomator-shell-android-2.4.0.aar).

Existing `UiDevice`, `By`, `Until` APIs remain in 2.4.0, so this harness does
not require adopting the new DSL. Obtain the device from the current
instrumentation. Use bounded waits for package/resource/state predicates,
re-query stale objects after transitions, then verify app outcomes. System UI
must run with its normal clock; a Compose semantics test or stubbed auth callback
cannot validate it. Avoid fixed coordinates and a global idle wait that assumes
all live animations or asynchronous work will stop.
[Cross-app testing](https://developer.android.com/training/testing/other-components/ui-automator),
[UiDevice API](https://developer.android.com/reference/androidx/test/uiautomator/UiDevice),
[exact sources](https://dl.google.com/dl/android/maven2/androidx/test/uiautomator/uiautomator/2.4.0/uiautomator-2.4.0-sources.jar).

## System PIN proof on each image

Use a known disposable **test PIN**, for example `246813`, never an operator,
physical phone or account credential. The following commands are documented
setup/check/cleanup candidates, **not executed by this research**:

```sh
adb -s "$test_serial" shell locksettings set-pin --user 0 246813
adb -s "$test_serial" shell locksettings verify --old 246813 --user 0
adb -s "$test_serial" shell locksettings clear --old 246813 --user 0
```

Require a known initially unsecured disposable emulator, explicit serial and
successful setup output. A previously configured test PIN requires `--old` for
changes; fail rather than guess unknown credentials. Cleanup uses that same test
PIN, preferably in a host `finally`/trap. Shell verification confirms setup,
**not application authentication**.
[Android 10 shell source](https://android.googlesource.com/platform/frameworks/base/+/android-10.0.0_r1/services/core/java/com/android/server/locksettings/LockSettingsShellCommand.java),
[current shell source](https://android.googlesource.com/platform/frameworks/base/+/main/services/core/java/com/android/server/locksettings/LockSettingsShellCommand.java).

Run the actual export action with no biometric enrolled and a secure test PIN.
API29 uses the legacy credential-allowed flag; API30+ uses allowed
authenticators. Depending on enrollment and image, credential UI may appear
directly or require choosing the prompt's PIN/credential fallback. Require an
identified system challenge, submit one wrong PIN, prove no success/backup
receipt, then the correct PIN and prove continuation. Do not force a particular
fallback button when the system already displays PIN entry. Keep wrong attempts
bounded to avoid lockout.
[Builder contract](https://developer.android.com/reference/android/hardware/biometrics/BiometricPrompt.Builder#setDeviceCredentialAllowed(boolean)).

Source-defined selector candidates, **not stable public resource contracts**:

| Surface | Candidate evidence |
| --- | --- |
| Android10 Settings credential confirmation | `com.android.settings:id/password_entry`; handler supports IME Next/Done and `next_button` where present. Normal layout need not contain a Next button. |
| Recent SystemUI BiometricPrompt credential view | `com.android.systemui:id/lockPassword`; binder accepts IME Done/Next or keyboard confirm key. Some configurations include `pin_pad`. |
| Android10 biometric prompt | SystemUI `button1`/`button2`; determine the visible label/role before selecting, never assume which is credential versus cancellation. |

Prefer package-qualified selectors and input focus. Use accessible text entry
when supported, otherwise inject real digit key events/tap the displayed system
keypad and submit its normal confirm action. Password nodes may redact text;
success evidence is the resulting operation, not reading PIN contents back.
Android37 image/resource matching still needs runtime observation: current
AOSP `main` is evidence of implementation structure, not an exact API37 image
commit. Bound diagnostic hierarchy collection to synthetic screens and avoid
retaining password-entry contents.
[Android10 credential handler](https://android.googlesource.com/platform/packages/apps/Settings/+/android-10.0.0_r1/src/com/android/settings/password/ConfirmLockPassword.java),
[normal layout](https://android.googlesource.com/platform/packages/apps/Settings/+/android-10.0.0_r1/res/layout/confirm_lock_password_normal.xml),
[recent credential binder](https://android.googlesource.com/platform/frameworks/base/+/main/packages/SystemUI/src/com/android/systemui/biometrics/ui/binder/CredentialPasswordViewBinder.kt),
[recent layout](https://android.googlesource.com/platform/frameworks/base/+/main/packages/SystemUI/res/layout/auth_credential_password_pin_content_view.xml),
[Android10 prompt](https://android.googlesource.com/platform/frameworks/base/+/android-10.0.0_r1/packages/SystemUI/res/layout/biometric_dialog.xml).

Exercise Back/cancel, no secure lock, recreation during authentication and
picker, and process loss. A process-death test cannot force-stop the app from
its own instrumentation and expect that runner to survive: use a separate host
ADB observer/controller or independently hosted UI test process, relaunch, then
assert a new authentication is required. Never turn an intercepted Activity
Result into a success for these system acceptance cases.

## Real picker and destination bytes

Operate actual SAF `ACTION_CREATE_DOCUMENT` for `application/json`, choose
Downloads or the development fixture root and a unique synthetic filename, then
Save. DocumentsUI can be `com.android.documentsui` or
`com.google.android.documentsui`; discover the resolved handler/package per
image. Android10 **and current** `SaveFragment` use `android:id/title` for name
and `android:id/button1` for Save. Scope these selectors to the picker window
and check button enabled; other windows also contain these IDs. Select a valid
directory/root before Save. Picker URI selection alone is no export proof.
[SAF contract](https://developer.android.com/training/data-storage/shared/documents-files#create-file),
[Android10 save fragment](https://android.googlesource.com/platform/packages/apps/DocumentsUI/+/android-10.0.0_r1/src/com/android/documentsui/picker/SaveFragment.java),
[current save fragment](https://android.googlesource.com/platform/packages/apps/DocumentsUI/+/main/src/com/android/documentsui/picker/SaveFragment.java).

Use a published synthetic wallet or the isolated emulator's throwaway `.dev`
wallet. Independently reopen the **actual returned URI** through ContentResolver
in the process holding its grant, read bounded bytes through EOF, compare exact
length/bytes with the intended backup and parse with `SaitoWallet.fromBackup()`
to compare public identity. Emit only test name/pass, byte count and public
identity or synthetic-vector digest. Assertions must not embed mismatched JSON
in failure messages. Do not `cat`, `pull`, tar, screenshot, attach or log private
backup contents. If the grant is not exposed to the test observer, select the
test provider and verify its independently stored bytes rather than guessing a
filesystem path. Broad storage permissions are unnecessary for SAF-granted URI
access. Delete fixture documents/reset the disposable emulator afterward.
[ContentResolver](https://developer.android.com/reference/android/content/ContentResolver),
[native backup validation](../../core/src/main/kotlin/nl/davidengelen/redsquare/saito/SaitoWallet.kt).

## Provider faults at the Android boundary

A development-only `DocumentsProvider`, preferably in a separate process, can
appear in the real picker: exported provider, `grantUriPermissions`, system-only
`MANAGE_DOCUMENTS` guard and `DOCUMENTS_PROVIDER` intent filter; create-capable
root/directory and supported MIME. Implement actual query/create/open methods,
never include the fixture provider in release. This tests real URI grants,
ContentResolver and descriptors instead of replacing the export callback.
[Provider guide](https://developer.android.com/guide/topics/providers/create-document-provider),
[provider API](https://developer.android.com/reference/android/provider/DocumentsProvider).

Useful deterministic modes are open failure; early closed write pipe/EPIPE;
successful write but truncated/corrupt persisted content; read-open failure;
and a reliable read pipe delivering bytes then `closeWithError`, so EOF/error
handling is exercised. Coordinate pipe faults explicitly rather than relying
on timing. Assert no completion receipt and a retryable error for every mode.
Regular descriptor close does not universally propagate remote persistence
errors. Reliable descriptors support `checkError`, but it can return with no
status yet, and ordinary providers may lack the channel. Therefore distinguish
observed write/read/error failures from a mocked stream-close exception; cover
the latter in a focused local seam test without claiming that proves a remote
SAF close failure. Independent readback catches dropped/partial content even
when local close succeeds.
[Reliable pipe/error API](https://developer.android.com/reference/android/os/ParcelFileDescriptor),
[actual checkError behavior](https://android.googlesource.com/platform/frameworks/base/+/main/core/java/android/os/ParcelFileDescriptor.java).

Finally verify operation/wallet/candidate binding and no stale authorization
after cancel, rotation, process restart or supersession, as required by the
existing export note. Nothing in this research establishes actual device
authentication, saved-document success, release isolation or a runtime pass;
those remain implementation evidence on both images and later the Pixel 7.

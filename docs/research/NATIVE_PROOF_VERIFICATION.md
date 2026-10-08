# Native compatibility proof verification

Ticket [#2](https://github.com/robelene112/redsquare-android/issues/2), checked
2026-10-08. This diagnostic proves native identity/signature and compatible
backup handling. It does not prove live feed access, posting, Chat or physical
Pixel 7 performance.

## Reproducible checks

```sh
scripts/android-build :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:lintDebug
scripts/android-emulator 29
scripts/android-emulator 37.0
```

The local Docker build with Bouncy Castle 1.86 passed nine independently
grounded core tests, debug/test/release APK assembly and Android lint. Evidence
is `.scratch/ticket01-bc186-build.log`; core results report zero failures/errors.
Reference tests cover pinned Saito public keys and empty/ASCII/Unicode/binary
signatures, tampering, scalar/signature range constraints, complete backup
options including unknown/null module fields, invalid pair/file cases and
fresh local identity creation. See [the contract and independent sources](NATIVE_SAITO_PROTOCOL.md).

Synthetic keys are compiled only into JVM tests and the separate instrumentation
APK. The user release excludes the diagnostic activity and fixture data. The
debug screen creates a temporary local identity, imports its backup and verifies
a signed message without network access or private material on screen.

## Toolchain

- Kotlin 2.1.20, AGP 8.10.1, Gradle 8.11.1 with distribution SHA-256.
- JDK 17, compile/target SDK 36, minimum SDK 29, build tools 35.0.0.
- Bouncy Castle 1.86 lightweight secp256k1/BLAKE3, Gson 2.12.1.
- Checksummed Google command-line tools build 15859902.
- Local emulator 37.2.12, Android 10/API 29 default x86_64 and
  Android 17/API 37.0 Google APIs x86_64 system images.

The hosted workflow uses the same Gradle wrapper, compiler/plugin/library
pins and build-tool levels with Temurin Java 17. Record actual Java patch and
emulator revisions from the retained run logs. Hosted runtime checks use KVM;
the VPS has no `/dev/kvm` and software-emulator boot was too slow for iteration.
The first hosted Docker attempt failed downloading SDK build tools. Native
hosted SDK provisioning subsequently passed after installing the required
headless runtime libraries. No production service was changed.

## Device verification

Passed on both Android 10 (SDK 29) and Android 17 (SDK 37) in
[hosted run 37834551583](https://github.com/robelene112/redsquare-android/actions/runs/37834551583),
testing source commit `6869690` and Bouncy Castle 1.86. Each runtime passed both
independent instrumentation tests (`OK (2 tests)`) and the visible random
identity/backup/signature diagnostic. Retained evidence includes SDK values,
instrumentation output, accessibility state and screenshots. Downloaded copy:
`.scratch/ci-evidence-37834551583/`. Durable diagnostic screenshots are under
`docs/evidence/ticket01/`.

That successful run used Temurin 17.0.20.1, emulator 37.2.12 build 16428233,
the checksummed command-line tools and pinned compiler/plugin/library versions
above. Core tests and all APK/lint tasks passed in the same run. The harness
bounds emulator data partitions to 2 GiB and frees unused native SDKs only on
the ephemeral hosted runner. Both reference tests and visible results are
required; application failure is not dismissed as a system boot dialog.

Earlier 1.81 instrumentation and interrupted software-emulator UI checks are
historical troubleshooting evidence, superseded by this successful 1.86 run.

No physical Pixel check has occurred. The owner's final device review remains
necessary for installation/update experience, performance and notification
lifecycle acceptance. This proof is not a distributed user review APK.

## Code review

Separate standards and specification reviewers found and verified fixes for
null backup-option preservation and independent Android fixtures. Subsequent
crypto security, harness and hosted setup changes passed Standards review.
Spec review found no remaining implementation defect; the previously pending
runtime checks above now pass. Final findings: Standards 0, Spec 0.

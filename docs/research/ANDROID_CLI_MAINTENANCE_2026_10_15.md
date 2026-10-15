# Android CLI maintenance, 2026-10-15

Read-only follow-up under the updated proactive-updates skill. Reused root's
session repository/skills synchronization. Only this document was written;
no fetch, pull, install, migration, build, device run or CI dispatch.

[Run 38761787929](../evidence/ticket40/sdk-image-attempt-android17/README.md)
failed preparing `x86_64-37.0_r06.zip`, before emulator/application tests, with
`Error reading Zip content from a SeekableByteChannel`. Its separate
`sdkmanager` deprecation warning does not establish the Zip failure's cause.
Root owns the unchanged fresh-runner retry. Neither corruption, a broken
published archive nor a required tool upgrade is proved.

## Configured versus observed versions

- [CI setup](../../scripts/ci-android-setup) and
  [local build Dockerfile](../../build-support/Dockerfile) checksum-pin
  `commandlinetools-linux-15859902_latest.zip`. Google's
  [repository metadata](https://dl.google.com/android/repository/repository2-3.xml)
  identifies it as stable Command-line Tools 22.0. The same metadata currently
  lists stable latest 23.0, archive 16111833. These are source/availability facts,
  not a runtime version measurement of the failed runner or existing image.
- CI installs platform 36, build-tools 35.0.0 and an unversioned stable emulator.
  [Device setup](../../scripts/ci-android-device) then resolves Android 29 default
  or Android 37.0 Google APIs images without pinning package revisions. The
  failed log identifies the selected image archive as r06.
- [Workflow](../../.github/workflows/android-proof.yml) uses Ubuntu 24.04 and
  Temurin 17, adds the pinned tools directory to subsequent PATH, and prints
  Java/emulator versions. The retained failure excerpt does not establish the
  resolved `sdkmanager` executable/version. Local emulator Dockerfile inherits
  the mutable `redsquare-android-build:agp8.10.1` tag; no existing image digest or
  installed package revisions were inspected here.

## Official replacement status

Google now explicitly [deprecates sdkmanager](https://developer.android.com/tools/sdkmanager)
in favor of `android sdk`. Android CLI is a released 1.0 tool: current
[release notes](https://developer.android.com/tools/agents/android-cli/release-notes)
list 1.0.16500706 (October 2026), matching Google's
[stable Debian metadata](https://dl.google.com/android/cli/latest/debian/dists/stable/main/binary-amd64/Packages).
This is stable distribution evidence, not a claim of verified project compatibility.
September releases document SDK locking/package validation and improved exit codes.

[SDK commands](https://developer.android.com/tools/agents/android-cli/commands/sdk)
default to stable packages, accept legacy semicolon paths and
[explicit package versions](https://developer.android.com/tools/agents/android-cli/commands/sdk_install).
However, [AVD creation](https://developer.android.com/tools/agents/android-cli/commands/emulator_create)
uses device profiles; existing scripts specify exact images, AVD directories,
partition size, GPU/memory/CPU flags and boot checks. Compatibility requires a
bounded comparison, not a command-name substitution.

## Recommendation

Keep the active app verification unchanged. For later maintenance, first retain
resolved executable paths, tools `source.properties`, image/package revisions
and actual emulator/Java versions. Evaluate checksum/version-pinned stable CLI
installation and SDK package management separately, preserving existing AVD
and instrumentation behavior until tested. Confirm installed licenses and
configuration compatibility; avoid automatic `latest` self-updates in CI.
Neither CLI adoption nor Command-line Tools 23.0 is a demonstrated Zip repair.

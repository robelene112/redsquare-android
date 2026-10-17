# Red Square Android

Native Kotlin Android client for Saito's Red Square. First-release behavior is
defined in [specification #1](https://github.com/robelene112/redsquare-android/issues/1).
Implementation is sequential across its 35 dependency-linked tickets.

## Build

Docker is the only required host tool for the reproducible build. The build
image includes JDK 17, Gradle 8.11.1 and Android SDK 36; sources target Android 10
and newer. Resource limits keep the build bounded on this shared VPS.

```sh
scripts/android-build :core:test :app:assembleDebug :app:lintDebug
```

The first build installs the SDK in an isolated Docker image and accepts its
SDK licenses. Gradle caches and build artifacts stay outside Git. The app opens the native
Red Square feed and uses one local wallet across Red Square and Chat. The debug
package is separate from signed phone-review builds; synthetic reference keys
are confined to tests.

Signed review updates and build notes are available at
[the review page](https://davidengelen.nl/redsquare/). Install updates over the
existing app to retain its wallet and local work. See
[review-build operations](docs/REVIEW_BUILDS.md) for signing and publication, and
[the current handoff](docs/handoffs/IMPLEMENTATION.md) for accepted features and
outstanding integrations.

The core wallet uses native JVM cryptography. The Compose UI is native Android;
no remote browser page owns wallet keys. Imported wallet options are protected
backup data, not authority for backend endpoints.

Independent reference tests run in a separate instrumentation APK. To check
Android 10 or Android 17 without a connected phone:

```sh
scripts/android-build :app:assembleDebugAndroidTest
scripts/android-emulator 29
scripts/android-emulator 37.0
```

Emulator images and synthetic device state remain in ignored local storage.
Hardware acceleration is used when `/dev/kvm` is available; software emulation
on this VPS is slow and does not establish Pixel 7 performance. Device evidence
is written to `.scratch/device-evidence/`.

- [Start a session](docs/STARTING_PROMPTS.md)
- [Development workflow and Wayfinder](docs/WORKFLOW.md)
- [Agreed scope and open decisions](docs/PROJECT_CONTEXT.md)
- [Environment and Saito references](docs/OPERATIONS.md)
- [Native protocol reference](docs/research/NATIVE_SAITO_PROTOCOL.md)
- [Current implementation handoff](docs/handoffs/IMPLEMENTATION.md)

Matt Pocock's 27 skills are vendored in `.agents/skills/`, pinned by
`.agents/skills.lock.json`. Their MIT license is in `.agents/UPSTREAM_LICENSE`.
Application licensing has not been decided.

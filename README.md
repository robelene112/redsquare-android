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
SDK licenses. Gradle caches and build artifacts stay outside Git. The debug
variant currently launches a local compatibility diagnostic; it is not a live
feed or a release preview. Synthetic reference keys are confined to tests.

The core wallet uses native JVM cryptography. The Compose UI is native Android;
no remote browser page owns wallet keys. Imported wallet options are protected
backup data, not authority for backend endpoints.

- [Start a session](docs/STARTING_PROMPTS.md)
- [Development workflow and Wayfinder](docs/WORKFLOW.md)
- [Agreed scope and open decisions](docs/PROJECT_CONTEXT.md)
- [Environment and Saito references](docs/OPERATIONS.md)
- [Native protocol reference](docs/research/NATIVE_SAITO_PROTOCOL.md)
- [Current implementation handoff](docs/handoffs/IMPLEMENTATION.md)

Matt Pocock's 27 skills are vendored in `.agents/skills/`, pinned by
`.agents/skills.lock.json`. Their MIT license is in `.agents/UPSTREAM_LICENSE`.
Application licensing has not been decided.

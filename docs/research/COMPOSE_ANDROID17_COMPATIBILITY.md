# Compose compatibility and Android 17 layout failure

Read-only primary-source research, 2026-10-08 UTC, for ticket #4. Inspected the
recorded Android 17 failure, current dependency declarations, official release
notes and Google Maven artifacts/sources. No implementation, dependency,
test, service or node changes; no application build/run performed by this
research agent.

## Decision

**BOM `2026.06.01`, mapping Compose UI/runtime/foundation/tests to `1.11.4`, is
the newest inspected stable BOM compatible with the existing compileSdk 36,
AGP 8.10.1, Kotlin/compiler plugin 2.1.20 and minSdk 29.** It maps Material3 to
1.4.0. This is a verified dependency choice, **not a confirmed fix for the
recorded crash**. Use the same BOM for application and instrumentation tests.
[published BOM](https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/2026.06.01/compose-bom-2026.06.01.pom),
[BOM guidance](https://developer.android.com/develop/ui/compose/bom).

The current latest published stable BOM is `2026.09.00`, mapping core Compose
to 1.12.1. That UI AAR requires compileSdk 37 and AGP 9.1.0; it does not fit
this project's fixed toolchain. The official compiler setup also documents
the compileSdk 37/AGP 9 transition beginning with Compose 1.12. No platform
migration is needed to select 1.11.4.
[BOM metadata](https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/maven-metadata.xml),
[latest BOM](https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/2026.09.00/compose-bom-2026.09.00.pom),
[UI 1.12.1 AAR](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-android/1.12.1/ui-android-1.12.1.aar),
[compiler/toolchain guidance](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler#compilesdk-and-android-gradle-plugin-compatibility).

## Artifact compatibility evidence

These are the actual downloaded AAR metadata/manifest values, rather than
guessed requirements from release names:

| Artifact | minCompileSdk | Minimum AGP | minSdk |
| --- | --- | --- | --- |
| UI 1.11.4 | 35 | 8.6.0 | 23 |
| UI test JUnit4 1.11.4 | 35 | 8.6.0 | 23 |
| Foundation 1.11.4 | 35 | 8.6.0 | 23 |
| Runtime 1.11.4 | 34 | 8.1.1 | 23 |
| Material3 1.4.0 | 35 | 8.6.0 | 21 |
| Existing activity-compose 1.10.1 | 35 | 1.0.0 | 21 |

Primary artifact links:
[UI](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-android/1.11.4/ui-android-1.11.4.aar),
[test](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-test-junit4-android/1.11.4/ui-test-junit4-android-1.11.4.aar),
[foundation](https://dl.google.com/dl/android/maven2/androidx/compose/foundation/foundation-android/1.11.4/foundation-android-1.11.4.aar),
[runtime](https://dl.google.com/dl/android/maven2/androidx/compose/runtime/runtime-android/1.11.4/runtime-android-1.11.4.aar),
[Material3](https://dl.google.com/dl/android/maven2/androidx/compose/material3/material3-android/1.4.0/material3-android-1.4.0.aar),
[activity](https://dl.google.com/dl/android/maven2/androidx/activity/activity-compose/1.10.1/activity-compose-1.10.1.aar).

UI and test POMs declare Kotlin stdlib **2.1.20**. Independently inspected
`MeasureAndLayoutDelegate.class` in the UI AAR: Kotlin metadata `mv=[2,1,0]`.
The existing Kotlin 2.1.20 compiler/plugin is compatible with that metadata;
the Compose compiler plugin version continues to match Kotlin, independently
of the library BOM. UI's published graph additionally requests Lifecycle
runtime-compose/viewmodel **2.9.2**, SavedState Compose 1.3.2 and Window 1.5.0.
Their inspected AARs require at most compileSdk 35/AGP 8.6.0 and minSdk 23.
These are published requests, not the final resolved versions. Existing
activity-compose 1.10.1 exceeds the published Compose test/Material3 activity
minimums.
[UI POM](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-android/1.11.4/ui-android-1.11.4.pom),
[test POM](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-test-junit4-android/1.11.4/ui-test-junit4-android-1.11.4.pom),
[test-core POM](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-test-android/1.11.4/ui-test-android-1.11.4.pom),
[Lifecycle AAR](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-runtime-compose-android/2.9.2/lifecycle-runtime-compose-android-2.9.2.aar).

**Actual local graph:** the `scripts/android-build` report
`.scratch/compose-dependencies.txt` resolves the Lifecycle family to **2.9.4**,
including runtime, runtime-ktx, runtime-compose, viewmodel, common and process;
the graph shows same-family constraints. The direct 2.8.7 runtime-ktx request
is upgraded to 2.9.4. Aligning that declaration with **2.9.4**, rather than the
UI POM's 2.9.2 request, records what the application actually uses. Independently
inspected 2.9.4 runtime/runtime-ktx/viewmodel AARs require compileSdk 34,
AGP 8.1.1 and minSdk 21; runtime-compose requires compileSdk 35, AGP 8.6.0 and
minSdk 21. All fit this project's toolchain.
[runtime AAR](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-runtime-android/2.9.4/lifecycle-runtime-android-2.9.4.aar),
[runtime-ktx AAR](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-runtime-ktx-android/2.9.4/lifecycle-runtime-ktx-android-2.9.4.aar),
[runtime-compose AAR](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-runtime-compose-android/2.9.4/lifecycle-runtime-compose-android-2.9.4.aar),
[viewmodel AAR](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-viewmodel-android/2.9.4/lifecycle-viewmodel-android-2.9.4.aar).

The same graph resolves Kotlin stdlib to **2.2.21**, requested by OkHttp's
selected Android variant; Compose's 2.1.20 and other lower requests are upgraded.
This does not change the Kotlin/compiler plugin version, which remains 2.1.20.
See [transport dependency evidence](NATIVE_TRANSPORT_DEPENDENCY.md). The parent
agent reports the local upgraded Compose application/test build passed; device
verification of the final test factory migration remains separate. No resolved
dependency observation establishes a cause or fix for the layout crash.

Current Runner 1.7.0, AndroidX test JUnit 1.3.0 and Espresso 3.7.0 exceed the
Compose test POM's declared versions (core 1.5.0, monitor 1.6.1, JUnit 1.1.5,
Espresso 3.5.0). Published constraints do not demand downgrades or newer test
libraries. This is graph compatibility evidence, not proof of all binary or
device behavior; Gradle resolution, AAR checks and existing tests still decide
the actual application combination.

Provenance: UI AAR SHA-256
`893c3b0a4f90631bbd8cc053a0271e51c525b427b900cc10c6f1a4b3c89e3041`;
BOM POM SHA-256
`271a09ce8d1cd55d259074fc73c3e4baef9d7d399ed49165b7c5e79782bbd819`.

## What the crash proves

The recorded `OwnerLiveFeedTest` trace at
`.scratch/feed-final-full-evidence/android-native-proof/.scratch/device-evidence/api-37.0/live-owner-feed.txt`
shows `AndroidComposeView.dispatchDraw` invoking `measureAndLayout`, whose
delegate rejects an already-active measurement pass. The published **1.8.0**
source matches the trace's `dispatchDraw:1880`; the delegate's guard checks
`!duringMeasureLayout` and clears the flag in `finally`.

Published **1.11.4** source retains that guard and the same dispatchDraw call.
It has other layout/test changes, including exception handling, but no inspected
release note or accessible official issue established a fix for this exact
Android 17/live-read failure. The stack identifies the rejected reentry, not
the first pass's cause, app state transition, test scheduling trigger or a
platform-specific regression.
[1.8.0 sources](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-android/1.8.0/ui-android-1.8.0-sources.jar),
[1.11.4 sources](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-android/1.11.4/ui-android-1.11.4-sources.jar),
[official UI release notes](https://developer.android.com/jetpack/androidx/releases/compose-ui).

A first-party September 2026
[AndroidX fix](https://android.googlesource.com/platform/frameworks/support/+/076731b44ad19089722528bdff3b5e008329d35c)
uses this same exception text, but concerns **Wear Material3 status-bar
suppression in dialogs**, with different APIs/configurations. It is evidence
the guard has multiple possible triggers, not a matching fix for this phone
app. Searches of official issue/source results did not establish an applicable
issue; the linked test-dispatcher issue
[455601592](https://issuetracker.google.com/issues/455601592) could not be read
through the available public browser tool. Mozilla reports are external-app
observations and were not used as evidence of this app's cause.

## Test-clock evidence and a useful distinction

Official test synchronization guidance recommends `waitUntil` for external
data loading and Android measure/draw. Therefore using it around an external
feed is not itself documented misuse. Semantic queries implicitly synchronize;
the test clock advances Compose work, while Android also performs traversals.
Published 1.8.0 test source supplies an `onPerformTraversals` callback that
manually calls each root's `measureAndLayoutForTest`, and its `waitUntil` loop
advances frames then yields wall-clock time to Android. The corresponding
1.11.4 source retains both mechanisms.
[synchronization guidance](https://developer.android.com/develop/ui/compose/testing/synchronization),
[1.8.0 test sources](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-test-android/1.8.0/ui-test-android-1.8.0-sources.jar),
[1.11.4 test sources](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-test-android/1.11.4/ui-test-android-1.11.4-sources.jar).

Compose 1.11 adds **v2** test APIs using StandardTestDispatcher rather than
eager UnconfinedTestDispatcher. Legacy rules remain available with their older
scheduling behavior; changing only the BOM does not automatically migrate
`createEmptyComposeRule` to v2 or prove this reentry disappears. The documented
v2 migration changes explicit coroutine scheduling/synchronization requirements.
[v2 guidance](https://developer.android.com/develop/ui/compose/testing/migrate-v2),
[release notes](https://developer.android.com/jetpack/androidx/releases/compose-ui#1.11.0-alpha03).

A separate normal launch observed through UI Automator/ADB can test the actual
production scheduler independently of a Compose test rule. UI Automator is
documented for black-box device/UI testing, including application launches.
Keep the controlled Compose tests and crash detection: a passing external
observer establishes that launch/read path, but does not retroactively explain
or erase the failed instrumented path. A test-clock interaction is a plausible
**hypothesis**, pending reproduction/comparison, not the established cause.
[UI Automator guidance](https://developer.android.com/training/testing/other-components/ui-automator).

### Exact 1.11.4 empty-rule migration

The published
[JUnit4 sources](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-test-junit4-android/1.11.4/ui-test-junit4-android-1.11.4-sources.jar),
`androidMain/.../junit4/v2/AndroidComposeTestRule.android.kt:132–169`, define
`createEmptyComposeRule(effectContext = EmptyCoroutineContext)` without an
experimental opt-in annotation. It returns the existing
`androidx.compose.ui.test.junit4.ComposeTestRule`; shared helper/type imports
remain valid. Only the factory import needs the `.v2` package. The rule provides
no host or `setContent`; its documented use is launching an Activity after
dependency injection, so the current manual `ActivityScenario.launch(...).use`
pattern is compatible.

Keep default clock auto-advance and bounded `waitUntil` for current external
feed/wallet conditions. In 1.11.4, `waitUntil` runs queued scheduler tasks before
its first condition evaluation when using StandardTestDispatcher, then advances
frames and yields to Android/external work. Existing semantic polling already
provides this synchronization. Immediate assertions on raw asynchronous state
may need `waitForIdle`/`runOnIdle`; they should not rely on effects completing
inline. No general additional sleep or clock manipulation is supported by this
inspection. New `ComposeUiTestConfig` examples in current documentation require
1.13; they must not be copied into this 1.11.4 migration. The factory change
retains manual test traversals and is not established as a reentrancy-crash fix.
[migration guidance](https://developer.android.com/develop/ui/compose/testing/migrate-v2),
[1.11.4 waitUntil implementation](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-test-android/1.11.4/ui-test-android-1.11.4-sources.jar).

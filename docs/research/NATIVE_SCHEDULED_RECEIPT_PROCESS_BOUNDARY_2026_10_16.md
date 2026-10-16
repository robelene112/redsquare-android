# Native scheduled receipt across process death, 2026-10-16

Source-only proposal for open #32. No implementation, build, dispatch or runtime
feasibility result is established. The current malformed-response scheduler slice
and its separate verifier-timeout observation precede this larger step.

[ControlledFeedPeer](../../app/src/androidTest/kotlin/nl/davidengelen/redsquare/ControlledFeedPeer.kt)
uses core signing/envelopes, MockWebServer, OkHttp/Okio, JUnit assertions and JDK
classes, with no Android imports. It already binds host address127.0.0.1 and owns
the existing authenticated binary socket/Archive implementation. Reimplementing
that protocol in Python would create an unnecessary second oracle.

Recommendation: move that one file unchanged into a shared test-only directory
such as test-fixtures/kotlin, compiled by both app.androidTest and existing core.test
source set. Keep its package/internal visibility. Add a small host main under
core/src/test, and MockWebServer5.4.0 to core.testImplementation; JUnit4.13.2 and
core's Gson/BouncyCastle dependencies already exist. See
[core](../../core/build.gradle.kts), [app](../../app/build.gradle.kts) and
[settings](../../settings.gradle.kts). No fixture belongs in core.main or the
production APK.

This is smaller than a custom core hostPeer source set, which requires its own
compilation/runtime configurations. A new test-support module offers a packaged
artifact but adds project/dependency wiring and would require exposing today's
internal peer across module boundaries. Neither is needed for one host executable.
Shared source avoids both protocol copies and a new public fixture API.

Proposed build hook: a core JavaExec task depending on testClasses, with
sourceSets.test.runtimeClasspath and an explicit JDK17 launcher. That classpath
includes test output, core output, Kotlin, crypto/JSON, JUnit and MockWebServer's
transitives. [JavaExec](https://docs.gradle.org/current/dsl/org.gradle.api.tasks.JavaExec.html)
runs a separate JVM; [source sets](https://docs.gradle.org/current/userguide/java_testing.html#sec:configuring_java_integration_tests)
define classpaths. Existing [CI](../../.github/workflows/android-proof.yml) already
runs core:test before device scopes. The eventual host harness must supervise the
JVM/Gradle child, bound startup, retain stderr and terminate its owned process.
Exact Kotlin/Android shared-source wiring still needs compilation verification.

The host main should accept only public fixtures and expose ready metadata plus
a bounded unavailable/available control, outside target instrumentation. Preserve
native-signing versus independent web-fixture provenance. Use
`adb reverse --no-rebind tcp:P tcp:P`, retaining the app's strict
ws://127.0.0.1:P/wsopen fixture endpoint. The [ADB manual](https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/docs/user/adb.1.md)
documents reverse sockets; verify the owned mapping and remove only it. The peer
remains loopback-bound, not a public service.

Existing [social recovery](../../scripts/social-push-process-check) deliberately
restarts instrumentation and therefore proves assisted recovery. For autonomy,
publish the ready checkpoint while setup instrumentation waits, verify the exact
target PID alive, then kill it and use
[assert_process_gone](../../scripts/process_check_utils.py). Do not assume a completed
instrumentation invocation leaves its target alive. Between death and observation,
start no target instrumentation/Activity, force-stop or forced JobScheduler run.
Require a different PID, post-death peer reads and the exact package/tag's actual
system alert before any tap. Preserve actual timeout/failure outcomes.

Use existing device-only snapshot and independent-restorer patterns. Only after
the autonomous observation window may cleanup stop the target, cancel/drain owned
receipt work and restore files/full wallet/backend, preserving primary failures.
Never pull encrypted originals. A restarted instrumentation test proves less than
autonomous OS scheduling; either result remains separate from real FCM, locked
device, Doze, physical Pixel and complete #32 acceptance.

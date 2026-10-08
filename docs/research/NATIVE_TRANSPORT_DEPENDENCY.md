# Native WebSocket dependency

Researched 2026-10-08 for ticket #4. This is dependency preparation, not proof
of a connected Saito client. No application dependencies or production services
were changed.

## Recommendation

Use `com.squareup.okhttp3:okhttp:5.4.0` through Gradle in the Android transport
boundary, without Retrofit, logging, DNS-over-HTTPS, compression add-ons or
`okhttp-coroutines`. It supplies asynchronous binary WebSockets and can also
serve the app's later HTTPS needs. Keep Saito framing and session state in the
shared foundation behind a small app-owned transport interface. This is an
engineering recommendation based on the evidence below, subject to the first
real project build and Android 10/17 transport checks.

OkHttp's original [Square repository](https://github.com/square/okhttp) now
redirects to [lysine-dev/okhttp](https://github.com/lysine-dev/okhttp). The official
[release history](https://lysine.dev/okhttp/changelogs/changelog/) lists 5.5.0
dated 2026-08-16 and 5.4.0 dated 2026-06-08. **5.4.0 is the newest Android
artifact compatible with the project's current compile SDK 36.** The actual
published 5.5.0 AAR requires compile SDK 37. Do not mistake runtime minimum SDK
for the build requirement or suppress the AAR metadata check.

## Compatibility evidence

The official [requirements](https://github.com/lysine-dev/okhttp#requirements)
are Android API 21+ and Java 8+. Android 10/API 29 and the project's Java 17
therefore satisfy the runtime baseline. Android initialization uses AndroidX
Startup; leave its initializer enabled, or follow the documented explicit
`OkHttp.initialize(applicationContext)` path if deliberately removing it.

Downloaded these upstream-published AARs directly from Maven Central and
inspected `META-INF/com/android/build/gradle/aar-metadata.properties` in memory:

| Artifact | minCompileSdk | min AGP in AAR | SHA-256 of inspected AAR |
| --- | --- | --- | --- |
| [5.4.0](https://repo.maven.apache.org/maven2/com/squareup/okhttp3/okhttp-android/5.4.0/okhttp-android-5.4.0.aar) | 36 | 1.0.0 | `0e9cbf6c7c373a5f0b0cde7f773185eb69b485faa19f76bcfab3310badbda901` |
| [5.5.0](https://repo.maven.apache.org/maven2/com/squareup/okhttp3/okhttp-android/5.5.0/okhttp-android-5.5.0.aar) | 37 | 1.0.0 | `6c7fd12f092e64ca2eae0b8a8023900c0d7ffec57888cf8abc8085c6f42e1dcc` |

Both declare no core-library desugaring requirement. These are artifact
observations, not a successful AGP 8.10.1 app assembly.

The [5.4.0 build catalog](https://github.com/lysine-dev/okhttp/blob/parent-5.4.0/gradle/libs.versions.toml)
uses compiler 2.2.21 but deliberately targets Kotlin core compatibility 2.1.21.
Its [compiler convention](https://github.com/lysine-dev/okhttp/blob/parent-5.4.0/build-logic/src/main/kotlin/okhttp.jvm-conventions.gradle.kts)
applies that compatibility setting and Java 8 bytecode. Independently inspected
`okhttp3.WebSocket` with Java 17 `javap`: class major version 52 and
`kotlin.Metadata.mv=[2,1,0]`. Thus compiler version alone is not evidence that
the existing Kotlin 2.1.20 compiler must be upgraded.

However, published [Gradle Android metadata](https://repo.maven.apache.org/maven2/com/squareup/okhttp3/okhttp-android/5.4.0/okhttp-android-5.4.0.module)
also declares Kotlin stdlib 2.2.21; Gradle can select that version rather than
the 2.1.21 shown by its POM. It brings Okio 3.17.0; Android runtime dependencies
also include Annotation 1.10.0 and Startup 1.2.0. Verify the resolved dependency
graph, compilation, lint and runtime. Kotlin's [binary compatibility policy](https://kotlinlang.org/docs/kotlin-evolution-principles.html#evolving-the-binary-format)
describes next-language forward compatibility as preferred, not guaranteed.
Do not bypass metadata checks to force a build.

Do not substitute the 5.5.0 JVM JAR to evade its Android AAR requirement:
the [JVM platform registry](https://github.com/lysine-dev/okhttp/blob/parent-5.5.0/okhttp/src/jvmMain/kotlin/okhttp3/internal/platform/PlatformRegistry.kt)
reports `isAndroid=false`, while the separate
[Android registry](https://github.com/lysine-dev/okhttp/blob/parent-5.5.0/okhttp/src/androidMain/kotlin/okhttp3/internal/platform/PlatformRegistry.kt)
selects Android TLS integration. Gradle's normal Android variant selection is
the intended path.

## Security and alternatives

The official [security policy](https://lysine.dev/okhttp/security/security/)
supports 5.x and 4.x, with 4.x support ending 2028-08-31. Therefore 4.12.0 is a
supported fallback, not an abandoned release, but prefer the newer compatible
5.x line. The [4.x history](https://lysine.dev/okhttp/changelogs/changelog_4x/)
documents the CVE-2021-0341 hostname-verifier fix in 4.9.2 and the
CVE-2022-24329 dependency-locking mitigation in 4.11.0. Avoid older versions.

The 5.5.0 notes also fix rejection of IP hostnames that fail canonicalization,
HTTP request timeout flushing and proxy-selector failures. These fixes are not
in the recommended 5.4.0 pin. For Saito, retain the two fixed DNS hosts and
disable redirects; do not accept arbitrary IP URLs or custom hostname/trust
verifiers. That limits exposure to the described IP-host path, but is not a
claim that 5.4.0 contains every later fix. Recheck the pin before public release.
The [maintainer advisory page](https://github.com/lysine-dev/okhttp/security/advisories)
showed no published advisories during this inspection; that is not proof of
absence of vulnerabilities.

[Java-WebSocket 1.6.0](https://github.com/TooTallNate/Java-WebSocket/releases/tag/v1.6.0)
is a smaller standalone alternative. Its
[README](https://github.com/TooTallNate/Java-WebSocket) documents Java 8+, WSS
and an SLF4J dependency, but no explicit Android minimum/support matrix.
Its [client source](https://github.com/TooTallNate/Java-WebSocket/blob/v1.6.0/src/main/java/org/java_websocket/client/WebSocketClient.java)
supports binary bytes and sets HTTPS endpoint identification on TLS sockets.
The maintainer's [CVE-2020-11050 advisory](https://github.com/TooTallNate/Java-WebSocket/security/advisories/GHSA-gw55-jm4h-x339)
marks <=1.4.1 affected and 1.5.0 patched. It is viable if OkHttp's actual build
fails, but offers less explicit Android integration and no shared HTTP client.
This comparison did not measure APK size or performance.

## Transport obligations

OkHttp's [binary send API](https://github.com/lysine-dev/okhttp/blob/parent-5.4.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/WebSocket.kt)
accepts `ByteString`; `send` returning true only means enqueueing. Its outgoing
queue limit is 16 MiB, and false must be handled. Close/cancel resources and
serialize app-level frame processing. Bound parser input and archive results,
correlate request indexes within one authenticated connection, and invalidate
callbacks on reconnect/backend changes. A WebSocket open callback does not
complete Saito's signed nonce handshake. See the
[verified Saito wire contract](NATIVE_SAITO_PROTOCOL.md).

Remaining evidence: resolve and build the chosen dependency, test controlled
binary/TLS failures and reconnect behavior on both supported SDK baselines,
then perform separately identified live reads against the approved backend.
This research opened no Saito connection and read no wallet material.

## Implementation follow-up

Ticket #4 declares 5.4.0 normally in Gradle. The actual debug graph selects
`okhttp-android:5.4.0`, Okio 3.17.0 and Kotlin stdlib 2.2.21. Source assembly
(debug, test and release) and lint passed locally and in hosted run 37853290748
at 7837753 without metadata suppression or compiler/SDK upgrades. That run's
Android feed UI failed its visible-post wait, so it does not establish connected
transport acceptance. Runtime evidence remains tracked in
[NATIVE_FEED_VERIFICATION.md](NATIVE_FEED_VERIFICATION.md).

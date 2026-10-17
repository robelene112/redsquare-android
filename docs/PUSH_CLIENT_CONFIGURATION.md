# Android push client configuration

Part of open[#32](https://github.com/robelene112/redsquare-android/issues/32).
The client source integrates Firebase Messaging26.0.0 using the supported
[manual initialization](research/FCM_MANUAL_INITIALIZATION_2026_10_17.md) path.
It does not require the Google-services Gradle plugin or checked-in JSON.
Actual Firebase registration and cloud delivery remain unverified. The Android
gateway adapter has controlled Android10/17 acceptance under#42, with synthetic
FIDs and an owned HTTP gateway boundary.

## Build options

Supply public client options through an external Gradle properties file or build
properties. Every option is required when configuring a variant; all absent means
unconfigured. Do not use server credentials, service-account JSON or wallet keys.
The debug and release Android apps must be registered with their respective
package names in the authorized Firebase project. The declared Package value is
an explicit assertion of that association, not an online registration check.

| Property suffix | Value |
| --- | --- |
| Package | Registered Android package, `nl.davidengelen.redsquare.dev` or `nl.davidengelen.redsquare` |
| FirebaseAppId | Firebase Android app ID, `mobilesdk_app_id` |
| FirebaseApiKey | Public Firebase Android client API key |
| FirebaseProjectId | Firebase project ID |
| FirebaseSenderId | Numeric Firebase project number |
| ResolverUrl | App-configured HTTPS URL ending exactly `/v1/wakes/resolve` |

Prefix every suffix with `pushDebug` or `pushRelease`, for example
`pushDebugFirebaseAppId`. Partial configuration or a mismatched declared package/
app ID/sender association fails the build. No real project options are in Git.
Valid syntax does not prove a working project or app association.

## Lifecycle

A custom Application initializes the default Firebase app before a cold service
entry. FirebaseInitProvider is removed. Manifest/runtime automatic Messaging
initialization is disabled; analytics/default data collection and notification
delegation are disabled. This is configuration, not observed zero-network behavior
or a physical startup performance result.

A durable WorkManager worker serializes explicit register/unregister operations.
It reads the existing wallet, selected backend, wallet-specific social consent and
Android permission from durable state, without creating a wallet. It stores only
installation registration phase/FID/time in encrypted device-only storage. Pending
phases precede SDK requests so an interrupted registration still needs opt-out.
Startup/resume, committed social consent, permission response, wallet import and
backend changes replace obsolete scheduled retries with current reconciliation.
The execution lock and retained in-process outstanding Task prevent opposite SDK
operations from overlapping after a waiting timeout or worker cancellation. A
replacement drains that Task before reading fresh demand or starting another.
Process loss destroys this in-memory Task; durable pending phases and actual SDK
callback/registration recovery must be verified with the configured project. Daily work refreshes registration. Fresh
state is reread after SDK completion. SDK callbacks request reconciliation rather
than blindly replacing state; matching pending/completed callbacks avoid cancelling
their own awaited operation and avoid callback loops.
Late callbacks can force a corrective registration/unregistration for the current
choice. Failures retry independently of reading and consent persistence.

The gateway must send data-only `{version: "1", wake: <opaque identifier>}`.
The service accepts only those two keys and waits for durable scheduling before
returning. It does not accept a resolver, wallet, backend or notification text from
the message. Content resolution and Android publication use the existing worker.
A Firebase notification/mixed payload can be automatically displayed by the SDK
outside this callback, so it is forbidden in the sender contract. Client parsing
alone does not establish that the eventual sender obeys this contract.

## Remaining acceptance

Source/manifest/build checks and unconfigured app regressions do not establish
configured Firebase startup, actual registration/callback ordering, task timeout/
process interruption behavior, rotation, Google delivery or real closed-app taps.
The owner Firebase setup question remains pending. No project, IAM role, API,
account, sender credentials or deployed service is created by this source change.

The signed gateway contract and portable recovery client have local acceptance;
the native encrypted journal/HTTP binding adapter has controlled Android10/17
acceptance in#42. Actual SDK registration/cloud acceptance remains separate.
The[isolated gateway](PUSH_GATEWAY.md) has controlled durable observer/event,
lost-acknowledgement recovery, both-selector and external sender-adapter acceptance.
See[autonomous processing evidence](evidence/ticket42/runtime-green/README.md);
configured SDK callbacks/rotation and actual Google/device delivery remain#32
acceptance. Android reconciliation binds the registered FID to the gateway through
its separately verified signed contract. Chat must join the shared consent
calculation before using this installation transport, so one module cannot
unregister another. Native Chat presentation in#44 now implements cached private
and group alert destinations, mounted-work recovery and wallet-scoped private
message previews in Settings. Previews default off; unsupported content stays
generic, and disabling previews withdraws older private alerts for that wallet.
This retained-alert presentation does not connect Chat to Firebase or the gateway.
See[the current handoff](handoffs/IMPLEMENTATION.md) for its actual verification
status and signed review publication. Configured push acceptance on Android10/17
and physical Pixel/locked/Doze checks remain outstanding.

## Dependency compatibility

Firebase's resolved Play-services-base POM requests Fragment1.1.0. The first build
compiled debug/test code but release lint rejected that old transitive version at
the existing wallet ActivityResult calls. The app constrains this transitive
dependency to[Fragment1.8.9](https://developer.android.com/jetpack/androidx/releases/fragment#1.8.9),
keeping its current Activity/Lifecycle pins. Current Fragment1.9.1 was observed in
the official release notes; a wider AndroidX upgrade is outside this bounded SDK
integration. This is a dependency correction, not a lint suppression.

# Android push client configuration

Part of open[#32](https://github.com/robelene112/redsquare-android/issues/32).
The client source integrates Firebase Messaging26.0.0 using the supported
[manual initialization](research/FCM_MANUAL_INITIALIZATION_2026_10_17.md) path.
It does not require the Google-services Gradle plugin or checked-in JSON.
Actual Firebase registration, gateway binding and cloud delivery remain unverified.

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
backend changes request reconciliation. Daily work refreshes registration. Fresh
state is reread after SDK completion. SDK callbacks request reconciliation rather
than blindly replacing state; matching registered state avoids callback loops.
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

The signed gateway subscription/revocation contract, durable observer/events,
reconciliation after dropped messages, both-backend coverage and actual cloud
sender still need implementation and verification. This client registration is
not a gateway subscription. Chat must join the shared consent calculation before
using this installation transport, so one module cannot unregister another.
Full Android10/17 and physical Pixel/locked/Doze acceptance remain outstanding.
Public beta24 is unchanged.

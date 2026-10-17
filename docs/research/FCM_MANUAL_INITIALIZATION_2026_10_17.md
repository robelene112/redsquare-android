# FCM manual initialization, 2026-10-17

Preparation for #32; complements the existing
[Messaging26 API observation](FCM_ANDROID_API_2026_10_16.md), without repeating
version/FID verification. Only public documentation/source was read. No project,
account, credentials or client configuration was inspected; no runtime was exercised.

**Supported setup:** Firebase explicitly permits supplying Android string
resources instead of the Google-services plugin/JSON. Alternatively,
`FirebaseApp.initializeApp(applicationContext, options)` initializes the default
app programmatically. Both are documented paths, not workarounds requiring a
checked-in JSON file. See [multiple-project setup](https://firebase.google.com/docs/projects/multiprojects#support_multiple_environments_in_your_android_application)
and [FirebaseApp reference](https://firebase.google.com/docs/reference/android/com/google/firebase/FirebaseApp).

**Options:** valid API key, Firebase project ID and Firebase application ID are
required; the application ID is `mobilesdk_app_id`, not the Android package name.
Use `FirebaseOptions.Builder.setApiKey`, `setProjectId`, `setApplicationId`.
Database URL, storage bucket and OAuth web-client ID are not required for this
Messaging-only configuration. The [initialization requirements](https://firebase.google.com/support/guides/init-options)
establish the three mandatory values. For an explicit Messaging sender, also set
`setGcmSenderId(projectNumber)`. Current official [Metadata source](https://github.com/firebase/firebase-android-sdk/blob/main/firebase-messaging/src/main/java/com/google/firebase/messaging/Metadata.java)
otherwise derives it from a valid `1:<project-number>:android:...` app ID; this
fallback is implementation evidence, not a reason to invent missing configuration.

**Resource equivalent:** `google_app_id`, `google_api_key`, `project_id`, and
explicit `gcm_defaultSenderId` map respectively to app ID, API key, project ID and
project number. These mappings and manual resource generation are documented in
the [plugin guide](https://firebase.google.com/docs/android/google-services-plugin-and-file#how_the_json_config_file_is_processed).
The [provider](https://github.com/firebase/firebase-android-sdk/blob/main/firebase-common/src/main/java/com/google/firebase/provider/FirebaseInitProvider.java)
runs before Application.onCreate and calls default resource initialization.
[FirebaseOptions.fromResource](https://github.com/firebase/firebase-android-sdk/blob/main/firebase-common/src/main/java/com/google/firebase/FirebaseOptions.java)
returns null without `google_app_id`; with no existing default app,
[resource initialization](https://github.com/firebase/firebase-android-sdk/blob/main/firebase-common/src/main/java/com/google/firebase/FirebaseApp.java)
then leaves it uninitialized and the provider logs unsuccessful initialization. Missing other required values
must not be mistaken for valid configuration. Source observations use official
main as read October17, not a new binary attestation.

**Manual lifecycle recommendation:** if choosing programmatic setup, remove
FirebaseInitProvider with the documented manifest `tools:node="remove"`, then
initialize the default app before any Messaging API, including cold service
entry. Firebase's [manual initialization article](https://firebase.blog/posts/2017/03/take-control-of-your-firebase-init-on/)
documents provider removal. Gate absent configuration and avoid placeholder
initialization. Messaging's public `getInstance()` uses the default app;
the [source overload accepting FirebaseApp](https://github.com/firebase/firebase-android-sdk/blob/main/firebase-messaging/src/main/java/com/google/firebase/messaging/FirebaseMessaging.java)
is hidden/package-private, not a supported named-app integration API.

Keep manifest `firebase_messaging_auto_init_enabled=false` and
`firebase_analytics_collection_enabled=false`; with the previously verified FID
flag enabled, explicitly call `register()` only after durable opt-in and handle
`onRegistered`. The [client guide](https://firebase.google.com/docs/cloud-messaging/android/get-started#prevent-auto-init)
supports manual registration while auto-init stays disabled. Persisted runtime
auto-init settings can [override the manifest](https://firebase.google.com/docs/reference/android/com/google/firebase/messaging/FirebaseMessaging#setAutoInitEnabled(boolean)). This establishes no zero-network
or successful registration/delivery guarantee.

**Variants:** existing [Gradle](../../app/build.gradle.kts) builds release
`nl.davidengelen.redsquare` and debug `.dev`. Supply each variant's corresponding
registered Android-app options. The plugin normally checks package matching;
manual options remove that build-time check, so validate association explicitly.
Separate Firebase projects are optional; existing owner setup remains pending.

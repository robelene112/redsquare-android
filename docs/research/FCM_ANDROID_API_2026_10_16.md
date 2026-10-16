# FCM Android API observation, 2026-10-16

Preparation for [#32](https://github.com/robelene112/redsquare-android/issues/32).
No Firebase dependency, client configuration, service registration, account,
gateway or node change has been made. This note supersedes the dated token/FID
API observation in the [October15 prerequisites](NATIVE_CLOSED_APP_PUSH_REQUIREMENTS_2026_10_15.md),
not its still-open delivery gates. Sources were independently checked on October16;
their current contents do not establish when each API was first introduced.

The [Android client guide](https://firebase.google.com/docs/cloud-messaging/android/get-started)
(updated October15) describes FID registration enabled by the manifest metadata
`firebase_messaging_installation_id_enabled=true`. With automatic initialization
disabled, explicit `FirebaseMessaging.getInstance().register()` starts registration.
To prevent automatic registration, the guide specifies both
`firebase_messaging_auto_init_enabled=false` and
`firebase_analytics_collection_enabled=false`; this is documentation, not an
observed zero-network guarantee. Its device prerequisite is the Play Store app
or an emulator with Google APIs; distribution itself need not use Play.

The [service reference](https://firebase.google.com/docs/reference/android/com/google/firebase/messaging/FirebaseMessagingService)
(updated June16) provides `onRegistered(String)` and `onUnregistered(String)`.
The former reports the FID after registration, including manual registration;
the latter confirms explicit unregistration. `onNewToken(String)` is deprecated.

The [Messaging reference](https://firebase.google.com/docs/reference/android/com/google/firebase/messaging/FirebaseMessaging)
specifies `register()`/`unregister()` as `Task<Void>` operations, with the FID
delivered through the callbacks. They require the FID manifest flag. Conversely,
deprecated `getToken()`/`deleteToken()` reject that flag being enabled.
Unregistering does not delete the FID; leaving auto-init enabled permits
registration again on next startup. These distinctions matter for explicit opt-out.

The [HTTPv1 Message schema](https://firebase.google.com/docs/reference/fcm/rest/v1/projects.messages)
(updated September8) includes the `fid` target. Its older `token` field is
deprecated and accepts FIDs during transition. Integration inference: the gateway
target and registration lifecycle must agree. No transition end date is established here.

The [Android release notes](https://firebase.google.com/support/release-notes/android)
map BoM35.0.1 (October14) to Messaging26.0.0, released October7 with minimum API24.
The retained AAR manifest independently specifies API24, below this app's minimum29.
This compatibility observation does not establish
successful resolution, merged-manifest behavior, runtime initialization or delivery.

[Google Maven metadata](https://dl.google.com/dl/android/maven2/com/google/firebase/firebase-messaging/maven-metadata.xml)
reports latest/release26.0.0 in the retained snapshot, lastUpdated20261007184340.
Read-only inspection of the [public AAR](https://dl.google.com/dl/android/maven2/com/google/firebase/firebase-messaging/26.0.0/firebase-messaging-26.0.0.aar)
confirms both new methods/callbacks and the old APIs' Deprecated attributes.
The extracted classes.jar matches the AAR entry byte for byte.
AAR size156491bytes, SHA256
`6677d405b84c38729bbca87059a4ae4b1ae1913ad8e6c5dc6d549aefbd526cac`.
The downloaded public AAR, classes.jar, javap output and metadata remain under
`/mnt/storage/redsquare-android-artifacts/push32-fcm-doc-observation/`.
No private configuration or wallet material was inspected.

Next integration should preserve optional opt-in, persist only opaque routing
references, and use the existing local verifier before Android publication.
SDK setup, signed gateway registration, device/identity/backend lifecycle and
actual closed-process delivery still require implementation and acceptance.
The existing owner setup question stays pending. This observation does not
authorize production deployment or claim viable transport on either backend.

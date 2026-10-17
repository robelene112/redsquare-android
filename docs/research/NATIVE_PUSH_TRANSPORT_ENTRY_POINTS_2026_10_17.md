# Native push transport entry points, 2026-10-17

Preparation for [#32](https://github.com/robelene112/redsquare-android/issues/32),
from local source at `57ffd6f8`. Proposal only. No implementation, probes,
configuration, accounts, deployment, builds or CI were changed.

## Actual entry points

| Boundary | Existing source and proposed integration |
| --- | --- |
| First-use social consent | [SocialNotificationPermissionPrompt](../../app/src/main/kotlin/nl/davidengelen/redsquare/notifications/SocialNotificationPermissionPrompt.kt) `onEnable` persists encrypted consent and the owner offer flag. Reconcile registration after consent commits; Skip does not authorize registration. |
| Settings opt-in/out | [SocialAlertChoice](../../app/src/main/kotlin/nl/davidengelen/redsquare/notifications/SocialAlertChoice.kt) writes `SocialPushSettings.setEnabled(requested)`. Both paths need the same durable reconciliation hook, including retry after process interruption. Consent is wallet-scoped, not backend-scoped. |
| Wallet import | [MainActivity](../../app/src/main/kotlin/nl/davidengelen/redsquare/MainActivity.kt) `WalletImport.activate` saves work, replaces the wallet and recovers failures. Retire the previous subscription and reconcile from reread durable identity after this transition, not merely the requested incoming key. |
| Backend selection | `MainActivity` saves through [AndroidBackendStore](../../app/src/main/kotlin/nl/davidengelen/redsquare/saito/AndroidBackendStore.kt), whose uncertain commit leaves an explicit recovery barrier. Reconcile after success; unreadable selection must disable binding instead of falling back. |
| Startup/resume | `MainActivity` separately loads backend and wallet. Reconciliation must tolerate either being unavailable and run again after committed state becomes readable, including OS-permission changes on resume. |
| Cold delivery/registration callback | [Manifest](../../app/src/main/AndroidManifest.xml) has no custom Application or messaging service. A future service must initialize from application context and durable stores, never Compose state or `loadOrCreate()`. [AndroidWalletStore.loadExisting](../../app/src/main/kotlin/nl/davidengelen/redsquare/AndroidWalletStore.kt) already supplies that distinction. |

[ChatScreen](../../app/src/main/kotlin/nl/davidengelen/redsquare/chat/ChatScreen.kt)
uses the generic Android permission prompt. This grants no social consent.
[SocialPush](../../app/src/main/kotlin/nl/davidengelen/redsquare/notifications/SocialPush.kt)
already rechecks current wallet/backend, consent, permissions and channels before
publication. Preserve those checks after transport registration succeeds.

## Smallest coherent next slice

Implement a transport-neutral, durable subscription coordinator plus a local
gateway contract fixture. Its desired binding is installation, current public
wallet, approved backend, social categories and expiry. Serialize reconciliation,
reread committed state before accepting asynchronous results, and reject stale
registration generations. Persist pending renew/revoke work; registration failure
must not roll back consent or block reading/import. Retain the previous binding's
revocation authority, not its wallet secret. Define that authority in the contract
before relying on revocation after wallet replacement; bounded expiry is a
backstop, not immediate remote deletion.

Use the existing [gateway proposal](SOCIAL_PUSH_GATEWAY_OBSERVER_2026_10_15.md)
for locally signed single-use challenge binding, renewal and revocation. These
are operations to implement locally, not existing URLs. Keep server signing
credentials outside the APK. Exercise opt-in, rotation, switch/import, opt-out
and interruption against an owned local fixture with an injected push sender.
Use controlled signed public candidates; do not call that a deployed observer.

Keep the external wake payload opaque. A bounded gateway resolver can translate
it into the existing wallet/backend/signature reference, then call
[SocialPushScheduling.enqueue](../../app/src/main/kotlin/nl/davidengelen/redsquare/notifications/SocialPushWorker.kt).
The current worker is not an opaque gateway resolver. Verify actual Android
publication through the existing controlled-peer boundary.

## Still external

The dated [FCM API observation](FCM_ANDROID_API_2026_10_16.md) identifies
register/unregister and onRegistered/onUnregistered integration points. A shared
installation transport must not unregister while another opted-in module needs
it. Firebase configuration, actual callback/rotation behavior, authorized gateway
deployment and sending remain unverified. The
[requirements](NATIVE_CLOSED_APP_PUSH_REQUIREMENTS_2026_10_15.md) and
[Archive discovery limits](SOCIAL_PUSH_ARCHIVE_DISCOVERY_2026_10_15.md) still apply:
local integration proves neither both-backend observer coverage nor real
closed-process, locked, Doze or Pixel FCM delivery. Existing owner setup remains
pending; no additional owner request or public endpoint is implied.

# Closed-app push prerequisites for #32–34

Checked2026-10-15 against [#1](https://github.com/robelene112/redsquare-android/issues/1),
[#32](https://github.com/robelene112/redsquare-android/issues/32),
[#33](https://github.com/robelene112/redsquare-android/issues/33) and
[#34](https://github.com/robelene112/redsquare-android/issues/34),
[project context](../PROJECT_CONTEXT.md), existing
[push feasibility](NATIVE_NOTIFICATIONS_FEASIBILITY.md),
[social](NATIVE_NOTIFICATIONS_CONTRACT.md),
[private](NATIVE_PRIVATE_CHAT_CONTRACT.md),
[group](NATIVE_GROUP_CHAT_VERIFICATION.md),
[queue](NATIVE_CHAT_QUEUE_VERIFICATION.md) and
[mute](NATIVE_CONVERSATION_MUTE_VERIFICATION.md) contracts.
Only this document was written. No credentials/configuration inspection,
Firebase mutations, connections to Saito backends, messages, services, builds or
CI. This prepares existing tickets; it claims no implementation or delivery.

## Firebase facts

| Phone state | Contract and limitation |
| --- | --- |
| Background, ordinary process death or OS memory reclamation | A missing Activity/process is compatible with FCM background delivery. Data messages use `FirebaseMessagingService.onMessageReceived`; no permanent app WebSocket is required. Android can kill cached processes. This is a supported path, not guaranteed delivery. |
| Task removed from Recents | Record separately from process death and Force stop; do not infer the device's package stopped state or OEM behavior from disappearance of its task. |
| Explicit Force stop | Firebase requires reopening before messages resume. Android's stopped state persists until user interaction; Android15+ also cancels pending intents. |
| Uninstalled | FCM discards delivery and invalidates the registration token. |

Sources: [Android receive](https://firebase.google.com/docs/cloud-messaging/android/receive-messages),
[process lifecycle](https://developer.android.com/guide/components/activities/process-lifecycle),
[Firebase Android force-stop prerequisite](https://firebase.google.com/docs/cloud-messaging/flutter/receive-messages),
[stopped packages](https://developer.android.com/about/versions/15/behavior-changes-all#enhanced-stop-states),
[message lifetime](https://firebase.google.com/docs/cloud-messaging/customize-messages/setting-message-lifespan).

Notification or mixed payloads display automatically in the background before
app filtering. Prefer **data-only** for authoritative local mute/privacy checks.
Payload maximum is4096bytes; transport encryption is not end-to-end encryption.
Keep private plaintext out of FCM. Large encrypted originals/photos require
bounded retrieval, not embedding whole transactions.
[Message types](https://firebase.google.com/docs/cloud-messaging/customize-messages/set-message-type).

Normal priority may wait through Doze. High priority attempts immediate delivery
with only seconds of processing and limited networking. Use it for visible
time-sensitive alerts; repeated invisible/suppressed deliveries can be
deprioritized based on seven days' behavior. Additional work needs WorkManager:
expedited immediately after high-priority receipt, regular otherwise. Neither
priority guarantees latency or successful fetching.
[Priority/processing](https://firebase.google.com/docs/cloud-messaging/android-message-priority).
TTL defaults to four weeks, maximum28days; TTL0 discards undeliverable messages.
An FCM message ID proves acceptance, not display.
[Lifetime](https://firebase.google.com/docs/cloud-messaging/customize-messages/setting-message-lifespan).
Android stores at most100 noncollapsible messages; overflow discards the backlog.
Collapsible delivery retains only four collapse keys. Reconcile durable events
after loss, rather than treating push as the message database.
[Collapse limits](https://firebase.google.com/docs/cloud-messaging/customize-messages/collapsible-message-types).

## Concrete external setup

Provide an authorized Firebase project, Android app registrations matching
`nl.davidengelen.redsquare` and the separate `.dev` identity, client configuration,
Google-services integration and Messaging SDK/service. `google-services.json`
contains non-secret client identifiers, unlike server credentials.
[Android setup](https://firebase.google.com/docs/android/setup).
Compatible Google Play services are required; sideloaded APK distribution works.
Implement supported registration-token acquisition/`onNewToken` rotation and
revocation. Current docs still document these APIs, correcting the earlier
feasibility note's suggestion to replace tokens with installation IDs.
[Android client](https://firebase.google.com/docs/cloud-messaging/android/get-started).

A separately authorized HTTPS gateway/observer deployment needs durable bounded
event/subscription storage and an enabled FCM API. Its server identity needs
`cloudmessaging.messages.create` on the target project and OAuth messaging scope,
using protected service credentials/ADC, never credentials in the APK.
[Send authorization](https://firebase.google.com/docs/cloud-messaging/send/v1-api),
[exact permission](https://firebase.google.com/docs/reference/fcm/rest/v1/projects.messages/send).
Existing project access and live gateway availability were not inspected.

## Saito coverage, source facts versus architecture

A/B are official source pins `9a4ca7e9442eb5e872168ced361ce741d7d139d7` and
`214504ecece76d1cd20a245fe4468eccbfca2951`, not verified deployment identities.

**Proposed common observer:** use its own service identity, observe public
included transactions and query each selected backend's available Archive.
Pinned [block routes](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/lib/saito/core/server.ts#L334)
provide full/lite blocks; ordinary
[Chat sends](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/chat/chat.js#L1498)
both propagate and Relay. Consequently block-confirmed wakeups need not inherently
require saito.io operator changes or enabling block production on David's node.
Submission still does not prove inclusion. Catch-up, reorganization handling,
retention and actual read capability require verification.

[Archive indexing](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/archive/archive.js#L314)
is conditional; [default indexes](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/lib/saito/storage.ts#L149)
store only the first recipient. Group-ID history needs known groups; wallet-only
queries miss some messages/controls. Root mentions need signed-content inspection,
not recipient-only indexing. Full public scanning can address indexing gaps,
but chain visibility does not prove acceptance under either backend's moderation.
[RedSquare indexing](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/redsquare.js#L1161).

Ordinary [Relay](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/relay/relay.js#L205)
targets connected recipient identities. A separate service cannot simply subscribe
to every user's Relay. The [Chat service exception](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/chat/chat.js#L964)
broadcasts wrappers addressed to the service itself to all peers; this is
conditional. Relay-only/unconfirmed traffic, pruned history and service outages
remain explicit coverage gaps. Do not silently impose official-node hooks or
declare those gaps resolved.

**Recipient-authenticated gateway alternative is unproved:** a phone could
theoretically sign a live handshake challenge without uploading its key. But
[handshakes](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/rust/saito-core/src/core/routing_thread.rs#L461)
use fresh connection nonces; reconnect requires fresh wallet signing.
[Disconnect](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/rust/saito-core/src/core/network/peer.rs#L176)
clears authentication. Also, [handshake completion](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/lib/saito/modules.ts#L709)
maps one socket per public key, and
[server sending](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/rust/saito-js/shared_methods.server.ts#L21)
uses that [single mapping](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/rust/saito-js/saito.ts#L654).
These selected A/B paths match. Phone/gateway connections can compete for routing;
a surviving session is not reliable autonomous offline reconnection or fan-out.

## Required on-device behavior, proposed implementation

Register devices with a replay-protected locally signed challenge, backend and
expiry; never upload wallet/conversation secrets or publish registrations on
chain. Send opaque event references, fetch bounded signed originals, verify and
decrypt locally, then classify replies/mentions/private/group events. Ciphertext
recipients alone cannot classify encrypted Chat. Apply fresh wallet/backend,
signature dedup, group membership, account and conversation controls before
posting. Keep default **New message**, opt-in local previews and correct tap
destination/privacy label. Preserve encrypted queued originals and drafts.
These follow the linked Saito contracts and accepted tickets, not an existing
gateway API. Prove local key access while locked; unavailable keys require safe
pending processing, not bypassed filters.

Retain skippable permission on first Chat/Notifications use and four categories.
[Permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
and [channels](https://developer.android.com/develop/ui/compose/notifications/channels)
supplement application policy. Acceptance requires actual notification display
and taps after normal process death/background/lock/Doze on Android10/17 and
Pixel, both backends, denials/mutes, token/identity/backend changes, loss/recovery
and duplicates. Record Force stop separately. Generic FCM success and existing
foreground alerts establish none of this closed-app acceptance.

# Chat push: existing transport boundary, 2026-10-15

Read-only prerequisite research for #33/#34 while #32 has one implementation
writer. **No existing closed-native-client subscription/wake protocol was
established for both approved backends.** This does not reduce accepted scope.
Read alongside [closed-app requirements](NATIVE_CLOSED_APP_PUSH_REQUIREMENTS_2026_10_15.md).

A=`9a4ca7e9442eb5e872168ced361ce741d7d139d7` is the documented owner runtime
source pin; B=`214504ecece76d1cd20a245fe4468eccbfca2951` is an inspected official
source revision, not a verified saito.io deployment identity. Existing public
A/B Chat copies, Relay, public module tree and first-party pinned sources were
read. A named `/opt/saito/node/mods/ntfy/ntfy.js` source-only read reconfirmed
its publisher flow through a network-disabled/read-only container; the module
was never initialized. That named-file observation recorded no file hash or Git
provenance, so it does not independently attest the A revision. No saved options, environment values, keys, DB data,
service state, backend probes, app edits, builds, CI or production writes.

## What exists

- [Relay A, L204-260](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/relay/relay.js#L204-L260)
  unwraps `relay peer message`, dispatches when addressed to the node, otherwise
  forwards to connected addressed peer identities. No matching peer yields an
  error. It has no demonstrated durable device-token subscription or wake queue.
- [Chat A, L960-992](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/chat/chat.js#L960-L992)
  can broadcast a `chat relay` wrapper to all peers only when the wrapper is
  addressed to the Chat service itself. This conditional community/service case
  does not subscribe a gateway-owned identity to everyone else's private relay.
  [Ordinary sends, L1498-1547](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/chat/chat.js#L1498-L1547)
  propagate and Relay; propagation is not guaranteed block inclusion.
- [NTFY B, L19-118](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/ntfy/ntfy.js#L19-L118)
  reads endpoint/user/password variable names, collects block transactions,
  excludes connected peers/sender/node, asks loaded modules for notification
  text, and POSTs recipient-public-key topics. The POST supplies no authorization
  header; user/password fields are unused there. No native registration,
  subscription challenge, FCM token binding, VAPID endpoint or reliable retry
  appears. Initialization itself publishes a startup notice. The named deployed
  file observation has this flow; B has alpha status metadata. That comparison
  does not prove the deployed file's Git revision.
- [Chat A's NTFY hook, L715-751](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/chat/chat.js#L715-L751)
  rejects nonplaintext `msg.module` before its encrypted-message branch and
  assigns `tx.to.length = 2`. Both defects persist in
  [B, L717-753](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/chat/chat.js#L717-L753).
  Its sender/message preview does not implement recipient mute/preview policy.
  Reusing it unchanged would miss ordinary encrypted DMs and violate safeguards.
- [Notifications B](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/notifications/notifications.js#L23-L73)
  is browser-facing display/sample behavior, not native wake infrastructure.
  The [public B module tree](https://github.com/SaitoTech/saito/tree/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods) contains NTFY/Notifications/Relay, not Push/WebPush modules.

## Configuration and availability

Public [A](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/config/.template.modules.config.js)
and [B module templates](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/config/.template.modules.config.js)
include Chat/Relay/Archive but omit NTFY/Notifications. These are templates, not
actual runtime configuration. Module [initialization honors active options](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/lib/saito/modules.ts#L232-L245).
Neither source presence nor a `relay`/`chat` service advertisement proves a
configured push publisher, subscription endpoint or delivery on either backend.
Those runtime facts remain unverified; no private configuration was inspected.

## Minimum proposed integration

Use an independent gateway-owned identity, authenticated device subscription
and authorized Android wake transport, with ciphertext/reference retrieval and
local decryption, category, membership, mute and preview checks. No user wallet
or conversation secret leaves the device. Existing
[full-block](SOCIAL_PUSH_GATEWAY_OBSERVER_2026_10_15.md) and
[Archive discovery](SOCIAL_PUSH_ARCHIVE_DISCOVERY_2026_10_15.md) paths provide
bounded public candidates, not exhaustive private-relay coverage.

That common observer needs no saito.io mutation, but cannot promise immediate
Relay-only/unconfirmed delivery. Legacy off-chain traffic would need an actually
supported operator integration or sender protocol, not impersonation of the
phone. A recipient-authenticated gateway still faces fresh reconnect signatures
and competing socket mappings documented in the prerequisites.

Prepare and review the concrete gateway, subscription/privacy/retention contract,
Firebase configuration and isolated end-to-end test first. Deployment and any
owner-node hook/configuration require separately bounded explicit authorization;
saito.io cooperation is not assumed. This note authorizes none of those changes
and proves neither closed-app delivery nor #33/#34 completion.

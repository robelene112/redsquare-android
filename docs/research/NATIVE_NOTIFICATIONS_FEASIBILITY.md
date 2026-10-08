# Native Android notification feasibility

Read-only implementation research for accepted tickets 31–33, 2026-10-08.
Applied the project-local research skill, current app instructions and Saito
routing/operations instructions. Inspected static deployed sources using a
network-disabled Docker runner with a read-only `/opt/saito/node` bind, and
public primary sources. No saved options, environment values, wallet keys,
database rows, subscriptions, live messages or service changes were accessed.
Only this research file was written. Source presence does not establish that
a module is enabled or configured on either live backend.

## Outcome

Closed-app Android notifications are feasible without uploading users' Saito
private keys. The inspected Saito modules do not expose a ready-made native
FCM registration/delivery service. They expose an **NTFY publisher**, and its
current Chat hook has defects that prevent treating it as the accepted push
feature. A developer-operated delivery service and Firebase project are the
practical first-release route. Source deficiencies are implementation work,
not grounds to remove accepted notifications.

Both fixed backends can be supported. Ordinary Chat messages are sent to the
network as well as relayed, so a service observing the shared chain can provide
block-confirmed wakeups regardless of the selected backend. This does not
inherently require saito.io operators to install an FCM module. Immediate
relay notifications, authoritative backend moderation filtering and any
backend-specific events unavailable on the public chain need additional
integration. Encrypted Chat classification and filtering require deliberate
client-side handling or additional notification metadata; a generic server
cannot decrypt them. These architecture conclusions are inferences from the
source paths below, not live interoperability results.

## Accepted product requirements

The current [project context](../PROJECT_CONTEXT.md:78) includes replies,
mentions, private Chat and group Chat notifications while the app is closed.
Permission is requested on first opening Chat or Notifications, with a skip
option. Four Android categories, a system-settings shortcut, conversation
mute, account mute/block and default private-chat text **"New message"** are
already agreed. Private message previews are opt-in. Account controls apply
to the active wallet across both backends. Backend choices remain
`https://saito.davidengelen.nl` and `https://saito.io`; this research introduces
no arbitrary-backend setting. Notification infrastructure is an agent
implementation responsibility. See also the
[current implementation handoff](../handoffs/IMPLEMENTATION.md).

## Source authority

The deployed runtime reference is official commit
[`9a4ca7e9442eb5e872168ced361ce741d7d139d7`](https://github.com/SaitoTech/saito/commit/9a4ca7e9442eb5e872168ced361ce741d7d139d7),
recorded in Snake's maintained `scripts/saito-runtime.json`.
Comparison used official `prod`
[`214504ecece76d1cd20a245fe4468eccbfca2951`](https://github.com/SaitoTech/saito/commit/214504ecece76d1cd20a245fe4468eccbfca2951)
and inspected `staging`
[`e9f3635fa56450008774f2d8d76731121268fe7a`](https://github.com/SaitoTech/saito/commit/e9f3635fa56450008774f2d8d76731121268fe7a).
These are source revisions, not a claim that saito.io runs either exact SHA.
See [version provenance](SAITO_VERSION_PROVENANCE.md).

The deployed module inventory and the official
[prod module directory](https://github.com/SaitoTech/saito/tree/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods)
contain `ntfy`, `notifications` and `relay`, rather than modules named Push or
WebPush. The inspected notification, Chat, Relay and browser paths do not
implement Firebase registration, native token binding or Web Push
subscription/VAPID handling. The separate
[Notifications module](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/notifications/notifications.js#L15)
is a browser Red Square refactor/sample interface, including sample tweet
data at line 93, not a native push gateway.

## What vanilla NTFY actually does

Deployed `/opt/saito/node/mods/ntfy/ntfy.js`:

- [Lines 19–22](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/ntfy/ntfy.js#L19)
  read configuration named `NTFY_SERVER`, `NTFY_USER`, `NTFY_PASSWORD`.
  Their actual values and live enablement were not inspected.
- [Lines 30–47](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/ntfy/ntfy.js#L30)
  collect transactions from a received block, not the instant Chat relay.
- [Lines 50–76](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/ntfy/ntfy.js#L50)
  exclude recipients currently connected as peers, the sender and the node's
  own wallet. This peer-connectivity heuristic is not Android foreground state
  or a user notification preference.
- [Lines 98–119](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/ntfy/ntfy.js#L98)
  ask loaded modules for `respondTo('ntfy-notification', payload)`, then POST
  their result to the NTFY endpoint with the recipient public key as `topic`.
  No authorization header is attached. The read user/password fields are not
  used here. There is no registration challenge, recipient access control,
  native device identifier or FCM token map in this code.
- The POST promise is not awaited, recipient entries are not deduplicated and
  the `lc` argument is not checked. No delivery acknowledgment, durable retry
  or reorganization-aware deduplication is established by this implementation.
- [Initialization, lines 128–179](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/ntfy/ntfy.js#L128)
  also posts a startup notification. Do not initialize this module as a
  supposedly read-only experiment.

Official prod/staging NTFY files are identical to each other. Relative to the
pin, the inspected difference is an added `this.status = 'alpha'`. The flow
above remains in
[current prod source](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/ntfy/ntfy.js#L31).

## Chat and Red Square limitations

The Chat NTFY hook at deployed `mods/chat/chat.js:715–751` has two concrete
problems that persist in current official
[prod lines 717–753](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/chat/chat.js#L717)
and staging:

1. [Its first guard](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/chat/chat.js#L718)
   rejects `tx.msg` unless it exposes plaintext `module: 'Chat'`. An ordinary
   encrypted DM has a ciphertext string instead. It returns before the later
   encrypted-message branch at line 738; that branch does not repair the guard.
2. [Line 728](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/chat/chat.js#L728)
   assigns `tx.to.length = 2` instead of comparing. It always chooses the DM
   title and mutates the recipient array, including for group messages.

The hook also includes a sender label and up to 50 plaintext message characters
at lines 736–746, logs its whole input at line 716, and does not consult the
recipient's mute/block/preview settings. Reusing it unchanged would fail the
accepted categories and notification privacy contract.

Ordinary Chat composition establishes the encrypted/public distinction:
[lines 1498–1525](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/chat/chat.js#L1498)
build `module`, `request`, `group_id`, message and mention fields; two-member
private conversations call forced encryption, while group messages are signed
without that encryption. The
[wallet encryption helper, lines 1290–1316](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/wallet.ts#L1290)
replaces the whole message with encrypted content. A server without conversation
keys cannot reliably derive its Chat type, group ID or preview. Visible sender
and recipient addresses alone are not sufficient to distinguish every Chat
message from other encrypted Saito module messages.

[Chat lines 1540–1546](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/chat/chat.js#L1540)
propagate the ordinary message and also relay it. Submission is not proof of
inclusion or availability; a chain watcher delivers after observing inclusion.
[Relay lines 204–260](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/relay/relay.js#L204)
forward to connected recipients and return an error if none are connected.
They do not supply a durable offline Android subscription. A chain watcher
will miss events that some modules only inject through Relay.

Chat's browser system-notification code is
[commented out at lines 2540–2564](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/chat/chat.js#L2540).
Active behavior after that is conversation-mute filtering, tab notifications
and sound. The browser's
[Notification/showNotification helper](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/browser.ts#L620)
is an API for displaying an already-received event, not a native closed-app
network subscription.

No `ntfy-notification` hook occurs in `redsquare.js` at the pin, prod or staging.
The deployed Red Square
[notification eligibility helper, lines 135–184](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/notifications.js#L135)
implements local notification decisions for contacts, replies and interactions.
It is not FCM delivery. The native accepted reply/mention requirements need
their own event eligibility path that respects the selected backend's policy.

## Concrete implementation routes

The following are proposals, not existing APIs or verified services.

| Route | Both backend choices | Additional dependency |
| --- | --- | --- |
| Independent shared-chain watcher, or observer attached to David's synchronized node, plus FCM gateway | Can observe included ordinary transactions addressed to registered wallets regardless of selected backend; native client classifies encrypted events and applies backend rules | Gateway/observer hosting, Firebase configuration, new event storage and native background filtering; no inherent official-server deployment requirement |
| Backend event publishers into one FCM gateway | Backend can provide validated public social/group events and real-time relay receipt; encrypted DMs still need opaque wakeups or compatible metadata | David's separately authorized node deployment and saito.io operator adoption/cooperation for the official backend path |
| NTFY ingress plus an app-owned bridge | Can reuse the existing module-hook concept after repairs and event coverage are implemented; not a complete solution by installing NTFY alone | Compatible publishers, protected subscriptions, bridge and app-owned Firebase credentials |

Recommended starting architecture: one app-owned gateway, with a chain observer
as the backend-independent delivery source and optional backend publisher
adapters for lower latency and stronger policy fidelity. Keep event observation
and notification rendering separate. This preserves both backend choices and
does not make official operator cooperation a blanket prerequisite.

For the chain route, capture transactions at observation time, deduplicate by
signature and bound pending-event retention. An opaque event ID can refer to a
gateway copy of the signed encrypted transaction. The native receiver verifies,
decrypts when permitted, classifies the message and applies personal controls.
For public replies/mentions, gateway indexing can derive eligibility from the
public social transaction and its parent, then native/backend filtering checks
availability and moderation. Do not equate a transaction seen on the common
chain with a post accepted by both Red Square backends.

Private/group distinction, conversation mute and optional previews must work
while the process is absent. If required local decryption keys are inaccessible
without an unlock, opaque ciphertext cannot immediately produce an accurate
category or conversation filter. Resolve this against the wallet's actual
device key policy in implementation. Options include an explicitly scoped
background decryption capability on the device or authenticated minimal
conversation metadata stored at the gateway. The latter exposes additional
metadata and cannot classify messages from unmodified legacy senders by itself.
Never solve this by uploading wallet or conversation private keys. A generic
fallback alone does not satisfy all four categories and muted-conversation
requirements; this needs a tested complete path before closing these tickets.

An observer/gateway can use its own service identity. It must not impersonate a
user to access protected archives. Public chain observations and public query
capabilities are distinct from user-authorized archive access. See
[native protocol research](NATIVE_SAITO_PROTOCOL.md) for peer authentication and
archive behavior. Nothing in this research grants node deployment authority.

## FCM and NTFY infrastructure requirements

FCM sending requires a trusted service authorized to the app's Firebase project.
HTTP v1 supports device targets and OAuth credentials; on a non-Google VPS,
the documented setup uses a protected service-account JSON credential. This
is a **Firebase service credential**, not a Saito wallet secret. Keep it out of
Git and the APK. The owner must create/provide project access or delegated IAM
authorization; the agent can implement the service and configuration plumbing.
[Firebase HTTP v1 documentation](https://firebase.google.com/docs/cloud-messaging/send/v1-api).

The Android build also needs Firebase app registration matching its package
identity, the client configuration and supported SDK registration handling.
Register refreshed installation/device identities with the gateway and remove
stale associations. The current documentation marks legacy token handling
deprecated in favor of installation IDs; select the actual supported API for
the SDK version used, rather than freeze a legacy token API into the product
contract. FCM requires compatible Google Play services, but APK distribution
need not itself use Google Play.
[Firebase Android setup](https://firebase.google.com/docs/cloud-messaging/android/get-started).

NTFY's own service supports FCM if the operator supplies a Firebase account/app,
server credential and matching custom Android build. Its Web Push support
instead uses VAPID keys and browser subscriptions. A browser endpoint is not
an FCM identity for this Kotlin app.
[NTFY configuration](https://docs.ntfy.sh/config/#firebase-fcm),
[Web Push configuration](https://docs.ntfy.sh/config/#web-push).
The stock ntfy Android app uses Firebase for `ntfy.sh`, while custom servers
use its persistent service. Installing ntfy and subscribing to a public-key
topic adds another app/setup flow. UnifiedPush can be an alternative transport,
but requires a distributor such as ntfy. Neither is required for the accepted
mainstream Android flow.
[NTFY phone subscriptions](https://docs.ntfy.sh/subscribe/phone/).

Concrete external inputs for the preferred route:

- Firebase project/app identity and authorized service sending credentials.
- A HTTPS gateway hostname, hosting and protected registration/event storage.
- Separately authorized service deployment and chain-observer integration on
  the chosen host. Block production need not be enabled just to observe blocks.
- Official operator cooperation only if choosing official-server event hooks,
  relay integration or access unavailable through ordinary public interfaces.

## Privacy, filtering and delivery implementation

The registration endpoint should accept a locally signed, replay-protected
challenge binding wallet public key, gateway audience, device identity,
backend selection, preferences and expiry. Verify ownership using the pinned
Saito signing protocol in [the protocol reference](NATIVE_SAITO_PROTOCOL.md).
This is a proposed new gateway protocol, not a built-in Saito registration
request. Do not publish device registrations on chain. Support revocation,
rotation, multi-device registrations and wallet replacement without exposing
another wallet's queued notifications.

Use minimal opaque event data by default, with no plaintext DM body in FCM,
gateway logs or public NTFY topics. Firebase explicitly says transport encryption
is not end-to-end encryption. Payloads are limited to 4096 bytes, so images and
large encrypted transactions need a bounded retrieval path rather than full
FCM embedding. Automatic background notification payloads display without
running the app's filtering first; prefer client-handled data delivery where
local wallet, backend, mute/block and privacy checks are necessary.
[FCM message types](https://firebase.google.com/docs/cloud-messaging/customize-messages/set-message-type).

Private previews, if enabled, should be produced on-device after compatible
decryption and current preference checks. Deep links carry event/backend IDs
and resolve against the active identity. Queued events from a previous wallet
or backend session must be rejected or safely resolved before rendering.
Android category controls supplement application eligibility; they do not
replace account block/mute or conversation mute. Permission and channel setup
are documented by
[Android notification permission](https://developer.android.com/develop/ui/views/notifications/notification-permission)
and [notification channels](https://developer.android.com/develop/ui/views/notifications/channels).

FCM data callbacks have a short processing window. Use bounded local decisions
and appropriate background work for fetching; notification delivery must not
depend on a permanently running WebSocket. High priority is for time-sensitive
visible notifications, not silent polling; repeated suppressed high-priority
events can be deprioritized. Sync preferences to reduce avoidable wakeups, and
test filtering that depends on a network fetch under slow/offline conditions.
[Android receiving](https://firebase.google.com/docs/cloud-messaging/android/receive-messages),
[FCM Android priority](https://firebase.google.com/docs/cloud-messaging/android-message-priority).

Validation must cover Pixel 7 foreground, background, normal process death,
screen lock/Doze, network recovery, permission denial, category disablement,
conversation/account controls, old-wallet queued events and both backends.
Explicit force-stop in Android settings is different from ordinary closed-app
use: Firebase documents that the app must be reopened after force-stop before
messages resume.
[Firebase device-state prerequisites](https://firebase.google.com/docs/cloud-messaging/flutter/receive-messages).

## Decisions versus engineering work

Agent work: native registration/receiver, compatible transaction parsing,
gateway ownership verification, event observation, privacy-safe payloads,
filtering/deduplication, retries, lifecycle handling and realistic device
validation. No additional product grilling is needed to establish that these
are necessary parts of the already accepted notifications feature.

Owner/operator input: authorize/access the Firebase project and service
deployment; decide any genuinely new privacy exposure if implementation would
store conversation metadata beyond the established requirements; obtain official
operator cooperation only if the chosen route needs it. Source-level defects
and incomplete research are technical tasks, not reasons to silently substitute
foreground-only notifications or remove either backend.

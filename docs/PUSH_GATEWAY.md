# Isolated notification gateway

Work in open[#42](https://github.com/robelene112/redsquare-android/issues/42), supporting
#32–34. The current first slice is a loopback Kotlin/JVM17 runtime with durable
subscription binding/status/retirement. It is not a deployed gateway or a complete
observer/sender. The public interface is `PushGateway(stateDirectory, publicOrigin)`;
`start()`, `port` and `close()` support the owned HTTP fixture. A standalone command,
Android binding client, resolver, observer, FCM sender and operational
acceptance remain later work. No account, credentials or live node state is used.

## Subscription interface, version1

The configured public origin is HTTPS with no path/query/fragment/userinfo. Its
host is lowercased, the default443port omitted and a trailing root slash removed.
The current runtime listens on an ephemeral IPv4 loopback port. Future deployment
requires a separately reviewed hosting configuration and explicit authorization.

| POST route | Request and result |
| --- | --- |
| `/v1/challenges` | `{version:1, owner}` returns201 with a random64hex nonce and expiresAt, valid5minutes and bound to that public wallet. |
| `/v1/subscriptions/bind` | Signed fields below commit before201 acknowledgement. An exact retransmission acknowledges the same binding. |
| `/v1/subscriptions/status` | `{version:1, subscription, revocationSecret}` returns authenticated active state and public owner/backend/expiry, or404 if unknown. It never returns the FID. |
| `/v1/subscriptions/revoke` | Same installation authority retires the binding before200 acknowledgement, without the outgoing wallet or a wallet signature. A tombstone prevents an older bind from resurrecting it. |

Bind fields: version, nonce, subscription, installation, fid, owner, backend,
categories, expiresAt and revocationHash. IDs/nonce/hash are64lowerhex; installation
is a canonical UUID. The FID contains1–256 ASCII letters/digits/underscore/hyphen.
Backend is `owner` or `official`. Categories are exactly `mentions`, `replies` or
`mentions,replies`. Expiry must be future and at most7days from the server clock.
A fresh signed challenge renews the same subscription with a changed FID, backend,
categories or expiry. Owner, installation and revocation hash stay fixed. A later
issued challenge supersedes an earlier one, so a delayed older request returns409.
After a server-instance restart, only newly issued challenges can renew. Changing
wallet identity requires retiring the previous subscription first.
The device must persist its random subscription ID and revocation secret before
sending so it can retire an uncertain/in-flight bind after wallet replacement.

The `X-Saito-Signature` is the ordinary Saito wallet signature over UTF-8 lines
with no trailing newline:

```text
Saito push bind v1
<public origin>/v1/subscriptions/bind
<nonce>
<subscription>
<installation>
<SHA256 of FID UTF-8, lowerhex>
<owner public key>
<backend>
<categories in canonical order>
<expiresAt decimal milliseconds>
<revocationHash>
```

revocationHash is SHA256 of the64hex secret's UTF-8 text. Send the secret only
for retirement/status over the configured gateway transport; never through FCM.
The server stores its hash. The gateway necessarily links public wallet/backend
with FID while registered. No private Saito wallet material is an accepted field.
This is an app-specific contract, not an existing Saito node API or on-chain action.

## Current persistence and limits

One runtime holds an exclusive state-directory file lock. Snapshot writes force
the pending file, atomically replace the committed file, force the directory and
read back before acknowledgement. A new runtime instance loads the committed
snapshot; the controlled journey observes this through HTTP status. Directory
permissions are0700 and snapshots0600. This first implementation targets POSIX
filesystems with atomic rename/directory sync and fails if unavailable.

At most2048 active subscriptions,2048 retirement records and2048 pending challenges;
state is at most4MiB. Requests are at most8192bytes. HTTP work uses four threads and
a32-entry queue. A nonce is removed after its successful binding commit. Exact
retransmission is idempotent; a retired ID returns410 while its retirement record is retained.
Retirement records have7day expiry. Expired records are pruned on mutation;
automatic physical retention cleanup is not yet implemented or claimed. HTTP
request-body timeouts and operational process/hosting supervision also remain
before deployment readiness. No permanently retained history is promised.

## Verification status

[Intended RED](evidence/ticket42/subscription-red/README.md) requires a challenge201
and observes the no-op501 at6bb9175e. Later tails were unexecuted there. Minimum
local core/gateway test task passes35s, including the complete normal signed
bind, new server instance, retained status, keyless retirement and rejected stale
bind. Bounded executor/origin/resource review changes then pass39s. Final ordinary
HTTP/storage error classification and CI wiring pass34s. Initial source review
found a numeric request conversion classified as503; a correction preserves400
for invalid request integers and503 for stored-record failures. Corrected
build PASS36s and source41cf0c76 reviews Standards0/Spec0.
[Retained first acceptance](evidence/ticket42/subscription-green/README.md) has
final Standards0/Spec0 reviews. Exact c35cb6be source CI38991601083 succeeds,
including gateway tests, core, release assembly and lint. Whole#42 remains open. This is one owned HTTP/protocol journey, not a JVM process crash,
Android SDK binding, Google delivery, both-backend observer or whole#42 acceptance.
The earlier SDK checkpoint is[separate](evidence/ticket32/firebase-client-preparation/README.md).

The renewal journey first fails28s at expected201/got409. The minimum then passes
39s with both gateway journeys passing and the core task up-to-date. It observes
new backend/expiry, rejected delayed renewal, idempotent retransmission and a
fresh renewal after a server-instance restart. It does not observe an outgoing
FCM target or actual Firebase identifier replacement. Minimum review is pending.

Run the owned protocol/core checks with
`scripts/android-build :core:test :push-gateway:test`. CI includes the gateway
test task in source and focused/full builds, with its build file included in
dependency cache keys. No new hosted dispatch choice is added.

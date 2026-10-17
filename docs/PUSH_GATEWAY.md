# Isolated notification gateway

Work in open[#42](https://github.com/robelene112/redsquare-android/issues/42), supporting
#32–34. The current first slice is a loopback Kotlin/JVM17 runtime with durable
subscription binding/status/retirement. It is not a deployed gateway or a complete
observer/sender. The public interface is `PushGateway(stateDirectory, publicOrigin)`;
`start()`, `port` and `close()` support the owned HTTP fixture. The optional
constructor port defaults to0; all listeners remain IPv4 loopback. The Android binding adapter has controlled acceptance; resolver, observer, FCM sender
and operational acceptance remain later work. No account, credentials or live node state is used.

## Local command

Build the standalone distribution with
`scripts/android-build :push-gateway:installDist`. With Java17 on the host:

```sh
push-gateway/build/install/push-gateway/bin/push-gateway \
  --state /mnt/storage/redsquare-gateway/local-state \
  --origin https://push.example.invalid --port 8080
```

This is an example command, not a deployed service or configured app endpoint.
It listens only on127.0.0.1, prints its listening address after startup and keeps
running until stopped. Port0 selects an ephemeral port for owned tests. State
must be on a filesystem supporting the documented sync/rename semantics. Do not
share its state directory across processes. Normal termination closes the listener,
handlers and state lock. Abrupt termination relies on already committed snapshots.
Reverse proxy/TLS, request-body timeouts and process supervision are still required
for later authorized hosting. The standalone
[process journey](evidence/ticket42/process-green/README.md) passes on corrected
sourcea71e70f5. This is controlled JVM termination/restart, not power-loss proof.

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
FCM target or actual Firebase identifier replacement. Minimum source reviews at1d72826a report Standards0/Spec0.

Run the owned protocol/core checks with
`scripts/android-build :core:test :push-gateway:test`. CI includes the gateway
test task in source and focused/full builds, with its build file included in
dependency cache keys. No new hosted dispatch choice is added.

## Portable client reconciliation

`core` now has `PushSubscriptionClient`, with an external HTTP boundary and a
journal whose adapter must durably replace its contents. The Android adapter has controlled Android10/17 acceptance. The caller serializes access to one installation journal and
supplies fresh demand from durable wallet/backend/consent/permission state.

The client retains a random installation UUID and subscription ID/revocation
secret before a request can bind them. The journal contains public owner/origin
and installation retirement authority, never an outgoing wallet signing key.
If a bind acknowledgement is lost, a newly constructed client can retire the
uncertain old binding with a replacement wallet or no wallet. It retires the old
owner/origin before binding a different one. Opt-out persists a retiring phase before its request and retains authority until
the server confirms retirement. A lost retirement acknowledgement is completed
before a later same-wallet opt-in allocates a fresh subscription ID. A same-owner invocation renews with a fresh signed
challenge, current FID/backend/categories and six-day expiry. Fresh demand is
checked again before binding and after its acknowledgement; changed demand
requires another reconciliation. It does not roll back a user choice.

The controlled client journey uses actual gateway HTTP, a durable owned filesystem
journal and an intentionally dropped acknowledgement after server commit. The no-op
client first fails1m26s, old3PASS/new1FAIL at the missing-operation assertion; none
of its HTTP/recovery tails ran then. First minimum passes1m24s/corefresh114/gateway4.
Review found missing durable retirement intent, a silent return if consent changed
between reads, and an opt-out assertion that could observe an older binding. The
strengthened journey reproduces lost retirement acknowledgement as FAIL35s:
same-wallet opt-in gets410 instead of201. The correction persists retirement phase,
signals retry for fresh null demand with a retained binding, and requires final
opt-out to target the newest ID. Corrected source2a1d774d reviews Standards0/Spec0.
[Retained corrected build](evidence/ticket42/client-green/README.md) passes1m29s,
corefresh114/0/0 and gateway4/0/0. Final evidence reviews at724a8455 report Standards0/Spec0.
This is not Android Keystore, actual Firebase registration/rotation, cloud delivery
or a real client-process crash proof. Native SDK and device acceptance stay open.

## Android binding adapter

The adapter stores the portable journal in encrypted device-only `push.binding.v1`,
separate from wallet-specific feature state and SDK phase/FID storage. It serializes
journal use, reads existing wallet/backend/consent/Android permission without
creating a wallet, and checks coroutine cancellation around bounded HTTP calls.
Requests use configured HTTPS origin, no redirects,12second total call timeout
and a4096byte strict UTF-8 response bound. The optional test endpoint is restricted
to IPv4 loopback HTTP on a debug emulator and comes from instrumentation, never
push payloads or a user backend setting.

The registration worker reconciles after obtaining the registered FID. Opt-out
attempts gateway retirement independently before SDK initialization or draining
an outstanding Task, then still attempts SDK unregistration. Already-unregistered
SDK state no longer skips retained gateway cleanup. Current identity/consent and enabled Android social categories are
shared between SDK and gateway reads. Missing channels use their enabled default;
disabling both removes social demand. Final local publication checks remain. Failures remain retryable background work
and never roll back wallet, backend or consent changes.

The actual Android10 tracer at60d00271 fails0.205s at its missing first binding;
later lifecycle tails and prior11UI/3worker cases were not run. Its[retained RED](evidence/ticket42/native-binding-red/android10/README.md)
and source-build success are separate. Corrected source17fb7260 reviews Standards0/Spec0;
[both native runs and source/release/lint](evidence/ticket42/native-binding-green/README.md)
succeed. Final independent evidence reviews report Standards0/Spec0 after the handoff
wording correction9c0e9168. A controlled synthetic FID is not SDK registration,
actual identifier rotation or Google delivery.

## Bounded social Archive discovery

`GatewayArchive(gatewayIdentity, backend)` reads with the gateway's own wallet,
using only the two approved backend endpoints. Each request has a12second total
connection/authentication/service/read budget. Close it after use; reconstruct
on connection failure. The optional HTTP loopback override is for owned fixtures.
The gateway does not receive subscriber signing keys or broadcast transactions.

`recentSocial()` loads at most100 available unflagged RedSquare rows, including
roots and replies, without recipient filtering. Original signed content is decoded
using the shared feed reader. This head read can discover newly inserted backdated
posts but cannot guarantee exhaustive history or recovery beyond the bounded head.
Candidate eligibility, exact parent/name proof, durable cursors and explicit
outage/pruning/reorganization gaps remain continuous-observer work.

[Controlled evidence](evidence/ticket42/archive-green/README.md) at9967ff40:
gateway5/0/0, including the new0.888second journey completing both selector loops;
core task up-to-date, build50s. Independent source and evidence reviews report
Standards0/Spec0. This is not a current public-backend availability claim.

# Isolated notification gateway

Work in open[#42](https://github.com/robelene112/redsquare-android/issues/42), supporting
#32–34. The standalone Kotlin/JVM17 runtime implements durable subscription and
opaque event storage, signed public-chain/Archive observation, recipient resolution
and an external FCM sender adapter. It is not deployed. Configured Google/device
acceptance remains in#32 and its successors. Public interface:
`PushGateway(stateDirectory, publicOrigin)` with `start()`, `port`, `close()`;
optional port defaults to0 and all listeners remain IPv4 loopback.

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
Retirement records have7day expiry. Expired records are pruned on mutation and by a startup/60second maintenance task
while the process is running. Physical removal can lag expiry by one interval
plus current journal work. Stopped processes do not clean files; startup catches
up. This does not manage independent backups, logs or filesystem snapshots.
HTTP request-body timeouts and operational process/hosting supervision remain
separate hosting requirements. No permanent history is promised.

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
Exact candidate/parent availability is checked before reply queuing. Durable chain
cursors and recent coverage warnings are described below.

[Controlled evidence](evidence/ticket42/archive-green/README.md) at9967ff40:
gateway5/0/0, including the new0.888second journey completing both selector loops;
core task up-to-date, build50s. Independent source and evidence reviews report
Standards0/Spec0. This is not a current public-backend availability claim.

## External FCM sender adapter

`FcmSender(project, credentials)` sends data-only version/wake to the current
FID at the Google HTTPS endpoint. It returns acceptance with the actual message
name, a retry delay, structured UNREGISTERED or terminal HTTP rejection. The
caller must persist the same logical wake and own bounded retries, backoff/jitter,
expiry and conditional retirement of the exact target generation. Acceptance is
not a delivery receipt. The callable gateway pass now persists pending events and retry deadlines;
standalone observation is explicitly enabled as described below.

OAuth metadata is requested afresh per attempt. Expiry and whole-second Android
TTL are checked after credential retrieval. Payloads contain no wallet, backend,
content or notification template. Send HTTP has5second connect/15second read/
20second call timeouts, no redirects and a64KiB response bound. Credential retrieval
uses the supplied Google SDK provider in one owned daemon task. Caller wait is
at most12seconds per attempt; a timed-out task is retained and drained on retry
rather than multiplied. ADC discovery is lazy inside that task. Cancellation
cannot guarantee an arbitrary provider terminates: one provider task may remain
until it returns, but it cannot mutate gateway state or perform the FCM send.
Normal attempts request fresh metadata after the previous task is consumed. Retry-After is honored,429 has at least60seconds and
other server/network failures at least10seconds. Successful bodies need a valid
message name; only an explicit structured404 marks a target unregistered.

`FcmSender.applicationDefault(project)` is an explicit operator factory for
externally provisioned Application Default Credentials with the messaging scope.
It is not called by the current standalone command or owned tests. The target
project must be supplied separately from the credential project. Authorized ADC,
FCM API availability, target-project cloudmessaging.messages.create permission
and a corresponding configured registered FID remain external inputs. Do not
place credentials in the APK/repository. See the sourced[API observation](research/FCM_GATEWAY_SENDER_2026_10_17.md).

[Controlled sender evidence](evidence/ticket42/sender-green/README.md) verifies
owned HTTP with synthetic credentials; it does not establish Google delivery.
The original no-op failure, first-minimum failure and fixture compile failure
remain retained separately. No production gateway is deployed.


## Durable social pass and resolver

`PushGateway.poll(archive, sender)` serializes passes for the Archive's explicit
approved backend. A head row is only a discovery hint: candidate and parent are
read exactly from that selected Archive before eligibility and durable queueing.
Self-authored posts and signed timestamps outside the seven-day window are
excluded. Reply-to-owned-parent and explicit public-key mention eligibility use
the subscription's current categories. Registrar-backed username eligibility now uses exact signed active Registry
proof on the selected connection, with[controlled acceptance](evidence/ticket42/name-green/README.md). Original content is never included in the FCM request or event file.

Events contain public owner/backend/signature/category, subscription ID, opaque
wake, expiry and send/retry status. References resolve for at most24hours, further
bounded by subscription expiry and signed post age. Dedup records remain until
the signed post is seven days old, preventing repeated head reads after resolution
expiry from recreating a wake. Up to4096 events share the existing4MiB snapshot;
capacity failure leaves the pass unacknowledged. Older version1 snapshots without
an events map are loaded with an empty map; older binaries cannot read the new
map, so do not downgrade against this snapshot.

POST `/v1/wakes/resolve` accepts only `{version:1,wake}`. X-Saito-Owner and
X-Saito-Signature authenticate the existing Android recipient-proof text against
the canonical full resolver URL. Correct recipients receive only the exact
version/wallet/backend/signature reference. Wrong recipients return401;
missing references404; expired or no-longer-current subscriptions410. Resolution
does not trust a caller-provided backend or contain post text.

Each send rereads durable current demand, and a delayed result applies only to
the same FID/binding generation. Retry state is retained with bounded exponential
backoff, jitter and the sender's minimum delay. A crash after cloud acceptance
but before durable completion can resend the same wake; device dedup remains
required. Controlled[reply evidence](evidence/ticket42/delivery-green/README.md)
passes both selector loops. [Lost acknowledgement recovery](evidence/ticket42/delivery-retry-green/README.md)
also passes both selectors. Live target renewal during a send and the continuous
runtime still need their own evidence; registered-name acceptance is linked below.


## Name-proof availability and observer status

Exact Archive candidate/parent evidence is collected before optional Registry
queries. Username mentions use the registrar-signed active ownership record from
the selected peer; only the exact provisional dispatcher fallback is ignored
while awaiting its Registry callback. Production uses the fixed Saito registrar.
The published unsafe fixture authority is allowed only with the owned loopback
endpoint and explicit fixture flag, never as a third production backend.

An empty name response means no currently established ownership. Missing service,
unreadable proof, timeout or over50 distinct name tokens in a candidate marks
name eligibility unavailable while preserving independently verified replies and
public-key mentions. Thread interruption still stops the pass. A Registry socket
disconnect cannot discard candidate/parent evidence already collected first.
No claim is made that this specific disconnect interleaving has been fixture-tested.

POST `/v1/observer/status` accepts `{version:1,backend}` for either approved backend
and returns version/backend/cursor/gap/gaps. The legacy latest gap contains reason,
since, lastAt and expiresAt. `gaps` retains one recent warning per supported
reason, so bootstrap history loss remains visible beside a later reorganization.
A warning expires seven days after its latest observation; repeating that same
reason preserves its original since-time while it remains retained. These are
recent coverage warnings, not a complete event audit or proof of a current outage.
Older latest-only snapshots retain only the warning they actually contained;
missing historical warnings are never invented. Expired records are filtered
from status and pruned on mutation. Periodic physical cleanup now runs on startup and every60seconds while running.
Name lookup failures use `name-proof-unavailable`; observer coverage adds the
chain/bootstrap/archive reasons described below.


[Name acceptance](evidence/ticket42/name-green/README.md) completes both approved
backend selector loops using owned peers and the existing independent registrar
fixture. Empty current ownership suppresses the username wake, valid current proof
permits it, and unavailable name evidence leaves the owned-parent reply working.
It retains the prior same-wake/restart/opt-out journey. This does not establish
live public Registry availability or the source-reviewed disconnect interleaving.


## Chain references and full-block reader

`GatewayChain(identity, backend)` shares the authenticated transport implementation
with Archive access, using a separate owned connection. `range(cursor)` bootstraps
with the source-supported recent SPV window, then requests Full continuation from
the explicit processed cursor. The111byte request contains zero extra keys. Chain
responses have no request ID, so one may be outstanding; an uncertain outcome
retires that connection. Authentication/response budget is12seconds.

A range contains at most128 references. The reader verifies header bounds, strictly
increasing heights, serializer-supported golden flags and matching final tip
identity when heights coincide. Height gaps remain visible. Empty bounds describe
the advertised tip, which does not alone prove the caller has processed that tip.
The ancestor full hash and a durable local journal must govern later reorg choices;
a fork-ID hint is not ancestry proof.

`block(reference)` fetches one full block over the selected backend's HTTP origin,
with no redirects,20second call timeout and8MiB limit. The existing shared parser
checks creator/header, framing and Merkle content; height/hash/timestamp must match
the exact reference. SPV relevance and Full transaction-count fields never justify
skipping a block. Callers fetch/process blocks individually to bound memory.
These checks establish supported block identity, not consensus admission/finality.

The callable observer now persists candidate outcomes before a processed cursor.
Reorg recovery and autonomous processing are implemented later in the checkpoints below.
Its[controlled chain evidence](evidence/ticket42/chain-green/README.md) passes
both selector loops; no exhaustive-history claim. Source review requires matching
final tip identity, with the conflict guard reviewed rather than separately fixture-tested.


## Callable durable observer

`PushGateway.observe(chain, archive, sender)` requires the same approved backend
on both readers. It verifies full-block originals, locates supported signed social
candidate IDs and verifies their current selected-Archive availability and parent/
name evidence before queuing. Candidate outcomes and the processed block cursor
commit together in one atomic snapshot, before any outgoing delivery. An
interrupted commit cannot retain an orphan event beyond the processed cursor.
All exact Archive candidate/parent reads for a block precede optional name queries.
Input lists are bounded by the65,536 full-block transaction limit; Archive head
and exact-row request limits stay100 and1 respectively.

The optional chains snapshot map stores height/hash/fork ID and at most128 recent
points per backend. HTTP observer status includes this processed cursor, distinct
from the peer's advertised tip. The first supported recent bootstrap records
`bootstrap-history-unavailable` when earlier history is not processed. Missing
ranges or an unproven empty response record `chain-history-unavailable`; unavailable
chain/full-block reads and unsupported ancestry have separate current reasons.
Advancement stops at unsupported ancestry or missing consecutive references.
The implementation can rewind only to a matching full height/hash already in
that retained history, trimming abandoned points and saving a reorganization
warning before replacement processing. The verified first block's predecessor
is explicitly retained as `processed:false`: its hash is authenticated by that
header, but its block has not been processed. Unknown ancestors stop advancement.
This bounded journal cannot recover an arbitrary old fork. Social-event source
provenance is retained with each chain-derived event. Rewinding atomically marks
pending descendants withdrawn; their resolver returns410 and they are not sent.
An ordinary Archive head read cannot reactivate them. Verified re-inclusion can
restore the same logical wake without extending its original expiry. Already
accepted sends cannot be recalled. The[controlled social path](evidence/ticket42/chain-social-green/README.md)
passes both approved selectors with a hidden Archive head, exact reply/parent
verification before FCM503, durable200 before reorg, then410/no-send across restart
beyond the retry deadline. Exactaadad53f build PASS1m46s/gateway10/0/0; source
reviews Standards0/Spec0. Interrupted commit and re-inclusion remain source-reviewed;
no actual Google/device delivery or consensus/finality acceptance.

Gap status includes the latest warning and retained recent warnings per reason,
with reason-specific since-times. It is not a complete historical audit.
The current non-social fixture establishes durable position and actual Full
continuation after a server-instance restart. It does not establish chain-triggered
social delivery, interrupted cursor writes or continuous operation. The[non-social reorganization journey](evidence/ticket42/reorg-green/README.md)
passes both selectors, including a positive HTTP retained-anchor precondition,
replacement block processing and fresh-instance Full continuation. Source3e9a3784
reviews Standards0/Spec0; owned build PASS1m42s/gateway9/0/0. This is cursor
recovery only, with the social-event withdrawal limitation above.

## Explicit autonomous operation and lifecycle

The six-argument command above runs HTTP and retention only. To enable observation,
append `--observe --project PROJECT_ID`, replacing PROJECT_ID with the authorized
Firebase project identifier. This starts one completion-based30second loop per
approved backend, serialized at gateway processing. Each pass reconstructs and
closes its own selected chain/Archive readers. It does not switch subscribers
between backends. The gateway creates an ephemeral, unfunded signing identity
in process memory; it rotates on restart and never uses a subscriber secret.

The operator must separately provision supported Application Default Credentials
with the messaging scope and `cloudmessaging.messages.create` on that target
project, matching the app's configured Firebase project/FID contract. Credentials
stay outside the repository/APK. This documentation does not create accounts,
grant permissions, configure the owner app, authorize hosting or claim Google
acceptance. Default HTTP mode never loads ADC.

A processing round reads at most8 full blocks and stops before starting another
block after90seconds. Exact Archive proof collection has a45second budget checked
between requests, plus one in-flight request's12second deadline. Optional names
are cached within a proof pass and limited to50 distinct lookups across it; missing
budget records unavailable name eligibility while preserving exact reply evidence.
Delivery examines at most32 due events and stops before another attempt after
30seconds, plus one in-flight authorization/send. These are work limits, not a
promise to recover arbitrary historical bursts. Unprocessed cursor continuation
and retained coverage warnings remain explicit. Head backfill only runs after
chain ancestry is reconciled; it cannot bypass a stopped chain pass.

Shutdown first signals every observer and cancels shared sending, then awaits
observer termination before closing HTTP/state. Owned chain/Archive calls are
cancelled. Cleanup attempts all stages and restores interruption. A valid pending
wake remains retryable during shutdown. Provider termination and every exceptional
shutdown interleaving are source-reviewed limits, not claimed device/cloud tests.

Current autonomous tracer is under correction/review. It requires verified
re-inclusion to restore the same wake, a second owned FCM attempt and another Full
range request. Initial no-op RED and the superseded first minimum are retained;
no whole42 acceptance is implied by those earlier records.

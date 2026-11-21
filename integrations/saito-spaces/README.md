# Spaces service adapter

Source-stage, opt-in integration through139 and the in-progress140. Nothing here installs
or activates a production node. The ordinary public app remains beta142.

Schema5 adds indexed message pages, with each original and its latest edit
returned as separately signed receipts. An authenticated page advertises
`message_history=true`; older services keep the raw-content read path. Upgrades
backfill routing metadata in bounded batches and retain original envelopes and
receipts byte for byte. This source change does not activate a node.

Schema4 adds a public profile index and authenticated discovery, including
owner-signed listing changes. It advertises `spaces-directory-v1` alongside the
existing `spaces-v1` capability. Public reading/joining, richer invitation rules,
independent channels and durable starter creation have owned source checks;
native137-139 evidence retains its recorded scope. New140 conversation controls
remain in progress; actual node activation requires its separate operating gate.

A matching service explicitly reporting readiness feature `unread-v1` additionally
advertises `spaces-unread-v1`. An older schema4 service without that feature keeps
its base/directory capabilities. The client coordinator must check the optional
capability before requesting summaries. Fresh caller/scope-bound reads summarize
up to fifty authorized channels, counting actual other-author message records.
They exclude key/history grants and expose no private text. Complete numeric
totals remain unknown while an eligible channel summary is missing. These are
cached observations, separate from personal mention/answer notifications.

## Compatibility and data contract

The Android client signs version1 `Spaces` events and fresh challenge/read RPCs.
The authenticated Saito binary peer transports each exact original. This adapter
uses the maintained `Transaction.serialize_to_web(app)` and callback object path;
it does not construct a replacement user transaction. The JVM service verifies
the actual wallet signature, recipients, scope, role, control order, epoch and
operation identity. Only its signed durable receipt confirms acceptance.

The existing node peer key is the service identity. The adapter advertises
its capabilities only after its local service reports that same key and a compatible schema4 or schema5.
Provisioning that signing identity is a separate operator-authorized secret
handoff. Do not copy node options, keys or private backups into this repository,
command arguments, CI artifacts or logs. No fallback/new wallet is generated.

The service listens only on IPv4 loopback, with bounded HTTP workers, request
sizes and read pages. The adapter accepts only the configured loopback HTTP
origin, refuses redirects and bounds responses. Signed private reads use a
60-second, caller/scope-bound, single-use challenge. Opening an invitation only
permits its limited preview; explicit joining queues a request, not membership.

SQLite stores signed controls, pending join requests, encrypted private content and
terminal rejection receipts. Private names, text and archive-key grants are
ciphertext. Explicit public profiles provide public name/description metadata;
their listing state comes from the owner's accepted signed control. The service
does not turn a private profile into a public directory entry. Public routing metadata includes identities, community/channel
IDs, membership, timing, epochs, positions and payload sizes. This service does
not receive device MLS state or private archive secrets. It does not store
community files or implement a storage provider. Existing public/social payload
limits are unchanged; only explicit Spaces service receipts/pages have their
separate bounded allowance.

A receipt is immutable and byte-exact on repeat delivery. A terminal rejection
fences its original permanently, including after a later state change. Clients
keep attempted originals until a verified acceptance or rejection. A rejected
MLS proposal restores its persisted cancelled branch before a fresh proposal.
An uncertain callback never authorizes an automatic new signed operation.

## Executable rehearsal

Pinned source/runtime pairs are Saito commit
`0db0062d873369dc013046ed77c97410b5683b64` with JS/WASM0.3.15 and commit
`9a4ca7e9442eb5e872168ced361ce741d7d139d7` with JS/WASM0.3.2. The latter is the
maintained owner-runtime pin, not a fresh production package observation.
`test/interop.cjs` verifies named source hashes and each exact source and JS/WASM input
before executing the actual transaction serializer, module template/dispatcher and
server callback against the loopback JVM service. Its wallet getter, unavailable
filesystem and unused browser boundaries are explicit adapters. This is not a
complete node bootstrap. No compatibility with a different deployed runtime is
claimed until its actual source/package observation and rehearsal are recorded.

Run the bounded HTTP adapter contracts with Node24.13.0:

```sh
node --test integrations/saito-spaces/test/bridge.test.cjs
./gradlew :core:test :spaces-service:test :spaces-service:writeHostFixtureClasspath
```

The test-only `SpacesInteropHost` takes a new owned fixture directory and uses
only published unsafe test identities. It writes the actual original, loopback
port and source-stage observations there. `interop.cjs` takes the verified
primary-source directory, exact installed reference-runtime directory and that
fixture directory. The JVM then verifies one durable creation, original bytes
and the actual returned receipt. The separate `SpacesNativeHost` and
`scripts/spaces-native-journey` exercise real Android Activity and distinct-PID
recovery through an owned binary peer. No fixture enters the service or APK.

## Deployment and migration, separate authorization required

Before activation, verify the current Saito operations entry point and observed
node runtime. Build this exact reviewed source using Java17. Prepare the generated
`spaces-service` application distribution, these adapter files and a prior source
release. Record source/artifact hashes and permissions. Do not infer node paths
from an old transcript or restart unrelated modules.

The service requires `SPACES_STATE_DIR`, `SPACES_IDENTITY_FILE` and
`SPACES_LISTEN_PORT`. The identity file is owner-only flat JSON with version1,
`publicKey` and `privateKey`, validated against its actual key pair. The state
path must be on the operator-selected persistent disk, writable only by the
service user, outside the node's web root. Verify the mount, free space, ownership,
backup destination and service account before any live write.

An isolated fresh start and a copy of existing state must pass before changing
node module configuration. With writers stopped, copy the complete SQLite
family consistently; retain the original and a streaming hash manifest. Never
copy a live main database alone while WAL writes continue. Rehearse against that
copy with the intended service identity. Schema1 adds content storage in2;
schema2 adds rejection receipts and indexed recipient/kind metadata in3, in one
atomic transaction. Upgrading3 to4 adds the public profile index, with bounded
100-row genesis backfill and unchanged accepted original/receipt bytes.
Metadata backfill is batched, preserving original/receipt
bytes. Unsupported versions, unversioned data and a different service identity
fail before a destructive schema or journal-mode change. Upgrading4 to5 adds message and mutation-target indexes with bounded100-row
metadata backfill. Reopening5 is idempotent; signed originals and receipts stay
unchanged. A schema4 binary cannot open5 and must not replace it over later writes.

Only after readiness, migration preservation and isolated signed write/read pass,
configure `SAITO_SPACES_SERVICE_URL=http://127.0.0.1:<chosen-port>` for the node,
install `spaces.js` plus `bridge.cjs` in its `mods/spaces/` directory and explicitly
include the module through that runtime's maintained configuration procedure.
Restarting/configuring the actual node requires the owner's separate approval.
Use the canonical node readiness checks for HTTP, wallet and fresh chain state;
a running PID or service readiness alone does not prove all three. Confirm actual
peer advertisement and an authorized fresh write independently of readiness.

## Write-preserving rollback

Do not erase or replace accepted state to roll back source. Disable the optional
Spaces advertisement/adapter using the maintained node deployment procedure;
leave the current database and signing identity intact. An unavailable service
keeps device conversations/drafts and original queues recoverable.

A prior service may restart only when it supports the current schema and all
accepted receipt/content formats. Schema3 binaries cannot reopen4. Prefer a
previous compatible4 build or keep Spaces disabled while repairing it. Restoring
a pre-migration database is a distinct recovery decision requiring reconciliation
of every write accepted since that copy. The migration fixture verifies original
bytes and receipts across migration and re-entry; it is not permission to replace
live state. No database down-migration, automatic node activation, provider billing
or community file hosting is performed by these sources.

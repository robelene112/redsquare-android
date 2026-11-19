# Spaces protocol and persistence, version1

Implementation contract for137 under134. Native integration must wait for the
executable contracts below. This document does not establish deployed service
availability, functional UI acceptance or completed Chat migration.

## Transport and authority

The existing wallet signs ordinary Saito transactions. A version1 Spaces body
has `module=Spaces`, `request=event`, `version=1`, a random256-bit `space_id`,
an immutable random256-bit `operation_id`, its event kind and kind-specific
fields. Authenticate the actual original with `SaitoEnvelope` before reading
an actor or content. Display names never grant authority. The transaction signer
is the actor. Retrying an uncertain operation uses the original signed bytes.

The selected authenticated peer must explicitly advertise `spaces-v1`.
Generic Archive save has neither an acceptance acknowledgment nor the required
membership/order contract. Existing Relay can carry signed messages but cannot
stand in for durable admission. Old nodes remain usable for existing modules;
Spaces shows unavailable instead of switching nodes or claiming success.

Control events form one linear, signed predecessor chain per Space. Creation
has no predecessor and establishes exactly one creator-owner. A control includes
the previous control transaction ID. Admission, removal and channel-key changes
are committed together. The receiving service performs a durable compare-and-swap
on that head after evaluating the actor against the preceding accepted state.
Clients independently replay the same authorization rules. Timestamps are display
data, never authority ordering. A conflicting head requires reconciliation,
not selecting the greatest timestamp. Duplicate IDs return the original result.
An operation ID cannot acquire a second accepted body.

Control receipts use `stream=control` and a zero-based control position. Their
signed `event` field retains the exact original Saito envelope. Ordinary native
events have the creator output plus the selected service output, deduplicated;
these are routing metadata, not content-access keys. Client receipt verification
requires the actual authenticated service signer and matching Space identity.

Content references an accepted control head, channel and key epoch. Contributions
require current access. The service gives accepted events monotonically increasing
positions per channel for bounded pagination, independently of their timestamps.
Control replay is separate from content paging, so opening a large channel never
requires replaying its entire message history. Exact signed
originals remain available for reconciliation. Read authentication includes a
fresh service challenge, caller identity and request scope. Public reading has
its own explicitly public route. A private link alone neither enrolls a member
nor delivers keys. Private preview information is deliberately shared by its
bearer invitation, followed by explicit Join and any required rules acceptance.

The service may observe opaque Space/channel/operation IDs, public identity and
membership/routing metadata, event kinds, epochs, lengths and timing. Encryption
does not promise anonymity or conceal these relationships. Private message,
article, file and reaction contents are never public transaction plaintext.
Private channel names and descriptions are encrypted metadata; an unauthorized
client cannot get their plaintext from the channel list.

### Initial creation and invitation control fields

Creation has `kind=create`, `visibility`, `channel_id`, `epoch=0`, encrypted
`metadata` and `devices`, the credential-to-public-MLS-signer map containing
exactly one device belonging to the creating wallet. It has no `control_id`.
Subsequent controls reference the accepted predecessor in `control_id`.

An authorized owner/admin's `kind=invite` stores `invite_key` (a public Saito
capability key) and `expires_at`. The invitation ID is its original transaction
signature. A bearer keeps the corresponding private capability locally in the
invitation fragment. It is not an MLS identity or an archive/history key.
Invitation expiry is evaluated against the service's signed acceptance time,
not the applicant's transaction timestamp; timestamps never order controls.

Explicit Join creates a guest-signed `kind=join` event with `channel_id`,
`control_id=invite_id`, `invite_id`, `key_package` and `invite_proof`. The package
object contains `wallet`, `device`, `package`, `signer` and `signature`, the
existing wallet-signed package binding. The capability signs these exact UTF8
bytes, without a final newline:

`SaitoSpacesJoin/v1:<groupId>:<inviteId>:<guestWallet>:<joinOperationId>:<packageWalletSignature>`

An existing authorized member prepares the actual MLS admission automatically
after observing that valid explicit request. Its `kind=admit` control includes
the original `join` envelope, `channel_id`, next `epoch`, `commit`, `welcome`
and exact resulting `devices`. The service and clients verify the original
guest signature, wallet/package binding, bearer proof, still-valid invitation,
preceding role and epoch, and original operation uniqueness. Admission adds a
member role, never a second owner. The crypto adapter additionally verifies the
actual MLS leaf/signers before committing or transmitting candidate state.
Until this accepted control and usable keys arrive, the app must show the real
pending join/key state. Opening or reading the invitation cannot enroll anyone.
Initial admission of a new wallet is distinct from replacing an existing
member's device; recovery needs its explicit device update contract.

## Maintained group encryption

Use [OpenMLS0.9.0](https://github.com/openmls/openmls/releases/tag/openmls-v0.9.0),
release source `3a3e35de3feeca8f6605143c464d5452ae584d43`, with the RustCrypto
provider0.6.0 and basic credentials0.6.0. Cargo.lock pins all transitive inputs.
No OpenMLS draft/test-utils features or private library fields are used. Only
RFC9420 suite1, X25519/AES128GCM/SHA256/Ed25519, is selected; the provider's
transitive feature selections remain recorded by Cargo.
This MLS signing identity is separate from the existing Saito wallet key.

The credential is `SaitoSpaces/v1:<Saito public key>:<random256-bit device ID>`.
The group ID is `SaitoSpaces/v1/<service key>/<Space ID>/<channel ID>`.
The wallet signs its device key-package binding, including channel/group scope
and package digest. Check that binding and the MLS credential against admission;
never accept an arbitrary credential label as a wallet identity. Key packages
are validated by the library and consumed once. Each channel has its own group
and epochs, including private channels inside a public Space. Owner/admin access
is represented by admitted cryptographic membership, not a UI-only exception.
The application additionally rejects package lifetimes outside OpenMLS's public
acceptable range; a currently valid, unbounded package is not admissible.

The actual MLS signer for every leaf is compared with its wallet-signed binding.
A valid wallet signature over a falsely labelled package is not sufficient.
Binding and group verification must complete before a returned candidate state
or Welcome is committed or transmitted. Restore before admission retains the
original public package and binding instead of generating replacement keys.

OpenMLS handles key generation, authenticated Welcome, commit processing,
membership changes, application-message authentication and exporter derivation.
Keep all calls behind the owned native `spaces-mls` interface. The UI does not
implement MLS or run crypto/disk work during composition. Rust1.100.0 is pinned;
Android ABI, API29 minimum and16KiB ELF alignment must be checked on the actual
libraries before app acceptance. Host success alone does not prove Android JNI.

## Available history and future exclusion

Private retained records use standard AES256-GCM with a random96-bit nonce and
a32-byte per-channel/epoch key from the MLS exporter, label
`SaitoSpacesArchiveV1`, context the exact group ID. This is application use of
the [RFC9420 exporter](https://www.rfc-editor.org/rfc/rfc9420.html#section-8.5),
not a new group key agreement. Associated data binds protocol version, Space,
channel, epoch, actor, operation and content kind. The outer Saito signature
also binds the exact ciphertext. Do not derive identity from successful AEAD
decryption alone: all authorized epoch members share the archive key.

The retained-history keyring deliberately preserves keys for available history.
Do not claim forward deletion of already received records or their keys. A new
member receives the available historical keys through an authenticated MLS
application message after its accepted admission. Historical-key bundles are
channel-scoped and admitted-member-only. They never contain another device's
MLS signer, private leaf key or full MLS snapshot. Missing historical keys are
shown as unavailable history, not empty content or an endless loader.

Leaving/removal/ban ends contribution rights and advances affected channel MLS
epochs before further private writes. A former member's retained archive keys
can read old records but cannot derive subsequent exporter keys. Actual future
content and independently held old-client state must demonstrate this, rather
than a cache-clear assertion. Pending writes at an obsolete epoch require an
observed rejection before rebuilding; uncertainty never authorizes a duplicate.

## Local durability and recovery

The engine exchanges opaque versioned serialized state, never global mutable
handles. Every operation loads a fresh provider and uses public OpenMLS storage
and group-loading interfaces. Values retain their self-describing library JSON;
the outer snapshot records version1 and `openmls-0.9.0`. Unknown versions or
missing signer/group state require recovery. They cannot create a replacement
identity silently. Persist each account/node/channel separately and atomically
with its original outbox operation before transmission. Unaccepted candidate
state remains distinct from the last accepted state.

Android owns authenticated encryption at rest with its existing device Keystore
and durable file/database conventions. Never put snapshots, keys or plaintext
in logs, public receipts, crash diagnostics or network storage. Persist applied
event IDs/read cursors alongside content and key changes. A fresh process must
recover the original operation, rather than sign/send a replacement. Background
callbacks retain their exact account/node/Space/channel generation.

The future guided backup includes identity and available archive keys, separately
from any claim of recovered server history. Active MLS sender state must not be
cloned onto two simultaneously operating devices. A replacement device uses a
fresh bound key package and an explicit membership/device update; until that
completes, show the actual missing-access state. Restoring the Saito identity
preserves owner authority but does not manufacture missing group/history keys.

## Existing Chat compatibility

Existing direct Chat uses its current ECDH/AES-CBC envelope with the outer Saito
signature. Existing named group Chat carries plaintext. Neither is relabelled
as the new MLS protocol. Ticket141 adds authenticated capability negotiation,
an explicit encrypted-group upgrade and guided backup/recovery, preserving old
readable history. Older clients must get an unsupported/upgrade explanation;
there is no silent plaintext downgrade. Existing signed Chat transactions and
wallet exports remain valid. Spaces keys never enter the legacy Chat payload.

## Service source and activation

A new service is required for the durable ordering/access contract. Supply its
source, isolated two-client rehearsal, schema version/migration re-entry checks,
readiness and write-preserving rollback instructions before requesting production
activation. Do not mutate the running Saito node, advertise a service it does not
provide, start file hosting or claim deployed compatibility from source tests.
Message storage does not automatically select a community file provider.

## Executable acceptance at the agreed seams

The owned Spaces application interface is the principal seam. Independently
received signed events, actual MLS ciphertext and durable state must prove:
explicit create/join; two identities and fresh-process reload; one owner;
denial; lost acknowledgment; duplicate/reversed controls; wrong account/node
callbacks; admission with available history; removed-member future exclusion;
truthful missing keys/service; no secret network plaintext and no duplicate
operation. Use independent fixture decisions instead of copying production
reducers into expected results. Native Activity/OS journeys prove approved
navigationB, input, visible state and real process loss. Contract tests alone
do not prove the final UI, Android/runtime persistence or a deployed provider.

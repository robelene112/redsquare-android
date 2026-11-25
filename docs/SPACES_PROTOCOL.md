# Spaces protocol and persistence, version1

## Accepted #140 conversations

The client and owned service use envelope version 1 and persistent schema 5.
Native conversations, edits, reactions, pins, and attention passed on Android 10
and 17. The independent-peer and process evidence is in the
[#140 acceptance record](evidence/ticket140/final-acceptance.json).
Earlier sections retain their explicitly stated stage and version scopes.

Accepted `content` and `public-content` records now distinguish `message`,
`thread-reply`, `message-edit` and `message-delete`. Every change retains its
own immutable operation, wallet signature, original bytes and service receipt.
An exact accepted or rejected original returns its existing disposition on
retry, including after restart. No retry creates a replacement signature.

Replies identify an accepted top-level parent with `target_message_id` and
`target_position`. Changes identify the actual accepted base at that same pair.
For edits/deletions of a reply, `thread_root_id` must match that base's real
parent. The service checks the actual stored base, independently of client
labels. Authors may edit; authors and current staff may remove. A new staff or
membership term cannot revive undelivered moderation from an earlier term.
Removal retains the original position as a placeholder and prevents later edits
from bringing its text back. Available earlier copies are not recalled.

Announcement publishing remains staff-only. Members may contribute to its
threads, including editing/removing their own replies, while current membership,
channel access, key epoch and archive permissions remain mandatory. Private
reply/change payloads use the existing archive AEAD. Target IDs/positions enter
the authenticated data; reply changes additionally bind their thread root.
Ordinary archive ciphertext retains its previous authenticated-data format.

Fresh caller/nonce-bound read pages advertise `message_history=true`. The
`messages` scope contains Space/channel, numeric cursor, `direction=before|after`
and `limit=1..50`, with optional `thread_id`. A before cursor is exclusive;
an after cursor is inclusive. Latest requests use the maximum signed64-bit
cursor. Main pages contain top-level bases; thread pages contain only replies
for that accepted parent. Pages return at most50 bases and2MiB, independently
of how many edits/key grants occupy the raw channel stream.

Each item carries its signed base receipt under `message`, with optional signed
`edit` and `deletion` receipts. Main items also provide an observed `reply_count`.
Items are ordered by accepted position. `next_cursor`, `has_more` and channel
`content_end` describe this page; they do not guarantee a permanent complete
archive. Each original, receipt, target, author and historical permission is
verified before the bounded page transaction commits. Older summaries/window
endpoints cannot overwrite newer observed state.

The raw `content` stream remains available for incremental replay and older
services. Clients keep separate root/thread indexes, pending work, drafts and
pixel anchors. Byte-limited latest pages cannot merge disconnected cached
windows. Refreshing an older visible window follows bounded forward pages for
at most50 bases, retaining both the channel and thread reading positions.

Schema5 adds target metadata and message/version indexes. Existing records are
backfilled in100-row batches; original envelopes and signed receipts remain
byte-exact. The adapter accepts compatible schema4/5 services. These source
changes do not install a module, expose a private channel, activate file hosting
or update the ordinary public beta.

### Bounded reaction histories and own choices

`message-reaction` signs the author's complete desired set (at most16 distinct
choices,32UTF8bytes each). Private selections use the existing archive AEAD,
including the actual base ID/position and optional thread root. Valid announcement
members may react without receiving top-level publishing permission.

Caller/nonce-bound pages advertise `message_reactions`. Opted-in
`reaction_history` reads add per-base latest-author pages of at most50 receipts,
`reaction_version`, `reaction_next_cursor` and `reactions_complete`. Scoped
`reaction_cursors` resume a fixed `through` observation. The client performs at
most two follow-up requests per read and resumes remaining hydration later.
Counts remain unavailable while the accepted group projection is incomplete.

Each opted-in item also carries nullable `own_reaction`, independently selected
for the authenticated caller within that same observation. A cold installation
therefore retains all its choices before the whole group is loaded. The client
validates its author, target, thread and position and advances own-choice knowledge
monotonically. Incomplete own knowledge or unavailable own historical keys prevent
full-set replacement. Genuine stale pages cannot undo later author revisions.

Protected local projections apply each actor revision once. Rendering reads
bounded count/own-choice pages instead of decrypting the entire actor history.
Own selections remain visible/removable even beyond50 emoji types. Retained
unreadable receipts are repaired fairly in bounded batches after exact historical
keys arrive, including subsequent offline reads. No repair signs another reaction.

### Local conversation selection and queued changes

A version1 local thread hint contains only channel, accepted message ID and
position. Rendering resolves that identity against the cached signed original
and current accepted changes. It never trusts a stored copy of parent text.
Explicit channel selection clears the thread hint. Drafts, history windows and
pixel anchors remain independent per channel/thread. Previously accepted local
copies and writing remain recoverable after access ends. Navigation within the
already selected cached scope never queues admission when access is absent;
new contributions still require current permission and keys.

A per-message latest-change pointer references the exact queued edit/deletion.
Projection distinguishes unattempted, attempted, cancelled and rejected intent and retains
requested edit text locally. Older acceptance/rejection only updates that pointer
if it still references the same operation. No delayed result can clear a newer
requested replacement.

Cancellation of an unattempted root/reply or edit/deletion and the durable delivery-attempt marker
are serialized in one store transaction. Successful cancellation removes pending
and operation pointers but retains the immutable original with a local cancelled
marker, separate from an authenticated node rejection. Received-message helpers
exclude cancelled originals. Current writing is never overwritten; restoring the
text is an explicit native action. Saved edits remain viewable/copyable after permissions change.
Conversation-owned menus, editor, confirmations and reaction details remain outside
the recycled list; active overlays suspend automatic scrolling/read marking.
Attempted/uncertain sends cannot be withdrawn
through this local cancellation path and retain exact-original recovery.

### Pins and channel attention

A `message-pin` is a staff decision on an accepted base ID and position.
Reply pins bind the original thread parent. Private decisions use archive AEAD.
The service verifies staff authority for the supplied control term. A local
pending change retains the exact signed original and permits cancellation
before its first attempt. The authenticated pin stream pages the latest signed
decisions independently of the visible message window. The client projects
active references from those decisions. Opening a reference resolves the exact
root or reply and keeps separate drafts for the channel and thread.

The protected receiving client derives personal unread activity from exact
wallet mentions and the authenticated parent of a reply. It checks
`@everyone` against staff authority at acceptance. It keeps separate read
positions for a channel and each thread. A negative root-only summary leaves
unknown thread activity unresolved. Only a complete local projection publishes
a personal count. An ordinary unread dot can appear while the count is pending.
The native list catches up through bounded signed reads outside the composition
thread. Preference defaults are Mentions and replies, with per-channel All and
Silent and a separate recipient setting for `@everyone`. The preferences persist
locally. Delivery through Activity and links is tracked in the later slice.

## In-progress138 public directory contract

Envelope version1 remains distinct from persistent schema4. Public creation may
include a bounded typed public_profile; private profile text remains encrypted.
Only explicit public profiles enter the new public_spaces index. Fresh signed
caller/query/cursor-bound directory requests return bounded actual creator and
listing receipts. A set-listed control requires the owner, a public Space and
the current control predecessor; it updates accepted control and index atomically.
A node-supplied flag cannot replace the creator's signed opt-out. A subsequent
accepted owner-signed listing receipt can deliberately change that initial choice.
Discovery cache retention is independently bounded at200 profiles and never
removes joined-community data, keys, originals or drafts.

Migration to4 adds a bounded100-row public-genesis index backfill without
rewriting accepted originals/receipts. New source readiness reports4; the adapter
also advertises spaces-directory-v1. Richer invitation/rule flows, native138,
updated deployed-runtime compatibility and node/provider activation remain separate.
See [the138 packet](evidence/ticket138/README.md).

## In-progress138 public reading and direct admission

Channel audience is explicit in channel_visibility at creation. Missing fields
remain PRIVATE, including older public Space roots; discovery never changes
channel confidentiality. A PUBLIC channel requires a PUBLIC containing Space.
Public visitors may read signed control history and explicitly public channel
content through the existing fresh authenticated read protocol. Opening creates
no MLS device and no My Spaces entry. Local visitor metadata remains separate
from accepted membership.

A direct join-public control references the current predecessor and public
channel. The receiver grants only MEMBER, without waiting for an owner or
pretending a private MLS admission occurred. The client retains the exact signed
original across lost responses and activates membership only after an accepted
control. Intentional public-content messages carry signed bounded plaintext;
only accepted members can contribute. Channel epoch/generation and authorizing
control remain checked. PRIVATE channels retain MLS/archive/device checks and
reject public-content, even inside a public Space. Per-channel private admission
and later visibility changes remain part of the following governance work.

The owned SQLite/client regression covers independent visitor reading, no
implicit join, refused pre-join sending, exact-original recovery after a lost
accepted join response, retained draft, independent received public reply and
no administrator grant. An independently constructed older public root accepts
real encrypted private content, rejects plaintext fallback and refuses public
opening of its unmarked channel. Full244-core/33-service checks pass. This does
not establish native138 or actual updated framework/deployed-node acceptance.

## In-progress138 invitation profiles and reviewed consent

New invite controls have preview_format=profile-v1 and a bound channel_id.
The capability AES-GCM preview carries the bounded profile, including description
and optional house rules; private profiles remain ciphertext. Parsed profile
JSON is capped at24KiB. Legacy controls without preview_format retain their
name-only presentation and original general-channel binding.

A private invitation's rules token is its actual signed invitation ID. That
identity binds its encrypted preview and channel without exposing a digest of
private rules. Public tokens are derived only from intentionally public rules,
and public invite acceptance must match the canonical profile. Join APIs require
the explicitly reviewed token when rules are present, rather than a Boolean
that could acknowledge different rules after a fresh read. Signed private join
records carry their invitation token, checked by the receiver. Public invitation
joining uses direct public admission and never waits for an MLS owner callback.
Expiry is presented from accepted invitation authority, with the seven-day
creation default retained. Full native presentation remains pending.

The complete owned JVM check passes244 core and37 service cases. Actual RED/GREEN
and original adverse/corrective review reports are in the138 packet. This does
not claim completed UI, live node compatibility or release acceptance.

## In-progress138 revocation and authenticated read dispositions

An owner/admin revoke-invite control references the current predecessor and an
active invitation ID. Acceptance removes that capability, never admitted members
or their keys. Pending request pages advance past revoked/expired capabilities.
Replacement uses a deliberate fresh invitation while preserving applicant writing.

An authenticated read refusal returns request=denied with reason=unavailable or
expired, bound to the fresh request/caller/scope/nonce and signed by the selected
service. It carries no items or content cursor. Only a valid capability proof can
learn that its invitation expired. Invalid caller/nonces remain protocol errors.
The client verifies all bindings before exposing typed access state.

A locally unexpired invitation can be revoked after its bearer has already been
admitted but before the Welcome arrives. After a trusted invitation denial only,
the client makes a fresh wallet-member control read. Actual accepted membership,
not the revoked link, authorizes recovery. A late exact join-request receipt can
then clear its original outbox row without changing membership. No fallback runs
for malformed/foreign/network responses. Actual REDs, receipt/crypto/draft recovery
and complete244-core/40-service results are retained in the138 packet. UI/native
and updated framework/runtime acceptance remain pending.

## In-progress138 bounded inline images

Profiles now allow an optional canonical inline JPEG with a16KiB byte bound.
Only the declared data URI format, JPEG framing and canonical base64 are admitted;
Android rendering remains responsible for decoding/fallback. No remote URL is
fetched merely to display a private community image. Full serialized profile
capacity grows from24KiB to32KiB to fit image plus maximum description/rules;
existing smaller profiles remain valid. The genesis metadata limit is unchanged.
Private preview encryption uses the same bounded profile and existing AES-GCM.

Directory pages omit listing when identical to creation, using the client's
existing authenticated-root fallback. Byte budgets count the actually included
receipt values. Actual large-image paging, cached profiles and private preview
round-trip pass, with complete244-core/41-service checks and corrected source
reviews. Native image picking/rendering and full138 acceptance remain pending.

## In-progress138 public channel state

A PUBLIC Space owner/admin may create-channel at the current control predecessor,
with a new ID/generation, epoch0 and bounded versioned channel_profile (name,
kind, category). Public records need no secret/device membership to read, while
publication still requires actual Space membership and channel policy. An
announcement channel additionally requires OWNER/ADMIN on the receiving boundary
and in the owned client. Role assignment and private extra channels follow later.

General's existing scope/key/content/draft storage remains in place. Parent
selected_channel defaults to General; extra public scopes keep separate local
state/content/drafts. Refresh/history capture channel identity before awaiting.
A late acknowledgment routes by its authenticated original, never the currently
selected channel. Fresh content/key pages must match request, receipt and original
channel before any cache/cursor mutation; accept independently checks the signed
original's channel. Malformed/misbound pages leave usable content and writing.

Per-channel pending/rejected indexes point at exact global originals, avoiding
another channel's queue hiding immediate feedback. A versioned, bounded-page
backfill reconstructs disposable pointers without rewriting signed work. Actual
prior-layout recovery retains all52 delivered originals and the independent draft.
Complete244-core/47-service checks and corrected source reviews pass; private
extra channels, starter creation and native138 remain pending.

## Earlier client and service integration under137

Source now includes the account-owned native coordinator and navigationB. It
retains exact signed originals, verifies terminal rejection receipts, saves the
cancelled MLS branch for rejected proposals, and keeps newly typed writing
independent of held network responses. Same-epoch queued content accepts later
unrelated controls; the receipt records its actual authorizing head. Invitation
expiry and replacement are explicit. Join and community lists traverse bounded
pages without dropping eligible rows. New members obtain targeted archive-key
grants before opening a bounded recent conversation page; older pages are lazy.
Archive grants use the existing epoch exporter/AES format and remain readable
when another admission advances the operational MLS epoch. An independent MLS
reference decrypts the actual grant and earlier archive ciphertext.

The optional [node adapter](../integrations/saito-spaces/README.md) defines the
source-stage transport, schema3 migration, readiness and write-preserving
rollback contract. Actual pinned Saito0db/JS-WASM0.3.15 and9a/JS-WASM0.3.2
framework rehearsals pass with explicit runtime adapters. Complete native10/17
v8 parents at7bebfc716 verify private-community UI and distinct-process recovery;
see the [evidence packet](evidence/ticket137/README.md) for exact scopes, originals
and subsequent client-boundary verification. Deployed-runtime observation and
actual node/provider activation remain separate gates.

Implementation contract for137 under134. The executable contracts below
preceded functional integration. This document alone does not establish deployed
service availability, broader Spaces feature acceptance or completed Chat migration.

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

Private RPC reads use wallet-signed `request=challenge` and `request=read`, with
`request_id`, a matching `scope`, and the returned one-use `nonce` on Read. Scope
currently contains `space_id`, `stream=control|join` and a nonnegative `cursor`.
The service binds nonce, caller and exact scope for60s; a different caller cannot
consume another caller's nonce. Control reading requires membership; pending
join requests are visible only to owner/admin. Expired/already admitted requests
are not presented as active join work, while originals remain durably stored.
Signed pages include caller, scope, nonce, items and next_cursor. Pages bound
receipts to2MiB/50 scanned positions; the Spaces-only reply serializer has a4MiB
limit because it nests original signed envelopes. Ordinary event/social limits
and wire signatures stay unchanged. These replies are application RPC, not
blockchain broadcasts or publication acceptance.

The service may observe opaque Space/channel/operation IDs, public identity and
membership/routing metadata, event kinds, epochs, lengths and timing. Encryption
does not promise anonymity or conceal these relationships. Private message,
article, file and reaction contents are never public transaction plaintext.
Private channel names and descriptions are encrypted metadata; an unauthorized
client cannot get their plaintext from the channel list.

Article channels accept `content_kind=article` at the root. The versioned
document holds either a title, subtitle and Markdown body or an exact public
Stack author and signed revision address. A public Stack share is verified at
the source before it is queued. The channel stores the reference. Native
private articles use the same authenticated channel encryption as private
messages. They have no Stack publication side effect.

An accepted edit is a new signed event addressed to the original publication
position and ID. Only its author can replace a native article. Removal uses
the normal author or current administrator authority. The reader verifies both
receipts and keeps the original publication address. New and per-article edit
drafts occupy separate encrypted device records. Queuing consumes only the
reviewed draft bytes in the same transaction that stores the original.

An article link identifies the node, service, Space, channel and publication.
The exact signed read uses `stream=messages` with `article_id` and returns up
to one root and its current changes. This lookup leaves the channel timeline
cursor alone. Cached results use the last verified read scope. An authenticated
denial takes precedence over the cache.

A write restriction is a signed owner or administrator control scoped to a
member and optionally one channel. The service checks the restriction at
receipt time. Clients replay that check using the signed acceptance timestamp
and control position. Reading and key delivery remain available. A previously
queued original that references an earlier contribution term cannot become
accepted after expiry or lift.

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
The group ID is
`SaitoSpaces/v1/<service key>/<Space ID>/<channel ID>/<key generation>`.
The generation is a256-bit hexadecimal identifier, initially all zeroes. A
replacement operational group uses a fresh generation, keeping channel identity
and reading anchors stable while separating its epoch0 from older epoch0 keys.
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

Membership changes retain both the prospective merged group and the serialized
pending group before merge, together with the original control. Only a
definitive rejection permits OpenMLS's public `clear_pending_commit` operation
on that pending state. This preserves library-owned state rather than rewinding
an emitted sender to an old snapshot. Admission/removal expose an explicit
`afterRejection` branch for the coordinator to persist before transmission;
uncertainty never selects it. A history message advances its sender state even
when a later service rejection prevents archival admission: persist that
advanced state before transmission and keep it, independently of send status.

The Kotlin key snapshot is version2 and explicitly records this generation;
the inner OpenMLS provider snapshot remains version1. Earlier source-only
Kotlin snapshots are not silently reinterpreted as a different group context.
No functional Spaces state exists in the ordinary beta142 predecessor.

`SpaceHistoryKeys` is a separate archive-only format: version1, account owner,
exact group scope and available epoch keys. It can decrypt retained signed
records but has no operational sender or membership-mutation interface. Its
raw bytes are secret component data. The guided wallet export seals them and
the Saito wallet together in a versioned Argon2id and AES-GCM file before
writing to the Android document provider. The same credential confirmation
used by wallet export gates this backup. Import authenticates the file and
stores archive keys only after the user confirms the wallet restore.

Android owns authenticated encryption at rest with its existing device Keystore
and durable file/database conventions. Never put snapshots, keys or plaintext
in logs, public receipts, crash diagnostics or network storage. Persist applied
event IDs/read cursors alongside content and key changes. A fresh process must
recover the original operation, rather than sign/send a replacement. Background
callbacks retain their exact account/node/Space/channel generation.

The backup includes identity and available epoch keys. The recovered history
reader opens signed records the authenticated service can still supply to that
wallet. It can read earlier messages using the restored keys before another
participant activates the new device. Active sender state and queued outbound
originals stay on the device that created them.

Device replacement signs a request under the wallet that owns the current
credential. For Spaces, an eligible owner or administrator with current
channel keys approves it. In a protected Chat, a different current member
with keys can approve it. The accepted MLS commit retires that exact old
credential and admits the fresh bound key package in the same epoch change.
The requesting device persists its fresh key material and exact signed
original before transport. A retried original reconciles against the
service's recorded disposition. Once replacement is accepted, the old device
can still read its retained history. Subsequent messages use the new epoch.

## Existing Chat compatibility

Existing direct Chat uses its current ECDH/AES-CBC envelope with the outer
Saito signature. Existing named group Chat carries plaintext. Both remain
readable in their original views. The Android transition from those views
remembers the original conversation on the active device. Existing group
administrators can select which members to invite into the protected group.

Protected Chat uses the same pinned MLS construction as private Spaces. The
client verifies the node capability from a signed challenge response. Each
recipient proves wallet ownership, client protocol version and a bound MLS
key package on the signed join. Addressed invitations are valid only for the
selected wallet. Protected writing becomes available when both the sender and
another member have current MLS credentials. Existing signed Chat
transactions and wallet exports remain valid. The protected composer sends
only through MLS.

## Service source and activation

A new service is required for the durable ordering/access contract. Supply its
source, isolated two-client rehearsal, schema version/migration re-entry checks,
readiness and write-preserving rollback instructions before requesting production
activation. Do not mutate the running Saito node, advertise a service it does not
provide, start file hosting or claim deployed compatibility from source tests.
Message storage does not automatically select a community file provider.

## File channels and provider leases

The pinned Spaces authority signs the storage capability and accepts an
owner's approval of a provider's signed origin, identity, byte quota, price
and deadline. The provider idempotently commits those exact terms in a
separate durable ledger. A separate owner control introduces the provider
lease to authenticated peers. Renewal extends the term at the same provider
and counts existing files against the renewed quota.

Each object has a random identity and a fresh AES-256-GCM key. A bounded
sequence of authenticated chunks binds the payload to the Spaces authority,
Space, channel, object and length. The channel record carries the filename,
media type, file key and plaintext length. A private channel uses its existing
encrypted archive record for the descriptor. Provider permissions contain the
opaque object identity, ciphertext length and digest. They carry no participant
identity, filename, media type or key. The payload uses 64 KiB length buckets,
so the provider sees a range of possible plaintext lengths.

The node issues upload permissions after a fresh signed challenge and checks
the upload's original contribution term again when accepting publication.
Only an accepted, undeleted file is eligible for a download permission.
The provider asks the pinned authority to recheck its permission before
streaming, again during long transfers, and before the upload is committed.
These signed exchanges identify the permission without identifying its holder.
Devices verify the complete ciphertext and recheck the download's membership
and channel key term before keeping or exposing a remote copy. Deleting a
shared record authorizes separate idempotent removal at the provider. Removing
a device copy changes local storage alone.

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

### Message pins

The optional signed `message_pins` capability enables staff-only `message-pin`
content with exact target ID/position and optional thread root. Private choices
use the existing archive cipher with target-bound associated data. Labels are
`pinned` and `unpinned`; a role removal/regrant cannot authorize an old decision.
At most one local undecided pin operation per target is queued. Its immutable
original can be cancelled before the durable attempt marker, or retried unchanged
after an uncertain reply. A refusal restores accepted state and remains visible.

Opt-in message pages request `pin_history`; the separate `pins` stream pages a
fixed-through latest-decision snapshot independently of the live window. Each
reference includes the authenticated base, current versions and actual parent
for a reply. Clients validate originals before atomic commit and retain monotonic
progress. Protected active-only indexes avoid paging through removed references;
missing historical keys remain explicit and are repaired locally in bounded
batches. Importing a reference does not move the live conversation window.


### Conversation attention

The receiving client derives personal attention from authenticated message text
and the authenticated thread parent. A direct mention uses an exact
`@<wallet-public-key>` token. An `@everyone` token can trigger group attention
only when the signed author had administrator authority for that accepted record.
Private content keeps this text inside the existing encrypted record.

Root and thread read positions are independent. Attention catches up through
bounded authenticated content pages, leaving unread counts unknown when the
necessary records or keys are unavailable. An ordinary unread dot can still use
a positive top-level head. Channel preferences are local to the receiving account,
node and device and do not change the unread counts.

# Red Square Android

Shared language for the native Red Square app and its first-release scope.

## Language

**Core social experience**:
The agreed release baseline: feed, threads, posting and replies, likes and
reposts, profiles, following, in-app notifications, and wallet creation,
import and export. Additional features can be included through separate decisions.

**Saito module**:
A Saito application or capability, such as Red Square, Chat or Stack, that the
Android app can support through a compatible native feature.

**Saito username**:
A registered name associated with a Saito wallet's public identity, displayed
in place of its anonymous name. It is distinct from the profile bio or banner.

**Display name**:
A readable label reported for a public key by a name directory, or retained
from an earlier lookup. It helps people recognise an account but does not
change that account’s public-key identity or establish current name ownership.

**Registered username ownership**:
An active association between a Saito username and a public key established
by a valid registration-authority signature. It is distinct from a directory’s
display-name report and from the name someone writes in a post.

**Available chat history**:
Messages retained on the current device or recoverable from the selected
backend or compatible peers, which the user's identity can read. Availability
does not imply permanent storage or complete recovery on another device.

**Node block observation**:
A selected node's reported block height and block time, paired with when the app
checked that information. It describes that node's report, rather than a global
network lag estimate or the completeness of post history.


## Spaces

**Space**:
A community with its own members and channels for conversations and shared
content. A person can participate in multiple Spaces.
_Avoid_: Server (when referring to the community).

**Space channel**:
A named area within a Space for a particular conversation or shared content.
Its type is text chat, live voice/video, articles or files.

**Private Space channel**:
A channel whose content is restricted to the members admitted to that channel,
including when its containing Space is public. The owner and administrators
control admission and have access to every channel in their Space. Members
without access do not see it in the channel list.

**Space announcement channel**:
A channel in which only the Space owner and administrators may publish
announcements. Members may reply in threads attached to those announcements.

**Archived Space channel**:
A channel that remains readable to people with access but accepts no new
contributions. The owner or administrators may reopen it.

**Space chat thread**:
A conversation attached to a particular chat message, opened through that
message's replies while keeping the main channel readable.

**Space category**:
An optional named grouping of channels within a Space.

**Public Space**:
A Space whose public channels can be viewed without membership. It may also
contain private channels restricted to admitted members.

**Space directory**:
A searchable listing for discovering public Spaces. An unlisted public Space
remains publicly readable through its link.

**Private Space**:
A Space whose shared content is restricted to its members. Ending membership
blocks access to new private content but cannot recall previously received content.

**Space member**:
A Saito identity whose membership of a Space is active. Posting requires
membership; reading public content does not.

**Space owner**:
The single person responsible for a Space, with authority to appoint its
administrators. Administrators cannot remove or ban the owner; the creator is
the first owner.

**Space ownership transfer**:
A handover of the owner role to another Space member, confirmed by both the
current owner and the successor. For private channels, consent starts temporary
read access and actual receiving-device key preparation; a separate readiness
acknowledgement completes the role change. Cancelling ends temporary access to
new private content but cannot recall already received copies.

**Space administrator**:
A person appointed by the Space owner to share the community's day-to-day
administration.

**Space removal**:
Ending a member's current membership. The same identity may rejoin through the
Space's normal admission rules.

**Space timeout**:
A temporary restriction on contributions within Spaces until a stated end
time, scoped to one channel or the whole Space. It covers messages, reactions,
articles, uploads and speaking while
preserving reading and listening access. It does not restrict independent
activity in other modules, such as writing a standalone Stack article.

**Space ban**:
Ending a Saito identity's membership and preventing it from rejoining until the
ban is lifted.

**Space invitation**:
A link issued by a Space owner or administrator that allows its holder to join
a private Space directly while the invitation remains valid. An invitation can
expire or be revoked without removing people who have already joined.

**Public Space link**:
A permanent reference to a public Space at a specified node and service identity.
It carries no invitation capability and grants no membership. An unlisted public
Space remains readable through it. Opening a reference for another node requires
an explicit node choice.

## Fileshare

**Retained received file**:
A completed file received through Chat and kept inside the app on this device
until deliberately removed. Saving a separate copy elsewhere does not remove it.

**Locally unavailable received file**:
A received transfer whose copy is no longer available for opening or saving
from the app, while the transfer's chat record remains.

## Vault and Store

**Vault file**:
A file retained by a Vault storage service whose retrieval is governed by an
access contract. The remote file, its access key and a local copy are distinct.

**Vault access key**:
A Saito NFT associated with a Vault file's access contract. Holding a key grants
the rights established by that contract, not an unconditional promise of access.

**Rental master key**:
A Vault key used to establish compatible temporary access under a rental
contract. It is distinct from the renter's received entitlement.

**Rental entitlement**:
A temporary right to access a specified file under the rental contract's
identity, duration and other conditions. Expiry does not recall downloaded copies.

**Offline copy**:
A file copy deliberately retained on the current device for disconnected use.
Removing it does not remove the remotely retained Vault file or transfer a key.

**Store listing**:
An offer of an identified item or access right, with a seller, price and terms.
An offer's existence does not establish its current availability or fulfilment.

**Store order**:
The record of a buyer's intended purchase or rental and its payment and
fulfilment progress. A submitted payment is distinct from delivered ownership
or access.

**Provider payment instructions**:
A provider's quoted currency, network, amount, destination and expiry for one
Store order. Receiving instructions does not establish payment or delivery.

**Store settlement payment**:
The Saito payment used by the Store to fulfil an order, funded by the provider
for an external-currency purchase while the buyer remains the asset's recipient.
A provider report is distinct from observed settlement and ownership.

**NFT holding observation**:
A selected node's report of an unspent NFT tuple and its current owner. The tuple
contains creator/quantity metadata, a normal owner/deposit slip and an identifier
metadata slip. It is a node observation, not independent consensus verification.

**NFT deposit**:
SAITO retained in the NFT's normal owner slip. It is distinct from the key's
quantity, a transaction fee and freely spendable wallet balance.

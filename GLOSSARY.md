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

**NFT holding observation**:
A selected node's report of an unspent NFT tuple and its current owner. The tuple
contains creator/quantity metadata, a normal owner/deposit slip and an identifier
metadata slip. It is a node observation, not independent consensus verification.

**NFT deposit**:
SAITO retained in the NFT's normal owner slip. It is distinct from the key's
quantity, a transaction fee and freely spendable wallet balance.

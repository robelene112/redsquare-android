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

# End-to-end protection for private Space content

The owner chose end-to-end encryption for private Space content so that routing
and storage operators cannot read it. The existing compatible group Chat protocol
carries plaintext message content (`core/src/main/kotlin/nl/davidengelen/redsquare/saito/GroupChat.kt`),
so restricting the interface to members alone cannot satisfy this requirement.
This accepts additional key-distribution and recovery complexity in exchange
for content privacy; existing Chat compatibility remains a separate boundary.

The owner also explicitly requires end-to-end protection for the existing Chat
experience, including one-to-one and group conversations. This is additional
agreed scope alongside private Spaces. Current encryption coverage, existing
history and interoperability with older clients must be examined during the
specification; this does not claim that the delivered Chat already meets the
new requirement.

Public Spaces may also contain private channels. Their content requires the
same end-to-end protection, restricted to the members admitted to that channel;
public Space membership alone does not grant access to it.

Joining a private Space grants access to its available earlier messages. The
key-sharing design must support this agreed history access; it does not promise
complete or permanent archive availability.

When a member leaves or is removed, access to subsequent private content ends.
Previously received messages and files cannot be guaranteed to disappear from
that person's devices or copies. Public Space content remains publicly readable.

The owner also requires guided backup and recovery of both the Saito identity
and the encryption keys needed for available encrypted history on a replacement
device. Restoring wallet identity alone must not be presented as proof that
history keys or archived content have been recovered. This is a recovery
requirement, not a claim that the current backup implementation covers these keys.

The private-community foundation in137 uses pinned OpenMLS0.9.0 for operational
group membership and exporter-derived archive encryption. This separates keys
needed to read available history from the operational sender state. Signed
receipts establish accepted membership; merely opening an invitation does not.
See the [implementation evidence](../evidence/ticket137/README.md) for the
executable library, persistence, Android and transport contracts and their limits.

The broader agreed work still includes private-channel governance, existing Chat
E2E compatibility and guided key backup/recovery in the subsequent Spaces tickets.
This foundation does not make legacy plaintext Chat private. Actual node/provider
activation remains separately authorized; storage of community files is not
enabled by this decision.

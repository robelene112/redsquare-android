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

Joining a private Space grants access to its available earlier messages. The
key-sharing design must support this agreed history access; it does not promise
complete or permanent archive availability.

The protocol, key management, metadata visibility and hosting
design are still open during the grill. This records the privacy requirement,
not an implemented capability or authorization to activate a node or provider.

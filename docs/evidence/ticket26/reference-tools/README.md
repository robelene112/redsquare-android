# Reproduce the public creation oracle

Uses public unsafe scalar 1/2/3/4 identities. Never fund them. The canonical tools
are retained here; they do not initialize Saito or read live wallet/configuration.

Copy the retained .cjs tools into an ignored scratch directory. Fetch `chat-A.js` and
`chat-B.js` from immutable public SaitoTech/saito `node/mods/chat/chat.js` revisions
A9a4ca7e and B214504ec. Exact revisions and source hashes are recorded in
`NATIVE_GROUP_CHAT_VERIFICATION.md` and the committed vector.

Install `saito-wasm@0.3.2`, `elliptic@6.6.1` and `base-58@0.0.1` under that
scratch directory's `dependencies`, using Node 24.13.0. Disable installation
scripts and use distinct empty user/global npm configuration files. The retained
package lock records exact public package integrity.

Mount the directory read-only at `/reference` in `node:24.13.0-alpine`. Disable
networking, use a read-only container root and a non-root UID, and limit the
container to 512 MiB and one CPU. Run `node /reference/group-reference.cjs`.

The generator extracts and compares both actual creation methods, executes them
against deterministic system-boundary fixtures, then serializes/signs with actual
WASM. Elliptic independently verifies signatures; WASM preimage/hash and
serialization checks must pass. The public output must match the core vector.

This proves constructor intent and wire compatibility. It does not prove live
backend admission, real client UI, all receiver authority rules or persistence.

The invitation extension also requires the pinned public Crypto wrapper at
`crypto.ts`, SHA256 61416839726c1877875062273fd704b37002f8f3a5d299e6c0883f5d24685683.
A fresh B-source fetch has the same hash. It extracts the actual UTF8 Base64
method and both actual generateChatGroupLink methods. Eight creator/member link
vectors include normal and alternate Unicode names; the alternate link metadata
is unverified and does not claim a signed rename. Some include raw '+'.
The signed creation and malformed-recipient vectors remain unchanged.

The join extension is `node /reference/join-reference.cjs`. It executes both
actual sendJoinGroupTransaction methods using an explicit admin derived from
known roles and a zero-payment wallet fixture whose outputs begin with the group,
then the sender. The signed request fields, group-first recipient and additional
admin/inviter recipients are independently serialized/signed by WASM and checked
with Elliptic. This is bounded fixture wire intent, not proof of arbitrary funded
wallet slip selection, live consensus admission or a complete web join session.
Creation/invitation vector output is unchanged by the recipient-order helper flag.

Join and self-leave share control-reference.cjs, retaining distinct entry scripts.
Run node /reference/leave-reference.cjs for self-leave. After extracting the shared
setup, both outputs were regenerated offline and compared byte-for-byte with the
committed join/leave corpora; both comparisons passed. No native source changed.

The message extension is node /reference/message-reference.cjs. It executes both
actual createChatTransaction methods with a two-member named group, zero-payment
self-first wallet outputs and a fixed public timestamp. Named-group messages are
plaintext even with two members. Original method text differs: B adds contact
registration inside the encrypted-DM branch. The comparison excludes exactly
that known addition; each original method executes unchanged, and the resulting
group envelopes/signatures are byte-identical. Initial whole-method equality
failed before producing a corpus; this was a reference assumption, not a product
failure. This oracle does not initialize Saito, admit live writes or test DM changes.

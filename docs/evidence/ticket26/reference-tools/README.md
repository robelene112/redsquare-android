# Reproduce the public creation oracle

Uses public unsafe scalar 1/2/3/4 identities. Never fund them. The canonical tools
are retained here; they do not initialize Saito or read live wallet/configuration.

Copy the two generators into an ignored scratch directory. Fetch `chat-A.js` and
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

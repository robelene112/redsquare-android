# Approved public Chat contract read

2026-10-13 fresh throwaway identities, actual TLS WebSockets, nonce-authenticated
handshakes. Both Owner and Official advertise Chat/Archive/Relay and accept
field3/limit50/created_earlier_than Archive loads, returning0 rows for unknown
fresh conversation IDs. No existing wallet keys, private options/databases or Chat payloads
were read/exported; fresh private keys stay local to handshake signing; no messages, saves or type4 broadcasts were sent. Public
peer identity/service metadata retained in two hashed observations. The repeat
explicitly encodes sorted public keys as UTF8 before BLAKE3; the initial read
is a diagnostic observation, not canonical group derivation evidence.

Canonical helper: build-support/references/private-chat-public-read.cjs. It
asserts the pinned pure WASM hash and uses named read-only dependency/runtime/
script mounts in a read-only, cap-drop, env-i container with public networking.
This proves current public service/load contracts, not Kotlin UI, available
nonempty private history, live forwarding/read receipt or chain inclusion.

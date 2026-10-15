# Native private photo interoperability

Consumes only the public unsafe envelope from run38720282977/exact8ce9d04a.
Its SHA256 is recorded by the crypto proof and linked by sanitizer proof. Actual
pinned browser ECDH/AES decrypts the native message; independent WASM/Elliptic
signature matches. The exact known photo wrapper/caption and participant routing
match. Actual pinned Chat.addTransactionToGroup accepts it for sender/recipient,
persists it and suppresses duplicate delivery. A/B receiver methods are asserted
byte-identical and source hashes are pinned.

Actual A/B Browser.sanitize plus inspected newline conversion produces the exact
caption and native normalized JPEG. Offline Chromium decodes12x8 RGB220/30/40
within5/channel. These are controlled native send and pure web receiver/DOM
boundaries, not live production writes or full initialized browser Chat admission.
Read-only named dependencies/source/public fixture mounts, no network, empty
environment, no capabilities and no Saito initialization. All assertions pass.
